package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "islamic_content")
data class IslamicContentEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val type: String,               // "quran", "hadith", "fatwa", "adhkar"
    val title: String,              // Head line or topic title
    val text: String,               // Real text (with original Arabic script & harakat etc.)
    val source: String,             // Sourced book or trusted website (e.g., صحيح البخاري, إسلام ويب)
    val keywords: String,           // Comma-separated listing for semantic terms
    val normalizedTitle: String,    // Prefiltered title (no diacritics, normalized characters)
    val normalizedText: String,     // Prefiltered body (no diacritics, normalized characters)
    val isFavorite: Boolean = false, // Saved marker
    val timestamp: Long = System.currentTimeMillis() // Creation/Caching time
)
