package com.sharedshoppinglists.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.sharedshoppinglists.app.data.local.entity.AffinityProgramEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AffinityProgramDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(program: AffinityProgramEntity)

    @Update
    suspend fun update(program: AffinityProgramEntity)

    @Query("DELETE FROM affinity_programs WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM affinity_programs WHERE userId = :userId ORDER BY name ASC")
    fun getByUserId(userId: String): Flow<List<AffinityProgramEntity>>

    @Query("SELECT * FROM affinity_programs WHERE userId = :userId")
    suspend fun getByUserIdOnce(userId: String): List<AffinityProgramEntity>

    @Query("SELECT * FROM affinity_programs WHERE userId = :userId AND supermarketId = :supermarketId")
    suspend fun getBySupermarket(userId: String, supermarketId: String): List<AffinityProgramEntity>
}
