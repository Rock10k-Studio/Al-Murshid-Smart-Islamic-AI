package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "quran_progress")
data class QuranProgressEntity(
    @PrimaryKey val id: Int, // Refers to IslamicContentEntity.id
    val status: String, // "unread", "in_progress", "read"
    val lastReadPosition: Int = 0, // Optional percentage or scroll position or verse marker
    val lastUpdated: Long = System.currentTimeMillis(),
    val notes: String = "" // Custom notes or user thoughts
)
