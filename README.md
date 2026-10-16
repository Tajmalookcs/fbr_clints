# FBR Clients

An offline, encrypted Android app for a tax practitioner. It keeps clients' IRIS logins and contact details, and a yearly Wealth Statement in the FBR IRIS format.

- Design: [docs/DESIGN.md](docs/DESIGN.md)
- Status: **Step 2 done** — lock screen, encrypted client database, client list + search, client page with Call / WhatsApp / SMS / Email / Copy / Open IRIS, add / edit / delete.

## Install on your phone

Open the **[latest build](../../releases/tag/latest-build)** on your phone, download the `.apk`, and open it. Android will ask you to allow installing from your browser the first time.

## How the APK is built

GitHub Actions (`.github/workflows/build.yml`) builds and tests the app on every push to `main`, then replaces the `latest-build` release.

Signing: the release key is rebuilt on each run from one GitHub Secret, `SIGNING_SEED` (see `scripts/derive_keystore.py`). The same seed always gives the same key, so every new build installs over the old one and keeps your data. **Keep a copy of the seed somewhere safe**: it is the only way to recreate the key. Without the secret, the build still runs but makes a `-TEST` APK that cannot update other builds.

> This repository contains code only. No client data is ever stored here.
