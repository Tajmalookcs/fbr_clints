package pk.fbrclients.app.data.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import pk.fbrclients.app.data.BackupData
import pk.fbrclients.app.data.BackupFile
import pk.fbrclients.app.data.Client
import pk.fbrclients.app.data.DiscoSeed
import pk.fbrclients.app.data.ElectricityBill
import pk.fbrclients.app.data.Kind
import pk.fbrclients.app.data.SeedData
import pk.fbrclients.app.data.Tombstone
import pk.fbrclients.app.data.WealthItem
import pk.fbrclients.app.data.WealthStatement

/** All data here is made up. */
class SyncTest {
    private val cats = SeedData.categories()

    private fun device(vararg clients: Client, statements: List<WealthStatement> = emptyList(), items: List<WealthItem> = emptyList(),
                       bills: List<ElectricityBill> = emptyList()) =
        BackupData(clients.toList(), cats, emptyList(), statements, items, bills, DiscoSeed.ALL)

    private fun client(id: Long, cnic: String, name: String, at: Long, uid: String = "c$id-$cnic") =
        Client(id = id, cnic = cnic, name = name, irisPassword = "pw", uid = uid, updatedAt = at)

    /** One full sync of [d] against [file]; returns the device's new data and the new file. */
    private fun sync(d: BackupData, deleted: List<Tombstone>, file: SyncSet?): Pair<BackupData, SyncSet> {
        val r = SyncEngine.run(d, deleted, listOfNotNull(file))
        return r.newLocal to r.merged
    }

    @Test fun builtInCategoriesHaveFixedUids() {
        assertEquals("builtin-cat-1", cats[0].uid)
        assertEquals(cats.size, cats.map { it.uid }.toSet().size)
    }

    @Test fun newDeviceGetsEverything() {
        val phone = device(client(1, "12345-1234567-1", "Test One", 100), client(2, "1234567", "Test Traders", 100))
        val (_, file) = sync(phone, emptyList(), null)
        val (pc, _) = sync(device(), emptyList(), file)
        assertEquals(listOf("Test One", "Test Traders"), pc.clients.map { it.name }.sorted())
        assertEquals(cats.map { it.uid }.sorted(), pc.categories.map { it.uid }.sorted())
    }

    @Test fun newestChangeWins() {
        val a = device(client(1, "12345-1234567-1", "Old name", 100, uid = "u1"))
        val b = device(client(7, "12345-1234567-1", "New name", 200, uid = "u1"))
        val m1 = Sync.merge(Sync.toSync(a, emptyList()), Sync.toSync(b, emptyList()))
        val m2 = Sync.merge(Sync.toSync(b, emptyList()), Sync.toSync(a, emptyList()))
        assertEquals(m1, m2) // same result whichever side merges
        assertEquals("New name", m1.clients.single().name)
        // The phone keeps its own number for the client.
        assertEquals(1L, Sync.applyTo(a, m1).clients.single().id)
    }

    @Test fun deletionSpreads() {
        val c = client(1, "12345-1234567-1", "Test One", 100, uid = "u1")
        val pcFile = Sync.toSync(device(c), emptyList())
        val phoneAfterDelete = device()
        val tomb = Tombstone("u1", Kind.CLIENT, 150)
        val (_, file) = sync(phoneAfterDelete, listOf(tomb), pcFile)
        assertTrue(file.clients.isEmpty())
        val (pc, _) = sync(device(c), emptyList(), file)
        assertTrue(pc.clients.isEmpty())
    }

    @Test fun editAfterDeleteKeepsTheClient() {
        val edited = client(1, "12345-1234567-1", "Edited later", 300, uid = "u1")
        val m = Sync.merge(Sync.toSync(device(edited), emptyList()), SyncSet(deleted = listOf(Tombstone("u1", Kind.CLIENT, 200))))
        assertEquals("Edited later", m.clients.single().name)
    }

    @Test fun sameUsernameOnBothDevicesIsJoined() {
        // Both devices imported the same client separately: different uids, same CNIC.
        val phone = device(
            client(1, "12345-1234567-1", "Phone copy", 100, uid = "p1"),
            statements = listOf(WealthStatement(id = 1, clientId = 1, taxYear = 2025, uid = "s-phone", updatedAt = 100)),
            bills = listOf(ElectricityBill(id = 1, clientId = 1, company = "FESCO", referenceNo = "123", uid = "b1", updatedAt = 100)),
        )
        val pc = device(
            client(1, "12345-1234567-1", "PC copy", 200, uid = "c1"),
            statements = listOf(WealthStatement(id = 1, clientId = 1, taxYear = 2026, uid = "s-pc", updatedAt = 200)),
        )
        val m = Sync.merge(Sync.toSync(phone, emptyList()), Sync.toSync(pc, emptyList()))
        assertEquals("PC copy", m.clients.single().name)
        assertEquals(setOf(2025, 2026), m.statements.map { it.s.taxYear }.toSet())
        assertTrue(m.statements.all { it.clientUid == "c1" })
        assertEquals("c1", m.bills.single().clientUid)
        assertEquals("c1", m.deleted.single { it.uid == "p1" }.replacedBy)

        // The phone applies it: one client, both years, the bill moved over.
        val newPhone = Sync.applyTo(phone, m)
        assertEquals(1, newPhone.clients.size)
        assertEquals(2, newPhone.statements.size)
        assertTrue(newPhone.statements.all { it.clientId == newPhone.clients.single().id })
        assertEquals(newPhone.clients.single().id, newPhone.bills.single().clientId)

        // An older copy of the file still pointing at the joined client is redirected again.
        val again = Sync.merge(m, Sync.toSync(phone, emptyList()))
        assertEquals(m, again)
    }

