package pk.fbrclients.app.ui.browser

/**
 * File names for downloads from the in-app browsers (owner, 2026-10-21):
 * "<Client name> Return.pdf" from IRIS, "<Client name> FESCO bill.pdf" from a bill page,
 * "<Client name> FESCO tax certificate.pdf" from a certificate page. The app asks before
 * saving, so the owner can change it.
 */
object DownloadNames {
    private val NOT_ALLOWED = Regex("""[\\/:*?"<>|\u0000-\u001f]""")

    /** What the page is, from its address. */
    fun label(pageUrl: String): String {
        val host = BrowserRules.host(pageUrl).orEmpty()
        val path = runCatching { java.net.URI(pageUrl.trim()).path.orEmpty() }.getOrDefault("").lowercase()
        return when {
            host == "fbr.gov.pk" || host.endsWith(".fbr.gov.pk") -> "Return"
            host.endsWith("pitc.com.pk") -> Regex("""/([a-z]+)bill""").find(path)?.groupValues?.get(1)?.uppercase()?.let { "$it bill" } ?: "Bill"
            host.endsWith("fesco.com.pk") -> "FESCO tax certificate"
            else -> "Download"
        }
    }

    /** The file type from the site's own file name or type; PDF when unknown. */
    fun extension(originalName: String?, mime: String?): String {
        val fromName = originalName?.substringAfterLast('.', "")?.lowercase()?.takeIf { it.length in 2..5 && it.all(Char::isLetterOrDigit) && it != "bin" }
        val fromMime = when (mime?.lowercase()?.substringBefore(';')?.trim()) {
            "application/pdf" -> "pdf"
            "image/png" -> "png"
            "image/jpeg" -> "jpg"
            "text/html" -> "html"
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> "xlsx"
            "application/vnd.ms-excel" -> "xls"
            "text/csv" -> "csv"
            else -> null
        }
        return fromName ?: fromMime ?: "pdf"
    }

    fun suggest(clientName: String?, pageUrl: String, originalName: String?, mime: String?): String {
        val who = clientName?.trim().orEmpty()
        val base = listOf(who, label(pageUrl)).filter { it.isNotEmpty() }.joinToString(" ")
        return clean(base) + "." + extension(originalName, mime)
    }

    /** A name Windows and Android accept; keeps [extension] when the owner typed the name without it. */
    fun finalName(typed: String, extension: String): String {
        val t = clean(typed).ifEmpty { "Download" }
        return if (t.lowercase().endsWith(".${extension.lowercase()}")) t else "$t.$extension"
    }

    private fun clean(s: String) = s.replace(NOT_ALLOWED, "_").replace(Regex("\\s+"), " ").trim().trimEnd('.').take(150)
}
