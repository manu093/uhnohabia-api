package com.sharedshoppinglists.app.presentation.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.sharedshoppinglists.app.presentation.auth.AuthViewModel
import com.sharedshoppinglists.app.presentation.auth.LoginScreen
import com.sharedshoppinglists.app.presentation.auth.RegisterScreen
import com.sharedshoppinglists.app.presentation.category.CategoryManagementScreen
import com.sharedshoppinglists.app.presentation.category.CategoryViewModel
import com.sharedshoppinglists.app.presentation.discountcard.DiscountCardsScreen
import com.sharedshoppinglists.app.presentation.knownproducts.KnownProductViewModel
import com.sharedshoppinglists.app.presentation.knownproducts.KnownProductsScreen
import com.sharedshoppinglists.app.presentation.manualcomparator.AffinityProgramsScreen
import com.sharedshoppinglists.app.presentation.manualcomparator.BarcodeScannerScreen
import com.sharedshoppinglists.app.presentation.manualcomparator.ManualComparatorScreen
import com.sharedshoppinglists.app.presentation.manualcomparator.ManualComparatorViewModel
import com.sharedshoppinglists.app.presentation.manualcomparator.ManualPricesScreen
import com.sharedshoppinglists.app.presentation.manualcomparator.MySupermarketsScreen
import com.sharedshoppinglists.app.presentation.manualcomparator.MyBankPromosScreen
import com.sharedshoppinglists.app.presentation.manualcomparator.PaymentMethodsScreen
import com.sharedshoppinglists.app.presentation.manualcomparator.PaymentMethodsViewModel
import com.sharedshoppinglists.app.presentation.manualcomparator.ListPrepScreen
import com.sharedshoppinglists.app.presentation.manualcomparator.ListPrepViewModel
import com.sharedshoppinglists.app.presentation.manualcomparator.PriceCatalogScreen
import com.sharedshoppinglists.app.presentation.manualcomparator.PriceCatalogViewModel
import com.sharedshoppinglists.app.presentation.search.GlobalSearchScreen
import com.sharedshoppinglists.app.presentation.settings.AppearanceScreen
import com.sharedshoppinglists.app.presentation.settings.ChainSelectionScreen
import com.sharedshoppinglists.app.presentation.settings.ExportScreen
import com.sharedshoppinglists.app.presentation.settings.SettingsScreen
import com.sharedshoppinglists.app.presentation.shoppinglist.ShoppingListDetailScreen
import com.sharedshoppinglists.app.presentation.shoppinglist.ShoppingModeScreen
import com.sharedshoppinglists.app.presentation.shoppinglist.ShoppingListsScreen

