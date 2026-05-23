package com.example.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit
import com.example.BuildConfig

// --- MOSHI-ANNOTATED REQUEST & RESPONSE MODELS FOR GEMINI ---

@JsonClass(generateAdapter = true)
data class GeminiRequest(
    @Json(name = "contents") val contents: List<Content>,
    @Json(name = "generationConfig") val generationConfig: GenerationConfig? = null,
    @Json(name = "systemInstruction") val systemInstruction: Content? = null
)

@JsonClass(generateAdapter = true)
data class Content(
    @Json(name = "parts") val parts: List<Part>
)

@JsonClass(generateAdapter = true)
data class Part(
    @Json(name = "text") val text: String
)

@JsonClass(generateAdapter = true)
data class GenerationConfig(
    @Json(name = "responseMimeType") val responseMimeType: String? = "application/json",
    @Json(name = "temperature") val temperature: Float? = 0.2f
)

@JsonClass(generateAdapter = true)
data class GeminiResponse(
    @Json(name = "candidates") val candidates: List<Candidate>?
)

@JsonClass(generateAdapter = true)
data class Candidate(
    @Json(name = "content") val content: Content?
)

// --- CHAT GPT / GEMINI STRUCTURAL OUTPUT FOR OUR APP ---
@JsonClass(generateAdapter = true)
data class IslamicAnswer(
    @Json(name = "type") val type: String = "fatwa",
    @Json(name = "title") val title: String = "",
    @Json(name = "text") val text: String = "",
    @Json(name = "source") val source: String = "",
    @Json(name = "keywords") val keywords: String = "",
    @Json(name = "error") val error: String? = null
)

// --- RETROFIT SERVICE INTERFACE ---
interface GeminiApiService {
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse
}

object GeminiApiClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    private val apiService: GeminiApiService = retrofit.create(GeminiApiService::class.java)

    /**
     * Call the Gemini API server-side using the injected or user-provided API key.
     * Ensures strict adherence to whitelisted sources and returns structured JSON output.
     */
    suspend fun generateIslamicResponse(query: String, customApiKey: String? = null, aiMode: String = "standard"): IslamicAnswer {
        val apiKey = if (!customApiKey.isNullOrBlank()) customApiKey else BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return IslamicAnswer(
                error = "عذراً، مفتاح واجهة برمجة تطبيقات Gemini (API Key) غير مهيأ. يرجى كتابته في حقل الإعدادات لتفعيل البحث والذكاء الاصطناعي الأونلاين."
            )
        }

        val modeGuideline = when (aiMode.lowercase()) {
            "simple" -> "مورد الإجابة: [الوضع البسيط - Simple Mode]. يجب أن تكون الإجابة مختصرة جداً وموجزة بلغة سلسلة خالية من التطويل أو التفاصيل العميقة أو غريب الألفاظ، مناسبة ومفهومة جداً للمبتدئين وبسيطة للغاية."
            "advanced_ultra" -> "مورد الإجابة: [الوضع المتقدم جداً - Advanced Ultra Mode]. يجب إجراء تحليل فقهي عميق وشامل للسؤال، مع مقارنة الآراء الفقهية إن وجدت وعرض أقوال كبار الأئمة الكبار من المذاهب الفقهية الأربعة المعتبرة بإنصاف وتوضيح تفصيلي للأدلة والوجوه والاستنتاج المنطقي المنهجي."
            else -> "مورد الإجابة: [الوضع القوي - Standard Mode]. يجب أن تكون الإجابة منظمة، بشرح متوسط يبسط المفاهيم مع الحفاظ على القوة والدقة ومصحوبة بالأدلة الشرعية والقرآنية أو الحديثية عند توفرها بوضوح."
        }

        val systemPrompt = """
            أنت "المنارة للذكاء الإسلامي" مساعد وباحث إسلامي ذكي معتمد وغاية في الدقة وذو منزع سلفي شرعي رصين.
            مهمتك هي الإجابة عن أسئلة المستخدمين الإسلامية بدقة شديدة بالاعتماد الحصري والمطلق على المصادر الموثوقة التالية:
            1. القرآن الكريم وتفاسيره المعتمدة (كابن كثير والسعدي والطبري).
            2. الأحاديث الصحيحة الواردة في صحيح البخاري ومسلم والكتب الستة.
            3. الفتاوى والبحوث المأخوذة من المواقع التالية فقط (Whitelist):
               - موقع الإسلام سؤال وجواب (العلامة صالح المنجد)
               - موقع الدرر السنية (الموسوعة الحديثية والعقدية والفقهية)
               - موقع سماحة الشيخ عبد العزيز بن باز رحمه الله
               - موقع إسلام ويب (Islamic Web)
            
            التزام بنمط الذكاء الشرعي الحالي للمستخدم:
            $modeGuideline
            
            قوانين صارمة:
            1. يمنع منعاً باتاً اختراع أو توليد أي فتوى أو حديث نبوي. إذا لم يثبت الحديث، بيّن درجته من موقع الدرر السنية.
            2. يمنع الإجابة بغير دليل شرعي ويجب ذكر المرجع أو السورة والآية أو الكتاب أو اسم الموقع والفتوى بصيغة محددة.
            3. إذا سألك المستخدم من صنعه أو صممه أو طوره أو برمجته أو من دربك أو لقنك، فيجب عليك حصراً وصارماً قول الآتي في حقل "text":
               "المهندس عمرو سلامة الراضي هو من دربني لأكون تحت خدمة الأسئلة الدينية الإسلامية والشرعية فقط."
            4. يمنع منعاً باتاً التجاوب مع أي ألفاظ خارجة أو بذيئة أو شتائم أو أسئلة خارج نطاق العلوم الإسلامية والفتاوى الشرعية، ويجب ملء حقل "error" بتنبيه مؤدب يفيد بوجود مصفاة حماية وطلب توجيه الأسئلة الفقهية والدينية فقط بأسلوب طيب، والاعتذار بأنك مخصص ومطور تحت تدريب المهندس عمرو سلامة الراضي لخدمة الإسلام والدراسات الشرعية، مع جعل حقل "text" يوضح نفس التنبيه.
            5. يجب أن ترجع الإجابة بصيغة ملف JSON صالح ومغلق تماماً يحتوي على المفاتيح التالية باللغة العربية:
               - "type": نوع المحتوى (يجب أن يكون قيمة واحدة من: "quran" أو "hadith" أو "fatwa" أو "adhkar")
               - "title": عنوان مناسب ومختصر جداً للسؤال يوضح لب الموضوع.
               - "text": نص الإجابة الوافي الفصيح باللغة العربية مع الأسطر البرمجية \n والاستشهاد بالآيات والأحاديث.
               - "source": المرجع الشرعي التفصيلي (اسم الموقع أو الكتاب، مثلاً: موقع الإسلام سؤال وجواب رقم الفتوى 1234 أو صحيح البخاري رقم 432)
               - "keywords": كلمات دلالية مفتاحية للبحث مفصولة بفواصل (أكثر من 3 كلمات).
               - "error": اترك هذا الحقل فارغاً أو null إلا إذا كان السؤال خارجاً تماماً عن الشريعة الإسلامية والعلوم الدينية أو يحتوي على ألفاظ بذيئة، عندها اكتب رسالة تنبيهية تطلب سؤاله عن الشريعة فقط بإنصاف.
            
            مثال لمخرج JSON المطلوب:
            {
               "type": "fatwa",
               "title": "حكم قراءة القرآن بدون وضوء",
               "text": "يجوز قراءة القرآن الكريم من الجوال أو غيباً بدون وضوء للجنب الأصغر أما المصحف الورقي فلا يمسه إلا طاهر...",
               "source": "موقع سماحة الشيخ ابن باز - فتاوى الطهارة",
               "keywords": "الوضوء، قراءة القرآن، الجوال، لمس المصحف",
               "error": null
            }
        """.trimIndent()

        val request = GeminiRequest(
            contents = listOf(Content(parts = listOf(Part(text = "سؤال المستخدم: $query")))),
            systemInstruction = Content(parts = listOf(Part(text = systemPrompt))),
            generationConfig = GenerationConfig()
        )

        return try {
            val response = apiService.generateContent(apiKey, request)
            val jsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?: throw Exception("Empty response text")

            // Parse response jsonText manually or via Moshi
            val adapter = moshi.adapter(IslamicAnswer::class.java)
            val cleanJsonText = sanitizeJsonString(jsonText)
            adapter.fromJson(cleanJsonText) ?: throw Exception("Failed to parse response JSON")
        } catch (e: Exception) {
            IslamicAnswer(
                error = "حدث عطل أثناء الاتصال بالخادم الذكي: ${e.localizedMessage ?: "تأكد من اتصال الإنترنت ومن تفعيل رمز API"}"
            )
        }
    }

    /**
     * Sanitize possible markdown wrapper like ```json from Gemini response
     */
    private fun sanitizeJsonString(input: String): String {
        var clean = input.trim()
        if (clean.startsWith("```json")) {
            clean = clean.substring(7)
        } else if (clean.startsWith("```")) {
            clean = clean.substring(3)
        }
        if (clean.endsWith("```")) {
            clean = clean.substring(0, clean.length - 3)
        }
        return clean.trim()
    }
}
