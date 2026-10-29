# FBR Clients — Approved Design

Status: **Design approved by the owner on 2026-10-16. All build-plan steps implemented (2026-10-16).**

Small additions made during the build (not in the original design):
- **IRIS registration** (owner, 2026-10-29): button on the client page (phone and PC) opens IRIS New Registration in the app browser; obvious empty boxes (CNIC, full name, mobile, email, address) are filled from the client once each, and fill buttons CNIC / Name / Mobile / Email / Address are shown; never presses Next / Submit. Phone browser: when an FBR / bill site asks for location or camera, Android's own permission question is shown and the answer passed on (other sites are refused; each request is listed in Page problems).
- **Reference** on each client (owner, 2026-10-21): who referred the client, free text; searchable, shown on the client page and list, Excel column "Reference" / "Ref", synced and backed up. Database schema version 5.
- **Downloads from the in-app browsers** (owner, 2026-10-21): before saving, the app asks for the file name, filled in as "<Client name> Return.pdf" (IRIS), "<Client name> FESCO bill.pdf" or "<Client name> FESCO tax certificate.pdf"; saved in Downloads, never overwriting ((1), (2) …). On the phone, files the page makes itself (IRIS Print, blob:/data:) are read from the page and saved too, and the page's Print command opens Android's print screen (Save as PDF), named the same way. PC: the same name box.
- **After saving a file** in the phone browser (owner, 2026-10-21): a bar with **WhatsApp client** (opens WhatsApp / WhatsApp Business with the PDF attached, straight to the client's chat when a mobile is saved, else the contact list), **Share…** and **Open**. Files from the site are fetched with the browser's login and saved under the chosen name.
- **Windows PC app and phone ↔ PC sync** (owner approved the plan, 2026-10-20): see §8 and §9. Database schema version 4.
- Clients and Wealth Statements can be deleted (with a confirmation).
- In the property form, a missing sub-type can be added straight from the sub-type list.
- Assets / Liabilities tabs show only group headings with one Add button that opens a list of types; categories appear once they have items (owner, 2026-10-17).
- **Open IRIS** (Chrome) copies the password, then the username, so the keyboard clipboard history shows 1st = username, 2nd = password; plain copies (not marked sensitive) so the keyboard keeps them; phone clipboard cleared after 60 s; reminder to clear the keyboard history (owner, 2026-10-20).
- IRIS username is filled / copied **without dashes** (1234512345671); shown with dashes in the app (owner, 2026-10-19).
- App browser opens sites in **desktop mode** by default (desktop user agent + 1280 px layout); pinch-zoom and moving always unlocked; windows opened by a site show on top with Close; ⋮ → Fit to screen; popups are kept no wider than the screen (width only; a height limit squashed IRIS popups); ⋮ → Safe mode turns off all page changes; pages load only after the browser view has its size; popup parts with max-height 0 (vh = 0 in WebView) get the real screen height; ⋮ → Page check describes popups for diagnosis; ⋮ → Page problems lists page errors (memory only); file upload boxes and site alert / confirm boxes work; frames inside a page always load; the "inside an app" request header is not sent (⋮ → Desktop site to switch); screenshots allowed (owner, 2026-10-19).
- In-app browser (owner, 2026-10-19): opens IRIS / bill / certificate pages inside the app, auto-fills empty login or reference boxes, and has Username / Password / Reference / NTN fill buttons. Never presses a page button. Only fbr.gov.pk, pitc.com.pk, fesco.com.pk and the links in Settings → Electricity companies (https only); other links open in Chrome. Cookies, storage and cache are wiped when it closes; Android autofill is blocked from saving passwords. Downloads go to Downloads; ⋮ → Save page as PDF.
- Electricity bills per client with bill / tax-certificate links; database schema version 3 (owner, 2026-10-19).
- Property land area entered in boxes with a calculated Total (owner, 2026-10-17). Database schema version 2 (upgrade from 1 keeps data).
- 7014 items have an optional "CNIC of holder" field (owner, 2026-10-17).
- Username may be an NTN for companies / AOPs; Business Name field added (owner, 2026-10-16).
- Import clients from Excel / CSV (owner request, 2026-10-16). Clients without a name get their CNIC as the name until edited.
- Wrong PINs: after 5 tries the keypad pauses (30 s, doubling each further try).

An offline Android app for one tax practitioner. It keeps clients' IRIS login details and contact information, plus a yearly **Wealth Statement** that follows the FBR IRIS layout.

---

## 1. Scope decisions

| Topic | Decision |
|---|---|
| Users | One person, one phone, and optionally one Windows PC (§8) |
| Connectivity | Offline database; no server. Optional sync with the PC through one encrypted file in the owner's Google Drive (§9). Internet is otherwise used **only** by the in-app browser for IRIS and the bill / tax-certificate sites |
| Backup | Encrypted backup file with its own password; restore on any phone |
| Language / stack | Kotlin, Jetpack Compose, Room (encrypted with SQLCipher), BiometricPrompt |
| Min Android | 8.0 (API 26) |
| Distribution | APK installed directly (not the Play Store), built by GitHub Actions |
| Return data | **Wealth Statement only.** No income or tax computation, filing status or PDF attachments |
| Data entry | Manual, plus import of clients (CNIC/username, password, optional name, NTN, mobile, email) from an Excel .xlsx file (only .xlsx; the file chooser shows all files and the app checks name and contents). No PDF import. |

## 2. Security

- Fingerprint or PIN unlock; the PIN is set on first launch.
- Auto-lock after about 60 seconds in the background (configurable).
- The whole database is encrypted (SQLCipher), with the key protected by the Android Keystore.
- IRIS password hidden by default: tap to reveal; tap to copy, and the clipboard clears after about 30 seconds.
- Screenshots are **allowed** everywhere in the app (owner's choice, 2026-10-19; was blocked before).
- Backup file (`.fbrbak`) encrypted with a user-chosen backup password (AES-GCM, key derived from the password).
- **This repo is public: never commit client data, sample returns, keystores or secrets.**

## 3. Client record

| Field | Notes |
|---|---|
| IRIS username* | CNIC `12345-1234567-1` for individuals, or NTN (`1234567` / `A123456`) for companies and AOPs |
| IRIS Password | Encrypted; hidden by default |
| Name* | |
| Business Name | Optional; shown on the client page and searchable |
| NTN | |
| Mobile | Format `03XX-XXXXXXX` |
| Email | Checked for a valid format |
| Address | |
| Notes | Free text |

Client screen buttons: **Call, WhatsApp, SMS, Email, Copy Username, Copy Password, Open IRIS** (iris.fbr.gov.pk).

Client screen also has **Electricity bills**: company (FESCO, GEPCO, HESCO, IESCO, LESCO, MEPCO, PESCO, QESCO, SEPCO, TESCO, K-Electric), reference number, optional label; buttons **Check bill**, **Tax certificate** (copy the reference, then open the company's official page) and **Copy ref**. Links are editable in Settings → Electricity companies; bill pages default to bill.pitc.com.pk/<company>bill, FESCO certificate page given by the owner, others empty until the owner adds them.

Client list: search by name, business name, CNIC, NTN or mobile.

## 4. Wealth Statement (one per client per tax year)

Hierarchy, matching IRIS: **Group → Category (IRIS code) → Items.**

### 4.1 Assets

**Immovable Properties (Non-Business)**: use the detailed property form (4.2)

| Code | Category |
|---|---|
| 7001 | Agricultural Property |
| 7002 | Commercial Property |
| ? | Industrial Property (code unknown; user adds it in Settings) |
| 7109 | Residential Property |

**Financial Assets & Investments (Non-Business)**: description + amount

**Moveable Assets (Non-Business)**: description + amount

| Code | Category |
|---|---|
| 7008 | Motor Vehicle(s) |
| 7009 | Precious Possession |
| 7010 | Household Effects |

**Other asset categories**: description + amount

| Code | Category |
|---|---|
| 7014 | Assets held in others' names (including non-filer spouse/dependents); optional CNIC of holder |
| 7013 | Any Other Asset(s) |
| 7003 | Business Capital |
| 700302 | Business Capital in AOPs/Companies |
| **7019** | **Total Assets** (calculated) |

### 4.2 Immovable property form

| Field | Type |
|---|---|
| Property type* | Agricultural / Commercial / Industrial / Residential |
| Property sub-type* | Dropdown that depends on the type |
| Date of Acquisition | Date |
| Land area* | Boxes: Acre, Kanal, Marla, Sarsahi, Sft, Sq m (fill any; at least one). **Total** = biggest filled unit in order Acre → Kanal → Marla → Sft (Sarsahi and Sq m count as Sft), rounded to 2 decimals. 1 Acre = 8 Kanal, 1 Kanal = 20 Marla, 1 Marla = 9 Sarsahi = 272 Sft, 1 Sq m = 10.7639 Sft. Example: 1 Acre 2 Kanal 200 Sft → 1.25 Acre; 4 Marla 118 Sft → 4.43 Marla |
| Property Address* | Free text: plot/house/shop number, mohalla, city, **share** (e.g. "1/2 share"), and any remarks |
| Acquisition cost/value | Amount; counts toward totals |

Not included on purpose: country, province, district, city, tehsil, and a separate share/remarks field. All of these go in **Property Address**.

Known sub-types:
- Residential: Constructed Property, Farm House, Flat, Open Plot
- Agricultural / Commercial / Industrial: unknown; the user adds them in Settings

Automatic IRIS-style description, with a copy button:
`[Type] – [Sub-type], [Total area] [Unit], [Property Address]` (only the Total, not each box)

Items may have **amount 0** (e.g. inherited or dowry items).

### 4.3 Liabilities

| Code | Category |
|---|---|
| 7021 | Payables (Borrowing / Loan / Credits, etc.): description + amount |
| **7029** | **Total Liabilities** (calculated) |

### 4.4 Net assets and reconciliation

| Code | Item |
|---|---|
| 703001 | Net Assets Current Year = 7019 − 7029 |
| 703002 | Net Assets Previous Year (filled from the prior year's statement; editable) |
| 703003 | Increase / Decrease in Assets |
| 7049 | Inflows = 7031 + 7032 + 7033 |
| 7031 | Income declared subject to normal tax |
| 7032 | Income declared exempt from tax |
| 7033 | Income attributable to receipts subject to final/fixed tax |
| 7099 / 7089 / 7087 | Outflows: personal / household expenses |
| 703000 | Unreconciled Amount = Inflows − Outflows − Increase (shown in red when not 0) |

### 4.5 Extras
- **New year from last year**: copies all items into a new tax year.
- Copy button on every item description, for pasting into IRIS.
- Amounts are whole rupees, shown as `1,234,567`.

## 5. Data model (Room)

- Every synced record also has **uid** and **updatedAt** (§9); deletions are kept in **sync_deleted** (uid, kind, deletedAt, replacedBy).
- **Client**: id, cnic (IRIS username: CNIC or NTN), irisPassword, name, businessName, ntn, mobile, email, address, notes, reference
- **WealthStatement**: id, clientId, taxYear, prevYearNetAssets, incomeNormal (7031), incomeExempt (7032), incomeFinal (7033), personalExpenses (7087)
- **Category**: code, name, group, kind (asset / liability), isProperty, sortOrder, editable
- **PropertySubType**: id, propertyCategoryCode, name
- **WealthItem**: id, statementId, categoryCode, description, amount, plus property-only fields: subType, acquisitionDate, areaUnit, area + areaUnit (calculated Total), areaAcre, areaKanal, areaMarla, areaSarsahi, areaSft, areaSqm, propertyAddress, holderCnic (7014 only)

## 6. Screens

1. Lock (fingerprint / PIN)
2. Client list + search
3. Client detail (profile, action buttons, list of wealth statements by year)
4. Add / edit client
5. Wealth Statement, with tabs: Assets / Liabilities / Reconciliation
6. Add / edit item (property form or description + amount)
7. Settings: change PIN, auto-lock time, manage categories and sub-types, backup and restore

## 7. Build plan

1. Project setup + GitHub Actions workflow that builds the APK + lock screen
2. Clients
3. Wealth Statement
4. Backup and restore
5. Release APK that the owner installs on their phone

There is no local Android Studio. The **APK is built by GitHub Actions** and published as a downloadable artifact/release, so the owner can install it from their phone.


## 8. Windows PC app (owner, 2026-10-20)

- A normal Windows program (not a web app), installed from an `.msi` file built by GitHub Actions next to the APK (latest build / release page). Windows 10 and 11.
- Same features and checks as the phone: clients (list, search, detail, add / edit / delete), Wealth Statement (assets, liabilities, reconciliation, property form with land-area boxes, 7014 holder CNIC), electricity bills, categories and sub-types, electricity-company links, Excel .xlsx import, `.fbrbak` backup and restore (the same files as the phone).
- **Lock:** a PIN (4–8 digits, same pause after wrong tries); locks after 1–60 minutes without use (default 5) and with the Lock button.
- **Data:** one encrypted file in `%APPDATA%\FBR Clients` (AES-256-GCM). Its key, the PIN hash and the sync password are locked to the Windows user with Windows data protection (DPAPI).
- **In-app browser on the PC** (owner, 2026-10-20): Chromium (the engine of Chrome, via JCEF) inside the app, about 170 MB in the installer, unpacked on first use. **IRIS in app** and **Check bill / Tax certificate** open in it and fill the username (no dashes) / password or the reference into empty boxes, again when IRIS changes page; never presses a button. Fill buttons (Username, Password, NTN, Reference) fill the box last clicked. Same sites as the phone (fbr.gov.pk, pitc.com.pk, fesco.com.pk and the Settings links, https only); other links open in the normal browser. Site popup windows open on top. Zoom −/100%/+, Save page as PDF and downloads go to Downloads. Pages, cookies and logins are kept in memory only and wiped when the last browser window closes.
- **Open IRIS in browser** (normal browser) copies the password, then the username: Ctrl+V pastes the username, Windows key + V shows the password as the 2nd item.
- Built with Kotlin + Compose for Desktop. Code shared with the phone: `shared/` (formats, maths, land area, models, import, backup, sync, PIN hashing) and `shared-ui/` (amount field, dropdown, dialogs).

## 9. Sync between phone and PC (owner, 2026-10-20)

- One sync file (`FBR-Clients.fbrsync`) in the owner's Google Drive. The phone opens it through Android's file picker (Google Drive appears there); the PC uses the Google Drive for Desktop folder. No Google sign-in or keys inside the app.
- The file is encrypted like a backup (AES-256-GCM, key from the **sync password** with PBKDF2, 310,000 rounds); Google only stores scrambled data. The sync password is typed once per device and kept locked (Android Keystore / Windows DPAPI). A backup restore refuses a sync file and the other way round.
- Each device keeps all its data and works offline. Sync runs after unlocking, every 5 minutes while the app is open, when it is closed / goes to the background, and with **Sync now**.
- Every record has a **uid** (the same on every device) and the time of its last change; deletions leave a tombstone. Merging: the newest change of each record wins; deletions spread; two clients with the same username are joined (the newest is kept, its statements and bills move over); two statements of the same client and tax year are joined (all items kept); a deleted category still used by an item is kept. The merge gives the same result on every device.
- Copies that Google Drive may make when both write at once (`FBR-Clients (1).fbrsync`) are read too, so nothing is lost.
- If the file can't be read (wrong password or damaged), the device says so and offers "Choose the sync file again" or "Replace the sync file with this device's data".
- Restoring a backup with sync on replaces the data on the other device too (the app warns first).
- Database (phone) **version 4**: columns `uid` and `updatedAt` on every synced table, table `sync_deleted` (tombstones); SQLite triggers keep them right on every save (see `data/Migrations.kt`). The upgrade from version 3 keeps all data and gives the built-in categories and sub-types fixed uids so both devices share them.
