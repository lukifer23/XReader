# Security

XReader is a local-first Android app for personal DRM-free libraries.

## Reporting

For now, report security issues privately to the repository owner through GitHub. Do not open a public issue for a vulnerability that exposes private user data or imported book contents.

## Data Handling

- Imported books are copied into app-owned private storage.
- Reading state, annotations, analytics, search indexes, and dictionary data are stored locally.
- The app does not request broad all-files access.
- Network access occurs only for a user-entered direct book or OPDS catalog URL. XReader has no account sync, background telemetry, or automatic network scan.
- Android backup is disabled in the manifest.
- User-created `.xreader-backup` archives exclude imported books/audio, covers, generated audio, neural models, private file paths, and checksum inventories intended for display. Backup v2 bounds compressed/uncompressed/section sizes and entry counts, rejects unsafe or undeclared ZIP paths, and validates per-section size/count/SHA-256 before restore.
- Backup v2 detects corruption and supports crash recovery across Room and DataStore. It is not encrypted and does not provide confidentiality or cryptographic authenticity; store exported backups accordingly.
- ACSM files are treated as Adobe license instructions and can only be handed to an external authorized app. XReader does not implement DRM removal, loan fulfillment, or library-card credential storage.

## Sensitive Test Data

Do not commit:

- imported commercial books
- screenshots with copyrighted book text
- commercial cover-art screenshots
- keystores
- signing passwords
- local Android SDK configuration
