package pk.fbrclients.desktop

import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Assume.assumeTrue
import org.junit.Test
import pk.fbrclients.app.data.Client
import pk.fbrclients.app.data.ElectricityBill
import pk.fbrclients.app.data.WealthItem
import pk.fbrclients.desktop.store.DataStore
import pk.fbrclients.desktop.store.Settings
import pk.fbrclients.desktop.ui.FbrTheme
import pk.fbrclients.desktop.ui.ItemEditDialog
import pk.fbrclients.desktop.ui.LockScreen
import pk.fbrclients.desktop.ui.MainScreen
import pk.fbrclients.desktop.ui.Page
import pk.fbrclients.desktop.ui.SettingsScreen
import pk.fbrclients.desktop.ui.StatementScreen
import java.io.File
import java.nio.file.Files

/**
 * Draws the main screens with made-up data into PNG files, to check the layout.
 * Runs only when SCREENSHOTS_DIR is set (CI review builds).
 */
@OptIn(ExperimentalComposeUiApi::class)
class ScreenshotTest {
    private val out = System.getenv("SCREENSHOTS_DIR")?.let(::File)

    private fun shot(name: String, w: Int = 1280, h: Int = 820, content: @Composable () -> Unit) {
        val scene = ImageComposeScene(w, h, Density(1f)) { FbrTheme { content() } }
        try {
            repeat(3) { scene.render(it * 500_000_000L) }
            val img = scene.render(2_000_000_000L)
            File(out, "$name.png").writeBytes(img.encodeToData(EncodedImageFormat.PNG)!!.bytes)
        } finally {
            scene.close()
        }
    }

    @Test fun screens() {
        assumeTrue(out != null)
        out!!.mkdirs()
        val dir = Files.createTempDirectory("fbr-shots").toFile()
        val store = DataStore(File(dir, "d"), File(dir, "p"), File(dir, "k"))
        store.load()
        val a = store.saveClient(Client(cnic = "12345-1234567-1", name = "Test Person One", businessName = "Test Traders", irisPassword = "Abc@123",
            ntn = "1234567", mobile = "0300-1234567", email = "test@example.pk", address = "House 1, Street 2, Faisalabad"))
        store.saveClient(Client(cnic = "1234568", name = "Sample Steel (AOP)", mobile = "0321-7654321"))
        store.saveClient(Client(cnic = "12345-7654321-3", name = "Another Client"))
        store.saveBill(ElectricityBill(clientId = a, company = "FESCO", referenceNo = "12345678901234", label = "Shop"))
        val y25 = store.createYear(a, 2025, null)
        store.saveItem(WealthItem(statementId = y25, categoryId = 4, description = "", amount = 2_500_000))
        val y26 = store.createYear(a, 2026, null)
        store.saveItem(WealthItem(statementId = y26, categoryId = 4, description = "Residential – Flat, 5 Marla, House 1, Street 2, Faisalabad", amount = 3_000_000,
            subType = "Flat", areaMarla = "5", area = "5", areaUnit = "Marla", propertyAddress = "House 1, Street 2, Faisalabad"))
        store.saveItem(WealthItem(statementId = y26, categoryId = 5, description = "Honda City 2019", amount = 2_800_000))
        store.saveItem(WealthItem(statementId = y26, categoryId = 12, description = "Loan from brother", amount = 500_000))

        val settings = Settings(File(dir, "settings.json"))
        val state = AppState(store, settings)

        shot("01-setup-pin") { LockScreen(state) }
        settings.setPin("1234")
        state.hasPin = true
        shot("02-unlock") { LockScreen(state) }
        state.selectedClientId = a
        shot("03-clients") { MainScreen(state) }
        state.editingClientId = a
        shot("04-edit-client") { MainScreen(state) }
        state.editingClientId = null
        state.screen = Screen.Statement(y26)
        shot("05-statement-assets") { MainScreen(state) }
        shot("06-reconciliation") { StatementScreen(state, y26, initialTab = 2) }
        val flat = store.data.items.first { it.statementId == y26 && it.categoryId == 4L }
        shot("07-property-dialog", h = 900) { ItemEditDialog(state, y26, 4, flat, onClose = {}) }
        state.screen = Screen.Settings
        shot("08-settings") { MainScreen(state) }
        shot("09-sync") { SettingsScreen(state, Page.Sync) }
        shot("10-categories") { SettingsScreen(state, Page.Categories) }
        shot("11-import") { SettingsScreen(state, Page.Import) }
    }
}
