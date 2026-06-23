package com.example.data

import android.content.Context
import kotlinx.coroutines.flow.Flow
import java.io.File

class IslamicRepository(
    private val database: AppDatabase,
    private val context: Context
) {
    private val dao = database.islamicContentDao()

    val allContent: Flow<List<IslamicContentEntity>> = dao.getAllContent()

    fun getContentByCategory(category: String): Flow<List<IslamicContentEntity>> {
        return dao.getContentByCategory(category)
    }

    suspend fun searchContent(query: String): List<IslamicContentEntity> {
        return dao.searchContent(query)
    }

    suspend fun searchContentByCategory(category: String, query: String): List<IslamicContentEntity> {
        return dao.searchContentByCategory(category, query)
    }

    suspend fun insertContent(item: IslamicContentEntity): Long {
        return dao.insertContent(item)
    }

    suspend fun insertContentList(items: List<IslamicContentEntity>) {
        dao.insertContentList(items)
    }

    suspend fun deleteById(id: Int) {
        dao.deleteById(id)
    }

    suspend fun deleteBySourceFile(sourceFile: String) {
        dao.deleteBySourceFile(sourceFile)
    }

    suspend fun clearCategory(category: String) {
        dao.clearCategory(category)
    }

    suspend fun clearAll() {
        dao.clearAll()
    }

    // --- Bookmarks / Favorites Support ---
    val allFavorites: Flow<List<IslamicContentEntity>> = dao.getFavoriteContent()

    suspend fun insertFavorite(id: Int) {
        dao.insertFavorite(FavoriteEntity(id))
    }

    suspend fun deleteFavorite(id: Int) {
        dao.deleteFavoriteById(id)
    }

    suspend fun isFavorite(id: Int): Boolean {
        return dao.isFavorite(id)
    }

    fun isFavoriteFlow(id: Int): Flow<Boolean> {
        return dao.isFavoriteFlow(id)
    }

    // --- Quran Progress Tracker ---
    val allQuranProgress: Flow<List<QuranProgressEntity>> = dao.getAllProgressFlow()

    suspend fun insertOrUpdateProgress(progress: QuranProgressEntity) {
        dao.insertOrUpdateProgress(progress)
    }

    suspend fun getProgressById(id: Int): QuranProgressEntity? {
        return dao.getProgressById(id)
    }

    fun getProgressByIdFlow(id: Int): Flow<QuranProgressEntity?> {
        return dao.getProgressByIdFlow(id)
    }

    suspend fun checkAndSeedDatabase() {
        val initialList = IslamicInitialData.getInitialContent()
        for (item in initialList) {
            val existing = dao.getContentByTitleAndCategory(item.title, item.category)
            if (existing == null) {
                dao.insertContent(item)
            }
        }
    }
}
