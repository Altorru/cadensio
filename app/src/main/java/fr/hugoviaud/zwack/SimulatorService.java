package fr.hugoviaud.zwack;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.*;

public final class SimulatorService extends Service {
    static final String STOP = "fr.hugoviaud.zwack.STOP";
    static final String CHANNEL = "bluetooth_simulation";
    BlePeripheral peripheral;
    SimulatorState state;
    boolean failed;
    PowerManager.WakeLock wakeLock;
    @Override public void onCreate() {
        super.onCreate(); state = SimulatorState.get(this);
        NotificationChannel channel = new NotificationChannel(CHANNEL, UiText.translate(this, "Diffusion du capteur"), NotificationManager.IMPORTANCE_LOW);
        channel.setDescription(UiText.translate(this, "Permet de garder le capteur actif et de l’arrêter à tout moment."));
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && STOP.equals(intent.getAction())) { stopSelf(); return START_NOT_STICKY; }
        if (peripheral != null) return START_NOT_STICKY;
        try {
            if (Build.VERSION.SDK_INT >= 31 && (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED)) {
                fail("Autorise les appareils à proximité pour démarrer."); return START_NOT_STICKY;
            }
            Notification notification = notification();
            if (Build.VERSION.SDK_INT >= 29) startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
            else startForeground(1, notification);
            wakeLock = getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Cadensio:BleSimulator");
            wakeLock.acquire(6 * 60 * 60 * 1000L);
            state.running = true; state.status = "Démarrage…"; state.changed();
            peripheral = new BlePeripheral(this, state, this::fail);
            peripheral.start();
        } catch (RuntimeException error) {
            fail("Démarrage Bluetooth impossible. Vérifie les autorisations et réessaie.");
        }
        return START_NOT_STICKY;
    }
    void fail(String message) { failed = true; state.status = message; state.changed(); stopSelf(); }
    Notification notification() {
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent stop = PendingIntent.getService(this, 1, new Intent(this, SimulatorService.class).setAction(STOP), PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_notification).setContentTitle(UiText.translate(this, "Cadensio · capteur actif"))
            .setContentText(UiText.translate(this, "Diffusion Bluetooth · touche ici pour régler puissance et cadence"))
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
            .addAction(new Notification.Action.Builder(null, UiText.translate(this, "Arrêter"), stop).build()).build();
    }
    @Override public void onDestroy() {
        if (peripheral != null) peripheral.stop();
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        state.running = false; state.advertising = false;
        if (!failed) state.status = "Diffusion arrêtée";
        state.changed(); stopForeground(STOP_FOREGROUND_REMOVE); super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