@Composable
fun AppNavigation(navController: NavHostController, startDestination: String, pendingProduct: String? = null, modifier: Modifier = Modifier) {
    NavHost(
        modifier = modifier,
        navController = navController,
        startDestination = startDestination,
        enterTransition = { fadeIn(tween(300)) + slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(300)) },
        exitTransition = { fadeOut(tween(200)) },
        popEnterTransition = { fadeIn(tween(300)) + slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(300)) },
        popExitTransition = { fadeOut(tween(200)) }
    ) {
        // Auth
        composable(Screen.Login.route) {
            val vm: AuthViewModel = hiltViewModel()
            LoginScreen(viewModel = vm,
                onNavigateToRegister = { navController.navigate(Screen.Register.route) },
                onLoginSuccess = { navController.navigate(Screen.ShoppingLists.route) { popUpTo(Screen.Login.route) { inclusive = true } } })
        }
        composable(Screen.Register.route) {
            val vm: AuthViewModel = hiltViewModel()
            RegisterScreen(viewModel = vm,
                onNavigateToLogin = { navController.popBackStack() },
                onRegisterSuccess = { navController.navigate(Screen.ShoppingLists.route) { popUpTo(Screen.Login.route) { inclusive = true } } })
        }

        // Main
        composable(Screen.ShoppingLists.route) {
            val vm: com.sharedshoppinglists.app.presentation.shoppinglist.ShoppingListViewModel = hiltViewModel()
            // Handle voice command: auto-add product to first list
            if (pendingProduct != null) {
                androidx.compose.runtime.LaunchedEffect(pendingProduct) {
                    // Wait for lists to load, then add product to first list
                    vm.shoppingLists.collect { lists ->
                        if (lists.isNotEmpty()) {
                            val firstList = lists.first()
                            vm.selectList(firstList.id)
                            vm.addProduct(name = pendingProduct, quantity = 1.0, unit = "Unidad",
                                categoryId = "", categoryName = "Otros", categoryEmoji = "📦", emoji = "")
                            // Navigate to the list
                            navController.navigate(Screen.ListDetail.createRoute(firstList.id, firstList.name, firstList.isShared))
                            return@collect
                        }
                    }
                }
            }
            ShoppingListsScreen(viewModel = vm,
                onListClick = { listId ->
                    val list = vm.shoppingLists.value.find { it.id == listId }
                    navController.navigate(Screen.ListDetail.createRoute(listId, list?.name ?: "", list?.isShared ?: false))
                },
                onDiscountCardsClick = { navController.navigate(Screen.DiscountCards.route) },
                onCategoryManagementClick = { navController.navigate(Screen.CategoryManagement.route) },
                onKnownProductsClick = { navController.navigate(Screen.KnownProducts.route) },
                onMySupermarketsClick = { navController.navigate(Screen.MySupermarkets.route) },
                onAffinityProgramsClick = { navController.navigate(Screen.AffinityPrograms.route) },
                onBarcodeScannerClick = { navController.navigate(Screen.BarcodeScanner.route) },
                onMyBankPromosClick = { navController.navigate(Screen.MyBankPromos.route) },
                onLogout = { navController.navigate(Screen.Login.route) { popUpTo(Screen.ShoppingLists.route) { inclusive = true } } })
        }

        composable(route = Screen.ListDetail.route, arguments = listOf(
            navArgument("listId") { type = NavType.StringType },
            navArgument("listName") { type = NavType.StringType },
            navArgument("isShared") { type = NavType.BoolType }
        )) { entry ->
            val listId = entry.arguments?.getString("listId") ?: ""
            val vm: com.sharedshoppinglists.app.presentation.shoppinglist.ShoppingListViewModel = hiltViewModel()
            val sharedVm: com.sharedshoppinglists.app.presentation.shared.SharedListViewModel = hiltViewModel()
            val context = androidx.compose.ui.platform.LocalContext.current
            ShoppingListDetailScreen(viewModel = vm, sharedListViewModel = sharedVm,
                listId = listId, listName = entry.arguments?.getString("listName") ?: "",
                isShared = entry.arguments?.getBoolean("isShared") ?: false,
                onBack = { navController.popBackStack() },
                onCopyLink = { url ->
                    val sendIntent = android.content.Intent().apply {
                        action = android.content.Intent.ACTION_SEND
                        putExtra(android.content.Intent.EXTRA_TEXT, "Unite a mi lista de compras en Uh No Había: $url")
                        type = "text/plain"
                    }
                    context.startActivity(android.content.Intent.createChooser(sendIntent, "Compartir lista"))
                },
                onManualComparator = { navController.navigate(Screen.ManualComparator.createRoute(it)) },
                onOptimize = { navController.navigate(Screen.ListPrep.createRoute(it)) },
                onShoppingMode = { navController.navigate(Screen.ShoppingMode.createRoute(it, entry.arguments?.getString("listName") ?: "")) })
        }

        // Discount cards, categories, known products
        composable(Screen.DiscountCards.route) {
            val vm: com.sharedshoppinglists.app.presentation.discountcard.DiscountCardViewModel = hiltViewModel()
            DiscountCardsScreen(viewModel = vm, onBack = { navController.popBackStack() })
        }
        composable(Screen.CategoryManagement.route) {
            val vm: CategoryViewModel = hiltViewModel()
            CategoryManagementScreen(viewModel = vm, onBack = { navController.popBackStack() })
        }
        composable(Screen.KnownProducts.route) {
            val vm: KnownProductViewModel = hiltViewModel()
            KnownProductsScreen(viewModel = vm, onBack = { navController.popBackStack() })
        }

        // Manual price comparator
        composable(Screen.MySupermarkets.route) {
            val vm: ManualComparatorViewModel = hiltViewModel()
            MySupermarketsScreen(viewModel = vm, onBack = { navController.popBackStack() },
                onSupermarketClick = { id, name -> navController.navigate(Screen.ManualPrices.createRoute(id, name)) })
        }
        composable(route = Screen.ManualPrices.route, arguments = listOf(
            navArgument("supermarketId") { type = NavType.StringType },
            navArgument("supermarketName") { type = NavType.StringType }
        )) { entry ->
            val vm: ManualComparatorViewModel = hiltViewModel()
            ManualPricesScreen(viewModel = vm,
                supermarketId = entry.arguments?.getString("supermarketId") ?: "",
                supermarketName = entry.arguments?.getString("supermarketName") ?: "",
                onBack = { navController.popBackStack() })
        }
        composable(Screen.AffinityPrograms.route) {
            val vm: ManualComparatorViewModel = hiltViewModel()
            AffinityProgramsScreen(viewModel = vm, onBack = { navController.popBackStack() })
        }
        composable(route = Screen.ManualComparator.route, arguments = listOf(
            navArgument("listId") { type = NavType.StringType }
        )) { entry ->
            val vm: ManualComparatorViewModel = hiltViewModel()
            ManualComparatorScreen(viewModel = vm, listId = entry.arguments?.getString("listId") ?: "",
                onBack = { navController.popBackStack() })
        }
        composable(Screen.BarcodeScanner.route) {
            val vm: ManualComparatorViewModel = hiltViewModel()
            BarcodeScannerScreen(viewModel = vm, onBack = { navController.popBackStack() })
        }
        composable(Screen.PriceCatalog.route) {
            val vm: PriceCatalogViewModel = hiltViewModel()
            PriceCatalogScreen(viewModel = vm, onBack = { navController.popBackStack() })
        }
        composable(Screen.MyBankPromos.route) {
            val vm: PriceCatalogViewModel = hiltViewModel()
            MyBankPromosScreen(viewModel = vm, onBack = { navController.popBackStack() })
        }
        composable(Screen.PaymentMethods.route) {
            val vm: PaymentMethodsViewModel = hiltViewModel()
            PaymentMethodsScreen(viewModel = vm, onBack = { navController.popBackStack() })
        }
        composable(Screen.GlobalSearch.route) {
            GlobalSearchScreen(
                onBack = { navController.popBackStack() },
                onListClick = { listId ->
                    navController.navigate(Screen.ListDetail.createRoute(listId, "", false))
                }
            )
        }
        composable(Screen.Settings.route) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onThemeClick = { navController.navigate(Screen.Appearance.route) },
                onPaymentMethodsClick = { navController.navigate(Screen.PaymentMethods.route) },
                onExportClick = { navController.navigate(Screen.Export.route) },
                onChainSelectionClick = { navController.navigate(Screen.ChainSelection.route) }
            )
        }
        composable(Screen.ChainSelection.route) {
            ChainSelectionScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.Export.route) {
            ExportScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.Appearance.route) {
            AppearanceScreen(onBack = { navController.popBackStack() })
        }
        composable(route = Screen.ShoppingMode.route, arguments = listOf(
            navArgument("listId") { type = NavType.StringType },
            navArgument("listName") { type = NavType.StringType }
        )) { entry ->
            val vm: com.sharedshoppinglists.app.presentation.shoppinglist.ShoppingListViewModel = hiltViewModel()
            ShoppingModeScreen(viewModel = vm, listId = entry.arguments?.getString("listId") ?: "", listName = entry.arguments?.getString("listName") ?: "", onBack = { navController.popBackStack() })
        }
        composable(route = Screen.ListPrep.route, arguments = listOf(
            navArgument("listId") { type = NavType.StringType }
        )) { entry ->
            val vm: ListPrepViewModel = hiltViewModel()
            ListPrepScreen(viewModel = vm, listId = entry.arguments?.getString("listId") ?: "",
                onBack = { navController.popBackStack() })
        }
    }
}
