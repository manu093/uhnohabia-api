package com.carrito.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.carrito.data.db.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @Singleton
    fun provideDb(@ApplicationContext ctx: Context): AppDatabase =
        Room.databaseBuilder(ctx, AppDatabase::class.java, "carrito.db")
            .addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    // Prepopulate catalog with common Argentine grocery items
                    CoroutineScope(Dispatchers.IO).launch {
                        val catalog = listOf(
                            Triple("Leche", "🥛", "Lácteos"), Triple("Yogur", "🥛", "Lácteos"), Triple("Queso", "🧀", "Lácteos"), Triple("Manteca", "🧈", "Lácteos"), Triple("Crema", "🥛", "Lácteos"),
                            Triple("Manzana", "🍎", "Frutas"), Triple("Banana", "🍌", "Frutas"), Triple("Naranja", "🍊", "Frutas"), Triple("Limón", "🍋", "Frutas"), Triple("Frutilla", "🍓", "Frutas"),
                            Triple("Tomate", "🍅", "Verduras"), Triple("Lechuga", "🥬", "Verduras"), Triple("Cebolla", "🧅", "Verduras"), Triple("Papa", "🥔", "Verduras"), Triple("Zanahoria", "🥕", "Verduras"), Triple("Ajo", "🧄", "Verduras"),
                            Triple("Pollo", "🍗", "Carnes"), Triple("Carne picada", "🥩", "Carnes"), Triple("Milanesa", "🥩", "Carnes"), Triple("Asado", "🥩", "Carnes"), Triple("Cerdo", "🥩", "Carnes"),
                            Triple("Arroz", "🍚", "Almacén"), Triple("Fideos", "🍝", "Almacén"), Triple("Aceite", "🫒", "Almacén"), Triple("Azúcar", "🍬", "Almacén"), Triple("Harina", "🌾", "Almacén"), Triple("Sal", "🧂", "Almacén"), Triple("Polenta", "🌽", "Almacén"),
                            Triple("Pan", "🍞", "Panadería"), Triple("Galletitas", "🍪", "Panadería"), Triple("Facturas", "🥐", "Panadería"), Triple("Tostadas", "🍞", "Panadería"),
                            Triple("Agua", "💧", "Bebidas"), Triple("Gaseosa", "🥤", "Bebidas"), Triple("Jugo", "🧃", "Bebidas"), Triple("Cerveza", "🍺", "Bebidas"), Triple("Vino", "🍷", "Bebidas"),
                            Triple("Jabón", "🧼", "Limpieza"), Triple("Detergente", "🧴", "Limpieza"), Triple("Lavandina", "🧪", "Limpieza"), Triple("Suavizante", "🌸", "Limpieza"), Triple("Papel higiénico", "🧻", "Limpieza"),
                            Triple("Shampoo", "🧴", "Higiene"), Triple("Jabón líquido", "🧴", "Higiene"), Triple("Desodorante", "🪥", "Higiene"), Triple("Pasta dental", "🪥", "Higiene"),
                            Triple("Huevos", "🥚", "Granja"), Triple("Jamón", "🥓", "Granja"), Triple("Salchichas", "🌭", "Granja")
                        )
                        catalog.forEach { (name, emoji, cat) ->
                            db.execSQL("INSERT OR IGNORE INTO catalog (name, emoji, category, defaultUnit, timesUsed) VALUES ('$name', '$emoji', '$cat', '', 0)")
                        }
                    }
                }
            })
            .build()

    @Provides @Singleton fun listDao(db: AppDatabase) = db.listDao()
    @Provides @Singleton fun itemDao(db: AppDatabase) = db.itemDao()
    @Provides @Singleton fun catalogDao(db: AppDatabase) = db.catalogDao()
}
