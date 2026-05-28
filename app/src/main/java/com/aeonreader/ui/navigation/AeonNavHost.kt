package com.aeonreader.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.aeonreader.ui.screens.article.ArticleReaderScreen
import com.aeonreader.ui.screens.bookmarks.BookmarksScreen
import com.aeonreader.ui.screens.feed.FeedScreen
import com.aeonreader.ui.screens.search.SearchScreen
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

data class BottomNavItem(
    val screen: Screen,
    val icon: ImageVector
)

private val navItems = listOf(
    BottomNavItem(Screen.Feed, Icons.Default.Home),
    BottomNavItem(Screen.Bookmarks, Icons.Default.FavoriteBorder),
    BottomNavItem(Screen.Search, Icons.Default.Search)
)

@Composable
fun AeonNavHost(
    navController: NavHostController = rememberNavController(),
    modifier: Modifier = Modifier
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    Scaffold(
        bottomBar = {
            NavigationBar {
                navItems.forEach { item ->
                    val selected = currentDestination?.hierarchy?.any {
                        it.route == item.screen.route
                    } == true

                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(item.screen.route) {
                                popUpTo(navController.graph.startDestinationId) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(item.icon, contentDescription = item.screen.title)
                        },
                        label = { Text(item.screen.title) }
                    )
                }
            }
        },
        modifier = modifier
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Feed.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Feed.route) {
                FeedScreen(
                    onArticleClick = { url ->
                        navController.navigate(Screen.ArticleReader.createRoute(url))
                    }
                )
            }

            composable(Screen.Bookmarks.route) {
                BookmarksScreen(
                    onArticleClick = { url ->
                        navController.navigate(Screen.ArticleReader.createRoute(url))
                    }
                )
            }

            composable(Screen.Search.route) {
                SearchScreen(
                    onArticleClick = { url ->
                        navController.navigate(Screen.ArticleReader.createRoute(url))
                    }
                )
            }

            composable(
                route = Screen.ArticleReader.route,
                arguments = Screen.ArticleReader.arguments
            ) { backStackEntry ->
                val encoded = backStackEntry.arguments?.getString("url") ?: return@composable
                val url = URLDecoder.decode(encoded, StandardCharsets.UTF_8.toString())
                ArticleReaderScreen(
                    articleUrl = url,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
