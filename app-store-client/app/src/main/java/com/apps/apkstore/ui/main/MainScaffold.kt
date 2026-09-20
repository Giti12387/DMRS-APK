package com.apps.apkstore.ui.main

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.hilt.navigation.compose.hiltViewModel
import com.apps.apkstore.ui.theme.*
import com.apps.apkstore.ui.downloads.DownloadsScreen
import com.apps.apkstore.ui.updates.UpdatesScreen
import com.apps.apkstore.ui.settings.SettingsScreen
import com.apps.apkstore.ui.detail.AppDetailScreen
import com.apps.apkstore.ui.category.CategoryScreen
import com.apps.apkstore.ui.search.SearchScreen
import com.apps.apkstore.ui.profile.ProfileScreen

sealed class BottomNavItem(val route: String, val selectedIcon: ImageVector, val unselectedIcon: ImageVector, val label: String) {
    data object Home : BottomNavItem("home", Icons.Filled.Home, Icons.Outlined.Home, "Home")
    data object Explore : BottomNavItem("explore", Icons.Filled.Explore, Icons.Outlined.Explore, "Explore")
    data object Search : BottomNavItem("search_nav", Icons.Filled.Search, Icons.Outlined.Search, "Search")
    data object Updates : BottomNavItem("updates", Icons.Filled.SystemUpdate, Icons.Outlined.SystemUpdate, "Updates")
    data object Profile : BottomNavItem("profile", Icons.Filled.Person, Icons.Outlined.Person, "Profile")
}

val bottomNavItems = listOf(BottomNavItem.Home, BottomNavItem.Explore, BottomNavItem.Search, BottomNavItem.Updates, BottomNavItem.Profile)

@Composable
fun MainScaffold(onLogout: () -> Unit, onThemeChanged: (Int) -> Unit = {}, currentThemeMode: Int = 0, deepLinkAppId: String? = null) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val showBottomBar = currentDestination?.route in bottomNavItems.map { it.route }

    LaunchedEffect(deepLinkAppId) {
        if (!deepLinkAppId.isNullOrEmpty()) {
            navController.navigate("detail/$deepLinkAppId")
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface, tonalElevation = 0.dp) {
                    bottomNavItems.forEach { item ->
                        val selected = currentDestination?.hierarchy?.any { it.route == item.route } == true
                        NavigationBarItem(
                            icon = { Icon(if (selected) item.selectedIcon else item.unselectedIcon, contentDescription = item.label, modifier = Modifier.size(24.dp)) },
                            label = { Text(item.label, fontSize = 11.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal) },
                            selected = selected,
                            onClick = { navController.navigate(item.route) { popUpTo(navController.graph.findStartDestination().id) { saveState = true }; launchSingleTop = true; restoreState = true } },
                            colors = NavigationBarItemDefaults.colors(selectedIconColor = Primary, selectedTextColor = Primary, unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), indicatorColor = Primary.copy(alpha = 0.1f))
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(navController = navController, startDestination = "home", modifier = Modifier.fillMaxSize().then(Modifier.padding(innerPadding))) {
            composable("home") { HomeScreen(viewModel = hiltViewModel(), onAppClick = { navController.navigate("detail/${it.id}") }, onCategoryClick = { navController.navigate("category/$it") }, onSearchClick = { navController.navigate("search_nav") }) }
            composable("explore") { ExploreScreen(viewModel = hiltViewModel(), onAppClick = { navController.navigate("detail/${it.id}") }, onCategoryClick = { navController.navigate("category/$it") }) }
            composable("search_nav") { SearchScreen(viewModel = hiltViewModel(), onAppClick = { navController.navigate("detail/${it.id}") }, onBack = { navController.popBackStack() }) }
            composable("updates") { UpdatesScreen(viewModel = hiltViewModel(), onBack = { navController.popBackStack() }) }
            composable("profile") { ProfileScreen(onLogout = onLogout, onNavigateToSettings = { navController.navigate("settings") }, onNavigateToDownloads = { navController.navigate("downloads") }, onNavigateToUpdates = { navController.navigate("updates_tab") }) }
            composable("detail/{appId}") { backStackEntry -> AppDetailScreen(appId = backStackEntry.arguments?.getString("appId") ?: "", viewModel = hiltViewModel(), onBack = { navController.popBackStack() }) }
            composable("downloads") { DownloadsScreen(viewModel = hiltViewModel(), onBack = { navController.popBackStack() }) }
            composable("settings") { SettingsScreen(onBack = { navController.popBackStack() }, onThemeChanged = onThemeChanged, currentThemeMode = currentThemeMode) }
            composable("updates_tab") { UpdatesScreen(viewModel = hiltViewModel(), onBack = { navController.popBackStack() }) }
            composable("category/{categoryName}") { backStackEntry -> CategoryScreen(categoryName = backStackEntry.arguments?.getString("categoryName") ?: "", viewModel = hiltViewModel(), onAppClick = { navController.navigate("detail/${it.id}") }, onBack = { navController.popBackStack() }) }
        }
    }
}
