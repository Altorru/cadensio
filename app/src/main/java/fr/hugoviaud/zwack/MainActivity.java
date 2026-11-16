package fr.hugoviaud.zwack;

import android.Manifest;
import android.app.*;
import android.bluetooth.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.*;
import android.widget.*;
import java.util.ArrayList;

public final class MainActivity extends Activity {
    static final int BG = Color.rgb(10,16,27), CARD = Color.rgb(20,30,46), TRACK = Color.rgb(43,57,76);
    static final int INK = Color.rgb(242,246,252), MUTED = Color.rgb(161,177,198), GREEN = Color.rgb(175,245,108);
    SimulatorState state;
    TextView status, details, target;
    SignalView signal;
    Button start;
    Switch erg;
    Control power, cadence;
    final Runnable listener = this::refresh;
    @Override protected void attachBaseContext(Context base) {
        String language = base.getSharedPreferences("simulator", MODE_PRIVATE).getString("language", "");
        if (!language.isEmpty()) {
            android.content.res.Configuration configuration = new android.content.res.Configuration(base.getResources().getConfiguration());
            configuration.setLocale(java.util.Locale.forLanguageTag(language));
            base = base.createConfigurationContext(configuration);
        }
        super.attachBaseContext(base);
    }
    @Override public void onCreate(android.os.Bundle savedInstanceState) {
        super.onCreate(savedInstanceState); state = SimulatorState.get(this);
        LinearLayout root = column(); root.setBackgroundColor(BG);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            int top, bottom;
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                top = bars.top; bottom = bars.bottom;
            } else { top = insets.getSystemWindowInsetTop(); bottom = insets.getSystemWindowInsetBottom(); }
            view.setPadding(dp(20), top + dp(12), dp(20), bottom + dp(10));
            return insets;
        });
        setContentView(root);
        LinearLayout heading = row();
        ImageView logo = new ImageView(this); logo.setImageResource(R.drawable.ic_cadensio);
        LinearLayout.LayoutParams logoSize = new LinearLayout.LayoutParams(dp(42), dp(42)); logoSize.rightMargin = dp(10); heading.addView(logo, logoSize);
        LinearLayout brand = column(); brand.addView(text("Cadensio", 28, INK, true));
        brand.addView(text(t("LABO VÉLO BLUETOOTH", "BLUETOOTH CYCLING LAB"), 9, GREEN, true));
        heading.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));
        Button settings = button(t("Aide", "Help"), CARD, INK, t("Ouvrir les réglages et l’aide", "Open settings and help"));
        settings.setTextSize(12); settings.setOnClickListener(v -> settings());
        heading.addView(settings, new LinearLayout.LayoutParams(dp(64), dp(48))); root.addView(heading);

        LinearLayout connection = card(); marginTop(connection, 18);
        status = text("Prêt à diffuser", 17, GREEN, true);
        details = text("FTMS  ·  Puissance  ·  Cadence", 12, MUTED, false);
        connection.addView(status); marginTop(details, 5); connection.addView(details);
        signal = new SignalView(this); connection.addView(signal, new LinearLayout.LayoutParams(-1, dp(38)));
        root.addView(connection);

        ScrollView scroll = new ScrollView(this); scroll.setClipToPadding(false); scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout content = column(); content.setPadding(0, dp(12), 0, dp(12));
        power = new Control(t("Puissance", "Power"), "W", true); content.addView(power.card);
        cadence = new Control(t("Cadence", "Cadence"), t("tr/min", "rpm"), false); marginTop(cadence.card, 12); content.addView(cadence.card);

        LinearLayout mode = card(); marginTop(mode, 12);
        erg = new Switch(this); erg.setText(t("Suivre la cible ERG", "Follow ERG target")); erg.setTextSize(14); erg.setTextColor(INK);
        erg.setPadding(0, dp(3), 0, dp(3)); erg.setMinHeight(dp(48));
        erg.setContentDescription(t("Suivre la puissance cible reçue en Bluetooth", "Follow the target power received over Bluetooth"));
        erg.setOnCheckedChangeListener((view, checked) -> {
            if (state.erg != checked) {
                state.erg = checked;
                if (checked && state.ergTarget != null) state.power = state.ergTarget;
                state.save();
            }
        });
        mode.addView(erg);
        target = text("En manuel, règle librement la puissance.", 12, MUTED, false); mode.addView(target);
        content.addView(mode);
        TextView hint = text(t("DONNÉES SIMULÉES · Pour les tests de connexion", "SIMULATED DATA · For connection testing"), 10, MUTED, true);
        hint.setPadding(dp(2), dp(14), dp(2), dp(4)); content.addView(hint);
        scroll.addView(content); root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        start = button("Démarrer la diffusion", GREEN, BG, "Démarrer la diffusion du capteur Bluetooth");
        start.setTextSize(16); start.setOnClickListener(v -> toggle());
        root.addView(start, new LinearLayout.LayoutParams(-1, dp(56)));
        TextView footer = text(t("Sans compte · Sans publicité · Hors ligne", "No account · No ads · Offline"), 11, MUTED, false);
        footer.setGravity(Gravity.CENTER); footer.setPadding(0, dp(9), 0, 0); root.addView(footer);
        refresh();
        if (!state.prefs.getBoolean("welcomeSeen", false)) root.post(this::welcome);
    }
    @Override protected void onResume() { super.onResume(); if (!state.listeners.contains(listener)) state.listeners.add(listener); refresh(); }
    @Override protected void onPause() { state.listeners.remove(listener); super.onPause(); }

    void refresh() {
        if (status == null) return;
        status.setText((state.advertising ? "●  " : "○  ") + UiText.translate(this, state.status));
        signal.invalidate();
        status.setTextColor(state.running ? GREEN : INK);
        if (state.running) details.setText(t("Cherche « ", "Find “") + state.bluetoothName + t(" »\n", "”\n") + state.connections + t(" connecté(s)  ·  ", " connected  ·  ") + state.subscriptions + t(" abonné(s) aux données", " receiving data"));
        else details.setText(t("FTMS · Puissance · Cadence\nConnecte ton app vélo sur un autre appareil.", "FTMS · Power · Cadence\nConnect your cycling app on another device."));
        power.refresh(); cadence.refresh();
        if (erg.isChecked() != state.erg) erg.setChecked(state.erg);
        target.setText(state.erg ? (state.ergTarget == null ? t("En attente d’une cible FTMS. Cadence manuelle.", "Waiting for an FTMS target. Manual cadence.")
            : t("Cible reçue : ", "Target received: ") + state.ergTarget + t(" W. Cadence manuelle.", " W. Manual cadence.")) : t("Mode manuel. Modifier la puissance désactive ERG.", "Manual mode. Changing power turns ERG off."));
        start.setText(state.running ? t("Arrêter la diffusion", "Stop broadcasting") : t("Démarrer la diffusion", "Start broadcasting"));
        start.setContentDescription(state.running ? t("Arrêter la diffusion", "Stop broadcasting") : t("Démarrer la diffusion", "Start broadcasting"));
        start.setBackground(background(state.running ? TRACK : GREEN, 18)); start.setTextColor(state.running ? INK : BG);
    }
    final class Control {
        final boolean isPower;
        final String unit;
        final LinearLayout card;
        final TextView value, live, variation;
        final SeekBar baseSlider, variationSlider;
        boolean baseTracking, variationTracking;
        Control(String title, String unit, boolean isPower) {
            this.isPower = isPower; this.unit = unit;
            card = card();
            LinearLayout header = row();
            header.addView(text(title.toUpperCase(java.util.Locale.FRANCE), 11, MUTED, true), new LinearLayout.LayoutParams(0, -2, 1));
            live = text("SIGNAL —", 11, GREEN, true); header.addView(live); card.addView(header);
            value = text("", 44, INK, true); value.setPadding(0, dp(3), 0, 0); card.addView(value);
            baseSlider = slider(isPower ? 1000 : 200, title);
            LinearLayout baseRow = row();
            Button minus = small("−", t("Diminuer : ", "Decrease: ") + title);
            Button plus = small("+", t("Augmenter : ", "Increase: ") + title);
            minus.setOnClickListener(v -> setBase(base() - (isPower ? 5 : 1)));
            plus.setOnClickListener(v -> setBase(base() + (isPower ? 5 : 1)));
            baseRow.addView(minus, new LinearLayout.LayoutParams(dp(48), dp(48)));
            baseRow.addView(baseSlider, new LinearLayout.LayoutParams(0, dp(48), 1));
            baseRow.addView(plus, new LinearLayout.LayoutParams(dp(48), dp(48))); card.addView(baseRow);
            baseSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) { if (fromUser) setBase(progress); }
                public void onStartTrackingTouch(SeekBar bar) { baseTracking = true; }
                public void onStopTrackingTouch(SeekBar bar) { baseTracking = false; refresh(); }
            });
            LinearLayout label = row(); label.setPadding(0, dp(10), 0, 0);
            label.addView(text(t("Variation du signal", "Signal variation"), 12, MUTED, false), new LinearLayout.LayoutParams(0, -2, 1));
            variation = text("", 12, INK, true); label.addView(variation); card.addView(label);
            variationSlider = slider(isPower ? 100 : 20, t("Variation : ", "Variation: ") + title);
            LinearLayout variationRow = row();
            Button less = small("−", t("Réduire l’écart : ", "Reduce variation: ") + title);
            Button more = small("+", t("Augmenter l’écart : ", "Increase variation: ") + title);
            less.setOnClickListener(v -> setVariation(spread() - 1)); more.setOnClickListener(v -> setVariation(spread() + 1));
            variationRow.addView(less, new LinearLayout.LayoutParams(dp(48), dp(48)));
            variationRow.addView(variationSlider, new LinearLayout.LayoutParams(0, dp(48), 1));
            variationRow.addView(more, new LinearLayout.LayoutParams(dp(48), dp(48))); card.addView(variationRow);
            variationRow.setVisibility(View.GONE);
            marginTop(label, 6); label.setMinimumHeight(dp(48));
            label.setContentDescription(t("Afficher ou masquer le réglage de variation", "Show or hide signal variation controls"));
            label.setBackground(background(TRACK, 12)); label.setPadding(dp(12), 0, dp(12), 0);
            label.setOnClickListener(v -> variationRow.setVisibility(variationRow.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE));
            if (isPower) {
                LinearLayout presets = row(); presets.setPadding(0, dp(10), 0, dp(6));
                for (int watts : new int[]{100, 150, 250}) {
                    Button preset = button(watts + " W", TRACK, INK, t("Régler la puissance à ", "Set power to ") + watts + " W");
                    preset.setTextSize(12); preset.setOnClickListener(v -> setBase(watts));
                    LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(0, dp(48), 1); pp.setMargins(dp(3),0,dp(3),0); presets.addView(preset, pp);
                }
                card.addView(presets, 3);
            }
            variationSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) { if (fromUser) setVariation(progress); }
                public void onStartTrackingTouch(SeekBar bar) { variationTracking = true; }
                public void onStopTrackingTouch(SeekBar bar) { variationTracking = false; refresh(); }
            });
        }
        int base() { return isPower ? state.power : state.cadence; }
        int spread() { return isPower ? state.powerVariation : state.cadenceVariation; }
        void setBase(int number) {
            if (isPower) { state.power = Protocol.clamp(number, 1000); state.erg = false; }
            else state.cadence = Protocol.clamp(number, 200);
            state.save();
        }
        void setVariation(int number) {
            if (isPower) state.powerVariation = Protocol.clamp(number, 100); else state.cadenceVariation = Protocol.clamp(number, 20);
            state.save();
        }
        void refresh() {
            value.setText(base() + " " + unit);
            variation.setText("± " + spread() + " " + unit + "  ⌄");
            if (!baseTracking && baseSlider.getProgress() != base()) baseSlider.setProgress(base());
            if (!variationTracking && variationSlider.getProgress() != spread()) variationSlider.setProgress(spread());
            live.setText(state.advertising ? "SIGNAL " + (isPower ? state.emittedPower : state.emittedCadence) + " " + unit : "SIGNAL —");
        }
    }
    void toggle() {
        if (state.running) { stopService(new Intent(this, SimulatorService.class)); return; }
        ArrayList<String> permissions = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 31) {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) permissions.add(Manifest.permission.BLUETOOTH_CONNECT);
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE);
        }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            && !state.prefs.getBoolean("notificationAsked", false)) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS); state.prefs.edit().putBoolean("notificationAsked", true).apply();
        }
        if (!permissions.isEmpty()) { requestPermissions(permissions.toArray(new String[0]), 101); return; }
        begin();
    }
    @android.annotation.SuppressLint("MissingPermission")
    void begin() {
        BluetoothManager manager = getSystemService(BluetoothManager.class);
        BluetoothAdapter adapter = manager == null ? null : manager.getAdapter();
        if (adapter == null) { state.status = "Bluetooth LE indisponible sur cet appareil."; refresh(); return; }
        try {
            if (!adapter.isEnabled()) {
                startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), 102); return;
            }
            state.running = true; state.status = "Démarrage…"; refresh();
            startForegroundService(new Intent(this, SimulatorService.class));
        } catch (RuntimeException error) {
            state.running = false; state.status = "Démarrage impossible. Vérifie les autorisations Bluetooth."; refresh();
        }
    }
    @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code, permissions, results);
        if (code != 101) return;
        if (Build.VERSION.SDK_INT >= 31 && (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
            || checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED)) {
            state.status = "Autorisation « Appareils à proximité » requise."; refresh();
            new AlertDialog.Builder(this).setTitle(UiText.translate(this, "Autoriser le Bluetooth"))
                .setMessage(UiText.translate(this, "Cadensio a besoin de l’autorisation Appareils à proximité pour diffuser un capteur. Aucune localisation n’est demandée."))
                .setPositiveButton(UiText.translate(this, "Paramètres"), (d,w) -> startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.parse("package:" + getPackageName())))).setNegativeButton(UiText.translate(this, "Plus tard"), null).show();
        } else begin();
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == 102) {
            if (result == RESULT_OK) begin();
            else { state.status = "Active le Bluetooth pour diffuser."; refresh(); }
        }
    }
    String t(String fr, String en) { return getResources().getConfiguration().getLocales().get(0).getLanguage().equals("fr") ? fr : en; }
    void welcome() {
        new AlertDialog.Builder(this).setTitle(t("Ton labo vélo, sans matériel", "Your cycling lab, no trainer needed"))
            .setMessage(t("Cadensio simule un capteur vélo Bluetooth pour tester une connexion. Ce n’est pas un capteur d’effort réel.\n\n1. Règle la puissance et la cadence.\n2. Démarre la diffusion Bluetooth.\n3. Connecte ton application vélo sur un AUTRE appareil.\n\nUn téléphone compatible avec la diffusion BLE est nécessaire. Les données sont synthétiques : ne les présente pas comme une activité sportive réelle.",
            "Cadensio simulates a Bluetooth cycling sensor to test a connection. It does not measure real exercise.\n\n1. Set power and cadence.\n2. Start Bluetooth broadcasting.\n3. Connect your cycling app on ANOTHER device.\n\nA phone supporting BLE peripheral advertising is required. The data is synthetic: do not present it as a real workout."))
            .setPositiveButton(t("C’est parti", "Let's start"), (d,w) -> state.prefs.edit().putBoolean("welcomeSeen", true).apply())
            .setNegativeButton(t("Confidentialité", "Privacy"), (d,w) -> privacy()).show();
    }
    void link(String url) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))); }
        catch (ActivityNotFoundException e) { new AlertDialog.Builder(this).setMessage(url).setPositiveButton("OK", null).show(); }
    }
    void privacy() {
        new AlertDialog.Builder(this).setTitle(t("Confidentialité", "Privacy policy"))
            .setMessage(t("Cadensio — Hugo Viaud · 16 novembre 2026\n\nL’app fonctionne sans Internet, compte, publicité ni outil de suivi. Aucun serveur ne reçoit de données de l’app. Les réglages sont stockés uniquement sur ton téléphone ; efface les données de l’app ou désinstalle-la pour les supprimer.\n\nLes autorisations Bluetooth servent à diffuser les valeurs simulées et à recevoir des commandes FTMS. Le nom Bluetooth du téléphone peut être visible à proximité. Les connexions restent en mémoire le temps de la session. Les notifications permettent de surveiller et arrêter la diffusion en arrière-plan. La localisation n’est pas demandée.\n\nL’application vélo que tu connectes peut enregistrer les données simulées selon sa propre politique. Tu choisis de lancer et d’arrêter la diffusion.\n\nQuestions : github.com/Altorru/cadensio/issues. N’y publie pas de données personnelles.",
            "Cadensio — Hugo Viaud · 16 November 2026\n\nThe app works without Internet, accounts, advertising or trackers. No app data is sent to a server. Settings are stored only on your phone; clear app storage or uninstall to delete them.\n\nBluetooth permissions broadcast simulated values and receive FTMS commands. Your phone's Bluetooth name may be visible nearby. Connections are held in memory during a session. Notifications let you monitor and stop background broadcasting. Location is not requested.\n\nThe cycling app you connect may record the simulated data under its own privacy policy. You choose when to start and stop broadcasting.\n\nQuestions: github.com/Altorru/cadensio/issues. Do not post personal information there."))
            .setPositiveButton(t("Fermer", "Close"), null)
            .setNeutralButton(t("Version en ligne", "Online policy"), (d,w) -> link("https://altorru.github.io/cadensio/privacy.html")).show();
    }
    void settings() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout box = column(); box.setPadding(dp(24), dp(8), dp(24), dp(16)); scroll.addView(box);
        TextView help = text(t("01  RÈGLE TON SIGNAL\nPuissance et cadence sont simulées, pas mesurées.\n\n02  LANCE LA DIFFUSION\nAutorise le Bluetooth. Le téléphone doit prendre en charge le rôle périphérique BLE.\n\n03  CONNECTE TON APP VÉLO\nSur un autre appareil, cherche le nom Bluetooth de ton téléphone. Sélectionne les capteurs de puissance, cadence et le home trainer contrôlable.\n\nLa diffusion reste active en arrière-plan jusqu’à Arrêter. En ERG, la cadence reste manuelle. La compatibilité dépend de l’app et du téléphone.",
            "01  SET YOUR SIGNAL\nPower and cadence are simulated, not measured.\n\n02  START BROADCASTING\nAllow Bluetooth. Your phone must support the BLE peripheral role.\n\n03  CONNECT YOUR CYCLING APP\nOn another device, find your phone's Bluetooth name. Select power, cadence and controllable trainer.\n\nBroadcasting continues in the background until you stop it. Cadence stays manual in ERG mode. Compatibility depends on the app and phone."), 14, INK, false); box.addView(help);
        TextView speedLabel = text(t("Vitesse simulée : ", "Simulated speed: ") + state.speed + " km/h", 14, GREEN, true);
        speedLabel.setPadding(0, dp(18), 0, 0); box.addView(speedLabel);
        SeekBar speed = slider(60, t("Vitesse simulée en kilomètres par heure", "Simulated speed in kilometres per hour")); speed.setProgress(state.speed); box.addView(speed);
        speed.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar v, int p, boolean user) { speedLabel.setText(t("Vitesse simulée : ", "Simulated speed: ") + p + " km/h"); }
            public void onStartTrackingTouch(SeekBar v) { }
            public void onStopTrackingTouch(SeekBar v) { }
        });
        Button language = button("Français / English", TRACK, INK, "Français / English");
        language.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("Langue / Language")
            .setItems(new String[]{"Français", "English"}, (d,w) -> {
                state.prefs.edit().putString("language", w == 0 ? "fr" : "en").apply(); recreate();
            }).show()); box.addView(language);
        Button privacy = button(t("Confidentialité", "Privacy policy"), TRACK, INK, t("Lire la politique de confidentialité", "Read the privacy policy"));
        privacy.setOnClickListener(v -> privacy()); marginTop(privacy, 8); box.addView(privacy);
        Button source = button(t("Code source & assistance ↗", "Source code & support ↗"), TRACK, INK, "GitHub");
        source.setOnClickListener(v -> link("https://github.com/Altorru/cadensio")); marginTop(source, 8); box.addView(source);
        new AlertDialog.Builder(this).setTitle(t("Connexion & réglages", "Connection & settings")).setView(scroll)
            .setPositiveButton(t("Enregistrer", "Save"), (d,w) -> { state.speed = speed.getProgress(); state.save(); })
            .setNegativeButton(t("Fermer", "Close"), null).show();
    }
    /** Decorative connection trace: it deliberately does not claim to be a measured chart. */
    final class SignalView extends View {
        final android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        final android.graphics.Path path = new android.graphics.Path();
        SignalView(Context context) { super(context); setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO); }
        @Override protected void onDraw(android.graphics.Canvas canvas) {
            super.onDraw(canvas); float mid = getHeight() / 2f;
            paint.setColor(TRACK); paint.setStrokeWidth(dp(1)); canvas.drawLine(0, mid, getWidth(), mid, paint);
            paint.setColor(state.advertising ? GREEN : MUTED); paint.setStrokeWidth(dp(2)); paint.setStyle(android.graphics.Paint.Style.STROKE);
            path.reset(); path.moveTo(0, mid);
            float w = getWidth(); path.lineTo(w*.30f,mid); path.lineTo(w*.37f,mid-dp(8)); path.lineTo(w*.43f,mid+dp(9)); path.lineTo(w*.51f,mid-dp(12)); path.lineTo(w*.59f,mid); path.lineTo(w,mid); canvas.drawPath(path,paint); paint.setStyle(android.graphics.Paint.Style.FILL);
        }
    }
    int dp(int number) { return Math.round(number * getResources().getDisplayMetrics().density); }
    LinearLayout column() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    LinearLayout row() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); l.setGravity(Gravity.CENTER_VERTICAL); return l; }
    TextView text(String value, int size, int color, boolean bold) {
        TextView v = new TextView(this); v.setText(UiText.translate(this, value)); v.setTextSize(size); v.setTextColor(color);
        if (bold) v.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        v.setLineSpacing(dp(2), 1); return v;
    }
    GradientDrawable background(int color, int radius) { GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d; }
    LinearLayout card() { LinearLayout c = column(); c.setPadding(dp(16), dp(15), dp(16), dp(15)); c.setBackground(background(CARD, 20)); return c; }
    void marginTop(View view, int margin) { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.topMargin = dp(margin); view.setLayoutParams(p); }
    Button button(String title, int background, int color, String description) {
        Button b = new Button(this); b.setText(UiText.translate(this, title)); b.setAllCaps(false); b.setTextColor(color); b.setTextSize(16);
        b.setBackground(background(background, 14)); b.setPadding(dp(8), 0, dp(8), 0); b.setMinWidth(0); b.setMinimumWidth(0);
        b.setContentDescription(description); return b;
    }
    Button small(String title, String description) { Button b = button(title, TRACK, INK, description); b.setTextSize(23); return b; }
    SeekBar slider(int max, String description) {
        SeekBar b = new SeekBar(this); b.setMax(max); b.setContentDescription(description);
        b.setProgressTintList(ColorStateList.valueOf(GREEN)); b.setProgressBackgroundTintList(ColorStateList.valueOf(TRACK));
        b.setThumbTintList(ColorStateList.valueOf(GREEN)); b.setPadding(dp(14), 0, dp(14), 0); b.setMinimumHeight(dp(48)); return b;
    }
}
