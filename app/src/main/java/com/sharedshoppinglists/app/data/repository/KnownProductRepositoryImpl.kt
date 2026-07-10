package com.sharedshoppinglists.app.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.sharedshoppinglists.app.data.local.dao.KnownProductDao
import com.sharedshoppinglists.app.data.local.entity.KnownProductEntity
import com.sharedshoppinglists.app.data.local.mapper.toDomain
import com.sharedshoppinglists.app.data.local.mapper.toEntity
import com.sharedshoppinglists.app.domain.model.KnownProduct
import com.sharedshoppinglists.app.domain.repository.KnownProductRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject

class KnownProductRepositoryImpl @Inject constructor(
    private val dao: KnownProductDao,
    private val appScope: CoroutineScope
) : KnownProductRepository {

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    init {
        // Sincroniza cuando hay usuario logueado (y en cada login futuro),
        // usando el scope de app inyectado en vez de crear uno que nunca se cancela.
        auth.addAuthStateListener { fb ->
            if (fb.currentUser != null) syncFromFirestore()
        }
    }

    private fun syncFromFirestore() {
        appScope.launch {
            try {
                val uid = auth.currentUser?.uid ?: return@launch
                val docs = firestore.collection("knownProducts")
                    .whereEqualTo("ownerId", uid)
                    .get()
                    .await()
                for (doc in docs.documents) {
                    val name = doc.getString("name") ?: continue
                    val existing = dao.getByName(name)
                    if (existing == null) {
                        val entity = KnownProductEntity(
                            id = UUID.randomUUID().toString(),
                            name = name,
                            emoji = doc.getString("emoji") ?: "",
                            categoryId = doc.getString("categoryId") ?: "",
                            defaultUnit = doc.getString("defaultUnit") ?: "Unidad",
                            timesUsed = 0
                        )
                        dao.insert(entity)
                    }
                }
            } catch (_: Exception) { }
        }
    }

    override suspend fun searchByName(query: String): List<KnownProduct> {
        if (query.isBlank()) return emptyList()
        return dao.searchByName(query).map { it.toDomain() }
    }

    override suspend fun saveOrIncrement(product: KnownProduct) {
        val existing = dao.getByName(product.name)
        if (existing != null) {
            dao.incrementTimesUsed(existing.id)
            dao.update(existing.copy(
                emoji = product.emoji,
                categoryId = product.categoryId,
                defaultUnit = product.defaultUnit
            ))
        } else {
            val id = if (product.id.isBlank()) UUID.randomUUID().toString() else product.id
            dao.insert(product.copy(id = id).toEntity())
        }
        // Sync to Firestore for Alexa access
        syncToFirestore(product)
    }

    private fun syncToFirestore(product: KnownProduct) {
        val uid = auth.currentUser?.uid ?: return
        val data = mapOf(
            "ownerId" to uid,
            "name" to product.name,
            "emoji" to product.emoji,
            "categoryId" to product.categoryId,
            "defaultUnit" to product.defaultUnit
        )
        // Firestore no admite '/' en el id del documento.
        val docId = "${uid}_${product.name}".replace("/", "_")
        firestore.collection("knownProducts").document(docId).set(data)
    }

    override fun getAll(): Flow<List<KnownProduct>> {
        return dao.getAll().map { list -> list.map { it.toDomain() } }
    }

    override suspend fun delete(id: String) {
        dao.deleteById(id)
    }

    override suspend fun update(product: KnownProduct) {
        dao.update(product.toEntity())
    }
    override fun getMostUsed(limit: Int) = dao.getMostUsed(limit).map { list -> list.map { it.toDomain() } }
}
