# Wizjer for Android

Wizjer helps people in Poland spot scams before they fall for them. It watches incoming SMS
messages and notifications for signs of fraud, warns the user, and offers an AI assistant to
check a suspicious message, link or phone number and to help after a scam.

The app is available in **Polish**, **English** and **Ukrainian**.

> This repository is the Android app. It is open source so anyone can see exactly what the app
> does on their phone. The Wizjer API it talks to is a separate, closed-source service.

## Privacy

- Scanning of messages and notifications is designed to run **on the device**; the content of
  your messages and notifications does not leave the phone.
- Lists of dangerous sites (including CERT Polska's warning list) are downloaded whole and
  checked on the phone, so the server never learns which links you received.
- The AI assistant runs in the cloud and needs an account. What you send to it is processed on
  Wizjer's servers.
- The session token is stored encrypted with a key from the Android Keystore, and app data is
  excluded from backups and device transfers.
- Release builds only connect over HTTPS.

## Status

Early development. Done so far: onboarding (profile, permissions, account with email
confirmation, sign-in), the chat home screen, voice-call and settings screens, background
protection (receiving SMS and notifications), and the on-device detector: text clean-up,
extraction of links and numbers, threat lists, rules and an AI model, testable in a debug-only
sandbox. Warnings and the assistant's replies are not connected yet.

### The AI model

`app/src/main/assets/model/wizjer-baseline.bin` (2 MB) is a small model trained in the
[wizjer-model](https://github.com/wizjerorg/wizjer-model) repository on public scam warnings and
ordinary messages. It runs on the phone like the rules do. To update it, train and export it there
and copy the file here:

```bash
python scripts/train_baseline.py --save dist/model.json
python scripts/export_model.py dist/model.json dist/wizjer-baseline.bin
```

The Kotlin feature code in `detection/model/` must compute exactly what the training code does;
`ScamModelTest` checks this against messages scored in Python, which are stored in the model file.

## Building

Requirements: Android Studio (recent), JDK 17+, Android SDK with API 37.

```bash
./gradlew assembleDebug
```

Or open the project in Android Studio and press Run.

### API address

Release builds use the production API. Debug builds use it too, unless you point them at a
development server in your `local.properties` (this file is not committed):

```properties
wizjer.apiUrl=http://192.168.1.10:4000
```

Debug builds allow plain HTTP for this; release builds never do.

## Project structure

```
app/src/main/java/org/wizjer/android/
├── MainActivity.kt          Entry point: theme, edge-to-edge, splash
├── data/
│   ├── api/                 Small HTTPS/JSON client
│   ├── account/             AccountSession (sign-up, sign-in, profile sync), stores
│   ├── lists/               Threat-list download and background refresh
│   └── model/               Loads the bundled AI model
├── detection/               On-device scam detector (clean-up, extraction, rules, model, scoring)
│   ├── lists/               Compact on-disk index of flagged domains
│   └── model/               The AI model: features, BLAKE2b hashing, scoring
├── protection/              Permissions, SMS receiver and the notification listener
└── ui/
    ├── WizjerApp.kt         Navigation: onboarding → main app
    ├── onboarding/          Welcome screen
    ├── profile/             Name and date of birth
    ├── permissions/         Permission requests
    ├── account/             Account creation, email code, sign-in
    ├── home/                Chat with the assistant, side menu
    ├── call/                Voice call with the assistant
    ├── protection/          Background protection status
    ├── sandbox/             Detector sandbox (debug builds only)
    ├── settings/            Settings
    ├── language/            In-app language picker
    ├── components/          Shared UI: buttons, fields, icons, the animated logo
    └── theme/               Colours, type (Manrope), theme
```

Translations live in `app/src/main/res/values` (English), `values-pl` and `values-uk`.

## Contributing

Issues and pull requests are welcome. Please keep the UI consistent with the existing
components in `ui/components`, add new strings in all three languages, and run
`./gradlew lintDebug` before opening a pull request.

## License

The app is released under the [MIT License](LICENSE).

The [Manrope](https://github.com/googlefonts/manrope) font is included under the SIL Open
Font License 1.1 (see [media/fonts/Manrope-OFL.txt](media/fonts/Manrope-OFL.txt)).
