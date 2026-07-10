package com.carrito.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

// ─── Entities ────────────────────────────────────────────────────────────────

@Entity(tableName = "shopping_lists")
data class ShoppingListEntity(
    @PrimaryKey val id: String,
    val name: String,
    val emoji: String = "🛒",
    val color: Long = 0xFF4CAF50,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "items",
    foreignKeys = [ForeignKey(entity = ShoppingListEntity::class, parentColumns = ["id"], childColumns = ["listId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("listId"), Index("category")]
)
data class ItemEntity(
    @PrimaryKey val id: String,
    val listId: String,
    val name: String,
    val emoji: String = "",
    val quantity: Int = 1,
    val unit: String = "",
    val category: String = "Otros",
    val inCart: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "catalog")
data class CatalogItem(
    @PrimaryKey val name: String,
    val emoji: String,
    val category: String,
    val defaultUnit: String = "",
    val timesUsed: Int = 0
)

// ─── DAOs ────────────────────────────────────────────────────────────────────

@Dao
interface ShoppingListDao {
    @Query("SELECT * FROM shopping_lists ORDER BY updatedAt DESC")
    fun getAll(): Flow<List<ShoppingListEntity>>

    @Query("SELECT * FROM shopping_lists WHERE id = :id")
    suspend fun getById(id: String): ShoppingListEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(list: ShoppingListEntity)

    @Query("DELETE FROM shopping_lists WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface ItemDao {
    @Query("SELECT * FROM items WHERE listId = :listId ORDER BY inCart ASC, category ASC, name ASC")
    fun getByList(listId: String): Flow<List<ItemEntity>>

    @Query("SELECT COUNT(*) FROM items WHERE listId = :listId AND inCart = 0")
    fun pendingCount(listId: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM items WHERE listId = :listId")
    fun totalCount(listId: String): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: ItemEntity)

    @Query("UPDATE items SET inCart = :inCart WHERE id = :id")
    suspend fun setInCart(id: String, inCart: Boolean)

    @Query("UPDATE items SET inCart = 0 WHERE listId = :listId")
    suspend fun uncheckAll(listId: String)

    @Query("DELETE FROM items WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface CatalogDao {
    @Query("SELECT * FROM catalog WHERE category = :category ORDER BY timesUsed DESC")
    fun getByCategory(category: String): Flow<List<CatalogItem>>

    @Query("SELECT * FROM catalog ORDER BY timesUsed DESC LIMIT 30")
    fun getFrequent(): Flow<List<CatalogItem>>

    @Query("SELECT * FROM catalog WHERE name LIKE '%' || :q || '%' ORDER BY timesUsed DESC LIMIT 15")
    suspend fun search(q: String): List<CatalogItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: CatalogItem)

    // Insert only if the catalog doesn't already have this item (name is the PK).
    // Prevents custom items from overwriting curated catalog rows (emoji/category/timesUsed).
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(item: CatalogItem)

    @Query("UPDATE catalog SET timesUsed = timesUsed + 1 WHERE name = :name")
    suspend fun incrementUsage(name: String)

    @Query("SELECT DISTINCT category FROM catalog ORDER BY category")
    fun getCategories(): Flow<List<String>>
}

// ─── Database ────────────────────────────────────────────────────────────────

@Database(entities = [ShoppingListEntity::class, ItemEntity::class, CatalogItem::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun listDao(): ShoppingListDao
    abstract fun itemDao(): ItemDao
    abstract fun catalogDao(): CatalogDao
}
