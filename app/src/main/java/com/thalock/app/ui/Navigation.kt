package com.thalock.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.thalock.app.data.model.DocumentCategory
import com.thalock.app.data.model.DocumentType
import com.thalock.app.ui.screens.add.AddDocumentFormScreen
import com.thalock.app.ui.screens.add.DocumentTypeSelectionScreen
import com.thalock.app.ui.screens.document.DocumentDetailScreen
import com.thalock.app.ui.screens.files.FilesScreen
import com.thalock.app.ui.screens.folder.FolderDetailScreen
import com.thalock.app.ui.screens.home.HomeScreen
import com.thalock.app.ui.screens.settings.SettingsScreen
import com.thalock.app.ui.theme.LocalSpacing

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Scan : Screen("scan")
    data object Files : Screen("files")
    data object Settings : Screen("settings")
    data object AddDocumentForm : Screen("add/{type}") {
        fun createRoute(type: DocumentType) = "add/${type.name}"
    }
    data object DocumentDetail : Screen("document/{id}") {
        fun createRoute(id: Long) = "document/$id"
    }
    data object FolderDetail : Screen("folder/{category}") {
        fun createRoute(category: DocumentCategory) = "folder/${category.name}"
    }
}

private data class BottomNavItem(
    val screen: Screen,
    val label: String,
    val selectedIcon: androidx.compose.ui.graphics.vector.ImageVector,
    val unselectedIcon: androidx.compose.ui.graphics.vector.ImageVector
)

private val bottomNavItems = listOf(
    BottomNavItem(Screen.Home, "Vault", Icons.Filled.Lock, Icons.Outlined.Lock),
    BottomNavItem(Screen.Scan, "Scan", Icons.Filled.DocumentScanner, Icons.Outlined.DocumentScanner),
    BottomNavItem(Screen.Files, "Files", Icons.Filled.Folder, Icons.Outlined.Folder),
    BottomNavItem(Screen.Settings, "Settings", Icons.Filled.Settings, Icons.Outlined.Settings),
)

@Composable
fun ThaLockNavGraph(navController: NavHostController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val showBottomBar = currentRoute in listOf(
        Screen.Home.route, Screen.Scan.route,
        Screen.Files.route, Screen.Settings.route
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showBottomBar) {
                PillBottomBar(
                    isSelected = { screen ->
                        navBackStackEntry?.destination?.hierarchy?.any {
                            it.route == screen.route
                        } == true
                    },
                    onSelect = { screen ->
                        navController.navigate(screen.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    onDocumentClick = { id ->
                        navController.navigate(Screen.DocumentDetail.createRoute(id))
                    },
                    onAddClick = {
                        navController.navigate(Screen.Scan.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onFolderClick = { category ->
                        navController.navigate(Screen.FolderDetail.createRoute(category))
                    }
                )
            }

            composable(Screen.Scan.route) {
                DocumentTypeSelectionScreen(
                    onTypeSelected = { type ->
                        navController.navigate(Screen.AddDocumentForm.createRoute(type))
                    },
                    onBack = {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }

            composable(
                route = Screen.AddDocumentForm.route,
                arguments = listOf(
                    navArgument("type") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val typeName = backStackEntry.arguments?.getString("type") ?: return@composable
                val documentType = runCatching { DocumentType.valueOf(typeName) }.getOrNull()
                    ?: return@composable

                AddDocumentFormScreen(
                    documentType = documentType,
                    onBack = { navController.popBackStack() },
                    onSaved = {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                inclusive = false
                            }
                            launchSingleTop = true
                        }
                    }
                )
            }

            composable(Screen.Files.route) {
                FilesScreen()
            }

            composable(Screen.Settings.route) {
                SettingsScreen()
            }

            composable(
                route = Screen.DocumentDetail.route,
                arguments = listOf(navArgument("id") { type = NavType.LongType })
            ) { backStackEntry ->
                val documentId = backStackEntry.arguments?.getLong("id") ?: return@composable
                DocumentDetailScreen(
                    documentId = documentId,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(
                route = Screen.FolderDetail.route,
                arguments = listOf(navArgument("category") { type = NavType.StringType })
            ) { backStackEntry ->
                val catName = backStackEntry.arguments?.getString("category")
                    ?: return@composable
                val category = runCatching { DocumentCategory.valueOf(catName) }.getOrNull()
                    ?: return@composable
                FolderDetailScreen(
                    category = category,
                    onBack = { navController.popBackStack() },
                    onDocumentClick = { id ->
                        navController.navigate(Screen.DocumentDetail.createRoute(id))
                    }
                )
            }
        }
    }
}

@Composable
private fun PillBottomBar(
    isSelected: (Screen) -> Boolean,
    onSelect: (Screen) -> Unit
) {
    val spacing = LocalSpacing.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = spacing.md, vertical = spacing.md)
    ) {
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            shadowElevation = 4.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                bottomNavItems.forEach { item ->
                    val selected = isSelected(item.screen)
                    PillNavItem(
                        label = item.label,
                        icon = if (selected) item.selectedIcon else item.unselectedIcon,
                        selected = selected,
                        onClick = { onSelect(item.screen) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PillNavItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    val bg = if (selected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent
    val content = if (selected) MaterialTheme.colorScheme.onSurface
    else MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = content,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = content,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}
