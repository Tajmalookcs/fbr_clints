package pk.fbrclients.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pk.fbrclients.app.data.Client
import pk.fbrclients.app.data.ElectricityBill
import pk.fbrclients.app.data.Kind
import pk.fbrclients.app.data.WealthItem
import pk.fbrclients.app.data.sync.Sync
import pk.fbrclients.app.data.sync.SyncEngine
import pk.fbrclients.desktop.store.DataStore
import pk.fbrclients.desktop.ui.ClientSearch
import java.io.File
import java.nio.file.Files

/** All data here is made up. */
class DataStoreTest {
    private val dir: File = Files.createTempDirectory("fbr-pc-test").toFile()
    private var now = 1_000L
    private fun store() = DataStore(File(dir, "data.fbrpc"), File(dir, "data.prev"), File(dir, "keys.bin")) { now++ }

    @Test fun savesEncryptedAndLoadsAgain() {
        val s = store().also { it.load() }
        val id = s.saveClient(Client(cnic = "12345-1234567-1", name = "Test Person", irisPassword = "Secret@1"))
        val raw = File(dir, "data.fbrpc").readBytes()
        assertFalse("password is not readable in the file", String(raw, Charsets.ISO_8859_1).contains("Secret@1"))
        val again = store().also { it.load() }
        assertEquals("Test Person", again.data.clients.single { it.id == id }.name)
        assertEquals(12, again.data.categories.size)
    }

    @Test fun changesGetNewTimesAndKeepUid() {
        val s = store().also { it.load() }
        val id = s.saveClient(Client(cnic = "1234567", name = "A"))
        val first = s.data.clients.single()
        s.saveClient(first.copy(name = "B", uid = "something else", updatedAt = 0))
        val second = s.data.clients.single { it.id == id }
        assertEquals(first.uid, second.uid)
        assertTrue(second.updatedAt > first.updatedAt)
    }

    @Test fun deletingAClientRemovesItsDataAndLeavesTombstones() {
        val s = store().also { it.load() }
        val id = s.saveClient(Client(cnic = "1234567", name = "A"))
        val st = s.createYear(id, 2026, null)
        s.saveItem(WealthItem(statementId = st, categoryId = 5, description = "Car", amount = 100))
        s.saveBill(ElectricityBill(clientId = id, company = "FESCO", referenceNo = "12345678"))
        val uids = listOf(s.data.clients.single().uid, s.data.statements.single().uid, s.data.items.single().uid, s.data.bills.single().uid)
        s.deleteClient(id)
        assertTrue(s.data.clients.isEmpty() && s.data.statements.isEmpty() && s.data.items.isEmpty() && s.data.bills.isEmpty())
        assertEquals(uids.toSet(), s.deleted.map { it.uid }.toSet())
        assertEquals(Kind.CLIENT, s.deleted.single { it.uid == uids[0] }.kind)
    }

    @Test fun newYearCopiesItemsWithNewUids() {
        val s = store().also { it.load() }
        val id = s.saveClient(Client(cnic = "1234567", name = "A"))
        val y1 = s.createYear(id, 2025, null)
        s.saveItem(WealthItem(statementId = y1, categoryId = 5, description = "Car", amount = 100))
        val y2 = s.createYear(id, 2026, y1)
        val items = s.data.items
        assertEquals(2, items.size)
        assertEquals(2, items.map { it.uid }.toSet().size)
        assertEquals("Car", items.single { it.statementId == y2 }.description)
    }

    @Test fun categoryInUseCantBeDeleted() {
        val s = store().also { it.load() }
        val id = s.saveClient(Client(cnic = "1234567", name = "A"))
        val st = s.createYear(id, 2026, null)
        s.saveItem(WealthItem(statementId = st, categoryId = 5, description = "Car", amount = 100))
        assertFalse(s.deleteCategory(5))
    }

    @Test fun syncsWithThePhone() {
        // The phone's data, as the phone would send it.
        val phone = DataStore.empty().copy(clients = listOf(Client(id = 40, cnic = "12345-1234567-1", name = "From phone", uid = "p", updatedAt = 5_000)))
        val phoneFile = Sync.merge(Sync.toSync(phone, emptyList()), pk.fbrclients.app.data.sync.SyncSet())

        val pc = store().also { it.load() }
        pc.saveClient(Client(cnic = "1234567", name = "From PC"))
        val r = pc.syncWith { local, deleted ->
            val x = SyncEngine.run(local, deleted, listOf(phoneFile))
            Triple(x.newLocal, x.merged.deleted, x)
        }
        assertEquals(setOf("From phone", "From PC"), pc.data.clients.map { it.name }.toSet())
        assertTrue(r.writeRemote)
        // Built-in categories are the same records on both sides (fixed uids), not doubled.
        assertEquals(12, pc.data.categories.size)
    }

    @Test fun restoreReplacesEverythingAndRemembersDeletions() {
        val s = store().also { it.load() }
        s.saveClient(Client(cnic = "1234567", name = "Old"))
        val oldUid = s.data.clients.single().uid
        s.replaceAll(DataStore.empty().copy(clients = listOf(Client(id = 1, cnic = "7654321", name = "Restored"))))
        assertEquals("Restored", s.data.clients.single().name)
        assertTrue(s.deleted.any { it.uid == oldUid })
    }

    @Test fun searchLikeThePhone() {
        val all = listOf(
            Client(id = 1, cnic = "42101-1234567-1", name = "Zahid Traders", mobile = "0300-1234567"),
            Client(id = 2, cnic = "1234567", name = "ali khan", businessName = "Khan Steel"),
        )
        assertEquals(listOf("ali khan", "Zahid Traders"), ClientSearch.filter(all, "").map { it.name })
        assertEquals(listOf(1L), ClientSearch.filter(all, "4210112").map { it.id })
        assertEquals(listOf(2L), ClientSearch.filter(all, "steel").map { it.id })
        assertEquals(listOf(1L), ClientSearch.filter(all, "03001234567").map { it.id })
    }
}
