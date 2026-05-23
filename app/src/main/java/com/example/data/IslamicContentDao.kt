package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface IslamicContentDao {

    @Query("SELECT * FROM islamic_content ORDER BY timestamp DESC")
    fun getAllContent(): Flow<List<IslamicContentEntity>>

    @Query("SELECT * FROM islamic_content WHERE type = :type ORDER BY timestamp DESC")
    fun getContentByType(type: String): Flow<List<IslamicContentEntity>>

    @Query("SELECT * FROM islamic_content WHERE isFavorite = 1 ORDER BY timestamp DESC")
    fun getFavorites(): Flow<List<IslamicContentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(content: IslamicContentEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(contents: List<IslamicContentEntity>)

    @Update
    suspend fun update(content: IslamicContentEntity)

    @Query("DELETE FROM islamic_content WHERE id = :id")
    suspend fun deleteById(id: Int)

    // Initial first-stage search on columns for speed and performance
    @Query("""
        SELECT * FROM islamic_content 
        WHERE normalizedText LIKE '%' || :queryFilter || '%' 
           OR normalizedTitle LIKE '%' || :queryFilter || '%' 
           OR keywords LIKE '%' || :queryFilter || '%'
    """)
    suspend fun searchLocalRaw(queryFilter: String): List<IslamicContentEntity>

    @Query("SELECT COUNT(*) FROM islamic_content")
    suspend fun count(): Int
}
