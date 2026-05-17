# ThaLock Privacy Policy

**Effective Date:** 2026-05-16
**Last Updated:** 2026-05-16

ThaLock ("the app", "we", "our") is a personal document vault that stores
identity, financial, and insurance documents locally on your device. This
policy explains what information the app handles and how it is protected.

## Summary

- ThaLock is **fully offline**. The app does not connect to the internet.
- We do **not** collect, transmit, sell, share, or have access to any of
  your data.
- All documents, scans, and OCR text remain on your device, inside an
  encrypted database.

## Information the app handles

The app stores the following information **only on your device**:

- **Documents you add manually** — files, photos, scans, and metadata
  (titles, categories, document types) you create.
- **OCR text extracted from scans** — text recognized from images you
  scan, used to make documents searchable inside the app.
- **App preferences** — your chosen theme, language, and similar settings.
- **Biometric authentication state** — whether biometric unlock is enabled.
  The app never sees or stores your fingerprint or face data; that data
  stays inside your device's secure hardware (Android Keystore / TEE).

## How your data is protected

- All documents and metadata are stored in a **SQLCipher-encrypted local
  database** (AES-256).
- The database encryption key is protected by the Android Keystore.
- App entry is gated by biometric authentication (fingerprint or face).
- Backups via Android's auto-backup system are **disabled** so your
  encrypted vault is not copied off-device.

## Information we do NOT collect

- No accounts, logins, or registration.
- No personal identifiers (name, email, phone, IP address).
- No usage analytics, telemetry, crash reports, or advertising IDs.
- No location data.
- No third-party SDKs that report data off-device.

## Permissions the app requests

- **Camera** — required only when you choose to scan a document.
  Images are processed locally and saved into your encrypted vault. They
  are never uploaded.
- **Biometric (fingerprint / face)** — used solely to unlock the app.
  Biometric data is handled by Android's secure hardware; the app only
  receives a yes/no authentication result.

## Third-party libraries

ThaLock uses Google's **ML Kit Text Recognition** library to extract text
from scanned images. This uses the **on-device** model: text recognition
happens entirely on your phone, with no network calls and no data sent to
Google. See: https://developers.google.com/ml-kit/vision/text-recognition

## Document sharing

When you choose to share, export, or hand off a document to another app
(for example, attaching it to an email or uploading it to a website), the
file leaves the encrypted vault for that single operation. ThaLock has no
control over what the receiving app does with the file.

## Children's privacy

ThaLock is not directed at children under 13 and does not knowingly
collect any data from anyone, regardless of age.

## Data retention and deletion

All your data lives on your device. **Uninstalling the app permanently
deletes every document, scan, and setting.** There is no cloud copy and
no way for us to recover it.

## Changes to this policy

If this policy changes, the updated version will be posted at the same
URL and the "Last Updated" date will change. Material changes will also
be noted in the app's release notes.

## Contact

If you have questions or concerns about this policy, contact:
**blackspaceprodn@gmail.com**
