package pk.fbrclients.app.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import pk.fbrclients.app.FbrApp
import pk.fbrclients.app.ui.clients.ClientDetailScreen
import pk.fbrclients.app.ui.clients.ClientEditScreen
import pk.fbrclients.app.ui.clients.ClientEditViewModel
import pk.fbrclients.app.ui.clients.ClientListScreen
import pk.fbrclients.app.ui.clients.ClientListViewModel

private const val NEW = -1L

@Composable
fun AppNav(app: FbrApp) {
    val nav = rememberNavController()
    val dao = app.database.clients()

    NavHost(navController = nav, startDestination = "clients") {
        composable("clients") {
            ClientListScreen(
                vm = viewModel { ClientListViewModel(dao) },
                onOpen = { nav.navigate("client/$it") },
                onAdd = { nav.navigate("edit/$NEW") },
                onLock = app.appLock::lockNow,
            )
        }
        composable(
            "client/{id}",
            arguments = listOf(navArgument("id") { type = NavType.LongType }),
        ) { entry ->
            val id = entry.arguments!!.getLong("id")
            ClientDetailScreen(
                id = id,
                dao = dao,
                clipboard = app.clipboard,
                onBack = { nav.popBackStack() },
                onEdit = { nav.navigate("edit/$id") },
            )
        }
        composable(
            "edit/{id}",
            arguments = listOf(navArgument("id") { type = NavType.LongType }),
        ) { entry ->
            val id = entry.arguments!!.getLong("id").takeIf { it != NEW }
            ClientEditScreen(
                vm = viewModel { ClientEditViewModel(dao, id) },
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
    }
}
