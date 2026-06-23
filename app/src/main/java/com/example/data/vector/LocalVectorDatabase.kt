package com.example.data.vector

import android.util.Log
import com.example.data.IslamicContentEntity
import com.example.nlp.ArabicNlpHelper
import com.example.ui.ScoredResult
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * A state-of-the-art on-device Local Vector Database with built-in
 * Semantic Embedding expansion and Cosine Similarity Indexing for Arabic text.
 * Runs 100% offline, privacy-safe, and instantly.
 */
object LocalVectorDatabase {
    private const val TAG = "LocalVectorDatabase"

    // Index tracking
    private var isIndexed = false
    private val vocabIndex = mutableMapOf<String, Int>()
    private val inverseVocabIndex = mutableMapOf<Int, String>()
    private val idfMap = mutableMapOf<String, Double>()
    
    // Cached Document Vectors: Map of dynamic Entity ID -> Vector Float Array representing L2-normalized weights
    private val documentVectors = mutableMapOf<Int, DoubleArray>()
    private var indexedEntities = mutableListOf<IslamicContentEntity>()

    // Lightweight Synonyms/Semantics Word Proximity Thesaurus
    // Maps a standardized Arabic word stem to a list of closely related semantic concepts (equivalent to word embeddings similarities)
    private val ArabicThesaurus = mapOf(
        "صوم" to setOf("صيام", "تصوموا", "رمضان", "امساك", "فطر", "افطار", "قضاء"),
        "صيام" to setOf("صوم", "تصوموا", "رمضان", "امساك", "فطر", "افطار", "قضاء", "البقرة"),
        "تصوموا" to setOf("صوم", "صيام", "رمضان", "امساك", "خير"),
        "رمضان" to setOf("صوم", "صيام", "تصوموا", "القدر", "تراويح", "ليله", "نزول", "القران"),
        "صلاه" to setOf("صلوات", "صلي", "سجود", "ركوع", "مسجد", "مساجد", "القبلة", "جمعه", "قيام"),
        "صلوات" to setOf("صلاه", "صلي", "سجود", "ركوع", "مسجد"),
        "سجود" to setOf("صلاه", "صلي", "ركوع", "مسجد", "مؤمن"),
        "ركوع" to setOf("صلاه", "صلي", "سجود", "مسجد"),
        "مسجد" to setOf("مساجد", "صلاه", "صلي", "سجود", "ركوع", "القدر"),
        "بر" to setOf("احسان", "والدين", "صدق", "رحمه", "اخلاق", "تقوى", "صالح", "معروف"),
        "احسان" to setOf("بر", "والدين", "رحمه", "تقوى", "صالح", "معروف"),
        "والدين" to setOf("بر", "احسان", "رحمه", "ام", "اب"),
        "صدق" to setOf("امانه", "اخلاص", "نيه", "البر", "صديق", "حق", "يهدي"),
        "امانه" to setOf("صدق", "اخلاص", "نيه", "عهد"),
        "اخلاص" to setOf("نيه", "اعمال", "صدق", "قلب", "امانه"),
        "نيه" to setOf("اعمال", "نوى", "اخلاص", "صدق", "هجره", "قلب"),
        "اعمال" to setOf("نيه", "نوى", "عمل", "صالح"),
        "كذب" to setOf("غش", "خديعه", "فجور", "كذاب", "نار"),
        "غش" to setOf("كذب", "خديعه", "فجور", "ليس", "طعام"),
        "فجور" to setOf("كذب", "غش", "كذاب", "نار"),
        "علم" to setOf("علماء", "فقيه", "حكم", "معرفه", "درايه", "طلب", "كتاب", "ذكر", "تعلم", "يسر"),
        "علماء" to setOf("علم", "فقيه", "حكم", "معرفه", "ذكر"),
        "فقيه" to setOf("علم", "حكم", "فتوى", "علماء", "دليل"),
        "فتوى" to setOf("حكم", "مفتي", "دليل", "سؤال", "ذكر", "علم", "ذين"),
        "سفر" to setOf("مسافر", "قصر", "جمع", "رخصه", "مريض", "يسر", "حرج"),
        "مرض" to setOf("مريض", "صحه", "رخصه", "علاج", "سفر", "يسر", "حرج"),
        "مريض" to setOf("مرض", "صحه", "رخصه", "سفر"),
        "يسر" to setOf("رخصه", "حرج", "سفر", "مرض", "شريعه", "سهل"),
        "تراحم" to setOf("تعاطف", "تواد", "مؤمنين", "جسد", "رحمه", "سهر"),
        "تعاطف" to setOf("تراحم", "تواد", "مؤمنين", "جسد"),
        "تواد" to setOf("تراحم", "تعاطف", "مؤمنين", "جسد"),
        "مؤمن" to setOf("مؤمنين", "جسد", "توكل", "تقوى", "عمل", "صالح", "ايمان", "حياء", "شعب"),
        "ايمان" to setOf("مؤمن", "مؤمنين", "اعمال", "طهور", "حياء", "شعب", "بر", "صدق", "صلاه", "اسلام"),
        "اسلام" to setOf("ايمان", "مؤمن", "مسلم", "صلاه", "زكاه", "حج", "صوم"),
        "مسلم" to setOf("اسلام", "سليم", "لسان", "يد", "حرام", "دم", "مال", "عرض", "اخ"),
        "حياء" to setOf("ايمان", "شعب", "اخلاق", "مؤمن", "جميل"),
        "جار" to setOf("جوار", "جيران", "ضيف", "أخ", "تراحم", "تعاطف", "معروف", "احسان"),
        "ضيف" to setOf("جار", "جيران", "جوار", "احسان", "اخلاق", "كريم"),
        "طهور" to setOf("طهارة", "وضوء", "ايمان", "صلاه", "سجود", "مسجد"),
        "وضوء" to setOf("طهور", "طهارة", "صلاه", "سجود", "مسجد"),
        "حرام" to setOf("دم", "مال", "عرض", "حرمة", "مسلم"),
        "دم" to setOf("حرام", "مال", "عرض", "مسلم"),
        "مال" to setOf("حرام", "دم", "عرض", "مسلم", "زكاة"),
        "عرض" to setOf("حرام", "دم", "مال", "مسلم"),
        "منافق" to setOf("كذب", "خلف", "خيانة", "خائن", "آية", "غش"),
        "قلوب" to setOf("اعمال", "صور", "أجساد", "نيه", "إخلاص"),
        "سكينة" to setOf("رحمة", "قرأن", "تدارس", "ملائكة", "ذكر", "مسجد")
    )

