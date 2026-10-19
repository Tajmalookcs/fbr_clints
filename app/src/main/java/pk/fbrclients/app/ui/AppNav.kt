package pk.fbrclients.app.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import pk.fbrclients.app.FbrApp
import pk.fbrclients.app.ui.browser.BrowserScreen
import pk.fbrclients.app.ui.clients.ClientDetailScreen
import pk.fbrclients.app.ui.clients.ClientEditScreen
import pk.fbrclients.app.ui.clients.ClientEditViewModel
import pk.fbrclients.app.ui.clients.ClientListScreen
import pk.fbrclients.app.ui.clients.ClientListViewModel
import pk.fbrclients.app.ui.settings.CategoriesScreen
import pk.fbrclients.app.ui.settings.ChangePinScreen
import pk.fbrclients.app.ui.settings.DiscosScreen
import pk.fbrclients.app.ui.settings.ImportScreen
import pk.fbrclients.app.ui.settings.ImportViewModel
import pk.fbrclients.app.ui.settings.SettingsScreen
import pk.fbrclients.app.ui.wealth.ItemEditScreen
import pk.fbrclients.app.ui.wealth.ItemEditViewModel
import pk.fbrclients.app.ui.wealth.StatementScreen
import pk.fbrclients.app.ui.wealth.StatementViewModel

private const val NEW = -1L

private fun longArgs(vararg names: String) = names.map { navArgument(it) { type = NavType.LongType } }

@Composable
fun AppNav(app: FbrApp) {
    val nav = rememberNavController()
    val db = app.database
    val clients = db.clients()
    val wealth = db.wealth()

    NavHost(navController = nav, startDestination = "clients") {
        composable("clients") {
            ClientListScreen(
                vm = viewModel { ClientListViewModel(clients) },
                onOpen = { nav.navigate("client/$it") },
                onAdd = { nav.navigate("edit/$NEW") },
                onLock = app.appLock::lockNow,
                onSettings = { nav.navigate("settings") },
                onImport = { nav.navigate("settings/import") },
            )
        }
        composable("client/{id}", arguments = longArgs("id")) { entry ->
            val id = entry.arguments!!.getLong("id")
            ClientDetailScreen(
                id = id,
                dao = clients,
                wealth = wealth,
                electricity = db.electricity(),
                clipboard = app.clipboard,
                onBack = { nav.popBackStack() },
                onEdit = { nav.navigate("edit/$id") },
                onOpenStatement = { nav.navigate("statement/$it") },
                onOpenBrowser = { url, ref ->
                    nav.navigate("browser/$id?url=${Uri.encode(url)}&ref=${Uri.encode(ref.orEmpty())}")
                },
            )
        }
        composable("edit/{id}", arguments = longArgs("id")) { entry ->
            val id = entry.arguments!!.getLong("id").takeIf { it != NEW }
            ClientEditScreen(
                vm = viewModel { ClientEditViewModel(clients, id) },
                onBack = { nav.popBackStack() },
                onSaved = { savedId ->
                    if (id == null) {
                        // New client: replace the form with the client's page.
                        nav.navigate("client/$savedId") { popUpTo("clients") }
                    } else {
                        nav.popBackStack()
                    }
                },
                onDeleted = { nav.popBackStack("clients", inclusive = false) },
            )
        }
        composable("statement/{id}", arguments = longArgs("id")) { entry ->
            val id = entry.arguments!!.getLong("id")
            StatementScreen(
                vm = viewModel { StatementViewModel(wealth, clients, id) },
                clipboard = app.clipboard,
                onBack = { nav.popBackStack() },
                onAddItem = { categoryId -> nav.navigate("item/$id/$categoryId/$NEW") },
                onEditItem = { item -> nav.navigate("item/$id/${item.categoryId}/${item.id}") },
                onDeleted = { nav.popBackStack() },
            )
        }
        composable("item/{statementId}/{categoryId}/{itemId}", arguments = longArgs("statementId", "categoryId", "itemId")) { entry ->
            val a = entry.arguments!!
            val statementId = a.getLong("statementId")
            val categoryId = a.getLong("categoryId")
            val itemId = a.getLong("itemId").takeIf { it != NEW }
            ItemEditScreen(
                vm = viewModel { ItemEditViewModel(wealth, statementId, categoryId, itemId) },
                clipboard = app.clipboard,
                onBack = { nav.popBackStack() },
                onDone = { nav.popBackStack() },
            )
        }
        composable("settings") {
            SettingsScreen(
                app = app,
                onBack = { nav.popBackStack() },
                onChangePin = { nav.navigate("settings/pin") },
                onCategories = { nav.navigate("settings/categories") },
                onImport = { nav.navigate("settings/import") },
                onDiscos = { nav.navigate("settings/discos") },
                onRestored = { /* Data changed underneath every screen: stay here; lists refresh on their own. */ },
            )
        }
        composable("settings/pin") {
            ChangePinScreen(app.pinStore, onDone = { nav.popBackStack() })
        }
        composable(
            "browser/{clientId}?url={url}&ref={ref}",
            arguments = listOf(
                navArgument("clientId") { type = NavType.LongType },
                navArgument("url") { type = NavType.StringType; defaultValue = "" },
                navArgument("ref") { type = NavType.StringType; defaultValue = "" },
            ),
        ) { entry ->
            val a = entry.arguments!!
            BrowserScreen(
                startUrl = a.getString("url").orEmpty(),
                clientId = a.getLong("clientId"),
                reference = a.getString("ref")?.takeIf { it.isNotEmpty() },
                clients = clients,
                electricity = db.electricity(),
                onClose = { nav.popBackStack() },
            )
        }
        composable("settings/discos") {
            DiscosScreen(db.electricity(), onBack = { nav.popBackStack() })
        }
        composable("settings/import") {
            ImportScreen(vm = viewModel { ImportViewModel(app) }, onBack = { nav.popBackStack() })
        }
        composable("settings/categories") {
            CategoriesScreen(wealth, onBack = { nav.popBackStack() })
        }
    }
}
