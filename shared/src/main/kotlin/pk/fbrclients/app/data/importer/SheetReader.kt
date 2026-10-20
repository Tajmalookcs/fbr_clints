package pk.fbrclients.app.data.importer

import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.math.BigDecimal
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Reads the first sheet of an .xlsx file, or a .csv file, into rows of text.
 * Plain JVM code (no Android), so it is unit-tested.
 */
object SheetReader {
    const val MAX_ROWS = 5_000
    private const val MAX_ZIP_BYTES = 50L * 1024 * 1024

    class UnsupportedFileException(message: String) : Exception(message)

    fun read(bytes: ByteArray): List<List<String>> = when {
        bytes.size >= 2 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte() -> readXlsx(bytes)
        bytes.size >= 4 && bytes[0] == 0xD0.toByte() && bytes[1] == 0xCF.toByte() && bytes[2] == 0x11.toByte() && bytes[3] == 0xE0.toByte() ->
            throw UnsupportedFileException("This is an old Excel file (.xls). In Excel, use Save As → “Excel Workbook (.xlsx)” or “CSV”, then import that file.")
        else -> readCsv(String(bytes, Charsets.UTF_8))
    }

    // ---------- CSV ----------

    fun readCsv(text: String): List<List<String>> {
        val t = text.removePrefix("﻿")
        val firstLine = t.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty()
        val sep = listOf(',', ';', '\t').maxBy { c -> firstLine.count { it == c } }
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0
        while (i < t.length) {
            val c = t[i]
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < t.length && t[i + 1] == '"') { cell.append('"'); i++ } else quoted = false
                } else cell.append(c)
            } else when (c) {
                '"' -> quoted = true
                sep -> { row.add(cell.toString()); cell.clear() }
                '\r' -> Unit
                '\n' -> {
                    row.add(cell.toString()); cell.clear()
                    rows.add(row); row = mutableListOf()
                    if (rows.size > MAX_ROWS) throw UnsupportedFileException("The file has more than $MAX_ROWS rows.")
                }
                else -> cell.append(c)
            }
            i++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) { row.add(cell.toString()); rows.add(row) }
        return rows
    }

    // ---------- XLSX ----------

    private fun readXlsx(bytes: ByteArray): List<List<String>> {
        val files = unzip(bytes)
        val shared = files["xl/sharedStrings.xml"]?.let(::sharedStrings).orEmpty()
        val sheet = files[firstSheetPath(files)] ?: files["xl/worksheets/sheet1.xml"]
            ?: throw UnsupportedFileException("No sheet found in this Excel file.")
        return sheetRows(sheet, shared)
    }

    private fun unzip(bytes: ByteArray): Map<String, ByteArray> {
        val out = HashMap<String, ByteArray>()
        var total = 0L
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                val name = e.name.removePrefix("/")
                if (e.isDirectory || !name.endsWith(".xml") && !name.endsWith(".rels")) continue
                val data = zip.readBytes()
                total += data.size
                if (total > MAX_ZIP_BYTES) throw UnsupportedFileException("The Excel file is too large.")
                out[name] = data
            }
        }
        if (out.isEmpty()) throw UnsupportedFileException("This file isn't an Excel (.xlsx) workbook.")
        return out
    }

    private fun firstSheetPath(files: Map<String, ByteArray>): String? {
        val wb = files["xl/workbook.xml"]?.let(::parse) ?: return null
        val rid = elements(wb, "sheet").firstOrNull()?.getAttribute("r:id")?.takeIf { it.isNotEmpty() } ?: return null
        val rels = files["xl/_rels/workbook.xml.rels"]?.let(::parse) ?: return null
        val target = elements(rels, "Relationship").firstOrNull { it.getAttribute("Id") == rid }?.getAttribute("Target") ?: return null
        return if (target.startsWith("/")) target.removePrefix("/") else "xl/" + target.removePrefix("./")
    }

    private fun sharedStrings(xml: ByteArray): List<String> =
        elements(parse(xml), "si").map { si -> text(si) }

    /** All <t> text inside [node], skipping phonetic hints (<rPh>). */
    private fun text(node: Node): String = buildString {
        fun walk(n: Node) {
            if (n is Element) {
                when (local(n)) {
                    "rPh" -> return
                    "t" -> { append(n.textContent); return }
                }
            }
            val kids = n.childNodes
            for (i in 0 until kids.length) walk(kids.item(i))
        }
        walk(node)
    }

    private fun sheetRows(xml: ByteArray, shared: List<String>): List<List<String>> {
        val doc = parse(xml)
        val rows = sortedMapOf<Int, MutableMap<Int, String>>()
        var nextRow = 0
        for (row in elements(doc, "row")) {
            val r = row.getAttribute("r").toIntOrNull()?.minus(1) ?: nextRow
            nextRow = r + 1
            if (rows.size >= MAX_ROWS) throw UnsupportedFileException("The sheet has more than $MAX_ROWS rows.")
            val cells = rows.getOrPut(r) { sortedMapOf() }
            var nextCol = 0
            for (c in childElements(row, "c")) {
                val col = columnIndex(c.getAttribute("r")) ?: nextCol
                nextCol = col + 1
                val v = childElements(c, "v").firstOrNull()?.textContent.orEmpty()
                cells[col] = when (c.getAttribute("t")) {
                    "s" -> v.trim().toIntOrNull()?.let { shared.getOrNull(it) }.orEmpty()
                    "inlineStr" -> childElements(c, "is").firstOrNull()?.let(::text).orEmpty()
                    "str", "e" -> v
                    "b" -> if (v == "1") "TRUE" else "FALSE"
                    else -> plainNumber(v)
                }
            }
        }
        if (rows.isEmpty()) return emptyList()
        return (0..rows.lastKey()).map { r ->
            val cells = rows[r] ?: return@map emptyList()
            if (cells.isEmpty()) emptyList() else (0..cells.keys.max()).map { cells[it].orEmpty() }
        }
    }

    /** Excel stores 1234512345671 as "1234512345671" or "1.234512345671E12". */
    fun plainNumber(v: String): String {
        val s = v.trim()
        if (s.isEmpty()) return ""
        return runCatching { BigDecimal(s).stripTrailingZeros().toPlainString() }.getOrDefault(s)
    }

    /** "B3" -> 1 */
    fun columnIndex(ref: String): Int? {
        val letters = ref.takeWhile { it.isLetter() }.uppercase()
        if (letters.isEmpty()) return null
        return letters.fold(0) { acc, ch -> acc * 26 + (ch - 'A' + 1) } - 1
    }

    // ---------- XML helpers ----------

    private fun parse(xml: ByteArray) = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = false
        isExpandEntityReferences = false
        runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
    }.newDocumentBuilder().parse(ByteArrayInputStream(xml)).documentElement

    /** Tag name without any "x:" prefix. */
    private fun local(e: Element) = e.nodeName.substringAfter(':')

    private fun elements(root: Node, name: String): List<Element> {
        val out = mutableListOf<Element>()
        fun walk(n: Node) {
            if (n is Element && local(n) == name) out.add(n)
            val kids = n.childNodes
            for (i in 0 until kids.length) walk(kids.item(i))
        }
        walk(root)
        return out
    }

    private fun childElements(parent: Node, name: String): List<Element> {
        val kids = parent.childNodes
        return (0 until kids.length).map { kids.item(it) }.filterIsInstance<Element>().filter { local(it) == name }
    }
}
