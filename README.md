# FBR Clients

An offline, encrypted Android app for a tax practitioner. It keeps clients' IRIS logins and contact details, and a yearly Wealth Statement in the FBR IRIS format.

- Design: [docs/DESIGN.md](docs/DESIGN.md)
- Status: **All 5 build-plan steps done.** Lock screen, clients, Wealth Statement, backup and restore, signed release builds.

## Install on your phone

- **Release:** open the [latest release](../../releases/latest) on your phone, download the `.apk`, and open it.
- **Test builds:** every change also goes to [latest build](../../releases/tag/latest-build).

Android asks you to allow installs from your browser the first time.

## Using the app

- **Clients:** search by name, CNIC, NTN or mobile. On a client's page: Call, WhatsApp, SMS, Email, Copy CNIC, Copy Password (clears after 30 s), Open IRIS (copies password then username for the keyboard's clipboard history, then opens Chrome).
- **Wealth Statement:** on a client's page, tap **New tax year**. It can copy every item from the previous year. Tabs: Assets, Liabilities, Reconciliation. Every description has a **Copy** button for pasting into IRIS; on the Reconciliation tab, tap any amount to copy it.
- **Settings** (gear icon on the client list): change PIN, fingerprint, auto-lock time, categories and property sub-types (add unknown IRIS codes here), backup and restore.
- **App browser:** **IRIS in app**, **Check bill** and **Tax certificate** open inside the app and fill the username / password / reference for you (you press Login / Submit). Fill buttons at the bottom work on any box: tap the box, then the button. Sites open in desktop mode (⋮ → Desktop site to switch off).
- **Electricity bills:** on a client's page, **Add bill** (company + reference number). **Check bill** / **Tax certificate** copy the reference and open the official page. Links: *Settings → Electricity companies*.
- **Import from Excel:** *Settings → Import clients from Excel*. Column A = CNIC (username), column B = password, or use headers (Username, Password, Name, NTN, Mobile, Email). The file is read on the phone only.
- **Backups:** *Settings → Back up now* makes an encrypted `.fbrbak` file with its own password. *Restore* replaces everything on the phone with the backup. The password cannot be recovered.

## How the APK is built

GitHub Actions (`.github/workflows/build.yml`) runs the tests and builds the app on every push.
- Every build on `main` replaces the `latest-build` pre-release (named `<version>-build<N>`).
- `version.txt` holds the release version. The first **signed** build after it changes publishes a permanent release `v<version>`. To release again, bump `version.txt` (e.g. to `1.0.1`).
- You can also start a build from GitHub: **Actions → Build APK → Run workflow**.

Signing: the release key is rebuilt on each run from one GitHub Secret, `SIGNING_SEED` (see `scripts/derive_keystore.py`). The same seed always gives the same key, so every new build installs over the old one and keeps your data. **Keep a copy of the seed somewhere safe**; it's the only way to recreate the key. Without the secret, `main` builds are `-TEST` APKs that can't update other builds.

## For developers

- Kotlin, Jetpack Compose, Room + SQLCipher, min Android 8.0.
- Database schema is **version 3** (see `data/Migrations.kt`). Any table change needs a version bump **and** a migration step, or clients' data will be lost on update.
- Unit tests cover PIN hashing, lockout, input formats, Wealth Statement maths and the backup file.

> This repository contains code only. No client data is ever stored here.
