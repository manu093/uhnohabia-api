package com.sharedshoppinglists.app.data.repository

import com.sharedshoppinglists.app.data.local.dao.CustomCategoryDao
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.sharedshoppinglists.app.data.local.mapper.toDomain
import com.sharedshoppinglists.app.data.local.mapper.toEntity
import com.sharedshoppinglists.app.domain.model.CustomCategory
import com.sharedshoppinglists.app.domain.repository.CustomCategoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class CustomCategoryRepositoryImpl @Inject constructor(
    private val dao: CustomCategoryDao
) : CustomCategoryRepository {

    override fun getAll(): Flow<List<CustomCategory>> {
        return dao.getAll().map { entities -> entities.map { it.toDomain() } }
    }

    override suspend fun getAllOnce(): List<CustomCategory> {
        return dao.getAllOnce().map { it.toDomain() }
    }

    override suspend fun insert(category: CustomCategory) {
        dao.insert(category.toEntity())
    }

    override suspend fun update(category: CustomCategory) {
        dao.update(category.toEntity())
    }

    override suspend fun delete(category: CustomCategory) {
        dao.delete(category.toEntity())
    }

    private fun syncCategoryToFirebase(category: com.sharedshoppinglists.app.domain.model.CustomCategory) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return@launch
                FirebaseFirestore.getInstance().collection("users").document(userId)
                    .collection("categories").document(category.id)
                    .set(mapOf("name" to category.name, "emoji" to category.emoji, "sortOrder" to category.sortOrder))
                    .await()
            } catch (_: Exception) {}
        }
    }

    suspend fun syncCategoriesFromFirebase() {
        try {
            val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
            val docs = FirebaseFirestore.getInstance().collection("users").document(userId)
                .collection("categories").get().await()
            for (doc in docs.documents) {
                val existing = dao.getAllOnce().find { it.id == doc.id }
                if (existing == null) {
                    dao.insert(com.sharedshoppinglists.app.data.local.entity.CustomCategoryEntity(
                        id = doc.id, name = doc.getString("name") ?: "", emoji = doc.getString("emoji") ?: "",
                        sortOrder = (doc.getLong("sortOrder") ?: 0).toInt()
                    ))
                }
            }
        } catch (_: Exception) {}
    }
}