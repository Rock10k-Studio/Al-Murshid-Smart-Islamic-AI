package com.example.data

import com.example.nlp.ArabicNlpHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class IslamicRepository(private val dao: IslamicContentDao) {

    val allContent: Flow<List<IslamicContentEntity>> = dao.getAllContent()
    val favorites: Flow<List<IslamicContentEntity>> = dao.getFavorites()

    fun getContentByType(type: String): Flow<List<IslamicContentEntity>> {
        return dao.getContentByType(type)
    }

    suspend fun insert(content: IslamicContentEntity): Long {
        return dao.insert(content)
    }

    suspend fun update(content: IslamicContentEntity) {
        dao.update(content)
    }

    suspend fun deleteById(id: Int) {
        dao.deleteById(id)
    }

    /**
     * Seeds initial database items if it is empty.
     */
    suspend fun checkAndSeedDatabase() {
        if (dao.count() == 0) {
            val seedList = IslamicInitialData.getSeedEntities()
            dao.insertAll(seedList)
        }
    }

    /**
     * Runs our lightweight Offline Arabic Semantic Keyword Search.
     * Combines SQL raw matching and local relevance-ranking calculations.
     */
    suspend fun performLocalSearch(query: String, typeFilter: String? = null): List<SearchResult> {
        val queryTerms = ArabicNlpHelper.extractSearchTerms(query)
        if (queryTerms.isEmpty()) {
            // If the query was empty or only had stop words, return all items of that type (or absolute all)
            val baseList = if (typeFilter != null && typeFilter != "all") {
                dao.getContentByType(typeFilter).first()
            } else {
                dao.getAllContent().first()
            }
            return baseList.map { SearchResult(it, 1.0) }
        }

        // We fetch candidates matching ANY of the query terms to maintain speed and low RAM
        val candidateSet = mutableSetOf<IslamicContentEntity>()
        for (term in queryTerms) {
            val localResults = dao.searchLocalRaw(term)
            candidateSet.addAll(localResults)
        }

        // Failsafe: if the candidate set is empty, we do a scan over the entire local DB
        // to ensure we capture any hidden semantic matching
        if (candidateSet.isEmpty()) {
            val allLocal = dao.getAllContent().first()
            candidateSet.addAll(allLocal)
        }

        // Now calculate detailed scores for each matched candidate
        val scoredList = candidateSet.mapNotNull { entity ->
            // Filter by type if a specific category is requested
            if (typeFilter != null && typeFilter != "all" && entity.type != typeFilter) {
                return@mapNotNull null
            }

            val score = ArabicNlpHelper.calculateMatchScore(
                normalizedQueryTerms = queryTerms,
                normalizedTitle = entity.normalizedTitle,
                normalizedText = entity.normalizedText,
                keywordsCsv = entity.keywords
            )

            if (score > 0.0) {
                SearchResult(entity, score)
            } else {
                null
            }
        }

        // Sort descending by calculated match weight
        return scoredList.sortedByDescending { it.score }
    }
}

/**
 * Encapsulates the matching search result along with its relevancy score
 */
data class SearchResult(
    val entity: IslamicContentEntity,
    val score: Double
)
