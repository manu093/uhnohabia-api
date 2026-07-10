package com.sharedshoppinglists.app.presentation.shoppinglist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sharedshoppinglists.app.data.remote.CatalogStatus
import com.sharedshoppinglists.app.data.remote.SepaCatalogClient
import com.sharedshoppinglists.app.domain.model.CustomCategory
import com.sharedshoppinglists.app.domain.model.KnownProduct
import com.sharedshoppinglists.app.domain.model.PendingInvitation
import com.sharedshoppinglists.app.domain.model.Product
import com.sharedshoppinglists.app.domain.model.ShoppingList
import com.sharedshoppinglists.app.domain.repository.AuthRepository
import com.sharedshoppinglists.app.domain.repository.CustomCategoryRepository
import com.sharedshoppinglists.app.domain.repository.KnownProductRepository
import com.sharedshoppinglists.app.domain.repository.SharedListRepository
import com.sharedshoppinglists.app.domain.repository.ShoppingListRepository
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ShoppingListViewModel @Inject constructor(
    private val shoppingListRepository: ShoppingListRepository,
    private val authRepository: AuthRepository,
    private val customCategoryRepository: CustomCategoryRepository,
    private val knownProductRepository: KnownProductRepository,
    private val sharedListRepository: SharedListRepository,
    private val sepaCatalogClient: SepaCatalogClient
) : ViewModel() {

    private val _currentUserId = MutableStateFlow<String?>(null)
    private val _selectedListId = MutableStateFlow<String?>(null)
    private val _currentUserDisplayName = MutableStateFlow<String>("")

    val currentUserId: StateFlow<String?> = _currentUserId.asStateFlow()

    private val _autocompleteSuggestions = MutableStateFlow<List<KnownProduct>>(emptyList())
    val autocompleteSuggestions: StateFlow<List<KnownProduct>> = _autocompleteSuggestions.asStateFlow()

    private val _pendingInvitations = MutableStateFlow<List<PendingInvitation>>(emptyList())
    val pendingInvitations: StateFlow<List<PendingInvitation>> = _pendingInvitations.asStateFlow()

    private val _notifications = MutableStateFlow<List<String>>(emptyList())
    val notifications: StateFlow<List<String>> = _notifications.asStateFlow()

    private val _catalogStatus = MutableStateFlow<CatalogStatus?>(null)
    val catalogStatus: StateFlow<CatalogStatus?> = _catalogStatus.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    fun clearError() { _error.value = null }

    val shoppingLists: StateFlow<List<ShoppingList>> = _currentUserId
        .flatMapLatest { userId ->
            if (userId != null) shoppingListRepository.getLists(userId) else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val products: StateFlow<List<Product>> = _selectedListId
        .flatMapLatest { listId ->
            if (listId != null) shoppingListRepository.getProducts(listId) else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val categories: StateFlow<List<CustomCategory>> = customCategoryRepository.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        loadCatalogStatus()
        viewModelScope.launch {
            authRepository.getCurrentUser().collect { user ->
                _currentUserId.value = user?.id
                if (user != null) {
                    // Get display name from Firebase Auth
                    val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
                    _currentUserDisplayName.value = auth.currentUser?.displayName?.takeIf { it.isNotBlank() }
                        ?: auth.currentUser?.email?.substringBefore("@")
                        ?: "Yo"
                    loadPendingInvitations(user.email)
                    loadNotifications(user.id)
                } else {
                    _pendingInvitations.value = emptyList()
                }
            }
        }
    }

    private fun loadCatalogStatus() {
        viewModelScope.launch {
            _catalogStatus.value = sepaCatalogClient.getStatus()
        }
    }

    fun selectList(listId: String) {
        _selectedListId.value = listId
    }

    fun clearSelection() {
        _selectedListId.value = null
    }

    fun createList(name: String, emoji: String = "") {
        val userId = _currentUserId.value ?: return
        viewModelScope.launch {
            shoppingListRepository.createList(name, userId, emoji)
                .onFailure { _error.value = it.message ?: "No se pudo crear la lista." }
        }
    }

    fun deleteList(listId: String) {
        viewModelScope.launch {
            shoppingListRepository.deleteList(listId)
                .onFailure { _error.value = it.message ?: "No se pudo eliminar la lista." }
        }
    }

    fun renameList(listId: String, newName: String) {
        viewModelScope.launch {
            shoppingListRepository.renameList(listId, newName)
                .onFailure { _error.value = it.message ?: "No se pudo renombrar la lista." }
        }
    }

    private val _duplicateProduct = MutableStateFlow<Product?>(null)
    val duplicateProduct: StateFlow<Product?> = _duplicateProduct.asStateFlow()
    private var _pendingNewProduct: Product? = null

    fun addProduct(
        name: String,
        quantity: Double,
        unit: String,
        categoryId: String,
        categoryName: String,
        categoryEmoji: String,
        emoji: String,
        preferredBrand: String = ""
    ) {
        val listId = _selectedListId.value ?: return
        val userId = _currentUserDisplayName.value.ifBlank { _currentUserId.value ?: "" }
        val newProduct = Product(
            id = "",
            name = name,
            quantity = quantity,
            unit = unit,
            categoryId = categoryId,
            categoryName = categoryName,
            categoryEmoji = categoryEmoji,
            emoji = emoji,
            preferredBrand = preferredBrand,
            isPurchased = false,
            lastModifiedBy = userId,
            lastModifiedAt = Instant.now()
        )
        // Check for duplicate (same name, different brand or no brand)
        val existing = products.value.find {
            it.name.equals(name, ignoreCase = true) && !it.isPurchased
        }
        if (existing != null) {
            _pendingNewProduct = newProduct
            _duplicateProduct.value = existing
            return
        }
        doAddProduct(listId, newProduct)
    }

    fun confirmAddDuplicate(merge: Boolean) {
        val listId = _selectedListId.value ?: return
        val existing = _duplicateProduct.value ?: return
        val pending = _pendingNewProduct ?: return
        _duplicateProduct.value = null
        _pendingNewProduct = null
        if (merge) {
            // Sum quantities to existing product
            viewModelScope.launch {
                shoppingListRepository.updateProduct(listId, existing.copy(
                    quantity = existing.quantity + pending.quantity,
                    lastModifiedAt = Instant.now()
                ))
            }
        } else {
            doAddProduct(listId, pending)
        }
    }

    fun dismissDuplicate() {
        _duplicateProduct.value = null
        _pendingNewProduct = null
    }

    private fun doAddProduct(listId: String, product: Product) {
        viewModelScope.launch {
            shoppingListRepository.addProduct(listId, product)
                .onFailure { _error.value = it.message ?: "No se pudo agregar el producto." }
            knownProductRepository.saveOrIncrement(
                KnownProduct(
                    id = UUID.randomUUID().toString(),
                    name = product.name,
                    emoji = product.emoji,
                    categoryId = product.categoryId,
                    defaultUnit = product.unit
                )
            )
        }
    }

    fun updateProduct(product: Product) {
        val listId = _selectedListId.value ?: return
        viewModelScope.launch {
            shoppingListRepository.updateProduct(listId, product)
                .onFailure { _error.value = it.message ?: "No se pudo actualizar el producto." }
        }
    }

    fun removeProduct(productId: String) {
        val listId = _selectedListId.value ?: return
        viewModelScope.launch {
            shoppingListRepository.removeProduct(listId, productId)
                .onFailure { _error.value = it.message ?: "No se pudo eliminar el producto." }
        }
    }

    fun toggleProductPurchased(productId: String, purchased: Boolean) {
        val listId = _selectedListId.value ?: return
        viewModelScope.launch {
            shoppingListRepository.markProductAsPurchased(listId, productId, purchased)
                .onFailure { _error.value = it.message ?: "No se pudo actualizar el producto." }
        }
    }

    fun searchProducts(query: String) {
        viewModelScope.launch {
            _autocompleteSuggestions.value = if (query.length >= 2) {
                knownProductRepository.searchByName(query)
            } else {
                emptyList()
            }
        }
    }

    fun clearSuggestions() {
        _autocompleteSuggestions.value = emptyList()
    }

    private fun loadPendingInvitations(email: String) {
        viewModelScope.launch {
            _pendingInvitations.value = sharedListRepository.getPendingInvitations(email)
        }
    }

    fun acceptInvitation(invitationId: String) {
        val userId = _currentUserId.value ?: return
        viewModelScope.launch {
            sharedListRepository.acceptInvitation(invitationId, userId)
            _pendingInvitations.value = _pendingInvitations.value.filter { it.id != invitationId }
            // Force refresh lists to include the newly shared list
            _currentUserId.value = null
            _currentUserId.value = userId
        }
    }

    fun declineInvitation(invitationId: String) {
        viewModelScope.launch {
            sharedListRepository.declineInvitation(invitationId)
            _pendingInvitations.value = _pendingInvitations.value.filter { it.id != invitationId }
        }
    }

    fun createCategory(name: String, emoji: String) {
        viewModelScope.launch {
            val maxOrder = categories.value.maxOfOrNull { it.sortOrder } ?: -1
            val category = CustomCategory(
                id = UUID.randomUUID().toString(),
                name = name,
                emoji = emoji,
                sortOrder = maxOrder + 1
            )
            customCategoryRepository.insert(category)
        }
    }

    private fun loadNotifications(userId: String) {
        viewModelScope.launch {
            try {
                val docs = FirebaseFirestore.getInstance().collection("notifications")
                    .whereEqualTo("userId", userId)
                    .whereEqualTo("read", false)
                    .get()
                    .await()
                val messages = docs.documents.mapNotNull { it.getString("message") }
                _notifications.value = messages
                // Mark as read
                for (doc in docs.documents) {
                    doc.reference.update("read", true)
                }
            } catch (_: Exception) { }
        }
    }


    fun duplicateList(listId: String, newName: String) {
        val userId = _currentUserId.value ?: return
        viewModelScope.launch {
            val sourceProducts = shoppingListRepository.getProducts(listId).stateIn(viewModelScope).value
            val result = shoppingListRepository.createList(newName, userId)
            result.getOrNull()?.let { newList ->
                sourceProducts.forEach { p ->
                    shoppingListRepository.addProduct(newList.id, p.copy(id = "", isPurchased = false))
                }
            }
        }
    }

    fun getFrequentProducts(): kotlinx.coroutines.flow.Flow<List<KnownProduct>> {
        return knownProductRepository.getMostUsed(10)
    }
    fun importProducts(names: List<String>) {
        val listId = _selectedListId.value ?: return
        viewModelScope.launch {
            names.forEach { name ->
                shoppingListRepository.addProduct(listId, com.sharedshoppinglists.app.domain.model.Product(
                    id = "", name = name, quantity = 1.0, unit = "Unidad",
                    categoryName = "Otros", categoryEmoji = "\uD83D\uDCE6",
                    isPurchased = false, lastModifiedBy = "", lastModifiedAt = java.time.Instant.now()
                ))
            }
        }
    }

    fun updateListEmoji(listId: String, emoji: String) {
        viewModelScope.launch {
            shoppingListRepository.updateListEmoji(listId, emoji)
                .onFailure { _error.value = it.message ?: "No se pudo cambiar el icono." }
        }
    }

    fun dismissNotifications() {
        _notifications.value = emptyList()
    }

    fun uncheckAllProducts() {
        val listId = _selectedListId.value ?: return
        viewModelScope.launch {
            products.value.filter { it.isPurchased }.forEach { product ->
                shoppingListRepository.markProductAsPurchased(listId, product.id, false)
            }
        }
    }
}
