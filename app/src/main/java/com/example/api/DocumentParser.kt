package com.example.api

import android.content.Context
import android.util.Log
import com.example.data.AppDatabase
import com.example.data.IslamicContentEntity
import java.io.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

object DocumentParser {
    private const val TAG = "DocumentParser"

    // Supported categories
    val CATEGORIES = listOf("quran", "hadith", "fatawa", "user_docs")

    /**
     * Get the base directories for each category in the application data folder.
     * Usually maps to: /storage/emulated/0/Android/data/com.example/files/<category>
     */
    fun getCategoryDir(context: Context, category: String): File {
        val baseDir = context.getExternalFilesDir(null) ?: context.filesDir
        val catDir = File(baseDir, category)
        if (!catDir.exists()) {
            catDir.mkdirs()
        }
        return catDir
    }

    /**
     * Get all initialized files inside the app directories
     */
    fun getFilesByCategory(context: Context, category: String): List<File> {
        val dir = getCategoryDir(context, category)
        return dir.listFiles { file -> file.isFile }?.toList() ?: emptyList()
    }

    /**
     * Create a new file in a category directory
     */
    fun writeTextFile(context: Context, category: String, filename: String, content: String): File {
        val dir = getCategoryDir(context, category)
        val file = File(dir, filename)
        file.writeText(content, Charsets.UTF_8)
        return file
    }

    /**
     * Decompress / Unzip a ZIP file, extract text files, and sort them into appropriate folders
     */
    fun unzipAndClassify(context: Context, zipFile: File): List<File> {
        val extractedFiles = mutableListOf<File>()
        try {
            ZipInputStream(FileInputStream(zipFile)).use { zipInputStream ->
                var entry: ZipEntry? = zipInputStream.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val name = entry.name.lowercase()
                        // Determine target folder based on file name contents
                        val targetCategory = when {
                            name.contains("quran") || name.contains("قرآن") || name.contains("قران") -> "quran"
                            name.contains("hadith") || name.contains("حديث") || name.contains("حديث") -> "hadith"
                            name.contains("fatwa") || name.contains("fatawa") || name.contains("فتوى") || name.contains("فتأوى") -> "fatawa"
                            else -> "user_docs"
                        }
                        
                        val targetDir = getCategoryDir(context, targetCategory)
                        // Sanitize filename to prevent directory traversal
                        val simpleName = File(entry.name).name
                        val targetFile = File(targetDir, simpleName)
                        
                        BufferedOutputStream(FileOutputStream(targetFile)).use { outputStream ->
                            val buffer = ByteArray(4096)
                            var len: Int
                            while (zipInputStream.read(buffer).also { len = it } > 0) {
                                outputStream.write(buffer, 0, len)
                            }
                        }
                        extractedFiles.add(targetFile)
                    }
                    zipInputStream.closeEntry()
                    entry = zipInputStream.nextEntry
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unzipping failed", e)
        }
        return extractedFiles
    }

    /**
     * Parse a text document file and slice it into smaller granular pieces (chunks) suitable for Room Indexing.
     * Chunks by double-newlines, paragraph numbers, custom markers like "---" or "الباب", and so on.
     */
    fun parseTextToEntities(file: File, category: String): List<IslamicContentEntity> {
        val entities = mutableListOf<IslamicContentEntity>()
        try {
            val content = file.readText(Charsets.UTF_8)
            val filename = file.name
            
            // Try splitting by common markers to obtain high quality paragraphs
            val rawChunks = when {
                content.contains("---") -> content.split("---")
                content.contains("\n\n") -> content.split("\n\n")
                content.contains("●") -> content.split("●")
                else -> content.split(Regex("(?<=\\.)\\s+\n")) // split by period followed by newline
            }

            var chunkIndex = 1
            for (raw in rawChunks) {
                val secText = raw.trim()
                if (secText.isEmpty() || secText.length < 10) continue // Skip tiny fragments

                // Extract a suitable title from the first line, falls back to file name
                val lines = secText.lines().map { it.trim() }.filter { it.isNotEmpty() }
                val title = if (lines.isNotEmpty()) {
                    val firstLine = lines[0]
                    if (firstLine.length < 50) firstLine else firstLine.take(47) + "..."
                } else {
                    "فقرة عدد $chunkIndex"
                }

                // Generate clean reference
                val reference = "ملف: $filename - فقرة $chunkIndex"

                entities.add(
                    IslamicContentEntity(
                        title = title,
                        content = secText,
                        category = category,
                        reference = reference,
                        sourceFile = filename
                    )
                )
                chunkIndex++
            }
        } catch (e: Exception) {
            Log.e(TAG, "Parsing failed for file: ${file.absolutePath}", e)
        }
        return entities
    }

    /**
     * Parse a whole folder of files, index into the Room database, deleting previous index entries of that file
     */
    suspend fun indexFileIntoDb(context: Context, database: AppDatabase, file: File, category: String): Int {
        val dao = database.islamicContentDao()
        val fileName = file.name
        
        // Remove prior entries of same file to avoid duplicate indexing
        dao.deleteBySourceFile(fileName)
        
        // Parse into entities
        val entities = parseTextToEntities(file, category)
        if (entities.isNotEmpty()) {
            dao.insertContentList(entities)
        }
        return entities.size
    }
}
