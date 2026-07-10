package com.sharedshoppinglists.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.sharedshoppinglists.app.data.local.dao.AffinityProgramDao
import com.sharedshoppinglists.app.data.local.dao.PriceHistoryDao
import com.sharedshoppinglists.app.data.local.dao.SavingsDao
import com.sharedshoppinglists.app.data.local.dao.CustomCategoryDao
import com.sharedshoppinglists.app.data.local.dao.DiscountCardDao
import com.sharedshoppinglists.app.data.local.dao.KnownProductDao
import com.sharedshoppinglists.app.data.local.dao.ManualPriceDao
import com.sharedshoppinglists.app.data.local.dao.ProductDao
import com.sharedshoppinglists.app.data.local.dao.ShoppingListDao
import com.sharedshoppinglists.app.data.local.dao.SupermarketDao
import com.sharedshoppinglists.app.data.local.entity.AffinityProgramEntity
import com.sharedshoppinglists.app.data.local.entity.PriceHistoryEntity
import com.sharedshoppinglists.app.data.local.entity.SavingsEntity
import com.sharedshoppinglists.app.data.local.entity.CustomCategoryEntity
import com.sharedshoppinglists.app.data.local.entity.DiscountCardEntity
import com.sharedshoppinglists.app.data.local.entity.KnownProductEntity
import com.sharedshoppinglists.app.data.local.entity.ManualPriceEntity
import com.sharedshoppinglists.app.data.local.entity.ProductEntity
import com.sharedshoppinglists.app.data.local.entity.ShoppingListEntity
import com.sharedshoppinglists.app.data.local.entity.SupermarketEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

@Database(
    entities = [
        ShoppingListEntity::class,
        ProductEntity::class,
        DiscountCardEntity::class,
        CustomCategoryEntity::class,
        KnownProductEntity::class,
        SupermarketEntity::class,
        ManualPriceEntity::class,
        AffinityProgramEntity::class,
        PriceHistoryEntity::class,
        SavingsEntity::class
    ],
    version = 8,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun shoppingListDao(): ShoppingListDao
    abstract fun productDao(): ProductDao
    abstract fun discountCardDao(): DiscountCardDao
    abstract fun customCategoryDao(): CustomCategoryDao
    abstract fun knownProductDao(): KnownProductDao
    abstract fun supermarketDao(): SupermarketDao
    abstract fun manualPriceDao(): ManualPriceDao
    abstract fun affinityProgramDao(): AffinityProgramDao
    abstract fun priceHistoryDao(): PriceHistoryDao
    abstract fun savingsDao(): SavingsDao

    companion object {
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS savings_history (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, amount REAL NOT NULL, chain TEXT NOT NULL, listName TEXT NOT NULL, recordedAt INTEGER NOT NULL)")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS price_history (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, productName TEXT NOT NULL, brand TEXT NOT NULL, chain TEXT NOT NULL, price REAL NOT NULL, recordedAt INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_price_history_productName ON price_history(productName)")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE shopping_lists ADD COLUMN emoji TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE shopping_lists ADD COLUMN color TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE products ADD COLUMN preferredBrand TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS supermarkets (
                        id TEXT NOT NULL PRIMARY KEY,
                        userId TEXT NOT NULL,
                        name TEXT NOT NULL,
                        address TEXT NOT NULL
                    )
                """.trimIndent())
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS manual_prices (
                        id TEXT NOT NULL PRIMARY KEY,
                        userId TEXT NOT NULL,
                        supermarketId TEXT NOT NULL,
                        productName TEXT NOT NULL,
                        price REAL NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        FOREIGN KEY (supermarketId) REFERENCES supermarkets(id) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_manual_prices_supermarketId ON manual_prices(supermarketId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_manual_prices_productName ON manual_prices(productName)")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS affinity_programs (
                        id TEXT NOT NULL PRIMARY KEY,
                        userId TEXT NOT NULL,
                        name TEXT NOT NULL,
                        supermarketId TEXT NOT NULL,
                        discountPercentage REAL NOT NULL,
                        validFrom INTEGER,
                        validUntil INTEGER
                    )
                """.trimIndent())
                // Ensure default categories exist after migration
                ensureDefaultCategories(db)
            }
        }

        fun ensureDefaultCategories(db: SupportSQLiteDatabase) {
            val cursor = db.query("SELECT COUNT(*) FROM custom_categories")
            cursor.moveToFirst()
            val count = cursor.getInt(0)
            cursor.close()
            if (count == 0) {
                val defaults = listOf(
                    Triple("Carnicería", "🥩", 0),
                    Triple("Verdulería", "🥬", 1),
                    Triple("Limpieza", "🧹", 2),
                    Triple("Perfumería", "🧴", 3),
                    Triple("Granja", "🥚", 4),
                    Triple("Bebidas", "🥤", 5),
                    Triple("Almacén", "🏪", 6),
                    Triple("Otros", "📦", 7)
                )
                defaults.forEach { (name, emoji, order) ->
                    val id = UUID.randomUUID().toString()
                    db.execSQL(
                        "INSERT INTO custom_categories (id, name, emoji, sortOrder) VALUES (?, ?, ?, ?)",
                        arrayOf(id, name, emoji, order)
                    )
                }
            }
        }

        fun prepopulateCallback(): Callback = object : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                ensureDefaultCategories(db)
            }
            override fun onOpen(db: SupportSQLiteDatabase) {
                super.onOpen(db)
                ensureDefaultCategories(db)
            }
        }

        @Volatile private var INSTANCE: AppDatabase? = null
        fun getInstance(context: android.content.Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "shopping_lists_db")
                    .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
                    .addCallback(prepopulateCallback())
                    .allowMainThreadQueries()
                    .fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }
        }
    }
}
