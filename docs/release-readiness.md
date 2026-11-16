# Release readiness — 1.0.0

Date: 2026-11-16. Application ID: `fr.hugoviaud.cadensio`. Publisher: VIAUD Hugo.

## Positioning

Cadensio is a free, offline Bluetooth cycling connection test tool. Audience: app developers, technical support and cyclists troubleshooting receiver setup. Core search intent: BLE bike simulator, FTMS simulator, cycling power simulator, Bluetooth cadence simulator; French: simulateur vélo Bluetooth, simulateur FTMS, capteur vélo virtuel. No traffic volume or search ranking has been measured or promised. Initial exact-name web search found no competing cycling app named Cadensio; this is not trademark clearance.

Store copy lives in `fastlane/metadata/android/{en-US,fr-FR}`. The app itself supports both languages. Use Tools as the initial store category: this is a diagnostic simulator, not a workout tracker. Price: free; no ads or purchases. No sign-in required.

## Validation

- Debug and signed release APK/AAB builds pass with Android SDK 36 / Java 17.
- Android lint passes; inspect the generated report for non-blocking platform deprecations.
- Protocol test suite passes (encoding, bounds, independent random variation, crank integration and rollover).
- Emulator UI verified: onboarding, power presets, persistence across relaunch, ERG toggle and automatic return to manual after power edit, English/French switching in both directions, help/privacy dialogs, Nearby devices and notification prompts, broadcasting start/stop. A language-recreation crash was found and fixed before this build. These tests do not validate radio interoperability.
- Physical Android phone + independent receiver test remains required: service discovery, data subscription, ERG requests, background persistence, disconnect/reconnect and stop.

## Play declarations to complete truthfully

- No ads, no account, no restricted login access.
- No analytics, crash-reporting or ad SDK; no Internet permission. Settings and live connection routing are local. Simulated values are sent over BLE to the user's receiver; the receiver may record them.
- Privacy policy: https://altorru.github.io/cadensio/privacy.html
- Website/source: https://github.com/Altorru/cadensio
- Connected-device foreground service: user explicitly starts the BLE simulator; it sends simulated sensor values to a connected fitness receiver, displays a persistent stop notification, and stops on user request. Record a truthful demonstration for review if required; do not fake a hardware connection.
- Content rating, target audience, health declaration and any account-specific forms must be answered in Console. Do not invent testing evidence or certify unverified app compatibility.

## Publication state

At repository creation, Google Play publication is still in preparation. The intended URL is https://play.google.com/store/apps/details?id=fr.hugoviaud.cadensio. A URL alone is not evidence that the listing is live. Update the README and website after actual availability is verified.

## Acquisition measurement after launch

Review Play Console search terms, store visitors, listing conversion, installs, active users, crashes and ANRs. Compare localized English/French listing performance. Improve screenshots and description based on observed queries; avoid competitor-brand stuffing and unsupported compatibility claims. Goal for the first month: establish a measured visitor-to-install baseline and receive real connection feedback, not guarantee a fabricated install count.

## References checked 2026-11-16

- https://support.google.com/googleplay/android-developer/answer/9859152 — listing limits (30/80/4,000 characters).
- https://support.google.com/googleplay/android-developer/answer/9866151 — icon, feature graphic and screenshots.
- https://support.google.com/googleplay/android-developer/answer/10144311 — privacy policy requirements.
- https://developer.android.com/studio/publish/app-signing — upload key and Play signing.
