package com.aeonreader.ui.navigation

import androidx.navigation.NamedNavArgument
import androidx.navigation.NavType
import androidx.navigation.navArgument
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

sealed class Screen(val route: String, val title: String = "") {

    data object Feed : Screen("feed", "Feed")

    data object Bookmarks : Screen("bookmarks", "Bookmarks")

    data object Search : Screen("search", "Search")

    data object Settings : Screen("settings", "Settings")

    data object Account : Screen("account", "Account")

    data object ArticleReader : Screen("article/{url}", "Article") {
        val arguments: List<NamedNavArgument> = listOf(
            navArgument("url") { type = NavType.StringType }
        )

        fun createRoute(url: String): String {
            val encoded = URLEncoder.encode(url, StandardCharsets.UTF_8.toString())
            return "article/$encoded"
        }
    }
}

val bottomNavItems = listOf(Screen.Feed, Screen.Bookmarks, Screen.Search)
