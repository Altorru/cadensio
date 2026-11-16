package fr.hugoviaud.zwack;

import android.content.Context;
import java.util.Map;

/** Localizes transport status without changing protocol/state identifiers. */
final class UiText {
    private UiText() { }
    static final Map<String, String> EN = new java.util.HashMap<>();
    static {
        EN.put("Prêt à diffuser", "Ready to broadcast");
        EN.put("Démarrage…", "Starting…");
        EN.put("Préparation du capteur…", "Preparing sensor…");
        EN.put("Diffusion active", "Broadcasting");
        EN.put("Diffusion arrêtée", "Broadcast stopped");
        EN.put("ton téléphone", "your phone");
        EN.put("Bluetooth LE indisponible sur cet appareil.", "Bluetooth LE is unavailable on this device.");
        EN.put("Démarrage impossible. Vérifie les autorisations Bluetooth.", "Unable to start. Check Bluetooth permissions.");
        EN.put("Autorisation « Appareils à proximité » requise.", "Nearby devices permission is required.");
        EN.put("Autoriser le Bluetooth", "Allow Bluetooth");
        EN.put("Cadensio a besoin de l’autorisation Appareils à proximité pour diffuser un capteur. Aucune localisation n’est demandée.", "Cadensio needs Nearby devices permission to broadcast a sensor. Location is not requested.");
        EN.put("Paramètres", "Settings"); EN.put("Plus tard", "Later");
        EN.put("Active le Bluetooth pour diffuser.", "Turn Bluetooth on to broadcast.");
        EN.put("Diffusion du capteur", "Sensor broadcasting");
        EN.put("Permet de garder le capteur actif et de l’arrêter à tout moment.", "Keeps the sensor active and lets you stop it at any time.");
        EN.put("Autorise les appareils à proximité pour démarrer.", "Allow Nearby devices to start.");
        EN.put("Démarrage Bluetooth impossible. Vérifie les autorisations et réessaie.", "Unable to start Bluetooth. Check permissions and try again.");
        EN.put("Cadensio · capteur actif", "Cadensio · sensor active");
        EN.put("Diffusion Bluetooth · touche ici pour régler puissance et cadence", "Broadcasting over Bluetooth · tap to adjust power and cadence");
        EN.put("Arrêter", "Stop");
        EN.put("Le Bluetooth ne répond pas. Arrête les autres diffusions et réessaie.", "Bluetooth is not responding. Stop other broadcasts and retry.");
        EN.put("Autorisation Bluetooth retirée. Relance la diffusion.", "Bluetooth permission removed. Restart broadcasting.");
        EN.put("Ce téléphone ne dispose pas de Bluetooth LE.", "This phone does not support Bluetooth LE.");
        EN.put("Active le Bluetooth, puis réessaie.", "Turn Bluetooth on, then try again.");
        EN.put("Ce téléphone ne peut pas diffuser de capteur Bluetooth LE.", "This phone cannot advertise a Bluetooth LE sensor.");
        EN.put("Impossible d’ouvrir le serveur Bluetooth.", "Unable to open the Bluetooth server.");
        EN.put("Publication du service Bluetooth refusée.", "Bluetooth service registration was rejected.");
        EN.put("Bluetooth occupé : ferme les autres apps de diffusion.", "Bluetooth busy: close other broadcasting apps.");
        EN.put("La diffusion Bluetooth LE n’est pas prise en charge.", "Bluetooth LE advertising is not supported.");
        EN.put("Une diffusion est déjà active. Arrête puis relance.", "Broadcast already active. Stop it and restart.");
        EN.put("Bluetooth désactivé. Réactive-le puis relance la diffusion.", "Bluetooth is off. Turn it on and restart broadcasting.");
        EN.put("Démarrer la diffusion", "Start broadcasting");
        EN.put("Démarrer la diffusion du capteur Bluetooth", "Start broadcasting the Bluetooth sensor");
        EN.put("En manuel, règle librement la puissance.", "Set power freely in manual mode.");
    }
    static String translate(Context context, String text) {
        String language = context.getSharedPreferences("simulator", Context.MODE_PRIVATE).getString("language", context.getResources().getConfiguration().getLocales().get(0).getLanguage());
        if (language.equals("fr")) return text;
        if (text.startsWith("Impossible de diffuser en Bluetooth (code ")) return text.replace("Impossible de diffuser en Bluetooth (code ", "Unable to broadcast over Bluetooth (code ");
        if (text.startsWith("Erreur de service Bluetooth : ")) return text.replace("Erreur de service Bluetooth : ", "Bluetooth service error: ");
        return EN.getOrDefault(text, text);
    }
}
