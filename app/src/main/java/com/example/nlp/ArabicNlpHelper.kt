package com.example.nlp

import java.util.regex.Pattern

object ArabicNlpHelper {

    // Redundant arabic diacritics pattern (Tashkeel)
    private val TASHKEEL_PATTERN = Pattern.compile("[\\u064B-\\u0652\\u0640]")

    /**
     * Remove all Arabic Tashkeel (diacritics: Fatha, Damma, Kasra, Shaddah, Sukun, Tanween...)
     */
    fun stripTashkeel(text: String): String {
        return TASHKEEL_PATTERN.matcher(text).replaceAll("")
    }

    /**
     * Standardize Arabic letters. This helps matching words regardless of spelling variations:
     * - Normalizes different Hamzas (أ, إ, آ) to standard Alef (ا)
     * - Normalizes Taa Marbuta (ة) to Haa (ه)
     * - Normalizes Alef Maksura (ى) to Yaa (ي)
     */
    fun normalizeArabic(text: String): String {
        var clean = stripTashkeel(text)
        clean = clean.replace("[أإآ]".toRegex(), "ا")
        clean = clean.replace("ة\\b".toRegex(), "ه")
        clean = clean.replace("ى\\b".toRegex(), "ي")
        return clean.trim().lowercase()
    }

    /**
     * Calculate a similarity matching score between a query and a target text based on Arabic word overlaps.
     * Returns a score between 0.0 (no match) and 1.0 (perfect match).
     */
    fun calculateMatchScore(query: String, targetTitle: String, targetContent: String): Double {
        val normQuery = normalizeArabic(query)
        if (normQuery.isEmpty()) return 0.0

        val normTitle = normalizeArabic(targetTitle)
        val normContent = normalizeArabic(targetContent)

        // Rule 1: Exact whole phrase matches get extreme bonus
        if (normContent.contains(normQuery)) {
            val ratio = normQuery.length.toDouble() / normContent.length.coerceAtLeast(1)
            return 0.5 + (ratio * 0.5) // Minimum 0.75 for exact inclusion
        }

        // Rule 2: Word intersection score
        val queryWords = normQuery.split("\\s+".toRegex()).filter { it.length > 2 }.toSet()
        if (queryWords.isEmpty()) {
            // fallback to small words check
            val smallWords = normQuery.split("\\s+".toRegex()).toSet()
            val targetWords = (normTitle + " " + normContent).split("\\s+".toRegex()).toSet()
            val intersection = smallWords.intersect(targetWords)
            return if (intersection.isNotEmpty()) 0.2 else 0.0
        }

        val targetWords = (normTitle + " " + normContent).split("\\s+".toRegex()).toSet()
        val intersection = queryWords.intersect(targetWords)

        if (intersection.isEmpty()) return 0.0

        // Bonus if words match the title
        val titleWords = normTitle.split("\\s+".toRegex()).toSet()
        val titleIntersection = queryWords.intersect(titleWords)
        val titleBonus = if (titleIntersection.isNotEmpty()) 0.25 else 0.0

        val wordOverlapRatio = intersection.size.toDouble() / queryWords.size
        return (wordOverlapRatio * 0.75 + titleBonus).coerceAtMost(1.0)
    }
}
