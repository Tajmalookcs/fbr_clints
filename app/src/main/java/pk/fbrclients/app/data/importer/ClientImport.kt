package pk.fbrclients.app.data.importer

import pk.fbrclients.app.data.Client
import pk.fbrclients.app.util.Formats

/**
 * Turns spreadsheet rows into clients. Columns are found from the header row
 * (e.g. "Username", "CNIC", "Password", "Name"). Without a header, column A
 * is the CNIC/username and column B the password.
 */
object ClientImport {
    enum class Field { CNIC, PASSWORD, NAME, NTN, MOBILE, EMAIL }

    data class Row(
        val rowNumber: Int,
        val cnic: String,
        val password: String,
        val name: String = "",
        val ntn: String = "",
        val mobile: String = "",
        val email: String = "",
    )

    data class Parsed(val rows: List<Row>, val problems: List<String>, val hadHeader: Boolean)

    class NoColumnsException : Exception(
        "Couldn't find the CNIC/username and password columns. Put them in columns A and B, or give them headers like “Username” and “Password”.",
    )

    /** Which field a header cell names, or null. Order matters: "User Name" is the CNIC, not the client name. */
    fun fieldFor(header: String): Field? {
        val h = header.lowercase().filter { it.isLetter() }
        return when {
            h.isEmpty() -> null
            "pass" in h || "pwd" in h -> Field.PASSWORD
            "cnic" in h || "user" in h || "login" in h || h == "id" || h == "irisid" -> Field.CNIC
            "ntn" in h -> Field.NTN
            "mobile" in h || "phone" in h || "cell" in h || "contact" in h || "whatsapp" in h -> Field.MOBILE
            "mail" in h -> Field.EMAIL
            "name" in h -> Field.NAME
            else -> null
        }
    }

    private fun headerColumns(row: List<String>): Map<Field, Int>? {
        val map = LinkedHashMap<Field, Int>()
        row.forEachIndexed { i, cell -> fieldFor(cell)?.let { f -> map.putIfAbsent(f, i) } }
        return map.takeIf { Field.CNIC in it && Field.PASSWORD in it }
    }

    fun parse(sheet: List<List<String>>): Parsed {
        val firstRows = sheet.withIndex().filter { (_, r) -> r.any { it.isNotBlank() } }.take(10)
        val header = firstRows.firstNotNullOfOrNull { (i, r) -> headerColumns(r)?.let { i to it } }

        val (startIndex, cols) = when {
            header != null -> header.first + 1 to header.second
            firstRows.isNotEmpty() -> {
                val (i, first) = firstRows.first()
                if (Formats.digitsOnly(first.getOrElse(0) { "" }).length != Formats.CNIC_DIGITS) throw NoColumnsException()
                i to mapOf(Field.CNIC to 0, Field.PASSWORD to 1)
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
            fun get(f: Field) = cols[f]?.let { r.getOrNull(it) }?.trim().orEmpty()

            val rawCnic = get(Field.CNIC)
            val cnicDigits = Formats.digitsOnly(rawCnic)
            if (cnicDigits.length != Formats.CNIC_DIGITS) {
                problems += "Row $n: “${rawCnic.ifEmpty { "empty" }}” is not a 13-digit CNIC — skipped"
                continue
            }
            val firstSeen = seen[cnicDigits]
            if (firstSeen != null) {
                problems += "Row $n: same CNIC as row $firstSeen — skipped"
                continue
            }
            seen[cnicDigits] = n

            var mobile = Formats.digitsOnly(get(Field.MOBILE))
            if (mobile.length == 10 && mobile.startsWith("3")) mobile = "0$mobile" // Excel dropped the leading 0
            if (mobile.length == 12 && mobile.startsWith("923")) mobile = "0" + mobile.drop(2)
            if (mobile.isNotEmpty() && !Formats.isValidMobile(mobile)) {
                problems += "Row $n: mobile “${get(Field.MOBILE)}” isn't valid — left empty"
                mobile = ""
            }
            var email = get(Field.EMAIL)
            if (email.isNotEmpty() && !Formats.isValidEmail(email)) {
                problems += "Row $n: email “$email” isn't valid — left empty"
                email = ""
            }
            rows += Row(
                rowNumber = n,
                cnic = Formats.formatCnic(cnicDigits),
                password = get(Field.PASSWORD),
                name = get(Field.NAME),
                ntn = get(Field.NTN),
                mobile = if (mobile.isEmpty()) "" else Formats.formatMobile(mobile),
                email = email,
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
        val byCnic = existing.associateBy { it.cnic }
        val add = mutableListOf<Client>()
        val update = mutableListOf<Client>()
        var same = 0
        for (r in rows) {
            val old = byCnic[r.cnic]
            if (old == null) {
                add += Client(
                    cnic = r.cnic,
                    irisPassword = r.password,
                    // Name is required; until it's known, show the CNIC.
                    name = r.name.ifBlank { r.cnic },
                    ntn = r.ntn, mobile = r.mobile, email = r.email,
                )
                continue
            }
            val merged = old.copy(
                irisPassword = if (updatePasswords && r.password.isNotEmpty()) r.password else old.irisPassword,
                name = if (old.name.isBlank() || old.name == old.cnic) r.name.ifBlank { old.name } else old.name,
                ntn = old.ntn.ifBlank { r.ntn },
                mobile = old.mobile.ifBlank { r.mobile },
                email = old.email.ifBlank { r.email },
            )
            if (merged == old) same++ else update += merged
        }
        return Plan(add, update, same)
    }
}
