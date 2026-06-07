package com.example.notes.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.notes.NotesApplication
import com.example.notes.ui.screens.CategoriesScreen
import com.example.notes.ui.screens.NoteEditScreen
import com.example.notes.ui.screens.NotesListScreen
import com.example.notes.ui.screens.SearchScreen
import com.example.notes.ui.screens.SettingsScreen
import com.example.notes.ui.viewmodel.NotesViewModel

object Routes {
    const val LIST = "list"
    const val EDIT = "edit/{noteId}"
    const val CATEGORIES = "categories"
    const val SEARCH = "search"
    const val SETTINGS = "settings"

    fun edit(noteId: Long) = "edit/$noteId"
}

@Composable
fun NotesNavGraph(viewModel: NotesViewModel) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val repository = remember {
        (context.applicationContext as NotesApplication).repository
    }

    NavHost(navController = navController, startDestination = Routes.LIST) {
        composable(Routes.LIST) {
            NotesListScreen(
                viewModel = viewModel,
                onAddNote = { navController.navigate(Routes.edit(0L)) },
                onOpenNote = { id -> navController.navigate(Routes.edit(id)) },
                onOpenCategories = { navController.navigate(Routes.CATEGORIES) },
                onOpenSearch = { navController.navigate(Routes.SEARCH) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) }
            )
        }
        composable(
            route = Routes.EDIT,
            arguments = listOf(navArgument("noteId") { type = NavType.LongType })
        ) { entry ->
            val id = entry.arguments?.getLong("noteId") ?: 0L
            NoteEditScreen(
                noteId = id,
                viewModel = viewModel,
                repository = repository,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.CATEGORIES) {
            CategoriesScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.SEARCH) {
            SearchScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onOpenNote = { id -> navController.navigate(Routes.edit(id)) }
            )
        }
        composable(Routes.SETTINGS) {
            val prefsRepository = remember {
                (context.applicationContext as NotesApplication).preferencesRepository
            }
            val uiState by viewModel.uiState.collectAsState(initial = null)
            SettingsScreen(
                preferencesRepository = prefsRepository,
                onSyncNow = { viewModel.syncNotes() },
                onBack = { navController.popBackStack() },
                isSyncing = uiState?.isSyncing ?: false
            )
        }
    }
}
