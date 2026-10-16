package pk.fbrclients.app.data.importer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import pk.fbrclients.app.data.Client
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** All data here is made up. */
class ClientImportTest {

    @Test fun headerWithUsernameAndPassword() {
        val sheet = listOf(
            listOf("Sr", "User Name", "Password"),
            listOf("1", "1234512345671", "Abc@123"),
            listOf("2", "12345-1234567-2", " x y "),
        )
        val p = ClientImport.parse(sheet)
        assertTrue(p.hadHeader)
        assertEquals(listOf("12345-1234567-1", "12345-1234567-2"), p.rows.map { it.cnic })
        assertEquals("Abc@123", p.rows[0].password)
        assertEquals("x y", p.rows[1].password)
        assertEquals(3, p.rows[1].rowNumber)
    }

    @Test fun noHeaderUsesColumnsAandB() {
        val p = ClientImport.parse(listOf(listOf("1234512345671", "pw1"), listOf("1234512345672", "pw2")))
        assertEquals(2, p.rows.size)
        assertEquals("pw2", p.rows[1].password)
    }

    @Test fun badRowsAreReported() {
        val p = ClientImport.parse(
            listOf(
                listOf("CNIC", "Password", "Name", "Mobile"),
                listOf("12345", "pw", "", ""),
                listOf("1234512345671", "pw", "Test One", "3001234567"),
                listOf("1234512345671", "other", "", ""),
                listOf("", "", "", ""),
                listOf("1234512345673", "pw", "", "12"),
            ),
        )
        assertEquals(2, p.rows.size)
        assertEquals("0300-1234567", p.rows[0].mobile) // leading 0 restored
        assertEquals("Test One", p.rows[0].name)
        assertEquals(3, p.problems.size)
    }

    @Test fun missingColumnsFail() {
        assertThrows(ClientImport.NoColumnsException::class.java) {
            ClientImport.parse(listOf(listOf("Name", "City"), listOf("A", "B")))
        }
    }

    @Test fun headerNames() {
        assertEquals(ClientImport.Field.CNIC, ClientImport.fieldFor("User Name"))
        assertEquals(ClientImport.Field.CNIC, ClientImport.fieldFor("IRIS ID"))
        assertEquals(ClientImport.Field.PASSWORD, ClientImport.fieldFor("PASSWORD"))
        assertEquals(ClientImport.Field.NAME, ClientImport.fieldFor("Client Name"))
        assertEquals(ClientImport.Field.MOBILE, ClientImport.fieldFor("Cell No."))
    }

    @Test fun planAddsUpdatesAndSkips() {
        val existing = listOf(
            Client(id = 1, cnic = "12345-1234567-1", irisPassword = "old", name = "Known Client"),
            Client(id = 2, cnic = "12345-1234567-2", irisPassword = "same", name = "12345-1234567-2"),
        )
        val rows = listOf(
            ClientImport.Row(2, "12345-1234567-1", "new"),
            ClientImport.Row(3, "12345-1234567-2", "same"),
            ClientImport.Row(4, "12345-1234567-3", "pw", name = ""),
        )
        val plan = ClientImport.plan(rows, existing, updatePasswords = true)
        assertEquals(1, plan.toAdd.size)
        assertEquals("12345-1234567-3", plan.toAdd[0].name) // CNIC until a name is entered
        assertEquals(listOf("new"), plan.toUpdate.map { it.irisPassword })
        assertEquals("Known Client", plan.toUpdate[0].name)
        assertEquals(1, plan.unchanged)

        val keep = ClientImport.plan(rows, existing, updatePasswords = false)
        assertEquals(0, keep.toUpdate.size)
        assertEquals(2, keep.unchanged)
    }

    // ---------- File reading ----------

    @Test fun csvWithQuotesAndSemicolons() {
        val rows = SheetReader.readCsv("﻿Username;Password\r\n1234512345671;\"a;b\"\"c\"\r\n")
        assertEquals(listOf("Username", "Password"), rows[0])
        assertEquals(listOf("1234512345671", "a;b\"c"), rows[1])
    }

    @Test fun numbersFromExcel() {
        assertEquals("1234512345671", SheetReader.plainNumber("1.234512345671E12"))
        assertEquals("1234512345671", SheetReader.plainNumber("1234512345671"))
        assertEquals("12345", SheetReader.plainNumber("12345.0"))
        assertEquals(27, SheetReader.columnIndex("AB12"))
    }

    @Test fun readsXlsx() {
        val xlsx = zip(
            "[Content_Types].xml" to "<Types/>",
            "xl/workbook.xml" to """<workbook xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Clients" sheetId="1" r:id="rId7"/></sheets></workbook>""",
            "xl/_rels/workbook.xml.rels" to """<Relationships><Relationship Id="rId7" Type="worksheet" Target="worksheets/data.xml"/></Relationships>""",
            "xl/sharedStrings.xml" to """<sst><si><t>Username</t></si><si><r><t>Pass</t></r><r><t>word</t></r></si><si><t>p@ss&amp;1</t></si></sst>""",
            "xl/worksheets/data.xml" to """<worksheet><sheetData>
                <row r="1"><c r="A1" t="s"><v>0</v></c><c r="B1" t="s"><v>1</v></c></row>
                <row r="3"><c r="A3"><v>1.234512345671E12</v></c><c r="B3" t="s"><v>2</v></c></row>
                <row r="4"><c r="B4" t="inlineStr"><is><t>inline</t></is></c><c r="A4" t="str"><v>12345-1234567-2</v></c></row>
                <row r="5"><c r="A5"><v>1234512345673</v></c><c r="B5"><v>987654</v></c></row>
                </sheetData></worksheet>""",
        )
        val sheet = SheetReader.read(xlsx)
        assertEquals(listOf("Username", "Password"), sheet[0])
        assertEquals(emptyList<String>(), sheet[1])
        assertEquals(listOf("1234512345671", "p@ss&1"), sheet[2])
        assertEquals(listOf("12345-1234567-2", "inline"), sheet[3])

        val p = ClientImport.parse(sheet)
        assertEquals(listOf("p@ss&1", "inline", "987654"), p.rows.map { it.password })
        assertEquals(5, p.rows[2].rowNumber)
    }

    @Test fun oldXlsIsRejected() {
        val e = assertThrows(SheetReader.UnsupportedFileException::class.java) {
            SheetReader.read(byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte(), 0, 0))
        }
        assertTrue(e.message!!.contains(".xlsx"))
    }

    private fun zip(vararg files: Pair<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            for ((name, text) in files) {
                z.putNextEntry(ZipEntry(name))
                z.write(text.toByteArray())
                z.closeEntry()
            }
        }
        return out.toByteArray()
    }
}
