<p align="center"><img src="docs/assets/icon.png" alt="Cadensio icon" width="112"></p>
<h1 align="center">Cadensio</h1>
<p align="center"><b>Your Bluetooth cycling test lab.</b><br>Free. Offline. No account. No ads.</p>
<p align="center"><a href="https://altorru.github.io/cadensio/">Website</a> · <a href="https://github.com/Altorru/cadensio/releases">Download APK</a> · <a href="https://altorru.github.io/cadensio/privacy.html">Privacy</a> · <a href="https://github.com/Altorru/cadensio/issues">Support</a></p>

Cadensio turns a compatible Android phone into a **simulated Bluetooth LE cycling sensor**. Set power and cadence, connect a cycling app on another device and inspect the connection. Useful for developers, technical support and connection diagnostics without a smart trainer.

**Google Play status (16 November 2026):** version 1.0.0 submitted for review. Automatic pre-review checks and Google approval are pending. Publication is configured to proceed automatically after approval. The intended listing is [Cadensio on Google Play](https://play.google.com/store/apps/details?id=fr.hugoviaud.cadensio); it becomes available only after Google approves and publishes the app. Do not interpret this link as proof of availability.

## What it does

- Simulates power (0–1,000 W), cadence (0–200 rpm) and speed (0–60 km/h).
- Broadcasts FTMS, Cycling Power and Cycling Speed and Cadence services.
- Follows incoming FTMS target-power commands in optional ERG mode; cadence stays manual.
- Offers independent random variation, fixed signals and quick power presets.
- Keeps broadcasting in the background with a visible stop notification.
- Stores settings locally. English and French interface. No Internet permission, account, advertising, analytics or in-app purchases.

## Connect in three steps

1. Install on **Android 8+** with BLE peripheral advertising support. Set power and cadence.
2. Tap **Start broadcasting**, allow Nearby devices and enable Bluetooth.
3. On a **second device**, open your cycling app and look for your phone's Bluetooth name. Pair power, cadence and controllable trainer as supported.

A Bluetooth connection is not the same as a data subscription. The app shows both. "Signal" is simulated output, not proof that a receiver consumes it. Phone names too long for BLE advertising may be omitted. Stop in the app or notification.

**This is a simulator, not an exercise sensor or a trainer controller.** It does not measure physical effort and must not be used to present synthetic data as real exercise, competition results or reward eligibility. Compatibility depends on phone and receiver implementation. No affiliation with cycling platforms; no platform-specific compatibility is certified yet.

## Build and verify

JDK 17+, Android SDK 36, `ANDROID_HOME` configured:

```sh
./gradlew assembleDebug lintDebug
sh verify-protocol.sh
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`. Release bundle: `./gradlew bundleRelease` (unsigned unless configured).

To sign, create a properties file **outside this repository** with `storeFile`, `storePassword`, `keyAlias`, `keyPassword`, then set `CADENSIO_SIGNING_PROPERTIES` to its absolute path. Never commit that file or a keystore. Play App Signing may use a different app-signing certificate from APKs downloaded from GitHub, so switching distribution channels can require reinstalling the app.

## Validation and limitations

Protocol checks exercise packet encoding, power limits, independent variation, signed power, fractional cadence and crank-counter wrapping. Build, lint and emulator UI results are documented in [release notes](docs/release-readiness.md). Physical phone-to-receiver interoperability and ERG still require hardware testing. Emulator advertising does not establish real radio compatibility. Manufacturer power management may interrupt long sessions. Broadcasting never restarts automatically after process/device restart.

## BLE surface

| Service | Characteristics |
| --- | --- |
| FTMS `1826` | Indoor Bike Data `2AD2`, Feature `2ACC`, Power Range `2AD8`, Control Point `2AD9`, Status `2ADA` |
| Cycling Power `1818` | Measurement `2A63`, Feature `2A65`, Location `2A5D` |
| Cycling Speed and Cadence `1816` | Measurement `2A5B` (crank only), Feature `2A5C`, Location `2A5D` |

Supported controls: request control, reset, target power, start/resume, stop/pause and simulation parameters. Slope is acknowledged but does not calculate power. One client owns control at a time. Measurements and indications are serialized; slow clients may lose old queued measurements.

## Français

**Ton labo vélo Bluetooth.** Cadensio est un simulateur gratuit de puissance et de cadence pour tester une connexion entre un téléphone Android et une application vélo sur un autre appareil. Sans compte, sans publicité, hors ligne. Il ne mesure pas ton effort réel. La compatibilité radio doit être vérifiée sur ton téléphone et ton application. [Confidentialité](https://altorru.github.io/cadensio/privacy.html) · [Assistance](https://github.com/Altorru/cadensio/issues).

## Project

MIT licensed. Native Android / Java, no UI framework or tracking SDK. The app started as a separate native implementation in a local Zwack compatibility kit; **this repository does not contain upstream Zwack Node.js code**. The internal Java namespace preserves its development history; the distributed application ID is `fr.hugoviaud.cadensio`.
