package com.sharedshoppinglists.app.presentation.category

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sharedshoppinglists.app.domain.model.CustomCategory
import com.sharedshoppinglists.app.domain.repository.CustomCategoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class CategoryViewModel @Inject constructor(
    private val repository: CustomCategoryRepository
) : ViewModel() {

    val categories: StateFlow<List<CustomCategory>> = repository.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addCategory(name: String, emoji: String) {
        viewModelScope.launch {
            val maxOrder = categories.value.maxOfOrNull { it.sortOrder } ?: -1
            repository.insert(
                CustomCategory(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    emoji = emoji,
                    sortOrder = maxOrder + 1
                )
            )
        }
    }

    fun updateCategory(category: CustomCategory) {
        viewModelScope.launch {
            repository.update(category)
        }
    }

    fun deleteCategory(category: CustomCategory) {
        viewModelScope.launch {
            repository.delete(category)
        }
    }
}
