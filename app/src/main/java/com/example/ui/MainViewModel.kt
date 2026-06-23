package com.example.ui

import android.app.Application
import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.api.DocumentParser
import com.example.api.GeminiApiClient
import com.example.data.AppDatabase
import com.example.data.IslamicContentEntity
import com.example.data.IslamicRepository
import com.example.data.QuranProgressEntity
import com.example.nlp.ArabicNlpHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val database = AppDatabase.getDatabase(context)
    val repository = IslamicRepository(database, context)

    // --- UI States ---

    val searchQuery = MutableStateFlow("")
    val selectedCategory = MutableStateFlow("all") // "all", "quran", "hadith", "fatawa", "user_docs"

    // Custom Scored Results for UI search
    private val _searchResults = MutableStateFlow<List<ScoredResult>>(emptyList())
    val searchResults: StateFlow<List<ScoredResult>> = _searchResults.asStateFlow()

    private val prefs = context.getSharedPreferences("almanara_prefs", Context.MODE_PRIVATE)

    // --- Search History Tracking ---
    val recentQueries = MutableStateFlow<List<String>>(
        prefs.getString("recent_search_queries", "")
            ?.split("\n")
            ?.filter { it.isNotBlank() }
            ?: emptyList()
    )

    fun addQueryToHistory(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty() || trimmed.length < 2) return
        val currentList = recentQueries.value.toMutableList()
        currentList.remove(trimmed)
        currentList.add(0, trimmed)
        val updated = currentList.take(15)
        recentQueries.value = updated
        prefs.edit().putString("recent_search_queries", updated.joinToString("\n")).apply()
    }

    fun removeQueryFromHistory(query: String) {
        val updated = recentQueries.value.filter { it != query }
        recentQueries.value = updated
        prefs.edit().putString("recent_search_queries", updated.joinToString("\n")).apply()
    }

    fun clearHistory() {
        recentQueries.value = emptyList()
        prefs.edit().remove("recent_search_queries").apply()
    }

    // --- Bookmarks / Favorites Management ---
    val favoritesList: StateFlow<List<IslamicContentEntity>> = repository.allFavorites
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val favoriteIds: StateFlow<Set<Int>> = repository.allFavorites
        .map { list -> list.map { it.id }.toSet() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptySet()
        )

    fun toggleFavorite(id: Int) {
        viewModelScope.launch {
            val isAlreadyFav = repository.isFavorite(id)
            if (isAlreadyFav) {
                repository.deleteFavorite(id)
            } else {
                repository.insertFavorite(id)
            }
        }
    }

    // Gemini states
    val aiAnswer = MutableStateFlow("")
    val isAiLoading = MutableStateFlow(false)
    val referencedContexts = MutableStateFlow<List<IslamicContentEntity>>(emptyList())
    val enableWebSearch = MutableStateFlow(prefs.getBoolean("enable_web_search", true))
    val webSources = MutableStateFlow<List<com.example.api.WebSource>>(emptyList())
    val webQueries = MutableStateFlow<List<String>>(emptyList())
    val pendingChatQuery = MutableStateFlow("")

    // --- Configurations & Customizations ---
    val shariahPersona = MutableStateFlow(prefs.getString("shariah_persona", "balanced") ?: "balanced") // "balanced", "fiqh", "spiritual", "tafsir"
    val temperature = MutableStateFlow(prefs.getFloat("temperature", 0.3f))
    val relevanceThreshold = MutableStateFlow(prefs.getFloat("relevance_threshold", 0.2f))
    val useLocalDataOnly = MutableStateFlow(prefs.getBoolean("use_local_data_only", false))
    val customSystemPrompt = MutableStateFlow(prefs.getString("custom_system_prompt", "") ?: "")
    val customApiKey = MutableStateFlow(prefs.getString("custom_api_key", "") ?: "")
    val themeMode = MutableStateFlow(prefs.getString("theme_mode", "system") ?: "system") // "system", "light", "dark", "high_contrast"
    
    // --- Late Night Quran Reading Comfort Mode State ---
    val isLateNightReading = MutableStateFlow(prefs.getBoolean("is_late_night_reading", false))
    val quranReadingFontSize = MutableStateFlow(prefs.getFloat("quran_reading_font_size", 18f))
    val nightReadingTint = MutableStateFlow(prefs.getString("night_reading_tint", "amber") ?: "amber") // "amber", "sepia", "mint", "rose"
    
    // --- Local Vector Database & Semantic Offline Search Mode ---
    val useVectorSearch = MutableStateFlow(prefs.getBoolean("use_vector_search", true))

    // --- Quran Progress Tracking Management ---
    val quranProgressList: StateFlow<List<QuranProgressEntity>> = repository.allQuranProgress
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val quranProgressMap: StateFlow<Map<Int, QuranProgressEntity>> = repository.allQuranProgress
        .map { list -> list.associateBy { it.id } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyMap()
        )

    val quranContentList: StateFlow<List<IslamicContentEntity>> = repository.getContentByCategory("quran")
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun updateQuranProgress(id: Int, status: String, notes: String = "") {
        viewModelScope.launch {
            repository.insertOrUpdateProgress(
                QuranProgressEntity(
                    id = id,
                    status = status,
                    lastReadPosition = 0,
                    lastUpdated = System.currentTimeMillis(),
                    notes = notes
                )
            )
        }
    }
    
    // Track file storage lists dynamically
    private val _storedFiles = MutableStateFlow<Map<String, List<File>>>(emptyMap())
    val storedFiles: StateFlow<Map<String, List<File>>> = _storedFiles.asStateFlow()

    init {
        // Flow persistence tracking
        viewModelScope.launch {
            shariahPersona.collect { prefs.edit().putString("shariah_persona", it).apply() }
        }
        viewModelScope.launch {
            temperature.collect { prefs.edit().putFloat("temperature", it).apply() }
        }
        viewModelScope.launch {
            relevanceThreshold.collect { prefs.edit().putFloat("relevance_threshold", it).apply() }
        }
        viewModelScope.launch {
            useLocalDataOnly.collect { prefs.edit().putBoolean("use_local_data_only", it).apply() }
        }
        viewModelScope.launch {
            customSystemPrompt.collect { prefs.edit().putString("custom_system_prompt", it).apply() }
        }
        viewModelScope.launch {
            enableWebSearch.collect { prefs.edit().putBoolean("enable_web_search", it).apply() }
        }
        viewModelScope.launch {
            customApiKey.collect { prefs.edit().putString("custom_api_key", it).apply() }
        }
        viewModelScope.launch {
            themeMode.collect { prefs.edit().putString("theme_mode", it).apply() }
        }
        viewModelScope.launch {
            isLateNightReading.collect { prefs.edit().putBoolean("is_late_night_reading", it).apply() }
        }
        viewModelScope.launch {
            quranReadingFontSize.collect { prefs.edit().putFloat("quran_reading_font_size", it).apply() }
        }
        viewModelScope.launch {
            nightReadingTint.collect { prefs.edit().putString("night_reading_tint", it).apply() }
        }
        viewModelScope.launch {
            useVectorSearch.collect { prefs.edit().putBoolean("use_vector_search", it).apply() }
        }

        @OptIn(FlowPreview::class)
        viewModelScope.launch {
            searchQuery
                .debounce(1500)
                .map { it.trim() }
                .filter { it.length >= 2 }
                .distinctUntilChanged()
                .collect { query ->
                    addQueryToHistory(query)
                }
        }

        // Prepare directories, check seeding, list initial files
        viewModelScope.launch {
            repository.checkAndSeedDatabase()
            refreshStoredFiles()
            
            // Re-build Vector Index whenever the database records update dynamically
            launch(Dispatchers.Default) {
                repository.allContent.collect { list ->
                    Log.d("MainViewModel", "Re-indexing Local Vector Database with ${list.size} documents...")
                    com.example.data.vector.LocalVectorDatabase.buildIndex(list)
                    performSearch() // re-evaluate search with the updated Vector DB values
                }
            }
        }
    }

    /**
     * Re-scans physical app storage subdirectories and updates the file tracker
     */
    fun refreshStoredFiles() {
        val filesMap = mutableMapOf<String, List<File>>()
        for (cat in DocumentParser.CATEGORIES) {
            filesMap[cat] = DocumentParser.getFilesByCategory(context, cat)
        }
        _storedFiles.value = filesMap
    }

    /**
     * Perform local normalized NLP search based on current query and category
     */
    fun performSearch() {
        viewModelScope.launch {
            val query = searchQuery.value.trim()
            val cat = selectedCategory.value

            if (query.isEmpty()) {
                // If empty search, fetch everything or category members as fallback
                val targetFlow = if (cat == "all") {
                    repository.allContent
                } else {
                    repository.getContentByCategory(cat)
                }
                val rawList = targetFlow.first()
                _searchResults.value = rawList.map { ScoredResult(it, 1.0) }.take(50)
                return@launch
            }

            val scoredList = if (useVectorSearch.value) {
                withContext(Dispatchers.Default) {
                    com.example.data.vector.LocalVectorDatabase.search(query, cat)
                        .filter { it.score >= relevanceThreshold.value }
                }
            } else {
                // Perform SQLite query, then run refined Arabic NLP matching
                val candidates = if (cat == "all") {
                    repository.searchContent(query)
                } else {
                    repository.searchContentByCategory(cat, query)
                }
                candidates.map { entity ->
                    val score = ArabicNlpHelper.calculateMatchScore(query, entity.title, entity.content)
                    ScoredResult(entity, score)
                }.filter { scored ->
                    scored.score >= relevanceThreshold.value
                }.sortedByDescending { it.score }
            }

            _searchResults.value = scoredList
        }
    }

    /**
     * Smart context-guided query using Hybrid RAG + Customized Gemini Persona parameters.
     * Guarantees highly relevant, context-grounded Islamic responses!
     */
    fun askGemini(question: String) {
        if (question.trim().isEmpty()) return

        viewModelScope.launch {
            isAiLoading.value = true
            aiAnswer.value = "جاري تجميع المصادر الدلالية وتحليل النطاق..."

            // 1. Fetch relevant local passages for the RAG prompt
            val scoredPassages = if (useVectorSearch.value) {
                withContext(Dispatchers.Default) {
                    com.example.data.vector.LocalVectorDatabase.search(question, selectedCategory.value)
                        .filter { it.score >= relevanceThreshold.value }
                }
            } else {
                val queryCandidatesByNlp = if (selectedCategory.value == "all") {
                    repository.searchContent(question)
                } else {
                    repository.searchContentByCategory(selectedCategory.value, question)
                }
                queryCandidatesByNlp.map { entity ->
                    val score = ArabicNlpHelper.calculateMatchScore(question, entity.title, entity.content)
                    ScoredResult(entity, score)
                }.filter { scored ->
                    scored.score >= relevanceThreshold.value
                }.sortedByDescending { it.score }
            }
            
            // Limit to top 5 scored fragments for compact token limits
            val topGroundedContext = scoredPassages.take(5).map { it.entity }
            referencedContexts.value = topGroundedContext

            // 2. Build the detailed context text
            val contextBuilder = StringBuilder()
            if (topGroundedContext.isNotEmpty()) {
                contextBuilder.append("المستندات والمصادر الشرعية الموثوقة المتاحة للرجوع إليها:\n")
                topGroundedContext.forEachIndexed { idx, item ->
                    val catArabic = when (item.category) {
                        "quran" -> "القرآن الكريم"
                        "hadith" -> "الحديث الشريف"
                        "fatawa" -> "الفتاوى والأحكام"
                        else -> "مستند مستخدم"
                    }
                    contextBuilder.append("[مصدر رقم ${idx + 1}] [$catArabic - ${item.title}] (المرجع: ${item.reference}):\n\"${item.content}\"\n\n")
                }
            } else {
                contextBuilder.append("ملاحظة: لا توجد نصوص متطابقة مباشرة في قاعدة البيانات المحلية الموثوقة لهذا السؤال المحدد.\n")
            }

            // 3. Define the Persona system instruction
            val personaName = when (shariahPersona.value) {
                "fiqh" -> "الفقيه أو الباحث الفقهي الدقيق (الالتزام بالأدلة التفصيلية وتوضيح الأحكام والأقوال المعتمدة)"
                "spiritual" -> "واعظ تربوي يعتني بتزكية النفوس ورقائق القلوب والمقاصد الإيمانية وصلاح العمل"
                "tafsir" -> "مفسر قرآني يعتني بالبلاغة القرآنية والبيان واللغة وأسباب النزول ودلالة اللفظ"
                else -> "باحث إسلامي متوازن يستعرض الأدلة بوسطية ويسر مع تجنب الفتاوى الشاذة بالاعتماد على الوحيين"
            }

            val systemInstruction = """
                أنت مساعد إرشادي إسلامي ذكي باسم 'مساعد المنارة للذكاء الإسلامي'.
                مهمتك الإجابة على استفسارات المستخدمين بلغة عربية فصحى راقية، بأسلوب موثوق، علمي، دقيق وخالٍ من الغلو أو التضليل.
                
                التموضع أو الأسلوب الشرعي المختار لك هو: ($personaName).
                
                $contextBuilder
                
                قواعد هامة للإجابة:
                1. التزم بدقة فائقة بمضمون المصادر والمستندات المحلية متى توفرت. 
                2. عند الاقتباس أو الإحالة، اذكر بوضوح اسم السورة ورقم الآية، أو رقم الحديث وكتابه، أو المستند المصدر لتسهيل التوثق من المعرفة.
                3. إذا كان السؤال خارج نطاق المصادر المتاحة، وكنت ستستخدم المعرفة العامة لـ Gemini، نبّه المستخدم بلطف قائلًا: "أجيبك بناءً على المعرفة العامة والقرائن الفقهية المعتمدة..." دون الخروج عن المنهج الوسطي السليم.
                4. إذا تعارض شيء في المعرفة العامة مع الأدلة الصحيحة المذكورة في المصادر المحلية المرفقة، يجب ترجيح النص المذكور في المصادر المحلية.
                5. احرص على تخريج الأحاديث وذكر درجاتها إن أمكن لضمان الموثوقية والدقة.
                
                ${if (customSystemPrompt.value.isNotEmpty()) "تنبيه إضافي من المستخدم مدمج كتعليمات للنظام:\n${customSystemPrompt.value}" else ""}
            """.trimIndent()

            // 4. Construct prompt body
            val finalPromptBuilder = StringBuilder()
            finalPromptBuilder.append("سؤال المستخدم: $question\n\n")
            if (useLocalDataOnly.value) {
                finalPromptBuilder.append("تنبيه: لقد قام المستخدم بتفعيل خيار 'البحث في البيانات المحلية والملفات فقط'. يرجى الحذر الشديد وعدم إدراج أي استنتاجات لا يدعمها سياق المصادر المرفقة المذكور أعلاه. في حال غياب الدليل، قل بوضوح: 'المعلومة غير متوفرة في الملفات المحلية المحفوظة'.")
            } else {
                finalPromptBuilder.append("يرجى الإجابة باستفاضة ووضوح ويسر ومطابقة للسياق.")
            }

            webSources.value = emptyList()
            webQueries.value = emptyList()

            try {
                val shouldWebSearch = enableWebSearch.value && !useLocalDataOnly.value
                val result = GeminiApiClient.generateAnswer(
                    prompt = finalPromptBuilder.toString(),
                    systemInstructionText = systemInstruction,
                    temperature = temperature.value,
                    enableWebSearch = shouldWebSearch,
                    customApiKey = customApiKey.value
                )
                aiAnswer.value = result.answer
                webSources.value = result.webSources
                webQueries.value = result.webQueries
            } catch (e: Exception) {
                aiAnswer.value = "حدث خطأ غير متوقع أثناء معالجة السؤال: ${e.localizedMessage}"
            } finally {
                isAiLoading.value = false
            }
        }
    }

    // --- File Storage Operations ---

    /**
     * Import raw text file directly to a category directory and immediately parse/index it.
     */
    fun importCustomFile(category: String, filename: String, content: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val file = DocumentParser.writeTextFile(context, category, filename, content)
                    DocumentParser.indexFileIntoDb(context, database, file, category)
                } catch (e: Exception) {
                    Log.e("ViewModel", "Failed to import custom document", e)
                }
            }
            refreshStoredFiles()
            performSearch()
        }
    }

    /**
     * Decompress a ZIP file uploaded or passed, unzipping text files, sorting into subdirectories and indexing them all.
     */
    fun importAndUnzipFile(zipFile: File) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val extracted = DocumentParser.unzipAndClassify(context, zipFile)
                    for (file in extracted) {
                        val category = when {
                            file.parent?.endsWith("quran") == true -> "quran"
                            file.parent?.endsWith("hadith") == true -> "hadith"
                            file.parent?.endsWith("fatawa") == true -> "fatawa"
                            else -> "user_docs"
                        }
                        DocumentParser.indexFileIntoDb(context, database, file, category)
                    }
                } catch (e: Exception) {
                    Log.e("ViewModel", "Failed to decompress ZIP package", e)
                }
            }
            refreshStoredFiles()
            performSearch()
        }
    }

    /**
     * Delete a physical file and clear its index entries from the local database
     */
    fun deleteStoredFile(file: File, category: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    if (file.exists()) {
                        file.delete()
                    }
                    repository.deleteBySourceFile(file.name)
                } catch (e: Exception) {
                    Log.e("ViewModel", "Failed to delete file", e)
                }
            }
            refreshStoredFiles()
            performSearch()
        }
    }

    /**
     * Parse/Re-index a stored file in the app directory
     */
    fun indexFile(file: File, category: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                DocumentParser.indexFileIntoDb(context, database, file, category)
            }
            performSearch()
        }
    }

    /**
     * Generate actual example files in standard app data path and index them immediately!
     * This makes testing the 'Android/data/.../files' decompressing, categorization and indexing features effortless!
     */
    fun seedSampleFilesToAndroidData() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    // Quran verse
                    DocumentParser.writeTextFile(
                        context, "quran", "القران_الصبر_اليسر.txt",
                        "مواضع الصبر والتيسير في القرآن الكريم:\n" +
                                "---\n" +
                                "موضع 1: إِنَّ مَعَ الْعُسْرِ يُسْرًا\n" +
                                "فرد رباني يرسخ الأمل بأن الصعاب متبوعة بتفريج إلهي مؤكد ومنة من الله على القلوب المؤمنة.\n" +
                                "المرجع: سورة الشرح الآية 6\n" +
                                "---\n" +
                                "موضع 2: وَبَشِّرِ الصَّابِرِينَ * الَّذِينَ إِذَا أَصَابَتْهُم مُّصِيبَةٌ قَالُوا إِنَّا لِلَّهِ وَإِنَّا إِلَيْهِ رَاجِعُونَ\n" +
                                "جزاء الصبر الجميل والتسليم لرب السموات وبشارة بصلوات من ربهم ورحمة.\n" +
                                "المرجع: سورة البقرة الآية 155 - 156"
                    )

                    // Hadith
                    DocumentParser.writeTextFile(
                        context, "hadith", "رياض_الصالحين_الادب.txt",
                        "أحاديث في حسن الخلق:\n" +
                                "---\n" +
                                "حديث البر والآونة:\n" +
                                "سألت رسول الله صلى الله عليه وسلم عن البر والإثم؟ فقال: البر حسن الخلق، والإثم ما حاك في صدرك وكرهت أن يطلع عليه الناس.\n" +
                                "المرجع: صحيح مسلم رقم 2553\n" +
                                "---\n" +
                                "حديث كمال الإيمان:\n" +
                                "قال رسول الله صلى الله عليه وسلم: أكمل المؤمنين إيماناً أحسنهم خلقاً، وخياركم خياركم لنسائهم خلقاً.\n" +
                                "المرجع: سنن الترمذي رقم 1162"
                    )

                    // Fatawa
                    DocumentParser.writeTextFile(
                        context, "fatawa", "احكام_الزكاة_والصدقة.txt",
                        "نوازل فقهية حول الزكاة:\n" +
                                "---\n" +
                                "فتوى زكاة المال المدخر لبناء المسكن:\n" +
                                "السؤال: هل تجب الزكاة في المال المحفوظ بنية شراء أو بناء منزل للسكن؟\n" +
                                "الجواب: نعم، تجب الزكاة في هذا المال المدخر إذا بلغ النصاب وحال عليه الحول الهجري، لأن النية المستقبلية لبناء مسكن لا تمنع وجوب الزكاة الحالية في النقد المتوفر.\n" +
                                "المرجع: فتاوى ابن باز رحمه الله 12/45\n" +
                                "---\n" +
                                "فتوى صدقة التطوع في مقابل الزكاة المفروضة:\n" +
                                "الجواب: لا تجزئ صدقة التطوع لإبراء ذمة الزكاة الواجبة؛ فالزكاة ركن مالي مستقل له مصارفه الثمانية المحددة في سورة التوبة آية 60، بينما الصدقة باب تطوع مفتوح."
                    )

                    // User documents
                    DocumentParser.writeTextFile(
                        context, "user_docs", "مفكرة_تدبرات_شخصية.txt",
                        "تدبرات يوم الجمعة:\n" +
                                "---\n" +
                                "تأملات في سورة الكهف:\n" +
                                "أهمية الكهف كملجأ للمؤمن، وكيف يحفظ الله دينه ويثبت قلبه في خضم الفتن الصعبة.\n" +
                                "المرجع: تدبر ذاتي عام 2026\n" +
                                "---\n" +
                                "أذكار الصباح والمساء المفضلة:\n" +
                                "قراءة آية الكرسى والمعوذات ثلاث مرات صباحا ومساء، ورضيت بالله ربا وبالإسلام دينا وبمحمد صلى الله عليه وسلم نبيا."
                    )

                    // Additionally, let's trigger a dummy ZIP mock creation to test ZIP unzipping.
                    // We will write a small zip file inside app internal directory
                    val zipFile = File(context.cacheDir, "test_archive.zip")
                    FileOutputStream(zipFile).use { fos ->
                        java.util.zip.ZipOutputStream(fos).use { zos ->
                            // Entry 1- Hadith text inside zip
                            zos.putNextEntry(ZipEntry("bukhari_zip_excerpt_hadith.txt"))
                            val hadithData = "أحاديث من البخاري مجمعة:\n---\n" +
                                    "حديث طلب العلم:\n" +
                                    "قال صلى الله عليه وسلم: إن الله لا يقبض العلم انتزاعا ينتزعه من العباد ولكن يقبض العلم بقبض العلماء.\n" +
                                    "المرجع: البخاري 100"
                            zos.write(hadithData.toByteArray(Charsets.UTF_8))
                            zos.closeEntry()

                            // Entry 2- Quran text inside zip
                            zos.putNextEntry(ZipEntry("quran_zip_excerpt.txt"))
                            val quranData = "آيات قصيرة:\n---\n" +
                                    "سورة الملك موضع 1:\n" +
                                    "الَّذِي خَلَقَ الْمَوْتَ وَالْحَيَاةَ لِيَبْلُوَكُمْ أَيُّكُمْ أَحْسَنُ عَمَلًا وَهُوَ الْعَزِيزُ الْغَفُورُ\n" +
                                    "المرجع: سورة الملك الآية 2"
                            zos.write(quranData.toByteArray(Charsets.UTF_8))
                            zos.closeEntry()
                        }
                    }

                    // Run automatic decompression & indexing on the test ZIP!
                    val extracted = DocumentParser.unzipAndClassify(context, zipFile)
                    for (file in extracted) {
                        val category = when {
                            file.parent?.endsWith("quran") == true || file.name.contains("quran") -> "quran"
                            file.parent?.endsWith("hadith") == true || file.name.contains("hadith") -> "hadith"
                            file.parent?.endsWith("fatawa") == true || file.name.contains("fatwa") -> "fatawa"
                            else -> "user_docs"
                        }
                        DocumentParser.indexFileIntoDb(context, database, file, category)
                    }

                    // Scan the remaining created files
                    for (cat in DocumentParser.CATEGORIES) {
                        val files = DocumentParser.getFilesByCategory(context, cat)
                        for (file in files) {
                            DocumentParser.indexFileIntoDb(context, database, file, cat)
                        }
                    }

                } catch (e: Exception) {
                    Log.e("Seed", "Failed to seed storage directories", e)
                }
            }
            refreshStoredFiles()
            performSearch()
        }
    }

    // --- Search Recitation Media Player Support ---
    val availableReciters = listOf(
        Reciter("alafasy", "مشاري العفاسي", "Mishary Alafasy", "Alafasy_128kbps"),
        Reciter("husary", "محمود خليل الحصري", "Mahmoud Al-Husary", "Husary_128kbps"),
        Reciter("abdulbasit", "عبد الباسط عبد الصمد", "عبد الباسط (مجود)", "Abdul_Basit_Mujawwad_128kbps"),
        Reciter("ghamadi", "سعد الغامدي", "Saad Al-Ghamdi", "Ghamadi_40kbps")
    )

    val selectedReciter = MutableStateFlow(availableReciters[0])
    val currentAudioEntity = MutableStateFlow<IslamicContentEntity?>(null)
    val isAudioPlaying = MutableStateFlow(false)
    val isAudioBuffering = MutableStateFlow(false)
    val audioDuration = MutableStateFlow(0)
    val audioPosition = MutableStateFlow(0)
    val audioProgress = MutableStateFlow(0f)
    val currentVerseIndex = MutableStateFlow(0)
    val totalVersesInEntity = MutableStateFlow(0)

    private var mediaPlayer: MediaPlayer? = null
    private var activeVerseNumbers = emptyList<Int>()
    private var activeSurahNumber = 0
    private var progressJob: kotlinx.coroutines.Job? = null

    val surahMap = mapOf(
        "الفاتحة" to 1, "البقرة" to 2, "آل عمران" to 3, "النساء" to 4, "المائدة" to 5, "الأنعام" to 6, "الأعراف" to 7, "الأنفال" to 8, "التوبة" to 9, "يونس" to 10,
        "هود" to 11, "يوسف" to 12, "الرعد" to 13, "إبراهيم" to 14, "الحجر" to 15, "النحل" to 16, "الإسراء" to 17, "الكهف" to 18, "مريم" to 19, "طه" to 20,
        "الأنبياء" to 21, "الحج" to 22, "المؤمنون" to 23, "النور" to 24, "الفرقان" to 25, "الشعراء" to 26, "النمل" to 27, "القصص" to 28, "العنكبوت" to 29, "الروم" to 30,
        "لقمان" to 31, "السجدة" to 32, "الأحزاب" to 33, "سبأ" to 34, "فاطر" to 35, "يس" to 36, "الصافات" to 37, "ص" to 38, "الزمر" to 39, "غافر" to 40,
        "فصلت" to 41, "الشورى" to 42, "الزخرف" to 43, "الدخان" to 44, "الجاثية" to 45, "الأحقاف" to 46, "محمد" to 47, "الفتح" to 48, "الحجرات" to 49, "ق" to 50,
        "الذاريات" to 51, "الطور" to 52, "النجم" to 53, "القمر" to 54, "الرحمن" to 55, "الواقعة" to 56, "الحديد" to 57, "المجادلة" to 58, "الحشر" to 59, "الممتحنة" to 60,
        "الصف" to 61, "الجمعة" to 62, "المنافقون" to 63, "التغابن" to 64, "الطلاق" to 65, "التحريم" to 66, "الملك" to 67, "القلم" to 68, "الحاقة" to 69, "المعارج" to 70,
        "نوح" to 71, "الجن" to 72, "المزمل" to 73, "المدثر" to 74, "القيامة" to 75, "الانسان" to 76, "المرسلات" to 77, "النبأ" to 78, "النازعات" to 79, "التكوير" to 80,
        "الانفطار" to 81, "المطففين" to 82, "الانشقاق" to 83, "البروج" to 84, "الطارق" to 85, "الأعلى" to 86, "الغاشية" to 87, "الفجر" to 88, "البلد" to 89, "الشمس" to 90,
        "الليل" to 91, "الضحى" to 92, "الشرح" to 93, "التين" to 94, "العلق" to 95, "القدر" to 97, "البيّنة" to 98, "الزلزلة" to 99, "العاديات" to 100,
        "القارعة" to 101, "التكاثر" to 102, "العصر" to 103, "الهمزة" to 104, "الفيل" to 105, "قريش" to 106, "الماعون" to 107, "الكوثر" to 108, "الكافرون" to 109, "النصر" to 110,
        "المسد" to 111, "الإخلاص" to 112, "الفلق" to 113, "الناس" to 114
    )

    fun parseReferenceToAyahs(reference: String): Pair<Int, List<Int>>? {
        try {
            val normalizedRef = reference.trim()
            var surahNum: Int? = null
            for ((name, num) in surahMap) {
                if (normalizedRef.contains(name)) {
                    surahNum = num
                    break
                }
            }
            if (surahNum == null) return null
            val ayahList = mutableListOf<Int>()
            if (normalizedRef.contains("كامل") || normalizedRef.contains("كاملة")) {
                val totalAyahs = when (surahNum) {
                    1 -> 7
                    97 -> 5
                    112 -> 4
                    113 -> 5
                    114 -> 6
                    else -> 10
                }
                for (i in 1..totalAyahs) {
                    ayahList.add(i)
                }
            } else {
                val numbers = Regex("\\d+").findAll(normalizedRef).map { it.value.toInt() }.toList()
                if (numbers.size >= 2) {
                    val start = numbers[0]
                    val end = numbers[1]
                    if (start <= end) {
                        for (i in start..end) {
                            ayahList.add(i)
                        }
                    } else {
                        ayahList.add(start)
                    }
                } else if (numbers.size == 1) {
                    ayahList.add(numbers[0])
                } else {
                    ayahList.add(1)
                }
            }
            return Pair(surahNum, ayahList)
        } catch (e: Exception) {
            return null
        }
    }

    fun playQuranEntity(entity: IslamicContentEntity) {
        if (entity.category != "quran") return
        
        if (currentAudioEntity.value?.id == entity.id) {
            togglePlayPause()
            return
        }
        
        stopAudioInternal()
        currentAudioEntity.value = entity
        val parsed = parseReferenceToAyahs(entity.reference)
        if (parsed == null) {
            activeSurahNumber = 1
            activeVerseNumbers = listOf(1)
        } else {
            activeSurahNumber = parsed.first
            activeVerseNumbers = parsed.second
        }
        totalVersesInEntity.value = activeVerseNumbers.size
        currentVerseIndex.value = 0
        prepareAndPlayActiveVerse()
    }

    private fun prepareAndPlayActiveVerse() {
        val surah = activeSurahNumber
        val idx = currentVerseIndex.value
        if (idx < 0 || idx >= activeVerseNumbers.size) {
            stopAudioInternal()
            return
        }
        val ayah = activeVerseNumbers[idx]
        
        val reciterFolder = selectedReciter.value.folder
        val paddedSurah = String.format("%03d", surah)
        val paddedAyah = String.format("%03d", ayah)
        val url = "https://everyayah.com/data/$reciterFolder/$paddedSurah$paddedAyah.mp3"
        playUrl(url)
    }

    private fun playUrl(url: String) {
        isAudioBuffering.value = true
        isAudioPlaying.value = false
        audioPosition.value = 0
        audioProgress.value = 0f
        audioDuration.value = 1
        
        try {
            if (mediaPlayer == null) {
                mediaPlayer = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .build()
                    )
                }
            } else {
                mediaPlayer?.reset()
            }
            
            mediaPlayer?.apply {
                setDataSource(url)
                setOnPreparedListener { mp ->
                    isAudioBuffering.value = false
                    isAudioPlaying.value = true
                    audioDuration.value = mp.duration
                    mp.start()
                    startProgressTracking()
                }
                setOnCompletionListener {
                    progressJob?.cancel()
                    if (currentVerseIndex.value + 1 < activeVerseNumbers.size) {
                        currentVerseIndex.value += 1
                        prepareAndPlayActiveVerse()
                    } else {
                        stopAudioInternal()
                    }
                }
                setOnErrorListener { _, what, extra ->
                    Log.e("AudioPlayer", "Error playing audio from url: $url, what: $what, extra: $extra")
                    isAudioBuffering.value = false
                    isAudioPlaying.value = false
                    stopAudioInternal()
                    true
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            Log.e("AudioPlayer", "Exception in playUrl: ${e.message}", e)
            isAudioBuffering.value = false
            isAudioPlaying.value = false
            stopAudioInternal()
        }
    }

    private fun startProgressTracking() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch(Dispatchers.Main) {
            while (isAudioPlaying.value && mediaPlayer != null) {
                try {
                    val pos = mediaPlayer?.currentPosition ?: 0
                    val dur = mediaPlayer?.duration ?: 1
                    audioPosition.value = pos
                    if (dur > 0) {
                        audioDuration.value = dur
                        audioProgress.value = pos.toFloat() / dur.toFloat()
                    }
                } catch (e: Exception) {
                    // ignore
                }
                kotlinx.coroutines.delay(250)
            }
        }
    }

    fun togglePlayPause() {
        val player = mediaPlayer ?: return
        if (isAudioPlaying.value) {
            player.pause()
            isAudioPlaying.value = false
            progressJob?.cancel()
        } else {
            if (!isAudioBuffering.value) {
                player.start()
                isAudioPlaying.value = true
                startProgressTracking()
            }
        }
    }

    fun seekAudio(progressPercent: Float) {
        val player = mediaPlayer ?: return
        val dur = audioDuration.value
        if (dur > 0) {
            val targetMs = (progressPercent * dur).toInt()
            try {
                player.seekTo(targetMs)
                audioPosition.value = targetMs
                audioProgress.value = progressPercent
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    fun skipNext() {
        if (currentVerseIndex.value + 1 < activeVerseNumbers.size) {
            currentVerseIndex.value += 1
            prepareAndPlayActiveVerse()
        }
    }

    fun skipPrevious() {
        if (currentVerseIndex.value > 0) {
            currentVerseIndex.value -= 1
            prepareAndPlayActiveVerse()
        }
    }

    fun stopAudio() {
        stopAudioInternal()
        currentAudioEntity.value = null
    }

    private fun stopAudioInternal() {
        progressJob?.cancel()
        isAudioPlaying.value = false
        isAudioBuffering.value = false
        audioPosition.value = 0
        audioProgress.value = 0f
        try {
            mediaPlayer?.stop()
            mediaPlayer?.reset()
        } catch (e: Exception) {
            // Ignore
        }
    }

    fun selectReciter(reciter: Reciter) {
        selectedReciter.value = reciter
        if (currentAudioEntity.value != null && (isAudioPlaying.value || isAudioBuffering.value)) {
            prepareAndPlayActiveVerse()
        }
    }

    fun getActiveVerseRawNumber(): Int? {
        val idx = currentVerseIndex.value
        return if (idx >= 0 && idx < activeVerseNumbers.size) activeVerseNumbers[idx] else null
    }

    override fun onCleared() {
        super.onCleared()
        progressJob?.cancel()
        try {
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (e: Exception) {
            // Ignore
        }
    }
}

data class Reciter(
    val id: String,
    val nameAr: String,
    val nameEn: String,
    val folder: String
)


data class ScoredResult(
    val entity: IslamicContentEntity,
    val score: Double
)
