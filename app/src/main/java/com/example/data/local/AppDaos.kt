package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface UploadHistoryDao {
    @Query("SELECT * FROM upload_history ORDER BY timestamp DESC")
    fun observeHistory(): Flow<List<UploadHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: UploadHistoryEntity): Long

    @Query("DELETE FROM upload_history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM upload_history")
    suspend fun clearAll()
}

@Dao
interface SavedTargetDao {
    @Query("SELECT * FROM saved_targets ORDER BY createdAt DESC")
    fun observeTargets(): Flow<List<SavedTargetEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(target: SavedTargetEntity): Long

    @Query("DELETE FROM saved_targets WHERE id = :id")
    suspend fun deleteById(id: Long)
}
