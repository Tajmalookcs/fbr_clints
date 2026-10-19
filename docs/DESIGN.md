# FBR Clients — Approved Design

Status: **Design approved by the owner on 2026-10-16. All build-plan steps implemented (2026-10-16).**

Small additions made during the build (not in the original design):
- Clients and Wealth Statements can be deleted (with a confirmation).
- In the property form, a missing sub-type can be added straight from the sub-type list.
- Assets / Liabilities tabs show only group headings with one Add button that opens a list of types; categories appear once they have items (owner, 2026-10-17).
- App browser opens sites in **desktop mode** by default (⋮ → Desktop site to switch); screenshots allowed (owner, 2026-10-19).
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
| Users | One person, one phone |
| Connectivity | Offline database; no server or cloud sync. Internet is used **only** by the in-app browser for IRIS and the bill / tax-certificate sites |
| Backup | Encrypted backup file with its own password; restore on any phone |
| Language / stack | Kotlin, Jetpack Compose, Room (encrypted with SQLCipher), BiometricPrompt |
| Min Android | 8.0 (API 26) |
| Distribution | APK installed directly (not the Play Store), built by GitHub Actions |
| Return data | **Wealth Statement only.** No income or tax computation, filing status or PDF attachments |
| Data entry | Manual, plus import of clients (CNIC/username, password, optional name, NTN, mobile, email) from an Excel .xlsx or CSV file. No PDF import. |

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

- **Client**: id, cnic (IRIS username: CNIC or NTN), irisPassword, name, businessName, ntn, mobile, email, address, notes
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
