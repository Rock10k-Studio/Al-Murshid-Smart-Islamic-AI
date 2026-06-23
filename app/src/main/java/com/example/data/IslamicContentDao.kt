package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface IslamicContentDao {
    @Query("SELECT * FROM islamic_content ORDER BY id DESC")
    fun getAllContent(): Flow<List<IslamicContentEntity>>

    @Query("SELECT * FROM islamic_content WHERE category = :category ORDER BY id DESC")
    fun getContentByCategory(category: String): Flow<List<IslamicContentEntity>>

    @Query("SELECT * FROM islamic_content WHERE content LIKE '%' || :query || '%' OR title LIKE '%' || :query || '%' OR reference LIKE '%' || :query || '%'")
    suspend fun searchContent(query: String): List<IslamicContentEntity>

    @Query("SELECT * FROM islamic_content WHERE category = :category AND (content LIKE '%' || :query || '%' OR title LIKE '%' || :query || '%' OR reference LIKE '%' || :query || '%')")
    suspend fun searchContentByCategory(category: String, query: String): List<IslamicContentEntity>

    @Query("SELECT * FROM islamic_content WHERE title = :title AND category = :category LIMIT 1")
    suspend fun getContentByTitleAndCategory(title: String, category: String): IslamicContentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContent(item: IslamicContentEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContentList(items: List<IslamicContentEntity>)

    @Query("DELETE FROM islamic_content WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("DELETE FROM islamic_content WHERE sourceFile = :sourceFile")
    suspend fun deleteBySourceFile(sourceFile: String)

    @Query("DELETE FROM islamic_content WHERE category = :category")
    suspend fun clearCategory(category: String)

    @Query("DELETE FROM islamic_content")
    suspend fun clearAll()

    // --- Favorites CRUD / Bookmarking ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorite(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE id = :id")
    suspend fun deleteFavoriteById(id: Int)

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE id = :id LIMIT 1)")
    fun isFavoriteFlow(id: Int): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE id = :id LIMIT 1)")
    suspend fun isFavorite(id: Int): Boolean

    @Query("SELECT * FROM islamic_content WHERE id IN (SELECT id FROM favorites) ORDER BY id DESC")
    fun getFavoriteContent(): Flow<List<IslamicContentEntity>>

    // --- Quran Progress Tracking ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateProgress(progress: QuranProgressEntity)

    @Query("SELECT * FROM quran_progress WHERE id = :id LIMIT 1")
    suspend fun getProgressById(id: Int): QuranProgressEntity?

    @Query("SELECT * FROM quran_progress")
    fun getAllProgressFlow(): Flow<List<QuranProgressEntity>>

    @Query("SELECT * FROM quran_progress WHERE id = :id LIMIT 1")
    fun getProgressByIdFlow(id: Int): Flow<QuranProgressEntity?>
}
