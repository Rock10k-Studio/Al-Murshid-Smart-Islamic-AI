package com.example.api

import com.example.BuildConfig
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

// --- Gemini Content Serialization Models ---

@Serializable
data class GenerateContentRequest(
    val contents: List<Content>,
    val generationConfig: GenerationConfig? = null,
    val tools: List<Tool>? = null,
    val systemInstruction: Content? = null
)

@Serializable
data class Tool(
    val googleSearch: GoogleSearch? = null
)

@Serializable
class GoogleSearch

@Serializable
data class Content(
    val parts: List<Part>
)

@Serializable
data class Part(
    val text: String? = null,
    val inlineData: InlineData? = null
)

@Serializable
data class InlineData(
    val mimeType: String,
    val data: String
)

@Serializable
data class GenerationConfig(
    val responseFormat: ResponseFormat? = null,
    val temperature: Float? = null,
    val topP: Float? = null,
    val topK: Int? = null
)

@Serializable
data class ResponseFormat(
    val text: ResponseFormatText? = null
)

@Serializable
data class ResponseFormatText(
    val mimeType: String,
    val schema: JsonObject? = null
)

@Serializable
data class GenerateContentResponse(
    val candidates: List<Candidate>? = null
)

@Serializable
data class Candidate(
    val content: Content,
    val groundingMetadata: GroundingMetadata? = null
)

@Serializable
data class GroundingMetadata(
    val webSearchQueries: List<String>? = null,
    val groundingChunks: List<GroundingChunk>? = null
)

@Serializable
data class GroundingChunk(
    val web: WebSource? = null
)

@Serializable
data class WebSource(
    val uri: String? = null,
    val title: String? = null
)

data class GeminiAnswerResult(
    val answer: String,
    val webSources: List<WebSource> = emptyList(),
    val webQueries: List<String> = emptyList()
)

// --- Retrofit Network Interface ---

interface GeminiApiService {
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GenerateContentRequest
    ): GenerateContentResponse
}

object RetrofitClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    val service: GeminiApiService by lazy {
        val json = Json { ignoreUnknownKeys = true }
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        retrofit.create(GeminiApiService::class.java)
    }
}

object GeminiApiClient {
    /**
     * Call the Gemini API directly via REST.
     * Integrates custom temperature and system instructions for precise, context-aware answers.
     */
    suspend fun generateAnswer(
        prompt: String,
        systemInstructionText: String? = null,
        temperature: Float = 0.3f,
        enableWebSearch: Boolean = false,
        customApiKey: String? = null
    ): GeminiAnswerResult = withContext(Dispatchers.IO) {
        val apiKey = if (!customApiKey.isNullOrBlank()) customApiKey else BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "null" || apiKey == "YOUR_API_KEY_HERE") {
            return@withContext GeminiAnswerResult("خطأ: لم يتم تهيئة مفتاح API الخاص بـ Gemini. يرجى إضافته في إعدادات التطبيق الخاصة بك للتشغيل.")
        }

        val toolsList = if (enableWebSearch) {
            listOf(Tool(googleSearch = GoogleSearch()))
        } else {
            null
        }

        val request = GenerateContentRequest(
            contents = listOf(
                Content(parts = listOf(Part(text = prompt)))
            ),
            generationConfig = GenerationConfig(
                temperature = temperature,
                topP = 0.95f
            ),
            systemInstruction = systemInstructionText?.let {
                Content(parts = listOf(Part(text = it)))
            },
            tools = toolsList
        )

        try {
            val response = RetrofitClient.service.generateContent(apiKey, request)
            val candidate = response.candidates?.firstOrNull()
            val text = candidate?.content?.parts?.firstOrNull()?.text 
                ?: "لم يستطع الذكاء الاصطناعي إنتاج إجابة مناسبة. أعد المحاولة بصيغة أخرى."
            
            val metadata = candidate?.groundingMetadata
            val webQueries = metadata?.webSearchQueries ?: emptyList()
            val webSources = metadata?.groundingChunks?.mapNotNull { it.web } ?: emptyList()

            GeminiAnswerResult(
                answer = text,
                webSources = webSources,
                webQueries = webQueries
            )
        } catch (e: Exception) {
            GeminiAnswerResult("فشل الاتصال بخادم الذكاء الاصطناعي: ${e.localizedMessage ?: "خطأ غير معروف"}. يرجى التأكد من اتصال الإنترنت وصلاحية مفتاح API.")
        }
    }
}