    /**
     * Build the vector index for all local database entities.
     * Extracts vocabulary, calculates Document Frequency, IDFs, and transforms document text into vectors.
     */
    @Synchronized
    fun buildIndex(entities: List<IslamicContentEntity>) {
        try {
            Log.d(TAG, "Starting Vector Database indexing. Entity count: ${entities.size}")
            
            vocabIndex.clear()
            inverseVocabIndex.clear()
            idfMap.clear()
            documentVectors.clear()
            indexedEntities.clear()
            indexedEntities.addAll(entities)

            if (entities.isEmpty()) {
                isIndexed = false
                return
            }

            // 1. First Pass: Tokenize and build vocabulary index + calculate Document Frequency (DF)
            val docFrequencyMap = mutableMapOf<String, Double>()
            val totalDocs = entities.size.toDouble()

            for (entity in entities) {
                val uniqueTermsInDoc = extractStandardTerms(entity.title + " " + entity.content)
                for (term in uniqueTermsInDoc) {
                    docFrequencyMap[term] = (docFrequencyMap[term] ?: 0.0) + 1.0
                }
            }

            // Build Vocab mappings and IDF weights
            var vId = 0
            for ((term, df) in docFrequencyMap) {
                vocabIndex[term] = vId
                inverseVocabIndex[vId] = term
                
                // IDF formula with clipping to avoid zero
                val idf = ln(1.0 + (totalDocs / (1.0 + df)))
                idfMap[term] = idf
                vId++
            }

            Log.d(TAG, "Vector Space dimensions built successfully. Total unique vocabulary dimensions: $vId")

            // 2. Second Pass: Vectorize each document and save in local vector cache
            for (entity in entities) {
                val vector = extractVector(entity.title, entity.content)
                documentVectors[entity.id] = vector
            }

            isIndexed = true
            Log.d(TAG, "Vector database creation and text indexing completed successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Error building local vector database indexing: ${e.message}", e)
            isIndexed = false
        }
    }

    /**
     * Tokenize content, clean text from Tashkeel, normalize spelling, and filter short words.
     */
    private fun extractStandardTerms(text: String): Set<String> {
        val normalized = ArabicNlpHelper.normalizeArabic(text)
        val tokens = normalized.split("\\s+".toRegex())
        val cleanTerms = mutableSetOf<String>()
        for (tok in tokens) {
            val clean = tok.trim().filter { it.isLetter() }
            if (clean.length >= 2) {
                cleanTerms.add(clean)
            }
        }
        return cleanTerms
    }

