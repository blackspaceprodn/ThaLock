# ThaLock

A private, offline-first document vault for Android. Store your IDs, financial
records, and insurance documents in a single biometric-locked, encrypted
vault — no accounts, no cloud, no telemetry.

## Why

Most "document wallet" apps sync your scans to a server you don't control,
collect analytics, and want an account before you can use them. ThaLock does
none of that. Your documents live on your device, inside an AES-256 encrypted
database, and the app cannot phone home because it has no network code at all.

## Features

- **Encrypted local vault** — all documents and metadata stored in a
  SQLCipher (AES-256) database; key protected by the Android Keystore.
- **Biometric unlock** — fingerprint or face required to open the app.
- **In-app scanner** — capture documents with the camera and run on-device
  OCR (ML Kit) so they're searchable without leaving your phone.
- **Categories** — Identity, Financial, Insurance, with per-category sub-types
  (Passport, PAN, Aadhaar, bank statements, etc.).
- **Storage Access Framework provider** — other apps can request a file from
  ThaLock through the system picker, with biometric re-auth before anything
  leaves the vault.
- **No accounts, no network, no analytics.** The app requests `CAMERA` and
  `USE_BIOMETRIC` and nothing else.

## Privacy

The full privacy policy is in [PRIVACY.md](PRIVACY.md). Short version: the
app never connects to the internet. Uninstalling deletes everything.

## Tech stack

- Kotlin + Jetpack Compose (Material 3)
- Room + SQLCipher for encrypted persistence
- Android Keystore + Biometric Prompt for auth
- CameraX + ML Kit Text Recognition (on-device) for scanning
- Coil for image loading
- Navigation Compose
- Min SDK 26, target SDK 35

## Build

Requires Android Studio Ladybug or newer and JDK 17.

```bash
git clone https://github.com/blackspaceprodn/ThaLock.git
cd ThaLock
./gradlew assembleDebug
```

The debug build installs as `com.thalock.app.debug` so it can coexist with
the release build on the same device.

### Release builds

Release builds require an upload keystore. Copy the template:

```bash
cp keystore.properties.example keystore.properties
```

Fill in your keystore path and passwords, then:

```bash
./gradlew bundleRelease
```

The signed AAB lands at `app/release/app-release.aab`. The keystore and
`keystore.properties` are gitignored — never commit them.

## Project layout

```
app/src/main/java/com/thalock/app/
├── MainActivity.kt          # single-activity Compose host
├── ThaLockApp.kt            # Application class, DI wiring
├── data/                    # Room entities, DAOs, repositories
├── provider/                # DocumentsProvider + picker activity
├── security/                # SQLCipher key mgmt, biometric prompt
├── ui/                      # Compose screens, theme, components
│   └── screens/
│       ├── add/             # add / scan document flow
│       ├── document/        # document detail view
│       ├── files/           # file list
│       ├── folder/          # folder browser
│       ├── home/            # home screen
│       └── settings/        # settings
└── util/                    # OCR helper, file generation, misc
```

## Contributing

Issues and PRs welcome. This is a personal project, so response times vary.

## License

TBD.
