package pk.fbrclients.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupFileTest {
    private val iter = 10_000 // faster than the real 310,000; same logic

    private val sample = BackupData(
        clients = listOf(
            Client(id = 1, cnic = "12345-1234567-1", irisPassword = "p@ss \"quoted\" ✓", name = "Test Client", businessName = "Test Traders",
                ntn = "1234567-8", mobile = "0300-1234567", email = "t@example.pk", address = "Line 1\nLine 2", notes = ""),
        ),
        categories = listOf(
            Category(id = 1, code = "7109", name = "Residential Property", group = AssetGroup.IMMOVABLE, isProperty = true, sortOrder = 40, builtIn = true, editable = false),
            Category(id = 2, code = null, name = "Industrial Property", group = AssetGroup.IMMOVABLE, isProperty = true, sortOrder = 30, builtIn = true, editable = true),
            Category(id = 3, code = "7021", name = "Payables", group = AssetGroup.LIABILITY, sortOrder = 120, builtIn = true, editable = false),
        ),
        subTypes = listOf(PropertySubType(id = 1, categoryId = 1, name = "Flat")),
        statements = listOf(
            WealthStatement(id = 1, clientId = 1, taxYear = 2025, prevYearNetAssets = null, incomeNormal = 1_000_000),
            WealthStatement(id = 2, clientId = 1, taxYear = 2026, prevYearNetAssets = 900_000, personalExpenses = 400_000),
        ),
        items = listOf(
            WealthItem(id = 1, statementId = 2, categoryId = 1, description = "Residential – Flat, 5 Marla, X", amount = 0,
                subType = "Flat", acquisitionDate = "2019-03-12", areaUnit = "Marla", area = "5", propertyAddress = "X"),
            WealthItem(id = 2, statementId = 2, categoryId = 3, description = "Loan", amount = 250_000),
        ),
    )

    @Test fun jsonRoundTrip() {
        assertEquals(sample, BackupJson.read(BackupJson.write(sample, "2026-10-16")))
    }

    @Test fun encryptedRoundTrip() {
        val file = BackupFile.encrypt(BackupJson.write(sample, "2026-10-16"), "correct horse".toCharArray(), iter)
        val back = BackupJson.read(BackupFile.decrypt(file, "correct horse".toCharArray()))
        assertEquals(sample, back)
    }

    @Test fun wrongPasswordIsRejected() {
        val file = BackupFile.encrypt(byteArrayOf(1, 2, 3), "correct horse".toCharArray(), iter)
        assertThrows(BackupFile.WrongPasswordException::class.java) { BackupFile.decrypt(file, "wrong horse".toCharArray()) }
    }

    @Test fun tamperingIsDetected() {
        val file = BackupFile.encrypt(byteArrayOf(1, 2, 3), "correct horse".toCharArray(), iter)
        file[file.size - 1] = (file[file.size - 1].toInt() xor 1).toByte()
        assertThrows(BackupFile.WrongPasswordException::class.java) { BackupFile.decrypt(file, "correct horse".toCharArray()) }
    }

    @Test fun otherFilesAreRejected() {
        assertThrows(BackupFile.NotABackupException::class.java) {
            BackupFile.decrypt("%PDF-1.7 not a backup at all, just some bytes".toByteArray(), "x".toCharArray())
        }
    }
}
