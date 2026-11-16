package fr.hugoviaud.zwack;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;

/** Main-thread state. Only the configuration is persisted; broadcasting never auto-starts. */
final class SimulatorState {
    private static SimulatorState instance;
    static SimulatorState get(Context context) {
        if (instance == null) instance = new SimulatorState(context.getApplicationContext());
        return instance;
    }
    final SharedPreferences prefs;
    final ArrayList<Runnable> listeners = new ArrayList<>();
    int power, cadence, powerVariation, cadenceVariation, speed;
    boolean erg, running, advertising;
    int connections, subscriptions, emittedPower, emittedCadence;
    Integer ergTarget;
    String status = "Prêt à diffuser", bluetoothName = "ton téléphone";
    private SimulatorState(Context context) {
        prefs = context.getSharedPreferences("simulator", Context.MODE_PRIVATE);
        power = Protocol.clamp(prefs.getInt("power", 150), Protocol.MAX_POWER);
        cadence = Protocol.clamp(prefs.getInt("cadence", 90), Protocol.MAX_CADENCE);
        powerVariation = Protocol.clamp(prefs.getInt("powerVariation", 3), 100);
        cadenceVariation = Protocol.clamp(prefs.getInt("cadenceVariation", 1), 20);
        speed = Protocol.clamp(prefs.getInt("speed", 18), 60);
        erg = prefs.getBoolean("erg", false);
    }
    void save() {
        prefs.edit().putInt("power", power).putInt("cadence", cadence)
            .putInt("powerVariation", powerVariation).putInt("cadenceVariation", cadenceVariation)
            .putInt("speed", speed).putBoolean("erg", erg).apply();
        changed();
    }
    void changed() { for (Runnable listener : new ArrayList<>(listeners)) listener.run(); }
}
