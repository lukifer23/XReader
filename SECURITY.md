# Security

XReader is a local-first Android app for personal DRM-free libraries.

## Reporting

For now, report security issues privately to the repository owner through GitHub. Do not open a public issue for a vulnerability that exposes private user data or imported book contents.

## Data Handling

- Imported books are copied into app-owned private storage.
- Reading state, annotations, analytics, search indexes, and dictionary data are stored locally.
- The app does not request broad all-files access.
- The app does not intentionally use network access.
- Android backup is disabled in the manifest.

## Sensitive Test Data

Do not commit:

- imported commercial books
- screenshots with copyrighted book text
- commercial cover-art screenshots
- keystores
- signing passwords
- local Android SDK configuration
