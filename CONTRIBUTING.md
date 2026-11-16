# Contributing

Describe the problem in an issue before proposing a substantial change. Include Android version, phone model, receiver app version and steps to reproduce. Do not include Bluetooth addresses, device names that identify a person, account details or credentials.

Run `./gradlew assembleDebug lintDebug` and `sh verify-protocol.sh`. Changes to BLE behavior also need a physical phone and a separate receiver. State what was actually tested; an emulator does not prove radio interoperability.

Keep the app offline, without trackers or advertisements. Do not add claims that synthetic values are real exercise or guarantee compatibility without evidence.
