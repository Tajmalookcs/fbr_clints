package pk.fbrclients.app.data.importer

import pk.fbrclients.app.data.Client
import pk.fbrclients.app.util.Formats

/**
 * Turns spreadsheet rows into clients. Columns are found from the header row
 * (e.g. "Username", "Password", "Name", "Business Name", "Cell No", "Address").
 * Without a header, column A is the username (CNIC or NTN) and column B the password.
 */
object ClientImport {
    enum class Field { USERNAME, PASSWORD, NAME, BUSINESS, NTN, MOBILE, EMAIL, ADDRESS, REFERENCE }

    data class Row(
        val rowNumber: Int,
        /** CNIC (12345-1234567-1) or NTN (1234567 / A123456). */
        val username: String,
        /** Exactly as in the file; never trimmed or changed. */
        val password: String,
        val name: String = "",
        val businessName: String = "",
        val ntn: String = "",
        val mobile: String = "",
        val email: String = "",
        val address: String = "",
        /** Who referred the client. */
        val reference: String = "",
    )

    data class Parsed(val rows: List<Row>, val problems: List<String>, val hadHeader: Boolean)

    class NoColumnsException : Exception(
        "Couldn't find the username and password columns. Put them in columns A and B, or give them headers like “Username” and “Password”.",
    )

    /**
     * Which field a header cell names, or null. Order matters:
     * "User Name" is the username and "Business Name" the business, not the client name.
     */
    fun fieldFor(header: String): Field? {
        val h = header.lowercase().filter { it.isLetter() }
        // Words, so that "NTN" isn't found inside "clieNTName".
        val words = header.lowercase().split(Regex("[^a-z]+")).filter { it.isNotEmpty() }
        return when {
            h.isEmpty() -> null
            "pass" in h || "pwd" in h -> Field.PASSWORD
            "cnic" in h || "user" in h || "login" in h || h == "id" || h == "irisid" -> Field.USERNAME
            words.any { it == "ntn" || it.startsWith("ntn") } -> Field.NTN
            "mobile" in h || "phone" in h || "cell" in h || "contact" in h || "whatsapp" in h -> Field.MOBILE
            "mail" in h -> Field.EMAIL
            "business" in h || "company" in h || "firm" in h || "shop" in h -> Field.BUSINESS
            "address" in h || words.any { it == "addr" } -> Field.ADDRESS
            "reference" in h || "referred" in h || words.any { it == "ref" || it == "refer" } -> Field.REFERENCE
            "name" in h -> Field.NAME
            else -> null
        }
    }

    private fun headerColumns(row: List<String>): Map<Field, Int>? {
        val map = LinkedHashMap<Field, Int>()
        row.forEachIndexed { i, cell -> fieldFor(cell)?.let { f -> map.putIfAbsent(f, i) } }
        return map.takeIf { Field.USERNAME in it && Field.PASSWORD in it }
    }

    fun parse(sheet: List<List<String>>): Parsed {
        val firstRows = sheet.withIndex().filter { (_, r) -> r.any { it.isNotBlank() } }.take(10)
        val header = firstRows.firstNotNullOfOrNull { (i, r) -> headerColumns(r)?.let { i to it } }

        val (startIndex, cols) = when {
            header != null -> header.first + 1 to header.second
            firstRows.isNotEmpty() -> {
                val (i, first) = firstRows.first()
                if (Formats.normalizeUsername(first.getOrElse(0) { "" }) == null) throw NoColumnsException()
                i to mapOf(Field.USERNAME to 0, Field.PASSWORD to 1)
            }
            else -> throw NoColumnsException()
        }

        val rows = mutableListOf<Row>()
        val problems = mutableListOf<String>()
        val seen = HashMap<String, Int>()
        for (i in startIndex until sheet.size) {
            val r = sheet[i]
            if (r.all { it.isBlank() }) continue
            val n = i + 1 // row number as shown in Excel
            fun raw(f: Field) = cols[f]?.let { r.getOrNull(it) }.orEmpty()
            fun get(f: Field) = raw(f).trim()

            val rawUser = get(Field.USERNAME)
            val username = Formats.normalizeUsername(rawUser)
            if (username == null) {
                problems += "Row $n: “${rawUser.ifEmpty { "empty" }}” is not a 13-digit CNIC or a 7-character NTN — skipped"
                continue
            }
            val firstSeen = seen[username]
            if (firstSeen != null) {
                problems += "Row $n: same username as row $firstSeen — skipped"
                continue
            }
            seen[username] = n

            var mobile = Formats.normalizeMobileDigits(get(Field.MOBILE))
            if (mobile.isNotEmpty() && !Formats.isValidMobile(mobile)) {
                problems += "Row $n: mobile “${get(Field.MOBILE)}” isn't a Pakistani mobile — left empty"
                mobile = ""
            }
            var email = get(Field.EMAIL)
            if (email.isNotEmpty() && !Formats.isValidEmail(email)) {
                problems += "Row $n: email “$email” isn't valid — left empty"
                email = ""
            }
            rows += Row(
                rowNumber = n,
                username = username,
                password = raw(Field.PASSWORD),
                name = get(Field.NAME),
                businessName = get(Field.BUSINESS),
                ntn = get(Field.NTN),
                mobile = if (mobile.isEmpty()) "" else Formats.formatMobile(mobile),
                email = email,
                address = get(Field.ADDRESS),
                reference = get(Field.REFERENCE),
            )
        }
        return Parsed(rows, problems, hadHeader = header != null)
    }

    /** What importing would do, compared with the clients already on the phone. */
    data class Plan(
        val toAdd: List<Client>,
        /** Existing clients with a changed password (and any empty fields filled in). */
        val toUpdate: List<Client>,
        val unchanged: Int,
    )

    fun plan(rows: List<Row>, existing: List<Client>, updatePasswords: Boolean): Plan {
        val byUsername = existing.associateBy { it.cnic }
        val add = mutableListOf<Client>()
        val update = mutableListOf<Client>()
        var same = 0
        for (r in rows) {
            val old = byUsername[r.username]
            if (old == null) {
                add += Client(
                    cnic = r.username,
                    irisPassword = r.password,
                    // Name is required; until it's known, show the business name or the username.
                    name = r.name.ifBlank { r.businessName.ifBlank { r.username } },
                    businessName = r.businessName,
                    ntn = r.ntn, mobile = r.mobile, email = r.email, address = r.address,
                    reference = r.reference,
                )
                continue
            }
            val merged = old.copy(
                irisPassword = if (updatePasswords && r.password.isNotEmpty()) r.password else old.irisPassword,
                name = if (old.name.isBlank() || old.name == old.cnic) r.name.ifBlank { old.name } else old.name,
                businessName = old.businessName.ifBlank { r.businessName },
                ntn = old.ntn.ifBlank { r.ntn },
                address = old.address.ifBlank { r.address },
                mobile = old.mobile.ifBlank { r.mobile },
                email = old.email.ifBlank { r.email },
                reference = old.reference.ifBlank { r.reference },
            )
            if (merged == old) same++ else update += merged
        }
        return Plan(add, update, same)
    }
}