    @Test fun sameTaxYearTwiceKeepsAllItems() {
        val c = client(1, "12345-1234567-1", "Test", 100, uid = "u1")
        val sA = WealthStatement(id = 1, clientId = 1, taxYear = 2026, uid = "sa", updatedAt = 100)
        val sB = WealthStatement(id = 1, clientId = 1, taxYear = 2026, uid = "sb", updatedAt = 120)
        val car = WealthItem(id = 1, statementId = 1, categoryId = cats[4].id, description = "Car", amount = 1_000_000, uid = "i1", updatedAt = 100)
        val gold = WealthItem(id = 1, statementId = 1, categoryId = cats[5].id, description = "Gold", amount = 500_000, uid = "i2", updatedAt = 110)
        val m = Sync.merge(
            Sync.toSync(device(c, statements = listOf(sA), items = listOf(car)), emptyList()),
            Sync.toSync(device(c, statements = listOf(sB), items = listOf(gold)), emptyList()),
        )
        assertEquals("sb", m.statements.single().s.uid)
        assertEquals(setOf("Car", "Gold"), m.items.map { it.i.description }.toSet())
        assertTrue(m.items.all { it.statementUid == "sb" })
    }

    @Test fun deletedCategoryStillInUseIsKept() {
        val c = client(1, "12345-1234567-1", "Test", 100, uid = "u1")
        val s = WealthStatement(id = 1, clientId = 1, taxYear = 2026, uid = "s1", updatedAt = 100)
        val item = WealthItem(id = 1, statementId = 1, categoryId = cats[4].id, description = "Car", amount = 1, uid = "i1", updatedAt = 100)
        val other = SyncSet(deleted = listOf(Tombstone(cats[4].uid, Kind.CATEGORY, 500)))
        val m = Sync.merge(Sync.toSync(device(c, statements = listOf(s), items = listOf(item)), emptyList()), other)
        assertTrue(m.categories.any { it.uid == cats[4].uid })
        assertEquals(1, m.items.size)
    }

    @Test fun nothingToWriteWhenAlreadyEqual() {
        val phone = device(client(1, "12345-1234567-1", "Test", 100))
        val first = SyncEngine.run(phone, emptyList(), emptyList())
        assertTrue(first.writeRemote)
        assertTrue(first.diff.isEmpty)
        val second = SyncEngine.run(first.newLocal, first.merged.deleted, listOf(first.merged))
        assertFalse(second.writeRemote)
        assertTrue(second.diff.isEmpty)
    }

    @Test fun newRecordsGetNewNumbers() {
        val phone = device(client(5, "12345-1234567-1", "Mine", 100, uid = "a"))
        val pc = device(client(5, "12345-1234567-2", "Other", 100, uid = "b"))
        val r = SyncEngine.run(phone, emptyList(), listOf(Sync.toSync(pc, emptyList())))
        assertEquals(listOf(5L, 6L), r.newLocal.clients.map { it.id }.sorted())
        assertEquals(1, r.diff.clients.added.size)
        assertEquals(6L, r.diff.clients.added.single().id)
    }

    @Test fun fileRoundTripAndPassword() {
        val d = device(client(1, "12345-1234567-1", "Test \"quoted\" ✓", 100),
            statements = listOf(WealthStatement(id = 1, clientId = 1, taxYear = 2026, incomeNormal = 1_000_000, uid = "s", updatedAt = 1)),
            items = listOf(WealthItem(id = 1, statementId = 1, categoryId = 4, description = "Residential – Flat", amount = 0, areaMarla = "5", uid = "i", updatedAt = 1)))
        val set = Sync.merge(Sync.toSync(d, listOf(Tombstone("x", Kind.CLIENT, 9, "y"))), SyncSet())
        val bytes = SyncFile.write(set, "sync pass 1".toCharArray(), "test", iterations = 10_000)
        assertEquals(set, SyncFile.read(bytes, "sync pass 1".toCharArray()))
        assertThrows(BackupFile.WrongPasswordException::class.java) { SyncFile.read(bytes, "wrong".toCharArray()) }
        // A backup restore refuses the sync file.
        assertThrows(BackupFile.NotABackupException::class.java) {
            pk.fbrclients.app.data.BackupJson.read(BackupFile.decrypt(bytes, "sync pass 1".toCharArray()))
        }
    }

    @Test fun phoneAndPcEndUpEqual() {
        // Phone and PC change different things between syncs; after both sync twice they match.
        var phone = device(client(1, "12345-1234567-1", "A", 100, uid = "a"))
        var pc = device()
        var file: SyncSet? = null
        sync(phone, emptyList(), file).also { phone = it.first; file = it.second }
        sync(pc, emptyList(), file).also { pc = it.first; file = it.second }
        phone = phone.copy(clients = phone.clients.map { it.copy(name = "A from phone", updatedAt = 300) })
        pc = pc.copy(clients = pc.clients + client(9, "1234567", "New on PC", 250, uid = "n"))
        sync(phone, file!!.deleted, file).also { phone = it.first; file = it.second }
        sync(pc, file!!.deleted, file).also { pc = it.first; file = it.second }
        sync(phone, file!!.deleted, file).also { phone = it.first; file = it.second }
        assertEquals(Sync.toSync(phone, file!!.deleted).normalized().clients, Sync.toSync(pc, file!!.deleted).normalized().clients)
        assertEquals(setOf("A from phone", "New on PC"), pc.clients.map { it.name }.toSet())
    }
}
