package com.example.notes.nav

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
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
import com.example.notes.ui.screens.StatsScreen
import com.example.notes.ui.screens.TagsScreen
import com.example.notes.ui.screens.TodoListScreen
import com.example.notes.ui.screens.TrashScreen
import com.example.notes.ui.viewmodel.NotesViewModel
import com.example.notes.WidgetIntent

object Routes {
    const val LIST = "list"
    const val EDIT = "edit/{noteId}"
    const val CATEGORIES = "categories"
    const val TAGS = "tags"
    const val SEARCH = "search"
    const val SETTINGS = "settings"
    // F2: 回收站
    const val TRASH = "trash"
    // F13: 统计仪表盘
    const val STATS = "stats"
    // 待办任务
    const val TODOS = "todos"

    fun edit(noteId: Long) = "edit/$noteId"
}

@Composable
fun NotesNavGraph(
    viewModel: NotesViewModel,
    widgetIntent: WidgetIntent? = null
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val repository = remember {
        (context.applicationContext as NotesApplication).repository
    }

    // F3 + F4: 桌面小部件 / 快捷方式启动时直接跳到目标页
    androidx.compose.runtime.LaunchedEffect(widgetIntent) {
        when (widgetIntent) {
            is WidgetIntent.NewNote -> {
                navController.navigate(Routes.edit(0L)) { launchSingleTop = true }
            }
            is WidgetIntent.OpenNote -> {
                navController.navigate(Routes.edit(widgetIntent.noteId)) { launchSingleTop = true }
            }
            is WidgetIntent.OpenSearch -> {
                navController.navigate(Routes.SEARCH) { launchSingleTop = true }
            }
            is WidgetIntent.OpenTrash -> {
                navController.navigate(Routes.TRASH) { launchSingleTop = true }
            }
            null -> Unit
        }
    }

    NavHost(
        navController = navController,
        startDestination = Routes.LIST,
        enterTransition = {
            slideInHorizontally(
                initialOffsetX = { 300 },
                animationSpec = tween(300)
            ) + fadeIn(animationSpec = tween(300))
        },
        exitTransition = {
            slideOutHorizontally(
                targetOffsetX = { -300 },
                animationSpec = tween(300)
            ) + fadeOut(animationSpec = tween(300))
        },
        popEnterTransition = {
            slideInHorizontally(
                initialOffsetX = { -300 },
                animationSpec = tween(300)
            ) + fadeIn(animationSpec = tween(300))
        },
        popExitTransition = {
            slideOutHorizontally(
                targetOffsetX = { 300 },
                animationSpec = tween(300)
            ) + fadeOut(animationSpec = tween(300))
        }
    ) {
        composable(Routes.LIST) {
            NotesListScreen(
                viewModel = viewModel,
                onAddNote = { navController.navigate(Routes.edit(0L)) { launchSingleTop = true } },
                onOpenNote = { id -> navController.navigate(Routes.edit(id)) { launchSingleTop = true } },
                onOpenCategories = { navController.navigate(Routes.CATEGORIES) { launchSingleTop = true } },
                onOpenTags = { navController.navigate(Routes.TAGS) { launchSingleTop = true } },
                onOpenSearch = { navController.navigate(Routes.SEARCH) { launchSingleTop = true } },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) { launchSingleTop = true } },
                // F2: 回收站
                onOpenTrash = { navController.navigate(Routes.TRASH) { launchSingleTop = true } },
                // F13: 统计
                onOpenStats = { navController.navigate(Routes.STATS) { launchSingleTop = true } },
                // 待办任务
                onOpenTodos = { navController.navigate(Routes.TODOS) { launchSingleTop = true } }
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
                onOpenNote = { id -> navController.navigate(Routes.edit(id)) { launchSingleTop = true } }
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.TAGS) {
            TagsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
        // F2: 回收站
        composable(Routes.TRASH) {
            TrashScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
        // F13: 统计
        composable(Routes.STATS) {
            StatsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
        // 待办任务
        composable(Routes.TODOS) {
            TodoListScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
