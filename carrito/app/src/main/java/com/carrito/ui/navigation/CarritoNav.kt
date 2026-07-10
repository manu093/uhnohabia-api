package com.carrito.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.carrito.ui.screens.home.HomeScreen
import com.carrito.ui.screens.list.ListScreen

@Composable
fun CarritoNav(nav: NavHostController) {
    NavHost(nav, startDestination = "home") {
        composable("home") {
            HomeScreen(onOpenList = { nav.navigate("list/$it") })
        }
        composable("list/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) {
            ListScreen(listId = it.arguments?.getString("id") ?: "", onBack = { nav.popBackStack() })
        }
    }
}
