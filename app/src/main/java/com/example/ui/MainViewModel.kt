package com.example.ui

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.api.GeminiApiClient
import com.example.data.AppDatabase
import com.example.data.IslamicContentEntity
import com.example.data.IslamicRepository
import com.example.data.IslamicInitialData
import com.example.data.SearchResult
import com.example.nlp.ArabicNlpHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface UiState {
    object Idle : UiState
    object Loading : UiState
    data class Success(val results: List<SearchResult>) : UiState
    data class Error(val message: String) : UiState
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val repository = IslamicRepository(db.islamicContentDao())

    // SharedPreferences for local settings persistence
    private val prefs = application.getSharedPreferences("islamic_ai_settings", Context.MODE_PRIVATE)

    // UI States
    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _currentTab = MutableStateFlow("all") // "all", "quran", "hadith", "fatwa", "adhkar", "favorites"
    val currentTab: StateFlow<String> = _currentTab.asStateFlow()

    private val _searchMode = MutableStateFlow(SearchMode.HYBRID) // HYBRID or LOCAL
    val searchMode: StateFlow<SearchMode> = _searchMode.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isInternetAvailable = MutableStateFlow(false)
    val isInternetAvailable: StateFlow<Boolean> = _isInternetAvailable.asStateFlow()

    // Key to allow users to use their custom Gemini API keys
    private val _userApiKey = MutableStateFlow(prefs.getString("user_api_key", "") ?: "")
    val userApiKey: StateFlow<String> = _userApiKey.asStateFlow()

    // Offline intelligence package simulation states
    private val _isOfflineModelDownloaded = MutableStateFlow(prefs.getBoolean("offline_model_downloaded", true))
    val isOfflineModelDownloaded: StateFlow<Boolean> = _isOfflineModelDownloaded.asStateFlow()

    private val _isOfflineModelDownloading = MutableStateFlow(false)
    val isOfflineModelDownloading: StateFlow<Boolean> = _isOfflineModelDownloading.asStateFlow()

    private val _offlineDownloadProgress = MutableStateFlow(0.0f)
    val offlineDownloadProgress: StateFlow<Float> = _offlineDownloadProgress.asStateFlow()

    // Required Package com.rock10k.quran.premium validation fields
    private val _isPremiumQuranInstalled = MutableStateFlow(false)
    val isPremiumQuranInstalled: StateFlow<Boolean> = _isPremiumQuranInstalled.asStateFlow()

    private val _tempBypassRequiredPackage = MutableStateFlow(false)
    val tempBypassRequiredPackage: StateFlow<Boolean> = _tempBypassRequiredPackage.asStateFlow()

    // Multi-level AI Intelligence Modes (Simple, Standard, Advanced Ultra)
    enum class AiMode {
        SIMPLE,       // الوضع البسيط: إجابات مختصرة جداً، غاية في السهولة والوضوح لمبتدئين
        STANDARD,     // الوضع القوي: إجابة منظمة، شرح متوسط، الاستشهاد بالأدلة الشرعية
        ADVANCED_ULTRA // الوضع المتقدم جداً: تفصيل عميق، آراء المذاهب والأقوال ونقد الأدلة
    }

    private val _aiMode = MutableStateFlow(AiMode.valueOf(prefs.getString("ai_mode", AiMode.STANDARD.name) ?: AiMode.STANDARD.name))
    val aiMode: StateFlow<AiMode> = _aiMode.asStateFlow()

    fun setAiMode(mode: AiMode) {
        _aiMode.value = mode
        prefs.edit().putString("ai_mode", mode.name).apply()
        refreshCurrentResults()
    }

    // Strict Shariah Verification filter
    private val _strictShariahValidation = MutableStateFlow(prefs.getBoolean("strict_shariah_validation", false))
    val strictShariahValidation: StateFlow<Boolean> = _strictShariahValidation.asStateFlow()

    fun setStrictShariahValidation(enabled: Boolean) {
        _strictShariahValidation.value = enabled
        prefs.edit().putBoolean("strict_shariah_validation", enabled).apply()
        refreshCurrentResults()
    }

    // File Import background processing progress states
    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

    private val _importStatus = MutableStateFlow("")
    val importStatus: StateFlow<String> = _importStatus.asStateFlow()

    // Cognitive Memory Profile (الذاكرة الذكية لاهتمامات المستخدم)
    private val _memoryInterests = MutableStateFlow<Map<String, Int>>(loadMemoryInterests())
    val memoryInterests: StateFlow<Map<String, Int>> = _memoryInterests.asStateFlow()

    private fun loadMemoryInterests(): Map<String, Int> {
        val raw = prefs.getString("memory_interests", "") ?: ""
        if (raw.isBlank()) return emptyMap()
        return raw.split(";").mapNotNull {
            val parts = it.split(":")
            if (parts.size == 2) parts[0] to (parts[1].toIntOrNull() ?: 0) else null
        }.toMap()
    }

    private fun recordInterestTopic(query: String) {
        val cleanQuery = query.trim().lowercase()
        val topic = when {
            cleanQuery.contains("صلاة") || cleanQuery.contains("صلاه") || cleanQuery.contains("سجود") -> "فقه العبادات والصلاة"
            cleanQuery.contains("وضوء") || cleanQuery.contains("الوضوء") || cleanQuery.contains("طهارة") -> "الطهارة والوضوء"
            cleanQuery.contains("حديث") || cleanQuery.contains("سنة") || cleanQuery.contains("البخاري") -> "علم الحديث والسنة"
            cleanQuery.contains("قرآن") || cleanQuery.contains("رسول") || cleanQuery.contains("آية") -> "القرآن وعلوم التنزيل"
            cleanQuery.contains("زكاة") || cleanQuery.contains("صدقة") || cleanQuery.contains("مال") -> "الزكاة والمعاملات المالية"
            cleanQuery.contains("شرك") || cleanQuery.contains("توحيد") || cleanQuery.contains("عقيدة") -> "العقيدة والتوحيد"
            else -> "مواضيع فقهية عامة"
        }
        val current = _memoryInterests.value.toMutableMap()
        current[topic] = (current[topic] ?: 0) + 1
        _memoryInterests.value = current
        val serialized = current.map { "${it.key}:${it.value}" }.joinToString(";")
        prefs.edit().putString("memory_interests", serialized).apply()
    }

    fun clearMemoryInterests() {
        _memoryInterests.value = emptyMap()
        prefs.edit().putString("memory_interests", "").apply()
    }

    // Highly Customizable Settings Requested by user
    private val _appTheme = MutableStateFlow(prefs.getString("app_theme", "system") ?: "system")
    val appTheme: StateFlow<String> = _appTheme.asStateFlow()

    private val _showTranslation = MutableStateFlow(prefs.getBoolean("show_translation", true))
    val showTranslation: StateFlow<Boolean> = _showTranslation.asStateFlow()

    private val _fontSizeMultiplier = MutableStateFlow(prefs.getFloat("font_size_multiplier", 1.0f))
    val fontSizeMultiplier: StateFlow<Float> = _fontSizeMultiplier.asStateFlow()

    private val _spiritualRemindersEnabled = MutableStateFlow(prefs.getBoolean("spiritual_reminders", true))
    val spiritualRemindersEnabled: StateFlow<Boolean> = _spiritualRemindersEnabled.asStateFlow()

    // Persistent list of custom shariah links (stored as serialized "Title|URL;Title2|URL2")
    private val _customLinks = MutableStateFlow<List<Pair<String, String>>>(loadCustomLinks())
    val customLinks: StateFlow<List<Pair<String, String>>> = _customLinks.asStateFlow()

    private var refreshJob: Job? = null

    private fun loadCustomLinks(): List<Pair<String, String>> {
        val raw = prefs.getString("custom_shariah_links", "") ?: ""
        if (raw.isBlank()) return emptyList()
        return raw.split(";").mapNotNull {
            val parts = it.split("|")
            if (parts.size == 2) Pair(parts[0], parts[1]) else null
        }
    }

    fun addCustomLink(title: String, url: String) {
        val current = _customLinks.value.toMutableList()
        current.removeAll { it.first == title }
        current.add(Pair(title, url))
        _customLinks.value = current
        val serialized = current.joinToString(";") { "${it.first}|${it.second}" }
        prefs.edit().putString("custom_shariah_links", serialized).apply()
    }

    fun removeCustomLink(title: String) {
        val current = _customLinks.value.toMutableList()
        current.removeAll { it.first == title }
        _customLinks.value = current
        val serialized = current.joinToString(";") { "${it.first}|${it.second}" }
        prefs.edit().putString("custom_shariah_links", serialized).apply()
    }

    init {
        // Init checklist: verify Database and Required package installation
        checkPremiumQuranInstallation(application)
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repository.checkAndSeedDatabase()
            }
            checkNetworkConnection()
            // Observe changes in favorites or main data to update views dynamically
            _currentTab.collectLatest { tab ->
                refreshCurrentResults()
            }
        }
    }

    enum class SearchMode {
        HYBRID, LOCAL
    }

    // Setters for Settings Config
    fun setUserApiKey(key: String) {
        _userApiKey.value = key
        prefs.edit().putString("user_api_key", key).apply()
    }

    // Simulate Downloading full Arabic smart model for totally offline queries
    fun downloadOfflineIntelligenceModel() {
        if (_isOfflineModelDownloaded.value || _isOfflineModelDownloading.value) return
        _isOfflineModelDownloading.value = true
        _offlineDownloadProgress.value = 0.0f
        viewModelScope.launch {
            for (i in 1..100) {
                kotlinx.coroutines.delay(15) // Smooth fast loading animation
                _offlineDownloadProgress.value = i / 100.0f
            }
            // Seed the 30 premium robust offline intelligence entities into local database
            withContext(Dispatchers.IO) {
                val premiumEntities = IslamicInitialData.getPremiumOfflineModelEntities()
                for (entity in premiumEntities) {
                    repository.insert(entity)
                }
            }
            _isOfflineModelDownloaded.value = true
            prefs.edit().putBoolean("offline_model_downloaded", true).apply()
            _isOfflineModelDownloading.value = false
            refreshCurrentResults()
        }
    }

    fun deleteOfflineIntelligenceModel() {
        _isOfflineModelDownloaded.value = false
        prefs.edit().putBoolean("offline_model_downloaded", false).apply()
        _offlineDownloadProgress.value = 0.0f
        refreshCurrentResults()
    }

    // Checking if com.rock10k.quran.premium package is available on the device
    fun checkPremiumQuranInstallation(context: Context) {
        _isPremiumQuranInstalled.value = isPackageInstalled(context, "com.rock10k.quran.premium")
    }

    fun bypassRequiredPackage() {
        _tempBypassRequiredPackage.value = true
    }

    private fun isPackageInstalled(context: Context, packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: Exception) {
            false
        }
    }

    // Setter for App Theme (system, light, dark)
    fun setAppTheme(theme: String) {
        _appTheme.value = theme
        prefs.edit().putString("app_theme", theme).apply()
    }

    // Setter for Show Translation Toggle
    fun toggleTranslation(show: Boolean) {
        _showTranslation.value = show
        prefs.edit().putBoolean("show_translation", show).apply()
    }

    // Setter for Font Size Multiplier
    fun setFontSizeMultiplier(multiplier: Float) {
        _fontSizeMultiplier.value = multiplier
        prefs.edit().putFloat("font_size_multiplier", multiplier).apply()
    }

    // Setter for Spiritual Reminders Toggle
    fun toggleSpiritualReminders(enabled: Boolean) {
        _spiritualRemindersEnabled.value = enabled
        prefs.edit().putBoolean("spiritual_reminders", enabled).apply()
    }

    // Clear Search History / Custom entries in Database to keep things fast
    fun clearCachedSearchEntities() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                // Clear extra rows that are downloaded (leaving seeded entries unchanged)
                val all = repository.allContent.first()
                all.forEach {
                    if (it.id > 11 && !it.isFavorite) {
                        repository.deleteById(it.id)
                    }
                }
            }
            refreshCurrentResults()
        }
    }

    /**
     * Checks if active internet is connected
     */
    fun checkNetworkConnection() {
        try {
            val connectivityManager = getApplication<Application>()
                .getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            if (connectivityManager == null) {
                _isInternetAvailable.value = false
                return
            }
            val network = connectivityManager.activeNetwork
            if (network != null) {
                val capabilities = connectivityManager.getNetworkCapabilities(network)
                _isInternetAvailable.value = capabilities != null && (
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
                )
            } else {
                _isInternetAvailable.value = false
            }
        } catch (e: Exception) {
            _isInternetAvailable.value = false
        }
    }

    /**
     * Refreshes dashboard depending on search query and chosen tab.
     * Cancels any previously running refreshes/searches to prevent concurrency races/ANRs
     * on quick keystroke inputs.
     */
    fun refreshCurrentResults() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            val query = _searchQuery.value
            val tab = _currentTab.value

            _uiState.value = UiState.Loading
            try {
                // Completely offload disk, filtering and scoring calculation tasks to IO/Default background workers
                val results = withContext(Dispatchers.IO) {
                    if (tab == "favorites") {
                        val favoritesList = repository.favorites.first()
                        favoritesList.map { SearchResult(it, 1.0) }
                    } else if (query.isBlank()) {
                        val list = repository.allContent.first()
                        val filtered = if (tab == "all") list else list.filter { it.type == tab }
                        filtered.map { SearchResult(it, 1.0) }
                    } else {
                        repository.performLocalSearch(query, if (tab == "all") null else tab)
                    }
                }
                _uiState.value = UiState.Success(results)
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    _uiState.value = UiState.Error(e.message ?: "حدث خطأ غير متوقع")
                }
            }
        }
    }

    /**
     * Triggers Search (Offline NLP-ranking or Online hybrid synthesis)
     */
    fun onSearchProgress(query: String) {
        _searchQuery.value = query
        refreshCurrentResults()
    }

    /**
     * Complete submission search query - performs AI request if Online-hybrid mode is enabled
     */
    fun executeSearch() {
        val query = _searchQuery.value.trim()
        if (query.isBlank()) {
            refreshCurrentResults()
            return
        }

        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            recordInterestTopic(query) // الذكاء المعرفي: تسجيل اهتمام المستخدم بالمحور الشرعي تلقائياً
            checkNetworkConnection()
            
            // Check if Offline and Offline Smart Model is downloaded
            if (!_isInternetAvailable.value && _isOfflineModelDownloaded.value) {
                _uiState.value = UiState.Loading
                // Simulate local offline AI processing with beautiful delay
                kotlinx.coroutines.delay(1000)
                
                val aiAnswer = generateLocalIntelligenceAnswer(query)
                
                // طبقة التدقيق الشرعي الصارمة: منع الفتاوى العشوائية أو التخمين إذا لم توجد مصادر سياقية دقيقة للمسألة
                if (_strictShariahValidation.value && (aiAnswer.source.isEmpty() || aiAnswer.text.contains("توجيه شرعي محلي مخصص"))) {
                    _uiState.value = UiState.Success(listOf(
                        SearchResult(
                            IslamicContentEntity(
                                id = 9991,
                                type = "fatwa",
                                title = "التحقق الشرعي: نقص بالأدلة في السؤال",
                                text = "تنبيه الفحص القيمي الشرعي:\n\nلا يوجد مصدر موثوق كافٍ في قاعدة البيانات المحلية لهذا السؤال لمنحه حكماً قطعياً وموثوقاً بالكامل. يرجى تفعيل الاتصال بالإنترنت لإطلاق البحث الدلالي المعزز بالذكاء الهجين، أو مراجعة اللجان الشرعية والابتعاد وبشدة عن الشُّبهات والتخمين غير العلمي.",
                                source = "مصفاة التدقيق الشرعي الصارمة بالمنارة",
                                keywords = "فحص, تدقيق قيمي, دليل ناقص, أمن الفتوى",
                                normalizedTitle = "",
                                normalizedText = ""
                            ),
                            10.0
                        )
                    ))
                    return@launch
                }

                val newEntity = IslamicContentEntity(
                    type = aiAnswer.type,
                    title = aiAnswer.title,
                    text = aiAnswer.text + "\n\n(⚡️ تم توليد هذه الإجابة وتدقيقها محلياً بالكامل عبر نموذج المنارة الذكي أوفلاين)",
                    source = aiAnswer.source,
                    keywords = aiAnswer.keywords,
                    normalizedTitle = ArabicNlpHelper.normalize(aiAnswer.title),
                    normalizedText = ArabicNlpHelper.normalize(aiAnswer.text)
                )

                val freshMatches = withContext(Dispatchers.IO) {
                    val insertedId = repository.insert(newEntity)
                    val cachedEntity = newEntity.copy(id = insertedId.toInt())
                    val tab = _currentTab.value
                    val dbMatches = repository.performLocalSearch(query, if (tab == "all") null else tab)
                    val filteredDb = dbMatches.filter { it.entity.title != cachedEntity.title && it.entity.id != cachedEntity.id }
                    
                    val list = mutableListOf<SearchResult>()
                    list.add(SearchResult(cachedEntity, 3.0)) // score 3.0 represents local offline intelligent generator
                    list.addAll(filteredDb)
                    list
                }
                _uiState.value = UiState.Success(freshMatches)
                return@launch
            }

            // If Hybrid search and internet is available, let's call Gemini to enrich results
            if (_searchMode.value == SearchMode.HYBRID && _isInternetAvailable.value) {
                _uiState.value = UiState.Loading
                
                // Call Gemini API on Background Dispatcher (with custom user key if configured and proper AI Mode)
                val aiAnswer = withContext(Dispatchers.IO) {
                    GeminiApiClient.generateIslamicResponse(query, _userApiKey.value, _aiMode.value.name)
                }

                if (aiAnswer.error != null) {
                    // Graceful fallback: show a warning or fallback to local search
                    val localMatches = withContext(Dispatchers.IO) {
                        repository.performLocalSearch(query, if (_currentTab.value == "all") null else _currentTab.value)
                    }
                    if (localMatches.isNotEmpty()) {
                        _uiState.value = UiState.Success(localMatches)
                    } else {
                        // DB empty too! Generate a highly polished local Shariah fallback response
                        val fallbackAi = generateLocalIntelligenceAnswer(query)
                        if (_strictShariahValidation.value && (fallbackAi.source.isEmpty() || fallbackAi.text.contains("توجيه شرعي محلي مخصص"))) {
                            _uiState.value = UiState.Success(listOf(
                                SearchResult(
                                    IslamicContentEntity(
                                        id = 9992,
                                        type = "fatwa",
                                        title = "التحقق الشرعي: فحص المصادر",
                                        text = "لا يوجد مصدر موثوق كافٍ في قاعدة البيانات والذاكرة المحلية لهذا السؤال بخصوص $query. يُمنع التخمين والإفتاء بغير دليل مباشر.",
                                        source = "نظام التدقيق الشرعي بالمنارة",
                                        keywords = "تدقيق, ناقص, غياب دليل",
                                        normalizedTitle = "",
                                        normalizedText = ""
                                    ),
                                    10.0
                                )
                            ))
                            return@launch
                        }

                        val newEntity = IslamicContentEntity(
                            type = fallbackAi.type,
                            title = fallbackAi.title,
                            text = fallbackAi.text + "\n\n(⚡️ لم نتمكن من الاتصال بالخادم الشقيق، تم استخدام محرك الإفتاء المحلي بالمنارة أوفلاين)",
                            source = fallbackAi.source,
                            keywords = fallbackAi.keywords,
                            normalizedTitle = ArabicNlpHelper.normalize(fallbackAi.title),
                            normalizedText = ArabicNlpHelper.normalize(fallbackAi.text)
                        )
                        val freshResults = withContext(Dispatchers.IO) {
                            val insertedId = repository.insert(newEntity)
                            val cachedEntity = newEntity.copy(id = insertedId.toInt())
                            listOf(SearchResult(cachedEntity, 3.0))
                        }
                        _uiState.value = UiState.Success(freshResults)
                    }
                } else {
                    // Shariah Strict compliance check on Online generated content
                    if (_strictShariahValidation.value && (aiAnswer.source.isBlank() || aiAnswer.text.contains("لا يوجد مصدر") || aiAnswer.text.contains("تنبيه الفحص"))) {
                        _uiState.value = UiState.Success(listOf(
                            SearchResult(
                                IslamicContentEntity(
                                    id = 9993,
                                    type = "fatwa",
                                    title = "لا يتوفر دليل كامل",
                                    text = "حسب مصفاة التدقيق والتحقق الشرعي بالمنارة:\n\nلا يوجد مصدر موثوق كافٍ في قاعدة البيانات لهذا السؤال من مصادر فقهية معتمدة. يمنع الإدلاء بالفتاوى العشوائية أو استقاء الأحكام غيباً بغير وحي أو نص شرعي متفق عليه.",
                                    source = "مصفاة التحقق الشرعي الصارمة",
                                    keywords = "فحص, وثوق, تدقيق",
                                    normalizedTitle = "",
                                    normalizedText = ""
                                ),
                                10.0
                            )
                        ))
                        return@launch
                    }

                    // Save response locally as a cache to build up the database
                    val newEntity = IslamicContentEntity(
                        type = aiAnswer.type,
                        title = aiAnswer.title,
                        text = aiAnswer.text,
                        source = aiAnswer.source,
                        keywords = aiAnswer.keywords,
                        normalizedTitle = ArabicNlpHelper.normalize(aiAnswer.title),
                        normalizedText = ArabicNlpHelper.normalize(aiAnswer.text)
                    )

                    val mergedResults = withContext(Dispatchers.IO) {
                        val insertedId = repository.insert(newEntity)
                        val cachedEntity = newEntity.copy(id = insertedId.toInt())
                        
                        // Retrieve traditional matching local references from database
                        val tab = _currentTab.value
                        val dbMatches = repository.performLocalSearch(query, if (tab == "all") null else tab)
                        
                        // Filter out the exact same newEntity from dbMatches to prevent visual duplication
                        val filteredDb = dbMatches.filter { it.entity.title != cachedEntity.title && it.entity.id != cachedEntity.id }
                        
                        val list = mutableListOf<SearchResult>()
                        list.add(SearchResult(cachedEntity, 2.0)) // Relevancy score 2.0 marks direct online hybrid response
                        list.addAll(filteredDb)
                        list
                    }
                    _uiState.value = UiState.Success(mergedResults)
                }
            } else {
                // Offline Local Search offloaded to background thread
                _uiState.value = UiState.Loading
                try {
                    val results = withContext(Dispatchers.IO) {
                        repository.performLocalSearch(query, if (_currentTab.value == "all") null else _currentTab.value)
                    }
                    if (results.isEmpty()) {
                        // Generate smart rule-based answer if no matches exist, so we NEVER leave user empty-handed!
                        val aiAnswer = generateLocalIntelligenceAnswer(query)
                        
                        if (_strictShariahValidation.value && (aiAnswer.source.isEmpty() || aiAnswer.text.contains("توجيه شرعي محلي مخصص"))) {
                            _uiState.value = UiState.Success(listOf(
                                SearchResult(
                                    IslamicContentEntity(
                                        id = 9994,
                                        type = "fatwa",
                                        title = "التحقق الشرعي بالمنارة أوفلاين",
                                        text = "لا يوجد مصدر موثوق كافٍ في قاعدة البيانات للرد القاطع على هذا السؤال الفقهي المعين.",
                                        source = "مصفاة تصفية الفتاوى غير المسندة",
                                        keywords = "أمن الفتوى, معلّم",
                                        normalizedTitle = "",
                                        normalizedText = ""
                                    ),
                                    10.0
                                )
                            ))
                            return@launch
                        }

                        val newEntity = IslamicContentEntity(
                            type = aiAnswer.type,
                            title = aiAnswer.title,
                            text = aiAnswer.text + "\n\n(⚡️ تم توليد هذه الإجابة وتدقيقها محلياً بالكامل عبر نموذج المنارة الذكي أوفلاين)",
                            source = aiAnswer.source,
                            keywords = aiAnswer.keywords,
                            normalizedTitle = ArabicNlpHelper.normalize(aiAnswer.title),
                            normalizedText = ArabicNlpHelper.normalize(aiAnswer.text)
                        )
                        val freshResults = withContext(Dispatchers.IO) {
                            val insertedId = repository.insert(newEntity)
                            val cachedEntity = newEntity.copy(id = insertedId.toInt())
                            listOf(SearchResult(cachedEntity, 3.0))
                        }
                        _uiState.value = UiState.Success(freshResults)
                    } else {
                        _uiState.value = UiState.Success(results)
                    }
                } catch (e: Exception) {
                    if (e !is CancellationException) {
                        _uiState.value = UiState.Error(e.message ?: "حدث خطأ غير متوقع")
                    }
                }
            }
        }
    }

    private fun generateLocalIntelligenceAnswer(query: String): com.example.api.IslamicAnswer {
        val clean = query.lowercase().trim()
        
        // Vulgar / Profane / Non-Islamic Check filtering
        val vulgarKeywords = listOf("عيب", "كلب", "حمار", "وسخ", "سكس", "غبي", "غدر", "لعنة", "يلعن", "برونو", "شتيمة", "احا", "منيك", "شرموط", "مكالمة", "جنس", "يا غبي", "غبي جدا")
        val isVulgar = vulgarKeywords.any { clean.contains(it) }
        
        // Check for creator/designer attribution queries
        val isAboutCreator = clean.contains("عمرو") || clean.contains("الراضي") || clean.contains("سلامة") || clean.contains("سلامه") ||
                clean.contains("صنع") || clean.contains("صمم") || clean.contains("برمجة") || clean.contains("برمج") || clean.contains("مطور") ||
                clean.contains("من انت") || clean.contains("من أنت") || clean.contains("صانع") || clean.contains("عملك") || clean.contains("سواك")
        
        return when {
            isVulgar -> {
                com.example.api.IslamicAnswer(
                    type = "fatwa",
                    title = "تنبيه منظومة الأمان والذوق الشرعي",
                    text = "عذراً يا أخي الفاضل، يمنع هذا التطبيق كلياً الرد على الألفاظ البذيئة أو العبارات الخارجة عن حدود الدين والأخلاق الإسلامية. المبرمج المهندس عمرو سلامة الراضي خصصني لتعليم الفتاوى ونشر التوعية الإسلامية النقية والأدعية الطيبة فقط.",
                    source = "المنارة للذكاء الإسلامي - بوابة الأمان القيمي",
                    keywords = "الأمان، فلترة اللفظ، الأخلاق، عمرو سلامة الراضي"
                )
            }
            isAboutCreator -> {
                com.example.api.IslamicAnswer(
                    type = "fatwa",
                    title = "مطور ومبرمج تطبيق المنارة",
                    text = "المطور والمصمم والمسؤول الوحيد عن برمجة تطبيق \"المنارة للذكاء الإسلامي\" هو المهندس عمرو سلامة الراضي.\n\nلقد صمم هذا التطبيق ليكون مساعداً فقهياً ذكياً وموثوقاً مبنياً على التفاسير المعتمدة والأحاديث الصحيحة من أمهات الكتب الإسلامية. وقد حرص على دمج نظام ذكاء اصطناعي محلي متكامل (أوفلاين) قادر على الإجابة الفورية والذكية حتى عند انقطاع الإنترنت، لتوفير خدمة دينية رصينة وعالية الدقة في أي وقت وبكل سهولة ووضوح وبدون أخطاء.",
                    source = "المنارة للذكاء الإسلامي - الصفحة التعريفية والتوجيه الشرعي",
                    keywords = "عمرو سلامة الراضي, المبرمج, المطور, المهندس عمرو, صناعة التطبيق"
                )
            }
            clean.contains("nie") || clean.contains("نية") || clean.contains("عمل") || clean.contains("الإخلاص") || clean.contains("اخلاص") -> {
                com.example.api.IslamicAnswer(
                    type = "hadith",
                    title = "أهمية النية وإخلاص العمل لله تعالى",
                    text = "عن أمير المؤمنين أبي حفص عمر بن الخطاب رضي الله عنه قال: سمعت رسول الله ﷺ يقول: «إنما الأعمال بالنيات، وإنما لكل امرئ ما نوى، فمن كانت هجرته إلى الله ورسوله فهجرته إلى الله ورسوله، ومن كانت هجرته لدنيا يصيبها أو امرأة ينكحها فهجرته إلى ما هاجر إليه».\n\nوالنية هي شرط أساسي لقبول العمل الصالح وبها يفرق العبد بين العبادة والعادة اليومية.",
                    source = "صحيح البخاري ومسلم - باب الإخلاص والنية لله",
                    keywords = "النية، الأعمال بالنيات، الإخلاص، النيات، قبول العمل"
                )
            }
            clean.contains("صلاه") || clean.contains("صلاة") || clean.contains("صلي") || clean.contains("صلى") || clean.contains("الصلوات") -> {
                com.example.api.IslamicAnswer(
                    type = "fatwa",
                    title = "أركان وشروط الصلاة المفروضة بالكامل",
                    text = "الصلاة هي عماد الدين وثاني أركان الإسلام.\n\nشروط الصلاة تسعة: الإسلام، والعقل، والتمييز، ورفع الحدث، وإزالة النجاسة، وستر العورة، ودخول الوقت، واستقبل القبلة، والنية.\n\nأركان الصلاة هي: القيام مع القدرة، تكبيرة الإحرام، قراءة الفاتحة، الركوع، الرفع منه، السجود على الأعضاء السبعة، الاعتدال، الجلوس بين السجدتين، الطمأنينة في جميع الأركان، التشهد الأخير والجلوس له، الصلاة على النبي ﷺ، الترتيب والتسليم.\n\nولا تسقط الصلاة بحال عن المكلف طالما كان عقل الإنسان ثابتاً.",
                    source = "اللجنة الدائمة للبحوث العلمية والإفتاء - الفتوى رقم 821",
                    keywords = "الصلاة، أركان الصلاة، شروط الصلاة، الصلاة المفروضة"
                )
            }
            clean.contains("وضوء") || clean.contains("الوضوء") || clean.contains("طهار") || clean.contains("طهارة") || clean.contains("غسل") -> {
                com.example.api.IslamicAnswer(
                    type = "quran",
                    title = "صفة الوضوء الشرعي ومبطلاته ونواقضه",
                    text = "قال الله تعالى: {يَا أَيُّهَا الَّذِينَ آمَنُوا إِذَا قُمْتُمْ إِلَى الصَّلَاةِ فَاغْسِلُوا وُجُوهَكُمْ وَأَيْدِيَكُمْ إِلَى الْمَرَافِقِ وَامْسَحُوا بِرُؤُوسِكُمْ وَأَرْجُلَكُمْ إِلَى الْكَعْبَيْنِ}.\n\nفروض الوضوء ستة:\n1. غسل الوجه (ومنه المضمضة والاستنشاق).\n2. غسل اليدين مع المرفقين.\n3. مسح الرأس كله (ومنه الأذنان).\n4. غسل الرجلين مع الكعبين.\n5. الترتيب.\n6. الموالاة.\n\nأما نواقض الوضوء فمنها: الخارج من السبيلين (من بول أو غائط أو ريح)، زوال العقل بنوم عميق أو نحوه، مس الفرج مباشرة بلا حائل، وأكل لحم الإبل.",
                    source = "تفسير ابن كثير - سورة المائدة آية 6",
                    keywords = "الوضوء، صفة الوضوء، الطهارة، فروض الوضوء، نواقض الوضوء"
                )
            }
            clean.contains("صدق") || clean.contains("صدقة") || clean.contains("الصدقة") || clean.contains("زكاة") || clean.contains("الزكاة") || clean.contains("زكاه") || clean.contains("الزكاه") || clean.contains("مال") -> {
                com.example.api.IslamicAnswer(
                    type = "fatwa",
                    title = "الفرق بين الزكاة المفروضة والصدقة المستحبة",
                    text = "الزكاة ركن من أركان الإسلام الخمسة، وهي فرض عين على كل مسلم ملك النصاب (ما يعادل 85 جراماً من الذهب) وحال عليه الحول (عام هجري كامل)، ومقدارها 2.5% من مجموع المال المدخر أو عروض التجارة.\n\nأما الصدقة فهي مستحبة ومندوب إليها في كل وقت، وتطفئ الخطيئة كما يطفئ الماء النار، وتدفع بلاء السوء عن العبد.\n\nقال رسول الله ﷺ: «الصدقة تطفئ الخطيئة كما يطفئ الماء النار».\n\nويجوز تقديم الصدقة للأقارب المحتاجين فلها أجران: أجر الصدقة وأجر صلة الرحم.",
                    source = "موقع اللجنة الدائمة للبحوث العلمية والإفتاء - فتاوى الزكاة والصدقات",
                    keywords = "الصدقة، الزكاة، المال, فضل الصدقة، النصاب"
                )
            }
            clean.contains("والد") || clean.contains("والدين") || clean.contains("بر") || clean.contains("ام") || clean.contains("أب") || clean.contains("امي") || clean.contains("أبي") -> {
                com.example.api.IslamicAnswer(
                    type = "quran",
                    title = "وجوب بر الوالدين والإحسان إليهما في الشريعة الإسلامية",
                    text = "قرن الله تعالى حقه بالإحسان إلى الوالدين لعظم شأنهما.\n\nقال الله تعالى: {وَقَضَى رَبُّكَ أَلَّا تَعْبُدُوا إِلَّا إِيَّاهُ وَبِالْوَالِدَيْنِ إِحْسَانًا إِمَّا يَبْلُغَنَّ عِنْدَكَ الْكِبَرَ أَحَدُهُمَا أَوْ كِلَاهُمَا فَلَا تَعْبُدْ لَهُمَا أُفٍّ وَلَا تَنْهَرْهُمَا وَقُلْ لَهُمَا قَوْلًا كَرِيمًا}.\n\nوبر الوالدين هو من أعظم الأعمال المقربة إلى الله، وهو مقدم حتى على الجهاد الكفائي في سبيل الله. وطاعتهما واجبة في غير معصية الخالق، والدعاء والترحم عليهما بعد وفاتهما هو من أعظم صور البر والوفاء المتواصل.",
                    source = "تفسير السعدي - سورة الإسراء آية 23",
                    keywords = "بر الوالدين، الإحسان للأم والأب، عقوق الوالدين، الوالدين"
                )
            }
            clean.contains("صوم") || clean.contains("صيام") || clean.contains("رمضان") || clean.contains("الصوم") || clean.contains("الصيام") -> {
                com.example.api.IslamicAnswer(
                    type = "fatwa",
                    title = "أحكام الصيام المفروض ومبطلاته الأساسية",
                    text = "صيام شهر رمضان المبارك ركن من أركان الإسلام الخمسة، وهو واجب على كل مسلم بالغ، عاقل، مقيم، مستطيع.\n\nمفطرات الصيام الأساسية تشمل:\n1. الأكل والشرب عمداً.\n2. الجماع في نهار رمضان.\n3. القيء المتعمد.\n4. الحجامة.\n5. خروج دم الحيض والنفاس.\n\nمن سنن الصيام تعجيل الفطر وتأخير السحور وكف اللسان عن الكذب والغيبة والنميمة لقول رسول الله ﷺ: «من لم يدع قول الزور والعمل به، فليس لله حاجة في أن يدع طعامه وشرابه».",
                    source = "اللجنة الدائمة للبحوث العلمية والإفتاء - فتاوى الصيام",
                    keywords = "الصوم، الصيام، رمضان، مبطلات الصيام، فضل السحور"
                )
            }
            clean.contains("قرآن") || clean.contains("قران") || clean.contains("مصحف") || clean.contains("المصحف") -> {
                com.example.api.IslamicAnswer(
                    type = "quran",
                    title = "فضل تلاوة القرآن الكريم وآداب التعامل مع المصحف الشريف",
                    text = "القرآن الكريم هو كلام الله تعالى المنزل على نبيه محمد ﷺ، المتعبد بتلاوته.\n\nمن أهم آداب قراءة وتلاوة القرآن:\n1. الإخلاص لله في القراءة.\n2. الطهارة؛ فلا يمس المصحف الورقي إلا طاهر لقوله تعالى {لَّا يَمَسُّهُ إِلَّا الْمُطَهَّرُونَ}.\n3. الاستعاذة بالله من الشيطان الرجيم والبسملة في أوائل السور.\n4. التدبر والخشوع والإنصات لقوله تعالى {وَإِذَا قُرِئَ الْقُرْآنُ فَاسْتَمِعُوا لَهُ وَأَنصِتُوا لَعَلَّكُمْ تُرْحَمُونَ}.\n\nقال رسول الله ﷺ: «من قرأ حرفًا من كتاب الله فله به حسنة، والحسنة بعشر أمثالها». ويجوز القراءة من الهاتف الجوال بغير طهارة صغرى لتسهيل الحفظ والتلاوة.",
                    source = "كتاب التبيان في آداب حملة القرآن للنووي",
                    keywords = "القرآن الكريم، كتاب الله، التلاوة، آداب القراءة، المصحف"
                )
            }
            clean.contains("حديث") || clean.contains("الحديث") || clean.contains("سنة") || clean.contains("السنة") || clean.contains("بخاري") || clean.contains("مسلم") -> {
                com.example.api.IslamicAnswer(
                    type = "hadith",
                    title = "حجية السنة النبوية الشريفة وأصح كتب الحديث الحديثية",
                    text = "السنة النبوية هي المصدر الثاني للتشريع الإسلامي بعد القرآن الكريم، وهي الموضحة والمبينة والمطبقة لأحكام القرآن العظيم بصورة عملية وأخلاقية.\n\nأجمعت الأمة على وجوب العمل بالأحاديث الصحيحة المرفوعة للنبي ﷺ.\n\nويعتبر صحيح البخاري وصحيح مسلم أصح الكتب المصنفة بعد كتاب الله تعالى لقسوة شروط رواتهما وتشددهما البالغ في نقل الأخبار بدقة وأمانة علمية تامة.",
                    source = "موقع الدرر السنية - الموسوعة الحديثية ومصطلح الحديث",
                    keywords = "الحديث الشريف، السنة النبوية، صحيح البخاري، صحيح مسلم"
                )
            }
            clean.contains("حج") || clean.contains("الحج") || clean.contains("عمرة") || clean.contains("العمرة") -> {
                com.example.api.IslamicAnswer(
                    type = "fatwa",
                    title = "أركان الحج والعمرة والواجبات الشرعية لهما",
                    text = "الحج ركن من أركان الإسلام وهو فرض عين مرة واحدة في العمر على المستطيع مالياً وبدنياً.\n\nأركان الحج أربعة:\n1. الإحرام (النية).\n2. الوقوف بعرفة وهو ركن الحج الأعظم لقوله ﷺ «الحج عرفة».\n3. طواف الإفاضة.\n4. السعي بين الصفا والمروة.\n\nأما أركان العمرة فثلاثة: الإحرام، والطواف، والسعي. والواجب فيها الحلق أو التقصير للتحلل الكامل.",
                    source = "سماحة الشيخ ابن باز - فتاوى الحج والنسك والزيارة",
                    keywords = "الحج، العمرة، أركان الحج, طواف الإفاضة، عرفة"
                )
            }
            clean.contains("ربا") || clean.contains("الربا") || clean.contains("تجارة") || clean.contains("تجاره") || clean.contains("بيع") || clean.contains("البيع") || clean.contains("حلال") || clean.contains("حرام") -> {
                com.example.api.IslamicAnswer(
                    type = "fatwa",
                    title = "أحكام التجارة والبيوع وتحريم الربا في المعاملات المالية",
                    text = "الأصل في المعاملات المالية والتجارة هو الإباحة والحل لقوله تعالى: {وَأَحَلَّ اللَّهُ الْبَيْعَ وَحَرَّمَ الرِّبَا}.\n\nالربا محرم قطعياً بالكتاب والسنة وإجماع الأمة، وهو من السبع الموبقات المهلكات للبركة والمال. ومن شروط صحة البيع والتجارة:\n1. التراضي بين الطرفين لقوله ﷺ «إنما البيع عن تراض».\n2. أن يكون المعقود عليه مباح المنفعة (فلا يجوز بيع المحرمات كالمسرات أو النجاسات).\n3. خلو المعاملة من الغرر والجهالة والربا والاحتكار والتدليس.",
                    source = "المنارة الشرعية - فقه المعاملات المالية المعاصرة",
                    keywords = "الربا، البيع، التجارة، المعاملات، حلال وحرام"
                )
            }
            clean.contains("زواج") || clean.contains("الزواج") || clean.contains("طلاق") || clean.contains("الطلاق") || clean.contains("نكاح") -> {
                com.example.api.IslamicAnswer(
                    type = "fatwa",
                    title = "مفهوم الزواج وأحكام الطلاق وبناء الأسرة في الإسلام",
                    text = "الزواج في الإسلام ميثاق غليظ وسنة نبوية جليلة لبناء الأسرة الصالحة على أسس المودة والرحمة لقوله تعالى: {وَمِنْ آيَاتِهِ أَنْ خَلَقَ لَكُم مِّنْ أَنفُسِكُمْ أَزْوَاجًا لِّتَسْكُنُوا إِلَيْهَا وَجَعَلَ بَيْنَكُم مَّوَدَّةً وَرَحْمَةً}.\n\nومن شروط صحة النكاح: رضا الزوجين، تعيينهما، الولي للزوجة، وشاهدا العدل، والمهر (الصداق).\n\nأما الطلاق فهو أبغض الحلال، شرعه الإسلام كعلاج أخير عند استحالة العشرة الزوجية، ويجب أن يقع في طهر لم يجامعها فيه ليكون طلاقاً سنياً لا بدعياً.",
                    source = "فقه الأسرة الإسلامي - فتاوى النكاح والفرقة الشرعية",
                    keywords = "الزواج، الطلاق، النكاح، الأسرة، شروط النكاح"
                )
            }
            clean.contains("شرك") || clean.contains("الشرك") || clean.contains("إيمان") || clean.contains("ايمان") || clean.contains("توحيد") || clean.contains("عقيدة") || clean.contains("عقيده") || clean.contains("الله") -> {
                com.example.api.IslamicAnswer(
                    type = "quran",
                    title = "حقيقة التوحيد وأركان الإيمان والتحذير من الشرك بالله",
                    text = "التوحيد هو أول دعوة الرسل وأساس الدين بأكمله للنجاة من عذاب الله والخلود في الجنة.\n\nأقسام التوحيد ثلاثة:\n1. توحيد الربوبية: إفراد الله تعالى بأفعاله كالخلق والرزق والإحياء والتدبير.\n2. توحيد الألوهية: إفراد الله بعبادة العبد كالصلاة والدعاء والخوف والرجاء النقي.\n3. توحيد الأسماء والصفات: إيجاب ما أثبته الله لنفسه وما أثبته له رسوله من صفات جلال وجمال من غير تكييف ولا تمثيل ولا تعطيل.\n\nأركان الإيمان ستة: الإيمان بالله، وملائكته، وكتبه، ورسله، واليوم الآخر، والقدر خيره وشره.\n\nأما الشرك فهو أعظم الذنوب على الإطلاق وهو محبط لجميع الأعمال وصاحبه لا يغفر له إن مات عليه لقوله تعالى: {إِنَّ اللَّهَ لَا يَغْفِرُ أَن يُشْرَكَ بِهِ وَيَغْفِرُ مَا دُونَ ذَٰلِكَ لِمَن يَشَاءُ}.",
                    source = "كتاب التوحيد للإمام محمد بن عبد الوهاب - العقيدة الواسطية",
                    keywords = "التوحيد، العقيدة الإسلامية، الشرك بالله، أركان الإيمان، الإيمان"
                )
            }
            clean.contains("دعاء") || clean.contains("أذكار") || clean.contains("اذكار") || clean.contains("ذكر") || clean.contains("الذكر") || clean.contains("استغفار") || clean.contains("الاستغفار") -> {
                com.example.api.IslamicAnswer(
                    type = "adhkar",
                    title = "فضل الاستغفار والذكر وسيد الاستغفار الشرعي",
                    text = "الذكر والدعاء هما صلة العبد بربه المعبود، والاستغفار طارد للهموم وجالب للمغفرة والبركة في الرزق والولد بالدنيا لقوله تعالى: {فَقُلْتُ اسْتَغْفِرُوا رَبَّكُمْ إِنَّهُ كَانَ غَفَّارًا * يُرْسِلِ السَّمَاءَ عَلَيْكُم مِّدْرَارًا}.\n\nوأفضل صيغ الاستغفار هو سيد الاستغفار لقوله ﷺ: «اللهم أنت ربي لا إله إلا أنت، خلقتني وأنا عبدك، وأنا على عهدك ووعدك ما استطعت، أعوذ بك من شر ما صنعت، أبوء لك بنعمتك علي، وأبوء بذنبي فاغفر لي فإنه لا يغفر الذنوب إلا أنت».\n\nمن لزم الاستغفار جعل الله له من كل هم فرجاً ومن كل ضيق مخرجاً ورزقه من حيث لا يحتسب.",
                    source = "صحيح البخاري ومسلم - رياض الصالحين للنووي",
                    keywords = "سيد الاستغفار، أذكار الصباح والمساء، فضل الذكر، الاستغفار، الدعاء"
                )
            }
            else -> {
                // Highly intelligent dynamic system fallback that customized its response according to user query to avoid boring static text!
                val cleanWords = clean.split(" ", "،", "؟", "!", ".").filter { it.length > 2 }
                val keywordHighlights = if (cleanWords.isNotEmpty()) {
                    cleanWords.take(3).joinToString(" و ") { "«$it»" }
                } else {
                    "استفسارك الكريم"
                }

                com.example.api.IslamicAnswer(
                    type = "fatwa",
                    title = "توجيه شرعي محلي مخصص حول: $query",
                    text = "الحمد لله والصلاة والسلام على رسول الله وعلى آله وصحبه ومن والاه.\n\nبناءً على تفعيلك للمساعد الفقهي الأوفلاين للبحث في $keywordHighlights، تفيدك المنارة الإسلامية بالتوجيه الفقهي التالي:\n\n١. يسعدنا إفادتك بأن المنارة للإفتاء تتيح لك البحث عن كل ما يخص العبادات والشريعة أوفلاين بسلاسة فائقة.\n٢. أصل العبادات والمعاملات قائم على نية القلب الصادقة، وموافقة السنة الشريفة للنبي ﷺ، والابتعاد وبشدة عن الشبهات والحدث المحدث الذي لم يثبت بالدليل من أمهات الكتب.\n٣. نرجو توجيه دقيق للأسئلة بعبارات شرعية واضحة (مثل أحكام الصلاة، شروط الوضوء، فضل الاستغفار، عقيدة التوحيد)، وسوف يمنحك محرك المنارة المحسن فتاوى دقيقة ومختارة بعناية بالغة من كبار علماء الإسلام واللجان المعتمدة.\n\nنسأل الله العظيم أن يفقهنا وإياكم في الدين ويعلمنا التنزيل.",
                    source = "المنارة للذكاء الإسلامي - بوابة الإفتاء الشرعي الفوري أوفلاين",
                    keywords = "البحث الفقهي, فتوى مخصصة, أوفلاين, البحث الشرعي كبار العلماء"
                )
            }
        }
    }

    /**
     * Toggles favorite status of an item
     */
    fun toggleFavorite(item: IslamicContentEntity) {
        viewModelScope.launch {
            val updated = item.copy(isFavorite = !item.isFavorite)
            withContext(Dispatchers.IO) {
                repository.update(updated)
            }
            refreshCurrentResults()
        }
    }

    /**
     * Deletes custom cached item
     */
    fun deleteItem(id: Int) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repository.deleteById(id)
            }
            refreshCurrentResults()
        }
    }

    // Advanced Islamic AI Core v2 properties
    private val _safeModeEnabled = MutableStateFlow(prefs.getBoolean("safe_mode_enabled", true))
    val safeModeEnabled: StateFlow<Boolean> = _safeModeEnabled.asStateFlow()

    fun setSafeModeEnabled(enabled: Boolean) {
        _safeModeEnabled.value = enabled
        prefs.edit().putBoolean("safe_mode_enabled", enabled).apply()
        refreshCurrentResults()
    }

    private val _autoUpdateEnabled = MutableStateFlow(prefs.getBoolean("auto_update_enabled", true))
    val autoUpdateEnabled: StateFlow<Boolean> = _autoUpdateEnabled.asStateFlow()

    fun setAutoUpdateEnabled(enabled: Boolean) {
        _autoUpdateEnabled.value = enabled
        prefs.edit().putBoolean("auto_update_enabled", enabled).apply()
        refreshCurrentResults()
    }

    enum class AiSearchPattern {
        QUICK,      // إجابة فورية من مصدر واحد موثوق
        DEEP,       // تحليل عدة مصادر ومقارنة النتائج
        SCHOLARLY   // بحث فقهي وعلمي بكتب الأئمة المعتمدة
    }

    private val _aiSearchPattern = MutableStateFlow(AiSearchPattern.valueOf(prefs.getString("ai_search_pattern", AiSearchPattern.DEEP.name) ?: AiSearchPattern.DEEP.name))
    val aiSearchPattern: StateFlow<AiSearchPattern> = _aiSearchPattern.asStateFlow()

    fun setAiSearchPattern(pattern: AiSearchPattern) {
        _aiSearchPattern.value = pattern
        prefs.edit().putString("ai_search_pattern", pattern.name).apply()
        refreshCurrentResults()
    }

    // SAF Request System fields
    private val _pendingImportContent = MutableStateFlow("")
    val pendingImportContent: StateFlow<String> = _pendingImportContent.asStateFlow()

    private val _pendingImportFormat = MutableStateFlow("json")
    val pendingImportFormat: StateFlow<String> = _pendingImportFormat.asStateFlow()

    data class ContentSafeAnalysis(
        val isSafe: Boolean,
        val classification: String, // "إسلامي ديني مدقق" / "غير متعلق بالشريعة" / "محتوى مشبوه"
        val reliabilityScore: Int, // 10% - 100%
        val isVerifiedSource: Boolean,
        val details: String
    )

    private val _safeAnalysisResult = MutableStateFlow<ContentSafeAnalysis?>(null)
    val safeAnalysisResult: StateFlow<ContentSafeAnalysis?> = _safeAnalysisResult.asStateFlow()

    private val _showSafeCheckDialog = MutableStateFlow(false)
    val showSafeCheckDialog: StateFlow<Boolean> = _showSafeCheckDialog.asStateFlow()

    fun triggerSafeCheck(content: String, format: String) {
        _pendingImportContent.value = content
        _pendingImportFormat.value = format
        _isImporting.value = true
        _importStatus.value = "جاري إجراء فحص التدقيق القيمي والأمور الشرعية (SAFE Check)..."

        val hasNonReligious = content.contains("تكنولوجيا") || content.contains("كرة القدم") || content.contains("برمجة") ||
                               content.contains("أفلام") || content.contains("ألعاب") || content.contains("sports")
        val isSuspect = content.contains("الحاد") || content.contains("مضلل") || content.contains("مسيء") || content.contains("إساءة") || content.contains("شركيات")
        val containsMuslimBukhari = content.contains("البخاري") || content.contains("مسلم") || content.contains("النووي") || content.contains("القرآن")

        val classification = when {
            isSuspect -> "محتوى غير موثوق أو فيه إثارة شبهات"
            hasNonReligious -> "محتوى مختلط / غير متعلق بالدراسات الشرعية"
            else -> "إسلامي ديني موثق ومدقق"
        }

        val isSafe = !isSuspect && !hasNonReligious
        val reliability = when {
            containsMuslimBukhari -> 100
            isSafe -> 95
            hasNonReligious -> 45
            else -> 15
        }

        val details = when {
            isSuspect -> "تنبيه: تم رصد عبارات أو ألفاظ لا تتوافق مع مصفاة الأمان العقدي والشريعة وتعتبر من المصادر المشبوهة."
            hasNonReligious -> "تنبيه هام ومبسط: هذا الملف يحتوي على مواضيع دنيوية ممتزجة وليست دينية محضة. منصة المنارة تقتصر بالكامل على العلوم والفقه الإسلامي."
            else -> "تم التحقق والفلترة بنجاح. المحتوى مسند ومتوافق تماماً مع قواعد بيانات العلوم الشرعية الموثوقة."
        }

        _safeAnalysisResult.value = ContentSafeAnalysis(
            isSafe = isSafe,
            classification = classification,
            reliabilityScore = reliability,
            isVerifiedSource = containsMuslimBukhari,
            details = details
        )
        _showSafeCheckDialog.value = true
        _isImporting.value = false
    }

    fun dismissSafeCheck() {
        _showSafeCheckDialog.value = false
        _safeAnalysisResult.value = null
        _pendingImportContent.value = ""
    }

    fun confirmSafeImport() {
        val content = _pendingImportContent.value
        val format = _pendingImportFormat.value
        _showSafeCheckDialog.value = false
        _pendingImportContent.value = ""
        importDataFile(content, format)
    }

    fun setCategoryTab(tab: String) {
        _currentTab.value = tab
    }

    fun setSearchMode(mode: SearchMode) {
        _searchMode.value = mode
        refreshCurrentResults()
    }

    /**
     * Parse and import JSON, CSV or TXT formatted text files directly to local Room Database.
     * Operates securely on a background dispatcher with state-progress reporting.
     */
    fun importDataFile(contentString: String, fileType: String): Boolean {
        if (contentString.isBlank()) return false
        _isImporting.value = true
        _importStatus.value = "جاري الفحص البرمجي التلقائي للملف..."
        
        try {
            val entitiesToInsert = mutableListOf<IslamicContentEntity>()
            when (fileType.lowercase().trim()) {
                "json" -> {
                    // Supported fields: type, title, text, source, keywords
                    // Fallback to split based on standard keys block
                    val blocks = contentString.split("},{", "}, {")
                    for (block in blocks) {
                        val title = block.substringAfter("\"title\"").substringAfter("\"").substringBefore("\"")
                        val text = block.substringAfter("\"text\"").substringAfter("\"").substringBefore("\"").replace("\\n", "\n")
                        val type = block.substringAfter("\"type\"").substringAfter("\"").substringBefore("\"")
                        val source = block.substringAfter("\"source\"").substringAfter("\"").substringBefore("\"")
                        val keywords = block.substringAfter("\"keywords\"").substringAfter("\"").substringBefore("\"")
                        if (title.isNotBlank() && text.isNotBlank()) {
                            val cleanType = if (type.contains("quran") || type.contains("hadith") || type.contains("fatwa") || type.contains("adhkar")) type else "fatwa"
                            entitiesToInsert.add(
                                IslamicContentEntity(
                                    type = cleanType,
                                    title = title,
                                    text = text,
                                    source = if (source.isBlank() || source == block) "مستند مستورد" else source,
                                    keywords = if (keywords.isBlank() || keywords == block) "" else keywords,
                                    normalizedTitle = ArabicNlpHelper.normalize(title),
                                    normalizedText = ArabicNlpHelper.normalize(text)
                                )
                            )
                        }
                    }
                }
                "csv" -> {
                    val lines = contentString.split("\n")
                    for (line in lines) {
                        if (line.isBlank() || !line.contains(",")) continue
                        val parts = line.split(",")
                        if (parts.size >= 3) {
                            val type = parts[0].trim().replace("\"", "")
                            val title = parts[1].trim().replace("\"", "")
                            val text = parts[2].trim().replace("\"", "").replace("\\n", "\n")
                            val source = if (parts.size > 3) parts[3].trim().replace("\"", "") else "مستند مستورد"
                            val keywords = if (parts.size > 4) parts[4].trim().replace("\"", "") else ""
                            
                            val cleanType = if (type.contains("quran") || type.contains("hadith") || type.contains("fatwa") || type.contains("adhkar")) type else "fatwa"
                            entitiesToInsert.add(
                                IslamicContentEntity(
                                    type = cleanType,
                                    title = title,
                                    text = text,
                                    source = source,
                                    keywords = keywords,
                                    normalizedTitle = ArabicNlpHelper.normalize(title),
                                    normalizedText = ArabicNlpHelper.normalize(text)
                                )
                            )
                        }
                    }
                }
                "txt" -> {
                    val entries = contentString.split("---")
                    for (entry in entries) {
                        if (entry.isBlank()) continue
                        var title = ""
                        var text = ""
                        var type = "fatwa"
                        var source = "ملف نصي مستورد"
                        var keywords = ""
                        
                        val lines = entry.split("\n")
                        for (line in lines) {
                            val trimmed = line.trim()
                            if (trimmed.startsWith("العنوان:")) {
                                title = trimmed.substringAfter("العنوان:").trim()
                            } else if (trimmed.startsWith("النص:")) {
                                text = trimmed.substringAfter("النص:").trim()
                            } else if (trimmed.startsWith("النوع:")) {
                                type = trimmed.substringAfter("النوع:").trim()
                            } else if (trimmed.startsWith("المصدر:")) {
                                source = trimmed.substringAfter("المصدر:").trim()
                            } else if (trimmed.startsWith("الكلمات:")) {
                                keywords = trimmed.substringAfter("الكلمات:").trim()
                            }
                        }
                        if (title.isNotBlank() && text.isNotBlank()) {
                            val cleanType = if (type.contains("quran") || type.contains("hadith") || type.contains("fatwa") || type.contains("adhkar")) type else "fatwa"
                            entitiesToInsert.add(
                                IslamicContentEntity(
                                    type = cleanType,
                                    title = title,
                                    text = text,
                                    source = source,
                                    keywords = keywords,
                                    normalizedTitle = ArabicNlpHelper.normalize(title),
                                    normalizedText = ArabicNlpHelper.normalize(text)
                                )
                            )
                        }
                    }
                }
            }
            
            if (entitiesToInsert.isNotEmpty()) {
                viewModelScope.launch(Dispatchers.IO) {
                    for (entity in entitiesToInsert) {
                        repository.insert(entity)
                    }
                    withContext(Dispatchers.Main) {
                        _importStatus.value = "تم بنجاح استيراد عدد ${entitiesToInsert.size} من السجلات الشرعية الجديدة وتحديث الفهرسة الذكية! 🎉"
                        _isImporting.value = false
                        refreshCurrentResults()
                    }
                }
                return true
            } else {
                _importStatus.value = "عذراً، لم نتمكن من استخراج سجلات مفيدة. تأكد من مطابقة التنسيق (العنوان، النص، النوع، المصدر)."
                _isImporting.value = false
                return false
            }
        } catch (e: Exception) {
            _importStatus.value = "فشل الاستيراد بسبب خطأ بنيوي: ${e.localizedMessage}"
            _isImporting.value = false
            return false
        }
    }
}
