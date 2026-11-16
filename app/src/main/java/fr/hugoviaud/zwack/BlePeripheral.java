package fr.hugoviaud.zwack;

import android.annotation.SuppressLint;
import android.bluetooth.*;
import android.bluetooth.le.*;
import android.content.Context;
import android.os.*;
import java.util.*;
import java.util.function.Consumer;

/** BLE GATT peripheral. All callbacks and updates are serialized on the main thread. */
@SuppressLint("MissingPermission")
final class BlePeripheral {
    static final UUID FTMS = Protocol.uuid(0x1826), CPS = Protocol.uuid(0x1818), CSC = Protocol.uuid(0x1816);
    static final UUID BIKE = Protocol.uuid(0x2ad2), POWER = Protocol.uuid(0x2a63), CADENCE = Protocol.uuid(0x2a5b);
    static final UUID CONTROL = Protocol.uuid(0x2ad9), STATUS = Protocol.uuid(0x2ada), CCC = Protocol.uuid(0x2902);
    final Context context;
    final SimulatorState state;
    final Handler handler = new Handler(Looper.getMainLooper());
    final Consumer<String> onError;
    final Map<UUID, BluetoothGattCharacteristic> characteristics = new HashMap<>();
    final Map<UUID, byte[]> values = new HashMap<>();
    final Map<BluetoothDevice, Set<UUID>> subscribed = new HashMap<>();
    final Set<BluetoothDevice> connected = new HashSet<>();
    final ArrayDeque<BluetoothGattService> pendingServices = new ArrayDeque<>();
    final ArrayDeque<Notice> notices = new ArrayDeque<>();
    final Random random = new Random();
    final Protocol.Crank crank = new Protocol.Crank();
    BluetoothAdapter adapter;
    BluetoothGattServer server;
    BluetoothLeAdvertiser advertiser;
    BluetoothDevice controller;
    Notice inflight;
    boolean closed;
    final Runnable startupTimeout = this::startupTimedOut;
    void startupTimedOut() {
        if (!closed && !state.advertising) fail("Le Bluetooth ne répond pas. Arrête les autres diffusions et réessaie.");
    }
    final Runnable notificationTimeout = () -> {
        // Do not let a silent client block data or control replies for every other client.
        if (inflight != null) {
            BluetoothDevice device = inflight.device;
            inflight = null;
            notices.removeIf(n -> n.device.equals(device));
            try { if (server != null) server.cancelConnection(device); } catch (SecurityException ignored) { }
            drain();
        }
    };
    BlePeripheral(Context context, SimulatorState state, Consumer<String> onError) {
        this.context = context; this.state = state; this.onError = onError;
    }
    void post(Runnable action) { handler.post(() -> {
        if (!closed) try { action.run(); }
        catch (SecurityException error) { fail("Autorisation Bluetooth retirée. Relance la diffusion."); }
    }); }
    void start() {
        BluetoothManager manager = context.getSystemService(BluetoothManager.class);
        adapter = manager == null ? null : manager.getAdapter();
        if (adapter == null) { fail("Ce téléphone ne dispose pas de Bluetooth LE."); return; }
        if (!adapter.isEnabled()) { fail("Active le Bluetooth, puis réessaie."); return; }
        if (!adapter.isMultipleAdvertisementSupported() || adapter.getBluetoothLeAdvertiser() == null) {
            fail("Ce téléphone ne peut pas diffuser de capteur Bluetooth LE."); return;
        }
        String name = adapter.getName();
        state.bluetoothName = name == null || name.trim().isEmpty() ? "ton téléphone" : name;
        state.status = "Préparation du capteur…";
        handler.postDelayed(startupTimeout, 15000);
        advertiser = adapter.getBluetoothLeAdvertiser();
        server = manager.openGattServer(context, callback);
        if (server == null) { fail("Impossible d’ouvrir le serveur Bluetooth."); return; }
        BluetoothGattService ftms = service(FTMS);
        read(ftms, 0x2acc, Protocol.features());
        notifyCharacteristic(ftms, BIKE, false);
        notifyCharacteristic(ftms, STATUS, false);
        BluetoothGattCharacteristic control = new BluetoothGattCharacteristic(CONTROL,
            BluetoothGattCharacteristic.PROPERTY_WRITE | BluetoothGattCharacteristic.PROPERTY_INDICATE,
            BluetoothGattCharacteristic.PERMISSION_WRITE);
        control.addDescriptor(ccc());
        ftms.addCharacteristic(control); characteristics.put(CONTROL, control);
        read(ftms, 0x2ad8, Protocol.powerRange());
        BluetoothGattService cps = service(CPS);
        notifyCharacteristic(cps, POWER, false);
        read(cps, 0x2a65, Protocol.u32(8)); // Crank revolution data supported.
        read(cps, 0x2a5d, new byte[]{0});
        BluetoothGattService csc = service(CSC);
        notifyCharacteristic(csc, CADENCE, false);
        read(csc, 0x2a5c, Protocol.u16(2)); // Crank data only; no wheel sensor advertised.
        read(csc, 0x2a5d, new byte[]{0});
        pendingServices.addAll(Arrays.asList(ftms, cps, csc));
        addNextService();
    }
    BluetoothGattService service(UUID id) { return new BluetoothGattService(id, BluetoothGattService.SERVICE_TYPE_PRIMARY); }
    BluetoothGattDescriptor ccc() {
        return new BluetoothGattDescriptor(CCC, BluetoothGattDescriptor.PERMISSION_READ | BluetoothGattDescriptor.PERMISSION_WRITE);
    }
    void read(BluetoothGattService service, int id, byte[] value) {
        UUID uuid = Protocol.uuid(id);
        BluetoothGattCharacteristic c = new BluetoothGattCharacteristic(uuid,
            BluetoothGattCharacteristic.PROPERTY_READ, BluetoothGattCharacteristic.PERMISSION_READ);
        service.addCharacteristic(c); values.put(uuid, value);
    }
    void notifyCharacteristic(BluetoothGattService service, UUID id, boolean indicate) {
        BluetoothGattCharacteristic c = new BluetoothGattCharacteristic(id,
            indicate ? BluetoothGattCharacteristic.PROPERTY_INDICATE : BluetoothGattCharacteristic.PROPERTY_NOTIFY, 0);
        c.addDescriptor(ccc()); service.addCharacteristic(c); characteristics.put(id, c);
    }
    void addNextService() {
        if (pendingServices.isEmpty()) { advertise(true); return; }
        if (!server.addService(pendingServices.peek())) fail("Publication du service Bluetooth refusée.");
    }
    void advertise(boolean includeName) {
        AdvertiseSettings settings = new AdvertiseSettings.Builder().setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM).setConnectable(true).setTimeout(0).build();
        AdvertiseData data = new AdvertiseData.Builder().addServiceUuid(new ParcelUuid(FTMS))
            .addServiceUuid(new ParcelUuid(CPS)).addServiceUuid(new ParcelUuid(CSC))
            .addServiceData(new ParcelUuid(FTMS), new byte[]{1, 0x20, 0}).build();
        AdvertiseData scan = new AdvertiseData.Builder().setIncludeDeviceName(includeName).build();
        advertiser.startAdvertising(settings, data, scan, advertisingCallback);
    }
    final AdvertiseCallback advertisingCallback = new AdvertiseCallback() {
        @Override public void onStartSuccess(AdvertiseSettings settings) { post(() -> {
            handler.removeCallbacks(startupTimeout);
            state.advertising = true; state.status = "Diffusion active"; state.changed(); handler.post(tick);
        }); }
        @Override public void onStartFailure(int error) { post(() -> {
            if (error == ADVERTISE_FAILED_DATA_TOO_LARGE) {
                // Some phones have a Bluetooth name longer than the scan response allows.
                if (!retriedWithoutName) { retriedWithoutName = true; advertise(false); return; }
            }
            fail(switch (error) {
                case ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> "Bluetooth occupé : ferme les autres apps de diffusion.";
                case ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> "La diffusion Bluetooth LE n’est pas prise en charge.";
                case ADVERTISE_FAILED_ALREADY_STARTED -> "Une diffusion est déjà active. Arrête puis relance.";
                default -> "Impossible de diffuser en Bluetooth (code " + error + ").";
            });
        }); }
    };
    boolean retriedWithoutName;
    final BluetoothGattServerCallback callback = new BluetoothGattServerCallback() {
        @Override public void onServiceAdded(int status, BluetoothGattService service) { post(() -> {
            if (status != BluetoothGatt.GATT_SUCCESS) { fail("Erreur de service Bluetooth : " + status); return; }
            pendingServices.poll(); addNextService();
        }); }
        @Override public void onConnectionStateChange(BluetoothDevice device, int status, int newState) { post(() -> {
            if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                connected.add(device);
            } else {
                connected.remove(device); subscribed.remove(device);
                if (device.equals(controller)) { controller = null; state.ergTarget = null; }
                notices.removeIf(n -> n.device.equals(device));
                if (inflight != null && inflight.device.equals(device)) {
                    handler.removeCallbacks(notificationTimeout); inflight = null; drain();
                }
            }
            connectionState();
        }); }
        @Override public void onCharacteristicReadRequest(BluetoothDevice device, int requestId, int offset,
                                                         BluetoothGattCharacteristic characteristic) { post(() -> {
            byte[] data = values.get(characteristic.getUuid());
            respondRead(device, requestId, offset, data);
        }); }
        @Override public void onDescriptorReadRequest(BluetoothDevice device, int requestId, int offset,
                                                     BluetoothGattDescriptor descriptor) { post(() -> {
            UUID id = descriptor.getCharacteristic().getUuid();
            byte[] value = isSubscribed(device, id) ? (id.equals(CONTROL) ? new byte[]{2,0} : new byte[]{1,0}) : new byte[]{0,0};
            respondRead(device, requestId, offset, value);
        }); }
        @Override public void onDescriptorWriteRequest(BluetoothDevice device, int requestId, BluetoothGattDescriptor descriptor,
                                boolean prepared, boolean responseNeeded, int offset, byte[] value) { post(() -> {
            UUID id = descriptor.getCharacteristic().getUuid();
            int expected = id.equals(CONTROL) ? 2 : 1;
            int status = BluetoothGatt.GATT_SUCCESS;
            if (prepared) status = BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED;
            else if (offset != 0) status = BluetoothGatt.GATT_INVALID_OFFSET;
            else if (!descriptor.getUuid().equals(CCC) || value.length != 2 || value[1] != 0 || (value[0] != 0 && value[0] != expected))
                status = BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED;
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Set<UUID> set = subscribed.computeIfAbsent(device, d -> new HashSet<>());
                if (value[0] == 0) set.remove(id); else set.add(id);
                connectionState();
            }
            if (responseNeeded) server.sendResponse(device, requestId, status, offset, null);
        }); }
        @Override public void onCharacteristicWriteRequest(BluetoothDevice device, int requestId,
                                BluetoothGattCharacteristic characteristic, boolean prepared, boolean responseNeeded,
                                int offset, byte[] value) { post(() -> {
            int status = BluetoothGatt.GATT_SUCCESS;
            if (!characteristic.getUuid().equals(CONTROL) || prepared) status = BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED;
            else if (offset != 0) status = BluetoothGatt.GATT_INVALID_OFFSET;
            else if (value.length == 0) status = BluetoothGatt.GATT_INVALID_ATTRIBUTE_LENGTH;
            else if (!isSubscribed(device, CONTROL)) status = 0xfd; // CCC improperly configured.
            if (responseNeeded) server.sendResponse(device, requestId, status, offset, null);
            if (status == BluetoothGatt.GATT_SUCCESS) control(device, value);
        }); }
        @Override public void onExecuteWrite(BluetoothDevice device, int requestId, boolean execute) {
            post(() -> server.sendResponse(device, requestId, BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED, 0, null));
        }
        @Override public void onNotificationSent(BluetoothDevice device, int status) { post(() -> {
            if (inflight != null && inflight.device.equals(device)) {
                handler.removeCallbacks(notificationTimeout); inflight = null; drain();
            }
        }); }
    };
    void respondRead(BluetoothDevice device, int requestId, int offset, byte[] value) {
        int result = value == null ? BluetoothGatt.GATT_READ_NOT_PERMITTED
            : offset < 0 || offset > value.length ? BluetoothGatt.GATT_INVALID_OFFSET : BluetoothGatt.GATT_SUCCESS;
        server.sendResponse(device, requestId, result, offset,
            result == BluetoothGatt.GATT_SUCCESS ? Arrays.copyOfRange(value, offset, value.length) : null);
    }
    void connectionState() {
        state.connections = connected.size();
        state.subscriptions = (int) subscribed.values().stream().filter(s -> s.contains(BIKE) || s.contains(POWER) || s.contains(CADENCE)).count();
        state.changed();
    }
    boolean isSubscribed(BluetoothDevice device, UUID id) { return subscribed.getOrDefault(device, Collections.emptySet()).contains(id); }
    void control(BluetoothDevice device, byte[] data) {
        int opcode = data[0] & 0xff;
        int length = Protocol.expectedLength(opcode);
        int result = 1;
        byte[] status = null;
        if (length == -1) result = 2;
        else if (length != data.length) result = 3;
        else if (opcode == 0) {
            if (controller != null && !controller.equals(device)) result = 5; else controller = device;
        } else if (!device.equals(controller)) result = 5;
        else switch (opcode) {
            case 1 -> { state.ergTarget = null; controller = null; status = new byte[]{1}; }
            case 5 -> {
                int power = Protocol.target(data);
                if (power < 0 || power > Protocol.MAX_POWER) result = 3;
                else {
                    state.ergTarget = power;
                    if (state.erg) state.power = power;
                    status = new byte[]{8, data[1], data[2]};
                }
            }
            case 7 -> status = new byte[]{4};
            case 8 -> { if (data[1] != 1 && data[1] != 2) result = 3; else status = new byte[]{2, data[1]}; }
            case 0x11 -> { status = new byte[7]; status[0] = 0x12; System.arraycopy(data, 1, status, 1, 6); }
            default -> result = 2;
        }
        enqueue(device, CONTROL, Protocol.result(opcode, result), true);
        if (status != null) publish(STATUS, status);
        state.changed();
    }
    final Runnable tick = new Runnable() {
        @Override public void run() {
            if (closed) return;
            if (!adapter.isEnabled()) { fail("Bluetooth désactivé. Réactive-le puis relance la diffusion."); return; }
            state.emittedPower = Protocol.sample(state.power, state.powerVariation, Protocol.MAX_POWER, random);
            state.emittedCadence = Protocol.sample(state.cadence, state.cadenceVariation, Protocol.MAX_CADENCE, random);
            crank.update(SystemClock.elapsedRealtime(), state.emittedCadence);
            publish(BIKE, Protocol.indoorBike(state.emittedPower, state.emittedCadence, state.speed * 100));
            publish(POWER, Protocol.cyclingPower(state.emittedPower, crank.revolutions(), crank.lastEvent));
            publish(CADENCE, Protocol.cadence(crank.revolutions(), crank.lastEvent));
            state.changed(); handler.postDelayed(this, 1000);
        }
    };
    void publish(UUID characteristic, byte[] value) {
        for (BluetoothDevice device : new ArrayList<>(subscribed.keySet()))
            if (isSubscribed(device, characteristic)) enqueue(device, characteristic, value, false);
    }
    static final class Notice {
        final BluetoothDevice device;
        final UUID characteristic;
        final byte[] value;
        final boolean confirm;
        Notice(BluetoothDevice device, UUID characteristic, byte[] value, boolean confirm) {
            this.device = device; this.characteristic = characteristic; this.value = value; this.confirm = confirm;
        }
    }
    void enqueue(BluetoothDevice device, UUID characteristic, byte[] value, boolean confirm) {
        if (!confirm) notices.removeIf(n -> !n.confirm && n.device.equals(device) && n.characteristic.equals(characteristic));
        if (notices.size() >= 32) return;
        Notice notice = new Notice(device, characteristic, value, confirm);
        if (confirm) notices.addFirst(notice); else notices.addLast(notice);
        drain();
    }
    @SuppressWarnings("deprecation")
    void drain() {
        if (closed || inflight != null || server == null) return;
        while (!notices.isEmpty()) {
            Notice notice = notices.poll();
            if (!isSubscribed(notice.device, notice.characteristic)) continue;
            BluetoothGattCharacteristic c = characteristics.get(notice.characteristic);
            boolean sent;
            try {
                inflight = notice;
                if (Build.VERSION.SDK_INT >= 33) sent = server.notifyCharacteristicChanged(notice.device, c, notice.confirm, notice.value) == BluetoothStatusCodes.SUCCESS;
                else { c.setValue(notice.value); sent = server.notifyCharacteristicChanged(notice.device, c, notice.confirm); }
            } catch (SecurityException error) { inflight = null; fail("Autorisation Bluetooth retirée. Relance la diffusion."); return; }
            if (sent) { handler.postDelayed(notificationTimeout, 5000); return; }
            inflight = null;
        }
    }
    void fail(String message) { if (!closed) onError.accept(message); }
    void stop() {
        closed = true; handler.removeCallbacksAndMessages(null);
        try {
            if (advertiser != null) advertiser.stopAdvertising(advertisingCallback);
            if (server != null) {
                for (BluetoothDevice device : connected) server.cancelConnection(device);
                server.clearServices();
            }
        } catch (SecurityException ignored) { }
        finally {
            if (server != null) {
                try { server.close(); } catch (SecurityException ignored) { }
                server = null;
            }
        }
        notices.clear(); subscribed.clear(); connected.clear();
        state.running = false; state.advertising = false; state.connections = 0; state.subscriptions = 0;
        state.ergTarget = null; state.changed();
    }
}
