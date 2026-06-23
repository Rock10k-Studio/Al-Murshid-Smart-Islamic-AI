package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "islamic_content")
data class IslamicContentEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val content: String,
    val category: String, // "quran", "hadith", "fatawa", "user_docs"
    val reference: String, // E.g., "سورة البقرة: 183", "صحيح مسلم: 124"
    val sourceFile: String? = null, // Path or name of the text/zip file
    val dateAdded: Long = System.currentTimeMillis()
)
