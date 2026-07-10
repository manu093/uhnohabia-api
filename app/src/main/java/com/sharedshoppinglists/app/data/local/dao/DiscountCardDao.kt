package com.sharedshoppinglists.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.sharedshoppinglists.app.data.local.entity.DiscountCardEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DiscountCardDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(card: DiscountCardEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(cards: List<DiscountCardEntity>)

    @Update
    suspend fun update(card: DiscountCardEntity)

    @Delete
    suspend fun delete(card: DiscountCardEntity)

    @Query("DELETE FROM discount_cards WHERE id = :cardId")
    suspend fun deleteById(cardId: String)

    @Query("SELECT * FROM discount_cards WHERE id = :cardId")
    suspend fun getById(cardId: String): DiscountCardEntity?

    @Query("SELECT * FROM discount_cards WHERE userId = :userId")
    fun getByUserId(userId: String): Flow<List<DiscountCardEntity>>

    @Query("SELECT * FROM discount_cards")
    fun getAll(): Flow<List<DiscountCardEntity>>

    @Query("SELECT * FROM discount_cards")
    suspend fun getAllOnce(): List<DiscountCardEntity>
}
