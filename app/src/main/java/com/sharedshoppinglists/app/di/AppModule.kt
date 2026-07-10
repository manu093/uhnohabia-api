package com.sharedshoppinglists.app.di

import android.content.Context
import androidx.room.Room
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.sharedshoppinglists.app.data.local.AppDatabase
import com.sharedshoppinglists.app.data.local.dao.AffinityProgramDao
import com.sharedshoppinglists.app.data.local.dao.CustomCategoryDao
import com.sharedshoppinglists.app.data.local.dao.DiscountCardDao
import com.sharedshoppinglists.app.data.local.dao.KnownProductDao
import com.sharedshoppinglists.app.data.local.dao.ManualPriceDao
import com.sharedshoppinglists.app.data.local.dao.ProductDao
import com.sharedshoppinglists.app.data.local.dao.ShoppingListDao
import com.sharedshoppinglists.app.data.local.dao.SupermarketDao
import com.sharedshoppinglists.app.data.network.ConnectivityNetworkMonitor
import com.sharedshoppinglists.app.data.network.NetworkMonitor
import com.sharedshoppinglists.app.data.remote.SepaCatalogClient
import com.sharedshoppinglists.app.data.remote.SepaCatalogClientImpl
import com.sharedshoppinglists.app.data.repository.CustomCategoryRepositoryImpl
import com.sharedshoppinglists.app.data.repository.DiscountCardRepositoryImpl
import com.sharedshoppinglists.app.data.repository.FirebaseAuthRepository
import com.sharedshoppinglists.app.data.repository.KnownProductRepositoryImpl
import com.sharedshoppinglists.app.data.repository.ManualPriceComparatorRepositoryImpl
import com.sharedshoppinglists.app.data.repository.SharedListRepositoryImpl
import com.sharedshoppinglists.app.data.repository.ShoppingListRepositoryImpl
import com.sharedshoppinglists.app.domain.repository.AuthRepository
import com.sharedshoppinglists.app.domain.repository.CustomCategoryRepository
import com.sharedshoppinglists.app.domain.repository.DiscountCardRepository
import com.sharedshoppinglists.app.domain.repository.KnownProductRepository
import com.sharedshoppinglists.app.domain.repository.ManualPriceComparatorRepository
import com.sharedshoppinglists.app.domain.repository.SharedListRepository
import com.sharedshoppinglists.app.domain.repository.ShoppingListRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @Singleton fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()
    @Provides @Singleton fun provideFirebaseFirestore(): FirebaseFirestore = FirebaseFirestore.getInstance()

    @Provides @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        // Delegate to the singleton so the app and SyncWorker share ONE Room
        // instance (one InvalidationTracker) instead of two on the same file.
        AppDatabase.getInstance(context)

    // DAOs
    @Provides @Singleton fun provideShoppingListDao(db: AppDatabase) = db.shoppingListDao()
    @Provides @Singleton fun provideProductDao(db: AppDatabase) = db.productDao()
    @Provides @Singleton fun provideDiscountCardDao(db: AppDatabase) = db.discountCardDao()
    @Provides @Singleton fun provideCustomCategoryDao(db: AppDatabase) = db.customCategoryDao()
    @Provides @Singleton fun provideKnownProductDao(db: AppDatabase) = db.knownProductDao()
    @Provides @Singleton fun provideSupermarketDao(db: AppDatabase) = db.supermarketDao()
    @Provides @Singleton fun provideManualPriceDao(db: AppDatabase) = db.manualPriceDao()
    @Provides @Singleton fun provideAffinityProgramDao(db: AppDatabase) = db.affinityProgramDao()

    @Provides @Singleton
    fun provideNetworkMonitor(@ApplicationContext context: Context): NetworkMonitor =
        ConnectivityNetworkMonitor(context)

    // App-lifetime scope for fire-and-forget background sync (avoids creating a
    // new, never-cancelled CoroutineScope on every repository call).
    @Provides @Singleton
    fun provideAppCoroutineScope(): kotlinx.coroutines.CoroutineScope =
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)

    @Provides @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    // Repositories
    @Provides @Singleton
    fun provideAuthRepository(auth: FirebaseAuth, db: AppDatabase): AuthRepository =
        FirebaseAuthRepository(auth, db)

    @Provides @Singleton
    fun provideShoppingListRepository(
        listDao: ShoppingListDao, productDao: ProductDao, customCategoryDao: CustomCategoryDao,
        firestore: FirebaseFirestore, networkMonitor: NetworkMonitor,
        appScope: kotlinx.coroutines.CoroutineScope
    ): ShoppingListRepository = ShoppingListRepositoryImpl(listDao, productDao, customCategoryDao, firestore, networkMonitor, appScope)

    @Provides @Singleton
    fun provideSharedListRepository(firestore: FirebaseFirestore): SharedListRepository =
        SharedListRepositoryImpl(firestore)

    @Provides @Singleton
    fun provideDiscountCardRepository(dao: DiscountCardDao): DiscountCardRepository =
        DiscountCardRepositoryImpl(dao)

    @Provides @Singleton
    fun provideCustomCategoryRepository(dao: CustomCategoryDao): CustomCategoryRepository =
        CustomCategoryRepositoryImpl(dao)

    @Provides @Singleton
    fun provideKnownProductRepository(dao: KnownProductDao, appScope: kotlinx.coroutines.CoroutineScope): KnownProductRepository =
        KnownProductRepositoryImpl(dao, appScope)

    @Provides @Singleton
    fun provideSepaCatalogClient(httpClient: OkHttpClient): SepaCatalogClient =
        SepaCatalogClientImpl(httpClient)

    @Provides @Singleton
    fun provideManualPriceComparatorRepository(
        supermarketDao: SupermarketDao, manualPriceDao: ManualPriceDao,
        affinityProgramDao: AffinityProgramDao,
        shoppingListRepository: ShoppingListRepository,
        discountCardRepository: DiscountCardRepository,
        firestore: FirebaseFirestore
    ): ManualPriceComparatorRepository = ManualPriceComparatorRepositoryImpl(
        supermarketDao, manualPriceDao, affinityProgramDao,
        shoppingListRepository, discountCardRepository, firestore
    )
}
