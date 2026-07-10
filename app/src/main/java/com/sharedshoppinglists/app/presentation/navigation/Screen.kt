package com.sharedshoppinglists.app.presentation.navigation

sealed class Screen(val route: String) {
    data object Login : Screen("login")
    data object Register : Screen("register")
    data object ShoppingLists : Screen("shopping_lists")
    data object DiscountCards : Screen("discount_cards")
    data object CategoryManagement : Screen("category_management")
    data object KnownProducts : Screen("known_products")
    data object GlobalSearch : Screen("global_search")

    data object ListDetail : Screen("list_detail/{listId}/{listName}/{isShared}") {
        fun createRoute(listId: String, listName: String, isShared: Boolean): String =
            "list_detail/$listId/$listName/$isShared"
    }

    data object MySupermarkets : Screen("my_supermarkets")
    data object ManualPrices : Screen("manual_prices/{supermarketId}/{supermarketName}") {
        fun createRoute(supermarketId: String, supermarketName: String): String =
            "manual_prices/$supermarketId/$supermarketName"
    }
    data object AffinityPrograms : Screen("affinity_programs")
    data object ManualComparator : Screen("manual_comparator/{listId}") {
        fun createRoute(listId: String): String = "manual_comparator/$listId"
    }
    data object BarcodeScanner : Screen("barcode_scanner")
    data object PriceCatalog : Screen("price_catalog")
    data object MyBankPromos : Screen("my_bank_promos")
    data object PaymentMethods : Screen("payment_methods")
    data object Settings : Screen("settings")
    data object Appearance : Screen("appearance")
    data object ChainSelection : Screen("chain_selection")
    data object Export : Screen("export")
    data object ShoppingMode : Screen("shopping_mode/{listId}/{listName}") {
        fun createRoute(listId: String, listName: String): String = "shopping_mode/$listId/$listName"
    }
    data object ListPrep : Screen("list_prep/{listId}") {
        fun createRoute(listId: String): String = "list_prep/$listId"
    }
}