    /**
     * Convert an item's title & content into a high-dimensional TF-IDF vector of size `vocabIndex.size`.
     * Applies the Semantic Thesaurus to spread weights over semantic synonyms and reduce vocabulary mismatch.
     */
    private fun extractVector(title: String, content: String): DoubleArray {
        val vector = DoubleArray(vocabIndex.size)
        val cleanTitleTerms = extractStandardTerms(title)
        val cleanContentTerms = extractStandardTerms(content)

        // Count raw occurrences (TF - Term Frequency)
        val occurrences = mutableMapOf<String, Double>()
        
        // Give higher weight/importance to words appearing in the TITLE (e.g., 3.0x multiplier)
        for (term in cleanTitleTerms) {
            occurrences[term] = (occurrences[term] ?: 0.0) + 3.0
        }
        for (term in cleanContentTerms) {
            occurrences[term] = (occurrences[term] ?: 0.0) + 1.0
        }

        // Apply Semantic Synonyms Expansion (Embedding-like weight projection)
        val expandedOccurrences = mutableMapOf<String, Double>()
        for ((term, weight) in occurrences) {
            // Add original weight
            expandedOccurrences[term] = (expandedOccurrences[term] ?: 0.0) + weight
            
            // Distribute 60% of the weight to related semantic terms
            val synonyms = ArabicThesaurus[term]
            if (synonyms != null) {
                for (syn in synonyms) {
                    expandedOccurrences[syn] = (expandedOccurrences[syn] ?: 0.0) + (weight * 0.6)
                }
            }
        }

        // Compute final vector components: Vector(i) = TF(i) * IDF(i)
        for ((term, weight) in expandedOccurrences) {
            val vIndex = vocabIndex[term]
            val idf = idfMap[term]
            if (vIndex != null && idf != null) {
                vector[vIndex] = weight * idf
            }
        }

        // Normalize Vector to Unit Length (L2 Normalization) so cosine similarity is simply the dot product!
        normalizeL2(vector)
        return vector
    }

    /**
     * Compute L2 Normalization in place
     */
    private fun normalizeL2(vec: DoubleArray) {
        var sumSquares = 0.0
        for (v in vec) {
            sumSquares += v * v
        }
        val norm = sqrt(sumSquares)
        if (norm > 0.0) {
            for (i in vec.indices) {
                vec[i] = vec[i] / norm
            }
        }
    }

    /**
     * Perform Semantic Vector Search against local index.
     * Converts query to the standardized high-dimensional vector workspace, applies synonym-matching,
     * and performs matrix cosine similarity scoring over all on-device vectors.
     */
    fun search(query: String, category: String = "all", limit: Int = 15): List<ScoredResult> {
        val cleanQuery = query.trim()
        if (cleanQuery.isEmpty() || !isIndexed || indexedEntities.isEmpty()) {
            return emptyList()
        }

        try {
            // Build the query dense search vector in the unified vector space
            val queryVector = extractVector("", cleanQuery)

            val scoredResults = mutableListOf<ScoredResult>()

            for (entity in indexedEntities) {
                // Category filtering constraint
                if (category != "all" && entity.category != category) {
                    continue
                }

                val docVector = documentVectors[entity.id]
                if (docVector != null) {
                    // Cosine similarity of L2 normalized vectors is simply the dot product!
                    val similarity = dotProduct(queryVector, docVector)
                    
                    if (similarity > 0.0) {
                        scoredResults.add(ScoredResult(entity, similarity))
                    }
                }
            }

            // Sort by Cosine Similarity in descending order
            return scoredResults.sortedByDescending { it.score }.take(limit)
        } catch (e: Exception) {
            Log.e(TAG, "Search query vector database execution failed: ${e.message}", e)
            return emptyList()
        }
    }

    /**
     * Calculate vector dot product
     */
    private fun dotProduct(vecA: DoubleArray, vecB: DoubleArray): Double {
        var product = 0.0
        val len = vecA.size.coerceAtMost(vecB.size)
        for (i in 0 until len) {
            product += vecA[i] * vecB[i]
        }
        return product
    }

    /**
     * Dynamically insert or index a newly created/added text entity on the fly.
     */
    @Synchronized
    fun indexNewEntity(entity: IslamicContentEntity) {
        try {
            val updatedList = indexedEntities.toMutableList()
            updatedList.add(entity)
            // Re-index to ensure correct dynamic vocabulary and TF-IDF IDF weights are maintained
            buildIndex(updatedList)
            Log.d(TAG, "Dynamically indexed new entity ID ${entity.id} successfully into local vector space.")
        } catch (e: Exception) {
            Log.e(TAG, "Unable to dynamically index new entity into Vector database: ${e.message}", e)
        }
    }

    /**
     * Clear local indexing memory
     */
    @Synchronized
    fun clear() {
        Log.d(TAG, "Clearing complete Local Vector Database cache.")
        vocabIndex.clear()
        inverseVocabIndex.clear()
        idfMap.clear()
        documentVectors.clear()
        indexedEntities.clear()
        isIndexed = false
    }
}
