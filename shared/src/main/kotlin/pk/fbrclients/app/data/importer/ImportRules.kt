package pk.fbrclients.app.data.importer

/** Import accepts only Excel .xlsx files (owner's choice, 2026-10-20). Plain JVM code, tested. */
object ImportRules {
    fun isXlsxName(name: String) = name.trim().lowercase().endsWith(".xlsx")

    /** Names that are clearly another kind of file (the content is checked anyway). */
    fun isOtherKnownFile(name: String): Boolean {
        val n = name.trim().lowercase()
        return n.endsWith(".fbrbak") || n.endsWith(".xls") || n.endsWith(".csv")
    }

    /** .xlsx files are zip archives: they start with "PK". */
    fun isZip(bytes: ByteArray) = bytes.size >= 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()

    private fun isOldXls(bytes: ByteArray) = bytes.size >= 4 && bytes[0] == 0xD0.toByte() && bytes[1] == 0xCF.toByte() &&
        bytes[2] == 0x11.toByte() && bytes[3] == 0xE0.toByte()

    /** A real .xlsx is a zip that holds "xl/workbook.xml", whatever the file is called. */
    fun isXlsxWorkbook(bytes: ByteArray): Boolean {
        if (!isZip(bytes)) return false
        return runCatching {
            java.util.zip.ZipInputStream(java.io.ByteArrayInputStream(bytes)).use { z ->
                generateSequence { z.nextEntry }.any { it.name.removePrefix("/").equals("xl/workbook.xml", ignoreCase = true) }
            }
        }.getOrDefault(false)
    }

    fun wrongFileMessage(name: String, bytes: ByteArray? = null): String = when {
        name.lowercase().endsWith(".fbrbak") -> "This is a backup file. To use it, go to Settings → Restore from backup."
        name.lowercase().endsWith(".xls") || (bytes != null && isOldXls(bytes)) ->
            "This is an old Excel file (.xls). In Excel, use Save As → “Excel Workbook (.xlsx)”, then import that file."
        else -> "Please choose an Excel .xlsx file."
    }
}
