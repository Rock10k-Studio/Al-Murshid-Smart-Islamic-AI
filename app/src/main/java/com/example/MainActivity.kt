package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import com.example.data.IslamicContentEntity
import com.example.nlp.ArabicNlpHelper
import com.example.ui.MainViewModel
import com.example.ui.ScoredResult
import com.example.ui.Reciter
import com.example.ui.theme.AlManaraTheme
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: MainViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
            val themeMode by viewModel.themeMode.collectAsState()
            val isLateNightReading by viewModel.isLateNightReading.collectAsState()
            val effectiveTheme = if (isLateNightReading) "high_contrast" else themeMode
            AlManaraTheme(themeMode = effectiveTheme) {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    IslamicAppScreen(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        viewModel = viewModel
                    )
                }
            }
        }
    }
}

/**
 * Geometric Eight-Pointed Star (Rub el Hizb) Islamic Ornament Decoration
 */
@Composable
fun RubElHizbIcon(modifier: Modifier = Modifier, color: Color) {
    Canvas(modifier = modifier) {
        val sizePx = size.width
        val center = sizePx / 2f
        val radius = sizePx * 0.45f

        // Draw 1st square
        val path1 = Path().apply {
            moveTo(center, center - radius)
            lineTo(center + radius, center)
            lineTo(center, center + radius)
            lineTo(center - radius, center)
            close()
        }

        // Draw 2nd square rotated 45 degrees
        rotate(degrees = 45f) {
            val path2 = Path().apply {
                moveTo(center, center - radius)
                lineTo(center + radius, center)
                lineTo(center, center + radius)
                lineTo(center - radius, center)
                close()
            }
            drawPath(path2, color)
        }
        drawPath(path1, color)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IslamicAppScreen(
    modifier: Modifier = Modifier,
    viewModel: MainViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val context = LocalContext.current
    var activeTab by remember { mutableStateOf("chat") } // "chat", "storage", "search"

    // Custom configuration states
    var showConfigDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // --- Premium Brand Bar ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                        )
                    )
                )
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                RubElHizbIcon(
                    modifier = Modifier.size(34.dp),
                    color = MaterialTheme.colorScheme.secondary
                )
                Column {
                    Text(
                        text = "المنارة للذكاء الإسلامي",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.SansSerif
                    )
                    Text(
                        text = "مساعد ذكي وموثوق • RAG محلي متكامل",
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Light
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Quick Theme Cycle Button (system -> light -> dark -> high_contrast)
                val currentThemeMode by viewModel.themeMode.collectAsState()
                IconButton(
                    onClick = {
                        val nextTheme = when (currentThemeMode) {
                            "system" -> "light"
                            "light" -> "dark"
                            "dark" -> "high_contrast"
                            else -> "system"
                        }
                        viewModel.themeMode.value = nextTheme
                    },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("theme_quick_toggle")
                ) {
                    val themeIcon = when (currentThemeMode) {
                        "light" -> Icons.Default.LightMode
                        "dark" -> Icons.Default.DarkMode
                        "high_contrast" -> Icons.Default.Contrast
                        else -> Icons.Default.SettingsBrightness
                    }
                    val themeDescription = when (currentThemeMode) {
                        "light" -> "الوضع المضيء"
                        "dark" -> "الوضع الداكن"
                        "high_contrast" -> "التبويب الليلي فائق التباين"
                        else -> "تلقائي النظام"
                    }
                    Icon(
                        imageVector = themeIcon,
                        contentDescription = themeDescription,
                        tint = Color.White
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Quick customization icon
                IconButton(
                    onClick = { showConfigDialog = true },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("config_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "تخصيص الخوارزمية والذكاء",
                        tint = Color.White
                    )
                }
            }
        }

        // --- Custom Interactive Banner Warn ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f))
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "حالة المسار الأوتوماتيكية",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "مسار التخزين: Android/data/com.example/files/",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // --- View Content ---
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                ) {
            when (activeTab) {
                "chat" -> ChatTabContent(viewModel = viewModel)
                "storage" -> StorageTabContent(
                    viewModel = viewModel,
                    onOpenImport = { showImportDialog = true }
                )
                "search" -> SearchTabContent(viewModel = viewModel, onTabChange = { activeTab = it })
                "progress" -> QuranProgressTabContent(viewModel = viewModel, onTabChange = { activeTab = it })
                "favorites" -> FavoritesTabContent(viewModel = viewModel, onTabChange = { activeTab = it })
            }
        }

        // --- Audio Recitation Player Sticking Bar ---
        AudioRecitationPlayerBar(viewModel = viewModel)

        // --- Navigation Controller ---
        NavigationBar(
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            NavigationBarItem(
                selected = activeTab == "chat",
                onClick = { activeTab = "chat" },
                label = { Text("المجيب الذكي", fontWeight = FontWeight.SemiBold) },
                icon = {
                    Icon(
                        imageVector = if (activeTab == "chat") Icons.Default.AutoAwesome else Icons.Outlined.AutoAwesome,
                        contentDescription = "المجيب الذكي"
                    )
                },
                modifier = Modifier.testTag("tab_chat")
            )
            NavigationBarItem(
                selected = activeTab == "storage",
                onClick = { activeTab = "storage" },
                label = { Text("مسارات الملفات", fontWeight = FontWeight.SemiBold) },
                icon = {
                    Icon(
                        imageVector = if (activeTab == "storage") Icons.Default.FolderOpen else Icons.Outlined.FolderOpen,
                        contentDescription = "الملفات والأقسام"
                    )
                },
                modifier = Modifier.testTag("tab_storage")
            )
            NavigationBarItem(
                selected = activeTab == "search",
                onClick = { activeTab = "search" },
                label = { Text("البحث الدلالي", fontWeight = FontWeight.SemiBold) },
                icon = {
                    Icon(
                        imageVector = if (activeTab == "search") Icons.Default.SavedSearch else Icons.Outlined.SavedSearch,
                        contentDescription = "البحث الدلالي"
                    )
                },
                modifier = Modifier.testTag("tab_search")
            )
            NavigationBarItem(
                selected = activeTab == "progress",
                onClick = { activeTab = "progress" },
                label = { Text("متابعة الختم", fontWeight = FontWeight.SemiBold) },
                icon = {
                    Icon(
                        imageVector = if (activeTab == "progress") Icons.Default.CheckCircle else Icons.Outlined.CheckCircle,
                        contentDescription = "متابعة الختمة والتدبر"
                    )
                },
                modifier = Modifier.testTag("tab_progress")
            )
            NavigationBarItem(
                selected = activeTab == "favorites",
                onClick = { activeTab = "favorites" },
                label = { Text("المفضلة", fontWeight = FontWeight.SemiBold) },
                icon = {
                    Icon(
                        imageVector = if (activeTab == "favorites") Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = "المفضلة"
                    )
                },
                modifier = Modifier.testTag("tab_favorites")
            )
        }
    }

    // --- Configuration Dialog ---
    if (showConfigDialog) {
        TuningsDialog(viewModel = viewModel, onDismiss = { showConfigDialog = false })
    }

    // --- Import File Dialog ---
    if (showImportDialog) {
        ImportFileDialog(viewModel = viewModel, onDismiss = { showImportDialog = false })
    }
}

/**
 * Chat Tab: Users asks Islamic questions. Prompt generates RAG with custom temperature & parameters.
 */
@Composable
fun ChatTabContent(viewModel: MainViewModel) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    var queryText by remember { mutableStateOf("") }

    val pendingChatQuery by viewModel.pendingChatQuery.collectAsState()
    LaunchedEffect(pendingChatQuery) {
        if (pendingChatQuery.isNotEmpty()) {
            queryText = pendingChatQuery
            viewModel.pendingChatQuery.value = "" // clear
            viewModel.askGemini(pendingChatQuery)
        }
    }

    val aiAnswer by viewModel.aiAnswer.collectAsState()
    val isAiLoading by viewModel.isAiLoading.collectAsState()
    val referencedContexts by viewModel.referencedContexts.collectAsState()

    val shariahPersona by viewModel.shariahPersona.collectAsState()
    val temperature by viewModel.temperature.collectAsState()
    val enableWebSearch by viewModel.enableWebSearch.collectAsState()
    val webSources by viewModel.webSources.collectAsState()
    val webQueries by viewModel.webQueries.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "البارامترات الحالية",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "المحددات النشطة حالياً:",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val personaLabel = when (shariahPersona) {
                        "fiqh" -> "المنهج الفقهي الدقيق"
                        "spiritual" -> "منهج الرقائق والتزكية"
                        "tafsir" -> "التفسير والبيان البلاغي"
                        else -> "الوسطية والاعتدال"
                    }
                    Text(
                        text = "المنهج: $personaLabel",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "الحرارة: $temperature",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = "البحث والاستقصاء من الويب",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Column {
                            Text(
                                text = "تدقيق فوري من الويب (البحث في Google)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "استقصاء ديناميكي وتخريج الموثوقية فوق المضمون",
                                fontSize = 9.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Switch(
                        checked = enableWebSearch,
                        onCheckedChange = { viewModel.enableWebSearch.value = it },
                        modifier = Modifier.testTag("web_search_switch")
                    )
                }
            }
        }

        // --- Question Field Input ---
        OutlinedTextField(
            value = queryText,
            onValueChange = { queryText = it },
            label = { Text("اطرح سؤالك أو استفسارك الشرعي...") },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("question_input_field"),
            shape = RoundedCornerShape(12.dp),
            trailingIcon = {
                if (queryText.isNotEmpty()) {
                    IconButton(onClick = { queryText = "" }) {
                        Icon(imageVector = Icons.Default.Clear, contentDescription = "مسح")
                    }
                }
            },
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                imeAction = androidx.compose.ui.text.input.ImeAction.Search
            )
        )

        // Action Trigger
        Button(
            onClick = {
                viewModel.askGemini(queryText)
            },
            enabled = queryText.isNotEmpty() && !isAiLoading,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("ask_ai_button"),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            shape = RoundedCornerShape(10.dp)
        ) {
            if (isAiLoading) {
                CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text("جاري استخلاص الأدلة والسياقات...")
            } else {
                Icon(imageVector = Icons.Default.Send, contentDescription = "إرسال")
                Spacer(modifier = Modifier.width(8.dp))
                Text("توليد الإجابة الموثقة بـ RAG", fontWeight = FontWeight.Bold)
            }
        }

        // --- AI Answer Panel ---
        if (aiAnswer.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            RubElHizbIcon(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.primary)
                            Text(
                                text = "الإجابة العلمية المنتجة",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(aiAnswer))
                                    Toast.makeText(context, "تم نسخ الإجابة!", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "نسخ الإجابة",
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            IconButton(
                                onClick = {
                                    if (aiAnswer.isNotEmpty()) {
                                        try {
                                            val sendIntent = android.content.Intent().apply {
                                                action = android.content.Intent.ACTION_SEND
                                                putExtra(android.content.Intent.EXTRA_TEXT, aiAnswer)
                                                type = "text/plain"
                                            }
                                            val shareIntent = android.content.Intent.createChooser(sendIntent, "مشاركة الإجابة الشرعية عبر...")
                                            context.startActivity(shareIntent)
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "تعذر تشغيل مشاركة النص", Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        Toast.makeText(context, "لا توجد إجابة لمشاركتها حالياً", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "مشاركة الإجابة",
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 10.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    )

                    // --- Google Search Grounding Banner (If active) ---
                    if (webQueries.isNotEmpty() || webSources.isNotEmpty()) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                            ),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = "البحث والاستقصاء النشط",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "البحث والاستقصاء الدقيق من الويب (Google)",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                
                                if (webQueries.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "الاستعلامات الرقمية: " + webQueries.joinToString(" • "),
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "تم تدقيق ومطابقة الأدلة",
                                        tint = Color(0xFF2E7D32),
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = "تم دمج وتحقيق النتائج مع الأدلة الرقمية لضمان أعلى موثوقية فوق المضمون المعرفي.",
                                        fontSize = 9.sp,
                                        color = Color(0xFF2E7D32),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }

                    // Actual ResponseText
                    Text(
                        text = aiAnswer,
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = 24.sp,
                        textAlign = TextAlign.Right,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // --- Referenced Sources Section ---
                    if (referencedContexts.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(18.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MenuBook,
                                contentDescription = "الأدلة المحققة",
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "المصادر المحلية الدقيقة المحققة (${referencedContexts.size}):",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(6.dp))
                        referencedContexts.forEachIndexed { i, source ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .background(
                                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(6.dp)
                                    )
                                    .padding(8.dp)
                            ) {
                                Text(
                                    text = "[${i+1}] ${source.title}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = source.content,
                                    fontSize = 11.sp,
                                    lineHeight = 16.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "المرجع: ${source.reference}",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Light,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }

                    // --- Web Grounding WebSources click-to-verify catalog ---
                    if (webSources.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(18.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = "روابط الويب المحققة",
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "مراجع ومصادر الويب الموثقة المستكشفة (${webSources.size}):",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(6.dp))
                        webSources.forEachIndexed { i, source ->
                            val sourceUri = source.uri ?: ""
                            val sourceTitle = source.title ?: "مصدر ويب خارجي"
                            
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable {
                                        if (sourceUri.isNotEmpty()) {
                                            try {
                                                uriHandler.openUri(sourceUri)
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "عذراً، تعذر فتح الرابط الخارجي", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                ),
                                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Launch,
                                        contentDescription = "فتح المصدر للتحقق",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = sourceTitle,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = sourceUri,
                                            fontSize = 9.sp,
                                            color = MaterialTheme.colorScheme.outline,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Storage Tab: Listing files in folders (Quran, Hadith, Fatawa, User Docs) under Android/data
 */
@Composable
fun StorageTabContent(
    viewModel: MainViewModel,
    onOpenImport: () -> Unit
) {
    val context = LocalContext.current
    val storedFiles by viewModel.storedFiles.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()

    var activeStorageSection by remember { mutableStateOf("quran") } // quran, hadith, fatawa, user_docs

    val sectionFiles = storedFiles[activeStorageSection] ?: emptyList()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // --- Intro banner to storage ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "نظام إدارة ملفات وتنسيقات الأندرويد",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "يتم حفظ ملفات المصادر داخل مسار التطبيق الرسمي المخصص ليفك الضغط ويصنفها بتسهيل تام حسب نوع المقالة ومسارها الشرعي الدقيق.",
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        // --- Demo Seeder & Manual Add Actions ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = {
                    viewModel.seedSampleFilesToAndroidData()
                    Toast.makeText(context, "تم توليد الملفات وتفريغ ارشيفات ZIP التجريبية في مسارات الذاكرة وتصنيفها وفهرستها تلقائياً!", Toast.LENGTH_LONG).show()
                },
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp)
                    .testTag("test_files_seed_button"),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(imageVector = Icons.Default.Download, contentDescription = "توليد")
                Spacer(modifier = Modifier.width(6.dp))
                Text("توليد ملفات وأرشيف ZIP تجريبي", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
                onClick = onOpenImport,
                modifier = Modifier
                    .weight(0.8f)
                    .height(46.dp)
                    .testTag("manual_add_button"),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "إضافة")
                Spacer(modifier = Modifier.width(4.dp))
                Text("تأليف مستند يدوي", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        // --- Section Selector Tabs ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .background(MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(8.dp))
                .padding(4.dp)
        ) {
            val sections = listOf(
                "quran" to "القرآن (quran/)",
                "hadith" to "الحديث (hadith/)",
                "fatawa" to "الفتاوى (fatawa/)",
                "user_docs" to "مستندات (user_docs/)"
            )
            for ((key, label) in sections) {
                val isSelected = activeStorageSection == key
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                        )
                        .clickable { activeStorageSection = key }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // --- Visual Files list in active category folder ---
        Text(
            text = "الملفات الفيزيائية المكتشفة بالمسار (${sectionFiles.size}):",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.outline
        )

        if (sectionFiles.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(10.dp)
                    )
                    .border(
                        BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                        shape = RoundedCornerShape(10.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderZip,
                        contentDescription = "ملفات فارغة",
                        tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = "المسار فارغ بالوقت الحالي.",
                        color = MaterialTheme.colorScheme.outline,
                        fontSize = 13.sp
                    )
                    Text(
                        text = "جرب النقر على 'توليد ملفات وأرشيف ZIP تجريبي' بالأعلى لملء وتقسيم وفهرسة المسار تلقائياً!",
                        color = MaterialTheme.colorScheme.outline,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(sectionFiles.size) { index ->
                    val file = sectionFiles[index]
                    FileRowItem(
                        file = file,
                        category = activeStorageSection,
                        onReindex = {
                            viewModel.indexFile(file, activeStorageSection)
                            Toast.makeText(context, "تم مسح وإعادة فهرسة الملف بالكامل بنجاح!", Toast.LENGTH_SHORT).show()
                        },
                        onDelete = {
                            viewModel.deleteStoredFile(file, activeStorageSection)
                            Toast.makeText(context, "تم حذف الملف وإلغاء فهرسته من قاعدة البيانات الدائمة!", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun FileRowItem(
    file: File,
    category: String,
    onReindex: () -> Unit,
    onDelete: () -> Unit
) {
    var expandedMenu by remember { mutableStateOf(false) }
    val format = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    val lastModified = format.format(Date(file.lastModified()))
    val sizeKb = String.format(Locale.US, "%.1f", file.length().toDouble() / 1024)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.Description,
                    contentDescription = "ملف نصي",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = file.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "الحجم: $sizeKb KB",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Box(
                            modifier = Modifier
                                .size(3.dp)
                                .background(MaterialTheme.colorScheme.outline, shape = RoundedCornerShape(50))
                        )
                        Text(
                            text = lastModified,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                // Quick Index action
                IconButton(
                    onClick = onReindex,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "إعادة فهرسة",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Delete action
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "حذف الملف",
                        tint = Color.Red,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

/**
 * HighlightableText: Highlights matching normalized words from search matches while keeping original formatting intact.
 */
@Composable
fun HighlightableText(
    text: String,
    query: String,
    style: androidx.compose.ui.text.TextStyle = androidx.compose.ui.text.TextStyle.Default,
    highlightColor: Color = MaterialTheme.colorScheme.primary,
    highlightBgColor: Color = highlightColor.copy(alpha = 0.22f),
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    modifier: Modifier = Modifier
) {
    if (query.isBlank()) {
        Text(text = text, style = style, maxLines = maxLines, overflow = overflow, modifier = modifier)
        return
    }

    val normalizedQueryWords = remember(query) {
        ArabicNlpHelper.stripTashkeel(ArabicNlpHelper.normalizeArabic(query))
            .split(Regex("\\s+"))
            .filter { it.length > 2 }
            .toSet()
    }

    if (normalizedQueryWords.isEmpty()) {
        Text(text = text, style = style, maxLines = maxLines, overflow = overflow, modifier = modifier)
        return
    }

    val annotatedString = remember(text, normalizedQueryWords) {
        androidx.compose.ui.text.buildAnnotatedString {
            val words = text.split(Regex("(?=\\s)|(?<=\\s)"))
            for (word in words) {
                if (word.isBlank()) {
                    append(word)
                    continue
                }
                val normalizedWord = ArabicNlpHelper.stripTashkeel(ArabicNlpHelper.normalizeArabic(word))
                val isMatch = normalizedQueryWords.any { qWord ->
                    normalizedWord.contains(qWord) || qWord.contains(normalizedWord)
                }
                
                if (isMatch) {
                    withStyle(
                        style = SpanStyle(
                            background = highlightBgColor,
                            color = highlightColor,
                            fontWeight = FontWeight.Bold
                        )
                    ) {
                        append(word)
                    }
                } else {
                    append(word)
                }
            }
        }
    }

    Text(text = annotatedString, style = style, maxLines = maxLines, overflow = overflow, modifier = modifier)
}

/**
 * Search Tab: Interactive Arabic exact & semantic search tester utilizing normalizations
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchTabContent(
    viewModel: MainViewModel,
    onTabChange: (String) -> Unit
) {
    val context = LocalContext.current
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isLateNightReading by viewModel.isLateNightReading.collectAsState()
    
    var showAdvancedParams by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // --- Header Description Stamp ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.12f)),
            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Verified,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Column {
                    Text(
                        text = "محرك البحث والتحقيق الدلالي المعياري",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "يقوم بالتدقيق التلقائي لنصوص الشريعة بتجاوز تباينات التشكيل والكتابة لضمان عثور فوري ومؤكد.",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        lineHeight = 14.sp
                    )
                }
            }
        }

        // --- Custom Header Search ---
        OutlinedTextField(
            value = searchQuery,
            onValueChange = {
                viewModel.searchQuery.value = it
                viewModel.performSearch()
            },
            placeholder = { Text("ابحث بالكلمة أو الآية أو الراوي (تجاهل التشكيل والهمزة)...") },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("search_text_field"),
            shape = RoundedCornerShape(12.dp),
            leadingIcon = {
                Icon(imageVector = Icons.Default.Search, contentDescription = "بحث")
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = {
                        viewModel.searchQuery.value = ""
                        viewModel.performSearch()
                    }) {
                        Icon(imageVector = Icons.Default.Clear, contentDescription = "مسح")
                    }
                }
            }
        )

        // --- Search History Component ---
        val recentQueries by viewModel.recentQueries.collectAsState()
        if (recentQueries.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "سجل البحث",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "عمليات البحث الأخيرة",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(
                        onClick = { viewModel.clearHistory() },
                        modifier = Modifier.size(24.dp).testTag("clear_history_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "حذف سجل البحث بالكامل",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // Scrollable row of search term chips
                androidx.compose.foundation.lazy.LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(recentQueries.size) { index ->
                        val term = recentQueries[index]
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
                            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)),
                            modifier = Modifier
                                .testTag("history_chip_$index")
                        ) {
                            Row(
                                modifier = Modifier
                                    .clickable {
                                        viewModel.searchQuery.value = term
                                        viewModel.performSearch()
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = term,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Box(
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clickable {
                                            viewModel.removeQueryFromHistory(term)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "حذف الكلمة",
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f),
                                        modifier = Modifier.size(10.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Advanced tuning parameters panel
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showAdvancedParams = !showAdvancedParams }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "تخصيص حساسية المطابقة الدلالية الشرعية",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Icon(
                        imageVector = if (showAdvancedParams) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (showAdvancedParams) "إخفاء" else "عرض",
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(18.dp)
                    )
                }
                
                if (showAdvancedParams) {
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    val useVectorSearch by viewModel.useVectorSearch.collectAsState()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "تفعيل البحث الدلالي الذكي (قاعدة المتجهات) 🧠",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "يستخدم خوارزمية المتجهات المحلية والاشتقاق الدلالي لمطابقة المعاني والقرائن حتى مع اختلاف الحروف والمترادفات.",
                                fontSize = 9.sp,
                                color = MaterialTheme.colorScheme.outline,
                                lineHeight = 13.sp
                            )
                        }
                        Switch(
                            checked = useVectorSearch,
                            onCheckedChange = {
                                viewModel.useVectorSearch.value = it
                                viewModel.performSearch()
                            },
                            modifier = Modifier.testTag("use_vector_search_switch")
                        )
                    }

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
                        modifier = Modifier.padding(vertical = 8.dp)
                    )

                    val relevanceThreshold by viewModel.relevanceThreshold.collectAsState()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (useVectorSearch) "الحد الأدنى لقوة المطابقة الدلالية (الجاذبية):" else "الحد الأدنى لقوة مطابقة النص العيني:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Box(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), shape = RoundedCornerShape(8.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = String.format(Locale.US, "%.0f%%", relevanceThreshold * 100),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Slider(
                        value = relevanceThreshold,
                        onValueChange = {
                            viewModel.relevanceThreshold.value = it
                            viewModel.performSearch()
                        },
                        valueRange = 0.05f..0.85f,
                        modifier = Modifier.testTag("search_threshold_slider")
                    )
                    Text(
                        text = if (useVectorSearch) "• خفض النسبة يسمح بتبويب النتائج ذات الترابط المعنوي البعيد، بينما رفعها يحصر البحث في النصوص الأكثر عمقاً ومطابقة للمبدأ الشرعي المطلوب." else "• خفض النسبة يعرض نتائج تقريبية ومتشابهة، بينما رفع النسبة يحافظ على دلالة عالية الدقة.",
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.outline,
                        lineHeight = 13.sp
                    )
                }
            }
        }

        // Segmented Category filtering system (e.g., Quran, Hadith, Fiqh/Fatawa)
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.FilterList,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "تصفية الفئات الشرعية والموضوعية (القرآن، الحديث، الفقه):",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val categories = listOf(
                    "all" to "الكل 🌐",
                    "quran" to "القرآن الكريم 📖",
                    "hadith" to "الحديث الشريف 📜",
                    "fatawa" to "الفقه والفتاوى ⚖️",
                    "user_docs" to "مستندات خاصة 📁"
                )
                for ((key, label) in categories) {
                    val isSelected = selectedCategory == key
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            viewModel.selectedCategory.value = key
                            viewModel.performSearch()
                        },
                        label = { Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        modifier = Modifier.height(32.dp)
                    )
                }
            }
        }

        VerticalDivider(modifier = Modifier.height(1.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))

        if (selectedCategory == "quran" || selectedCategory == "all" || isLateNightReading) {
            LateNightReadingControlPanel(viewModel = viewModel)
        }

        // Stats summary row
        if (searchResults.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "نتائج البحث المستكشفة والمطابقة: ${searchResults.size} مصادر",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Results listing
        if (searchResults.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "لا توجد نتائج مطابقة لطلبك الرقمي حالياً.\nيرجى تعديل العبارة أو خفض حد قوة المطابقة من لوحة الإعدادات أعلاه لفتح دائرة الاستكشاف.",
                    color = MaterialTheme.colorScheme.outline,
                    textAlign = TextAlign.Center,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(24.dp)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(searchResults.size) { index ->
                    val result = searchResults[index]
                    ScoredResultRow(scored = result, searchQuery = searchQuery, onTabChange = onTabChange, viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
fun ScoredResultRow(
    scored: ScoredResult,
    searchQuery: String,
    onTabChange: (String) -> Unit,
    viewModel: MainViewModel
) {
    val entity = scored.entity
    val percentage = (scored.score * 100).toInt()
    val badgeColor = when {
        percentage >= 75 -> Color(0xFF2E7D32) // Emerald green
        percentage >= 40 -> Color(0xFFD84315) // Deep Orange
        else -> MaterialTheme.colorScheme.outline
    }
    
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    val isLateNightReading by viewModel.isLateNightReading.collectAsState()
    val quranReadingFontSize by viewModel.quranReadingFontSize.collectAsState()
    val nightReadingTint by viewModel.nightReadingTint.collectAsState()

    val favoriteIds by viewModel.favoriteIds.collectAsState()
    val isFavorited = favoriteIds.contains(entity.id)

    val isQuran = entity.category == "quran"
    val showSpecialNightDesign = isLateNightReading && isQuran

    val comfortTextColor = if (showSpecialNightDesign) {
        when (nightReadingTint) {
            "amber" -> Color(0xFFFFDF9B)
            "sepia" -> Color(0xFFE2D6C5)
            "mint" -> Color(0xFFC5F7D9)
            "rose" -> Color(0xFFFBDCE5)
            else -> Color(0xFFFFDF9B)
        }
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    val comfortHighlightColor = if (showSpecialNightDesign) {
        Color(0xFFFFEE70)
    } else {
        MaterialTheme.colorScheme.primary
    }

    val comfortBorderColor = if (showSpecialNightDesign) {
        when (nightReadingTint) {
            "amber" -> Color(0xFFFFB52D).copy(alpha = 0.45f)
            "sepia" -> Color(0xFFD4C1A8).copy(alpha = 0.45f)
            "mint" -> Color(0xFF50C878).copy(alpha = 0.45f)
            "rose" -> Color(0xFFF0A5C0).copy(alpha = 0.45f)
            else -> Color(0xFFFFB52D).copy(alpha = 0.45f)
        }
    } else {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
    }

    val comfortCardBg = if (showSpecialNightDesign) {
        Color(0xFF0F1512)
    } else {
        MaterialTheme.colorScheme.surface
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = comfortCardBg),
        border = BorderStroke(1.dp, comfortBorderColor),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header: Category Title & Percentage & Favorite Heart Icon
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    val icon = when (entity.category) {
                        "quran" -> Icons.Default.MenuBook
                        "hadith" -> Icons.Default.BookmarkBorder
                        "fatawa" -> Icons.Default.Gavel
                        else -> Icons.Default.InsertDriveFile
                    }
                    Icon(
                        imageVector = icon,
                        contentDescription = "نوع المصدر",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    HighlightableText(
                        text = entity.title,
                        query = searchQuery,
                        style = androidx.compose.ui.text.TextStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary
                        ),
                        highlightColor = MaterialTheme.colorScheme.secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Match percentage badge
                    Box(
                        modifier = Modifier
                            .background(badgeColor.copy(alpha = 0.12f), shape = RoundedCornerShape(12.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "تطابق: $percentage%",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = badgeColor
                        )
                    }

                    // Header Share & Favorite Heart Icons
                    IconButton(
                        onClick = {
                            try {
                                val sendIntent = android.content.Intent().apply {
                                    action = android.content.Intent.ACTION_SEND
                                    putExtra(android.content.Intent.EXTRA_TEXT, "${entity.content}\n[المصدر: ${entity.reference}]")
                                    type = "text/plain"
                                }
                                val shareIntent = android.content.Intent.createChooser(sendIntent, "مشاركة المضمون الشرعي...")
                                context.startActivity(shareIntent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "تعذر تشغيل المشاركة", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier
                            .size(34.dp)
                            .testTag("header_share_btn_${entity.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "مشاركة المضمون الشرعي",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Header Favorite Heart Icon
                    IconButton(
                        onClick = {
                            viewModel.toggleFavorite(entity.id)
                            val msg = if (isFavorited) "تم الإزالة من المفضلة" else "تم الإضافة إلى المفضلة"
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .size(34.dp)
                            .testTag("header_fav_btn_${entity.id}")
                    ) {
                        Icon(
                            imageVector = if (isFavorited) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = if (isFavorited) "إزالة من المفضلة" else "حفظ في المفضلة",
                            tint = if (isFavorited) Color(0xFFD32F2F) else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Body content area with dedicated separated design for Hadith
            if (entity.category == "hadith") {
                val (narratorChain, hadithBody) = remember(entity.content) {
                    val delimiter = ": "
                    val firstColonIndex = entity.content.indexOf(delimiter)
                    if (firstColonIndex != -1) {
                        val path1 = entity.content.substring(0, firstColonIndex).trim()
                        val path2 = entity.content.substring(firstColonIndex + delimiter.length).trim()
                        Pair(path1, path2)
                    } else {
                        val alternativeColon = ":"
                        val altColonIndex = entity.content.indexOf(alternativeColon)
                        if (altColonIndex != -1 && altColonIndex < 120) {
                            Pair(
                                entity.content.substring(0, altColonIndex).trim(),
                                entity.content.substring(altColonIndex + 1).trim()
                            )
                        } else {
                            Pair("", entity.content)
                        }
                    }
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 1. Narrator Chain Layer (السند والراوي)
                    if (narratorChain.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .border(
                                    width = 0.5.dp,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = "سند الحديث والراوي",
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.82f),
                                modifier = Modifier.size(14.dp)
                            )
                            HighlightableText(
                                text = narratorChain,
                                query = searchQuery,
                                style = androidx.compose.ui.text.TextStyle(
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f)
                                ),
                                highlightColor = comfortHighlightColor,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // 2. Prophetic Text Core Layer (متن الحديث الشريف) - highlighted with custom quotes styling
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.05f)
                        ),
                        border = BorderStroke(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FormatQuote,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "متن الحديث النبوي الشريف",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(6.dp))

                            HighlightableText(
                                text = hadithBody,
                                query = searchQuery,
                                style = androidx.compose.ui.text.TextStyle(
                                    fontSize = 13.5.sp,
                                    lineHeight = 22.sp,
                                    color = comfortTextColor,
                                    textAlign = TextAlign.Right,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = FontFamily.Serif
                                ),
                                highlightColor = comfortHighlightColor,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            } else {
                // Original highlighting content for other categories (Quran, Fatawa, User Docs)
                val customTextSize = if (showSpecialNightDesign) quranReadingFontSize.sp else 13.sp
                val customLineHeight = if (showSpecialNightDesign) (quranReadingFontSize * 1.55f).sp else 21.sp

                HighlightableText(
                    text = entity.content,
                    query = searchQuery,
                    style = androidx.compose.ui.text.TextStyle(
                        fontSize = customTextSize,
                        lineHeight = customLineHeight,
                        color = comfortTextColor,
                        textAlign = TextAlign.Right,
                        fontFamily = if (isQuran) FontFamily.Serif else FontFamily.Default
                    ),
                    highlightColor = comfortHighlightColor,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Verified Badge / Info & Source Reference Layer
            if (entity.category == "hadith") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MenuBook,
                            contentDescription = "المصدر والتخريج",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "التخريج والمصدر: ${entity.reference}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF2E7D32).copy(alpha = 0.12f), shape = RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "حديث صحيح موثق",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF2E7D32)
                        )
                    }
                }
            } else {
                // Original Verified Badge / Info Strip
                val stampText = when (entity.category) {
                    "quran" -> "تم التخريج: نص قرآني معتمد ومراجع"
                    "fatawa" -> "مدقق: حكم فقهي مع وثوقية الفتوى"
                    else -> "معالج: مستند رقمي محمل ومعقم للبحث"
                }
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), shape = RoundedCornerShape(6.dp))
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Verified,
                        contentDescription = "توثيق شرعي",
                        tint = Color(0xFF2E7D32),
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = stampText,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF2E7D32)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Original Meta Info: Category & References for other types
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val catArabic = when (entity.category) {
                        "quran" -> "القرآن الكريم"
                        "fatawa" -> "الفتاوى والأحكام"
                        else -> "مستند مستخدم"
                    }
                    Text(
                        text = "التصنيف: $catArabic",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Light,
                        color = MaterialTheme.colorScheme.outline
                    )

                    Text(
                        text = "المرجع: ${entity.reference}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Light,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            if (entity.category == "quran") {
                val quranProgressMap by viewModel.quranProgressMap.collectAsState()
                val currentProgress = quranProgressMap[entity.id]
                val status = currentProgress?.status ?: "unread"
                
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f), shape = RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "متابعة التلاوة والحفظ 📖",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf(
                            Triple("unread", "جديد ⚪", MaterialTheme.colorScheme.outline),
                            Triple("in_progress", "قيد القراءة ⏳", Color(0xFFD84315)),
                            Triple("read", "تمت القراءة ✓", Color(0xFF2E7D32))
                        ).forEach { (stateKey, stateLabel, color) ->
                            val isSelected = status == stateKey
                            val bgAlpha = if (isSelected) 0.2f else 0.05f
                            val borderThickness = if (isSelected) 1.2.dp else 0.5.dp
                            Box(
                                modifier = Modifier
                                    .background(color.copy(alpha = bgAlpha), shape = RoundedCornerShape(8.dp))
                                    .border(borderThickness, if (isSelected) color else color.copy(alpha = 0.2f), shape = RoundedCornerShape(8.dp))
                                    .clickable {
                                        viewModel.updateQuranProgress(entity.id, stateKey, currentProgress?.notes ?: "")
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = stateLabel,
                                    fontSize = 9.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) color else color.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
            Spacer(modifier = Modifier.height(8.dp))

            // Action Buttons (Full Touch Target compliant)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            val shariahContext = when (entity.category) {
                                "quran" -> "الآية الكريمة"
                                "hadith" -> "الحديث الشريف"
                                "fatawa" -> "الفتوى والضابط الفقهي"
                                else -> "المستند الشرعي"
                            }
                            viewModel.pendingChatQuery.value = "اشرح لي بالتفصيل سياق وأحكام ودلالات ومستنبطات $shariahContext التالي:\n\n${entity.content}\n\nالمرجع: ${entity.reference}"
                            onTabChange("chat")
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            contentColor = MaterialTheme.colorScheme.primary
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .height(34.dp)
                            .testTag("ai_explain_button")
                    ) {
                        Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("استنباط وشرح الذكاء", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }

                    if (entity.category == "quran") {
                        val currentAudioEntity by viewModel.currentAudioEntity.collectAsState()
                        val isAudioPlaying by viewModel.isAudioPlaying.collectAsState()
                        val isAudioBuffering by viewModel.isAudioBuffering.collectAsState()
                        val isThisPlaying = currentAudioEntity?.id == entity.id

                        Button(
                            onClick = { viewModel.playQuranEntity(entity) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isThisPlaying) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f),
                                contentColor = if (isThisPlaying) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .height(34.dp)
                                .testTag("quran_play_btn_${entity.id}")
                        ) {
                            if (isThisPlaying && isAudioBuffering) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(12.dp),
                                    strokeWidth = 1.5.dp,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                            } else {
                                Icon(
                                    imageVector = if (isThisPlaying && isAudioPlaying) Icons.Default.Pause else Icons.Default.VolumeUp,
                                    contentDescription = "استماع للتلاوة",
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isThisPlaying && isAudioPlaying) "إيقاف مؤقت" else if (isThisPlaying && isAudioBuffering) "جاري التحميل..." else "تلاوة",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    IconButton(
                        onClick = {
                            viewModel.toggleFavorite(entity.id)
                            val msg = if (isFavorited) "تم الإزالة من المفضلة" else "تم الإضافة إلى المفضلة"
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(34.dp).testTag("fav_btn_${entity.id}")
                    ) {
                        Icon(
                            imageVector = if (isFavorited) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = "حفظ في المفضلة",
                            tint = if (isFavorited) Color(0xFFD32F2F) else MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString("${entity.content}\n[المصدر: ${entity.reference}]"))
                            Toast.makeText(context, "تم نسخ النص والمرجع!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "نسخ النص الموثق",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            try {
                                val sendIntent = android.content.Intent().apply {
                                    action = android.content.Intent.ACTION_SEND
                                    putExtra(android.content.Intent.EXTRA_TEXT, "${entity.content}\n[المصدر: ${entity.reference}]")
                                    type = "text/plain"
                                }
                                val shareIntent = android.content.Intent.createChooser(sendIntent, "مشاركة المضمون الشرعي...")
                                context.startActivity(shareIntent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "تعذر تشغيل المشاركة", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "مشاركة المضمون",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Configurations Customizations Dialog: Let users customize persona, temperature and similarity thresholds
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TuningsDialog(
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val shariahPersona by viewModel.shariahPersona.collectAsState()
    val temperature by viewModel.temperature.collectAsState()
    val relevanceThreshold by viewModel.relevanceThreshold.collectAsState()
    val useLocalDataOnly by viewModel.useLocalDataOnly.collectAsState()
    val customSystemPrompt by viewModel.customSystemPrompt.collectAsState()
    val customApiKey by viewModel.customApiKey.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()

    var editingPrompt by remember { mutableStateOf(customSystemPrompt) }
    var editingApiKey by remember { mutableStateOf(customApiKey) }
    var apiKeyVisibility by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "تخصيص الخوارزمية والتوليد ⚙️",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Right
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // --- Persona Choice ---
                Text("منهج التموضع الشرعي للذكاء (Shariah Persona):", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val list = listOf(
                        "balanced" to "باحث إسلامي متوازن (وسطية واعتدال)",
                        "fiqh" to "مفتي أو باحث فقهي دقيق وموثق",
                        "spiritual" to "واعظ روحي وتزكية ورقائق",
                        "tafsir" to "شرح لغوي وبلاغة قرآنية"
                    )
                    for ((key, label) in list) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.shariahPersona.value = key
                                }
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            RadioButton(selected = shariahPersona == key, onClick = { viewModel.shariahPersona.value = key })
                            Text(label, fontSize = 11.sp)
                        }
                    }
                }

                HorizontalDivider()

                // --- Theme Selection ---
                Text("مظهر التطبيق والقراءة الليلية (Theme & Reading Mode):", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val themeList = listOf(
                        "system" to "تلقائي حسب النظام (System Theme)",
                        "light" to "الوضع المضيء - ورق البردي (Parchment)",
                        "dark" to "الوضع الداكن - غابة داكنة (Forest Obsidian)",
                        "high_contrast" to "قراءة ليلية فائقة التباين - سواد خالص (OLED Black 🖤)"
                    )
                    for ((key, label) in themeList) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.themeMode.value = key
                                }
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            RadioButton(
                                selected = themeMode == key,
                                onClick = { viewModel.themeMode.value = key },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = if (key == "high_contrast") Color(0xFF2AFF96) else MaterialTheme.colorScheme.primary
                                )
                            )
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = if (themeMode == key) FontWeight.Bold else FontWeight.Normal,
                                color = if (themeMode == key) {
                                    if (key == "high_contrast") Color(0xFF2AFF96) else MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )
                        }
                    }
                }

                HorizontalDivider()

                // --- Temperature ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("حرارة التوليد (LLM Temperature):", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("حرارة منخفضة تضمن توافقاً صارماً مع المصادر ودقة استشهادية.", fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                    }
                    Text(text = String.format(Locale.US, "%.1f", temperature), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
                Slider(
                    value = temperature,
                    onValueChange = { viewModel.temperature.value = it },
                    valueRange = 0.0f..1.0f,
                    steps = 10
                )

                HorizontalDivider()

                // --- Relevance Threshold ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("حد تطابق السياق (Context Score Threshold):", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("لتحديد وتجاهل الفقرات غير المتعلقة بالسؤال لعدم تشتيت الذكاء.", fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                    }
                    Text(text = String.format(Locale.US, "%.2f", relevanceThreshold), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                }
                Slider(
                    value = relevanceThreshold,
                    onValueChange = { viewModel.relevanceThreshold.value = it },
                    valueRange = 0.0f..0.8f,
                    steps = 8
                )

                HorizontalDivider()

                // --- Local only flag ---
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { viewModel.useLocalDataOnly.value = !useLocalDataOnly },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Checkbox(checked = useLocalDataOnly, onCheckedChange = { viewModel.useLocalDataOnly.value = it })
                    Column {
                        Text("البحث في الملفات والبيانات المحلية فقط", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("يمنع تماماً أي استنتاجات عامة من خوارزميات الذكاء الاصطناعي خارج الملفات المكتسبة.", fontSize = 10.sp, color = Color.Red)
                    }
                }

                HorizontalDivider()

                // --- Vector Search preference switch ---
                val useVectorSearch by viewModel.useVectorSearch.collectAsState()
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { viewModel.useVectorSearch.value = !useVectorSearch },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Checkbox(checked = useVectorSearch, onCheckedChange = { viewModel.useVectorSearch.value = it })
                    Column {
                        Text("تفعيل محرك البحث الدلالي وقاعدة المتجهات 🧠", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("يتيح استخدام خوارزميات الجبر الخطي لتسريع وإيجاد المرادفات اللغوية والتفسير المقاصدي للنصوص الشرعية المحلية.", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }

                HorizontalDivider()

                // --- Custom system instruction addition ---
                Text("تخصيص تعليمات إضافية للنظام (Custom System Prompt Override):", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = editingPrompt,
                    onValueChange = {
                        editingPrompt = it
                        viewModel.customSystemPrompt.value = it
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("مثال: ركز على المذهب الحنبلي، دقق النحوية والبيان...") },
                    shape = RoundedCornerShape(8.dp),
                    maxLines = 4
                )

                HorizontalDivider()

                // --- User Personal API Key input ---
                Text("مفتاح API لـ Gemini المخصص (Gemini API Key):", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = editingApiKey,
                    onValueChange = {
                        editingApiKey = it
                        viewModel.customApiKey.value = it
                    },
                    modifier = Modifier.fillMaxWidth().testTag("custom_api_key_field"),
                    placeholder = { Text("أدخل مفتاح Gemini الخاص بك (يبدأ بـ AIza...)") },
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true,
                    visualTransformation = if (apiKeyVisibility) {
                        androidx.compose.ui.text.input.VisualTransformation.None
                    } else {
                        androidx.compose.ui.text.input.PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (editingApiKey.isNotEmpty()) {
                                IconButton(onClick = {
                                    editingApiKey = ""
                                    viewModel.customApiKey.value = ""
                                }) {
                                    Icon(imageVector = Icons.Default.Clear, contentDescription = "مسح")
                                }
                            }
                            IconButton(onClick = { apiKeyVisibility = !apiKeyVisibility }) {
                                Icon(
                                    imageVector = if (apiKeyVisibility) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = if (apiKeyVisibility) "إخفاء المفتاح" else "إظهار المفتاح"
                                )
                            }
                        }
                    },
                    supportingText = {
                        Text("ملاحظة: إذا تركته فارغاً، فسيستخدم التطبيق مفتاح النظام المدمج تلقائياً لضمان استمرارية الخدمة.", fontSize = 9.sp, color = MaterialTheme.colorScheme.outline)
                    }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("تطبيق وحفظ التعديلات", fontWeight = FontWeight.Bold)
            }
        }
    )
}

/**
 * Manual file composition Dialog
 */
@Composable
fun ImportFileDialog(
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    var categorySelection by remember { mutableStateOf("user_docs") }
    var filenameText by remember { mutableStateOf("") }
    var textBody by remember { mutableStateOf("") }

    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "مؤلف المستندات اليدوية 📝",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Right
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("اختر القسم والمسار لتقسيم وتخزين الملف:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                
                // Section tabs mapping
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .background(MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(6.dp))
                        .padding(4.dp)
                ) {
                    val list = listOf(
                        "quran" to "قرآن",
                        "hadith" to "حديث",
                        "fatawa" to "فتاوى",
                        "user_docs" to "مستندات"
                    )
                    for ((key, label) in list) {
                        val isSel = categorySelection == key
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (isSel) MaterialTheme.colorScheme.primary else Color.Transparent)
                                .clickable { categorySelection = key }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(label, color = if (isSel) Color.White else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                        }
                    }
                }

                // File name
                Text("اسم الملف (مثال: my_notes.txt):", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = filenameText,
                    onValueChange = { filenameText = it },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true
                )

                // Body content
                Text("مضمون النص (اقسم الفقرات بسطر فارغ مزدوج لترسخ كفقرات منفصلة):", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = textBody,
                    onValueChange = { textBody = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    shape = RoundedCornerShape(8.dp),
                    placeholder = { Text("اكتب المستند أو المضمون هنا...") }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cleanFilename = if (filenameText.endsWith(".txt")) filenameText else "$filenameText.txt"
                    if (filenameText.trim().isEmpty() || textBody.trim().isEmpty()) {
                        Toast.makeText(context, "الرجاء تعبئة كافة الحقول أولاً!", Toast.LENGTH_SHORT).show()
                    } else {
                        viewModel.importCustomFile(categorySelection, cleanFilename, textBody)
                        Toast.makeText(context, "تم تخزين وفهرسة المستند اليدوي بالمسار بنجاح!", Toast.LENGTH_LONG).show()
                        onDismiss()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("تخزين وفهرسة بالمسار")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء الأمر", color = Color.Red)
            }
        }
    )
}

/**
 * Favorites Tab: Manage saved offline scriptures, hadiths, or fatawa with rich UI and search utilities
 */
@Composable
fun FavoritesTabContent(
    viewModel: MainViewModel,
    onTabChange: (String) -> Unit
) {
    val favoritesList by viewModel.favoritesList.collectAsState()
    val isLateNightReading by viewModel.isLateNightReading.collectAsState()
    var categoryFilter by remember { mutableStateOf("all") }
    var favoritesSearchQuery by remember { mutableStateOf("") }

    // Filtered list based on selected category and text query
    val filteredList = remember(favoritesList, categoryFilter, favoritesSearchQuery) {
        favoritesList.filter { entity ->
            val matchesCategory = categoryFilter == "all" || entity.category == categoryFilter
            val matchesSearch = favoritesSearchQuery.isBlank() || 
                entity.content.contains(favoritesSearchQuery, ignoreCase = true) ||
                entity.title.contains(favoritesSearchQuery, ignoreCase = true) ||
                entity.reference.contains(favoritesSearchQuery, ignoreCase = true)
            matchesCategory && matchesSearch
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // --- Header Stamp ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)),
            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = null,
                    tint = Color(0xFFD32F2F),
                    modifier = Modifier.size(24.dp)
                )
                Column {
                    Text(
                        text = "المحفوظات والمفضلة الشرعية المختارة",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "هنا تحفظ النصوص والآيات والفتواى المدققة الخاصة بك للرجوع السريع والأحكام غير المتصلة بالإنترنت.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                        lineHeight = 15.sp
                    )
                }
            }
        }

        if (favoritesList.isNotEmpty()) {
            // Local search within Favorites
            OutlinedTextField(
                value = favoritesSearchQuery,
                onValueChange = { favoritesSearchQuery = it },
                label = { Text("البحث السريع في المفضلة...", fontSize = 11.sp) },
                leadingIcon = {
                    Icon(imageVector = Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                },
                trailingIcon = {
                    if (favoritesSearchQuery.isNotEmpty()) {
                        IconButton(onClick = { favoritesSearchQuery = "" }) {
                            Icon(imageVector = Icons.Default.Clear, contentDescription = "مسح الكلمة", modifier = Modifier.size(16.dp))
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("favorites_search_field"),
                shape = RoundedCornerShape(10.dp),
                singleLine = true
            )

            // Segmented Category filters in favorites
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.FilterList,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "تصنيف المحفوظات الشرعية (القرآن، الحديث، الفقه):",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val categories = listOf(
                        "all" to "الكل المفضل 🌐",
                        "quran" to "القرآن الكريم 📖",
                        "hadith" to "الحديث الشريف 📜",
                        "fatawa" to "الفقه والفتاوى ⚖️",
                        "user_docs" to "مستندات خاصة 📁"
                    )

                    for ((key, label) in categories) {
                        val isSelected = categoryFilter == key
                        FilterChip(
                            selected = isSelected,
                            onClick = { categoryFilter = key },
                            label = { Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                            modifier = Modifier.testTag("fav_chip_$key")
                        )
                    }
                }
            }
        }

        if (categoryFilter == "quran" || categoryFilter == "all" || isLateNightReading) {
            LateNightReadingControlPanel(viewModel = viewModel)
        }

        // List Display
        if (filteredList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f), shape = RoundedCornerShape(12.dp))
                    .border(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f), shape = RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.FavoriteBorder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = if (favoritesList.isEmpty()) 
                            "المفضلة فارغة تماماً حالياً.\nاضغط على أيقونة القلب ❤️ بالنتائج الشرعية لحفظها هنا لفتح الوصول الفوري بلا شبكة."
                        else 
                            "لا توجد نتائج مطابقة لبحثك ومصنفك في المفضلة.",
                        color = MaterialTheme.colorScheme.outline,
                        textAlign = TextAlign.Center,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }
            }
        } else {
            androidx.compose.foundation.lazy.LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredList.size) { index ->
                    val entity = filteredList[index]
                    ScoredResultRow(
                        scored = ScoredResult(entity, 1.0),
                        searchQuery = favoritesSearchQuery,
                        onTabChange = onTabChange,
                        viewModel = viewModel
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuranProgressTabContent(
    viewModel: MainViewModel,
    onTabChange: (String) -> Unit
) {
    val quranContentList by viewModel.quranContentList.collectAsState()
    val quranProgressMap by viewModel.quranProgressMap.collectAsState()

    val totalChapters = quranContentList.size
    val completedCount = quranContentList.count { quranProgressMap[it.id]?.status == "read" }
    val inProgressCount = quranContentList.count { quranProgressMap[it.id]?.status == "in_progress" }
    val progressPercent = if (totalChapters > 0) (completedCount.toFloat() / totalChapters) else 0f

    // Find the last resume chapter (either in_progress or based on latest update)
    val latestActiveChapter = remember(quranContentList, quranProgressMap) {
        val activeIds = quranProgressMap.filter { it.value.status == "in_progress" || it.value.status == "read" }
        if (activeIds.isNotEmpty()) {
            val lastUpdatedId = activeIds.values.maxByOrNull { it.lastUpdated }?.id
            quranContentList.find { it.id == lastUpdatedId }
        } else {
            null
        }
    }

    var textSearchFilter by remember { mutableStateOf("") }
    var statusSelectionFilter by remember { mutableStateOf("all") } // "all", "unread", "in_progress", "read"

    val displayedList = remember(quranContentList, quranProgressMap, textSearchFilter, statusSelectionFilter) {
        quranContentList.filter { chapter ->
            val progress = quranProgressMap[chapter.id]
            val status = progress?.status ?: "unread"
            
            val matchesSearch = textSearchFilter.isBlank() || 
                chapter.title.contains(textSearchFilter, ignoreCase = true) ||
                chapter.content.contains(textSearchFilter, ignoreCase = true) ||
                chapter.reference.contains(textSearchFilter, ignoreCase = true)
                
            val matchesStatus = statusSelectionFilter == "all" || status == statusSelectionFilter
            
            matchesSearch && matchesStatus
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // --- Header Title ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = "متابعة الختمة وتدبر القرآن",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        // --- Stats Dashboard Card ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "معدل إنجاز الختم الشرعي",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "تمت قراءة $completedCount من أصل $totalChapters سورة/مواد شرعية وباقي $inProgressCount قيد المراجعة.",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = String.format(Locale.US, "%.0f%%", progressPercent * 100),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                LinearProgressIndicator(
                    progress = { progressPercent },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val progressSummaryLabel = when {
                        progressPercent >= 1.0f -> "اللهم لك الحمد! أكملت ختمة كاملة 🎉"
                        progressPercent >= 0.5f -> "همة عالية! أنجزت أكثر من نصف الورد 🌟"
                        progressPercent > 0.0f -> "بداية مباركة! استمر بالتلاوة بانتظام 📖"
                        else -> "ابدأ وردك اليومي الآن وسجل تقدمك المبارك."
                    }
                    Text(
                        text = progressSummaryLabel,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // --- Resume Reading Bar ---
        if (latestActiveChapter != null) {
            val status = quranProgressMap[latestActiveChapter.id]?.status ?: "unread"
            val statusLabel = if (status == "in_progress") "قيد القراءة ⏳" else "تمت قراءتها ✓"
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        viewModel.searchQuery.value = latestActiveChapter.title
                        viewModel.selectedCategory.value = "quran"
                        viewModel.performSearch()
                        onTabChange("search")
                    },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
                border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.primary)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Book,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text(
                                text = "مواصلة القراءة والاستماع الشرعي 📖",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "${latestActiveChapter.title} (${latestActiveChapter.reference}) • $statusLabel",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "انتقل للمصحف",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowLeft,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // --- Filters Section ---
        OutlinedTextField(
            value = textSearchFilter,
            onValueChange = { textSearchFilter = it },
            label = { Text("البحث السريع في السور والأوراد...", fontSize = 11.sp) },
            leadingIcon = {
                Icon(imageVector = Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
            },
            trailingIcon = {
                if (textSearchFilter.isNotEmpty()) {
                    IconButton(onClick = { textSearchFilter = "" }) {
                        Icon(imageVector = Icons.Default.Clear, contentDescription = "مسح", modifier = Modifier.size(16.dp))
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("tracker_search_field"),
            shape = RoundedCornerShape(10.dp),
            singleLine = true
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val statusFilters = listOf(
                "all" to "الكل",
                "unread" to "غير مقروء ⚪",
                "in_progress" to "قيد القراءة ⏳",
                "read" to "مكتمل ✓"
            )

            for ((key, label) in statusFilters) {
                val isSelected = statusSelectionFilter == key
                FilterChip(
                    selected = isSelected,
                    onClick = { statusSelectionFilter = key },
                    label = { Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                    modifier = Modifier.testTag("tracker_chip_$key")
                )
            }
        }

        // --- Chapters/Passages List ---
        if (displayedList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f), shape = RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "لا توجد أوراد أو سور مطابقة للمصنف أو البحث المطلوب.",
                    color = MaterialTheme.colorScheme.outline,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(displayedList.size) { index ->
                    val chapter = displayedList[index]
                    val progress = quranProgressMap[chapter.id]
                    val status = progress?.status ?: "unread"
                    var notesText by remember(progress?.notes) { mutableStateOf(progress?.notes ?: "") }
                    var isExpanded by remember { mutableStateOf(false) }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("tracker_card_${chapter.id}"),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(
                            width = if (status != "unread") 1.2.dp else 0.5.dp,
                            color = when (status) {
                                "read" -> Color(0xFF2E7D32)
                                "in_progress" -> Color(0xFFD84315)
                                else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                            }
                        ),
                        colors = CardDefaults.cardColors(
                            containerColor = when (status) {
                                "read" -> Color(0xFF2E7D32).copy(alpha = 0.03f)
                                "in_progress" -> Color(0xFFD84315).copy(alpha = 0.03f)
                                else -> MaterialTheme.colorScheme.surface
                            }
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .clickable { isExpanded = !isExpanded }
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = chapter.title,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        textAlign = TextAlign.Right
                                    )
                                    Text(
                                        text = chapter.reference,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }

                                // Interactive Segmented Buttons for quick click
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    listOf(
                                        Triple("unread", "جديد ⚪", MaterialTheme.colorScheme.outline),
                                        Triple("in_progress", "⏳", Color(0xFFD84315)),
                                        Triple("read", "✓", Color(0xFF2E7D32))
                                    ).forEach { (stateKey, labelStr, color) ->
                                        val isCurrent = status == stateKey
                                        Box(
                                            modifier = Modifier
                                                .background(
                                                    if (isCurrent) color.copy(alpha = 0.18f) else Color.Transparent,
                                                    shape = RoundedCornerShape(6.dp)
                                                )
                                                .border(
                                                    0.5.dp,
                                                    if (isCurrent) color else color.copy(alpha = 0.15f),
                                                    shape = RoundedCornerShape(6.dp)
                                                )
                                                .clickable {
                                                    viewModel.updateQuranProgress(chapter.id, stateKey, notesText)
                                                }
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = labelStr,
                                                fontSize = 9.sp,
                                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isCurrent) color else color.copy(alpha = 0.5f)
                                            )
                                        }
                                    }
                                }
                            }

                            // Collapsible/Expandable reading details & Tafsir notes capturing
                            if (isExpanded) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = chapter.content,
                                    fontSize = 12.sp,
                                    lineHeight = 18.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontFamily = FontFamily.Serif,
                                    textAlign = TextAlign.Right,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f), shape = RoundedCornerShape(6.dp))
                                        .padding(8.dp)
                                )

                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = notesText,
                                        onValueChange = {
                                            notesText = it
                                            viewModel.updateQuranProgress(chapter.id, status, it)
                                        },
                                        label = { Text("ملاحظات التدبر والحفظ...", fontSize = 9.sp) },
                                        placeholder = { Text("اكتب هنا خواطر وتدبرك أو رقم الآية للتوقف لاحقاً...", fontSize = 9.sp) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("tracker_notes_${chapter.id}"),
                                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 10.sp),
                                        shape = RoundedCornerShape(8.dp),
                                        singleLine = false,
                                        maxLines = 3
                                    )
                                    // Save Button
                                    Button(
                                        onClick = {
                                            viewModel.updateQuranProgress(chapter.id, status, notesText)
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), contentColor = MaterialTheme.colorScheme.primary),
                                        modifier = Modifier.height(40.dp)
                                    ) {
                                        Text("حفظ", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            } else {
                                // Inform user they can click to expand
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (notesText.isNotEmpty()) {
                                        Text(
                                            text = "📝 ملاحظة: $notesText",
                                            fontSize = 9.sp,
                                            color = MaterialTheme.colorScheme.primary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                    } else {
                                        Text(
                                            text = "انقر لعرض محتوى الآيات وتدوين التدبر...",
                                            fontSize = 8.sp,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                    
                                    Icon(
                                        imageVector = Icons.Default.ExpandMore,
                                        contentDescription = "عرض التفاصيل والتدبر",
                                        tint = MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AudioRecitationPlayerBar(viewModel: MainViewModel) {
    val currentAudioEntity by viewModel.currentAudioEntity.collectAsState()
    if (currentAudioEntity == null) return

    val entity = currentAudioEntity!!
    val isPlaying by viewModel.isAudioPlaying.collectAsState()
    val isBuffering by viewModel.isAudioBuffering.collectAsState()
    val progress by viewModel.audioProgress.collectAsState()
    val duration by viewModel.audioDuration.collectAsState()
    val position by viewModel.audioPosition.collectAsState()
    val currentVerseIdx by viewModel.currentVerseIndex.collectAsState()
    val totalVerses by viewModel.totalVersesInEntity.collectAsState()
    val selectedReciter by viewModel.selectedReciter.collectAsState()

    var showReciterDialog by remember { mutableStateOf(false) }

    if (showReciterDialog) {
        ReciterSelectionDialog(
            onDismiss = { showReciterDialog = false },
            viewModel = viewModel
        )
    }

    fun formatMs(ms: Int): String {
        val totalSecs = ms / 1000
        val mins = totalSecs / 60
        val secs = totalSecs % 60
        return String.format("%d:%02d", mins, secs)
    }

    val activeVerseRaw = viewModel.getActiveVerseRawNumber()

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("audio_player_bar"),
        tonalElevation = 6.dp,
        shadowElevation = 10.dp,
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatMs(position),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Slider(
                    value = progress,
                    onValueChange = { viewModel.seekAudio(it) },
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                        .height(14.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                    )
                )

                Text(
                    text = formatMs(duration),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = { viewModel.stopAudio() },
                        modifier = Modifier.size(36.dp).testTag("stop_audio_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "إغلاق المشغل",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = { viewModel.skipPrevious() },
                        enabled = currentVerseIdx > 0,
                        modifier = Modifier.size(36.dp).testTag("prev_audio_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "الآية السابقة"
                        )
                    }

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(38.dp)
                    ) {
                        if (isBuffering) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            FilledIconButton(
                                onClick = { viewModel.togglePlayPause() },
                                modifier = Modifier.size(36.dp).testTag("play_pause_audio_button"),
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaying) "إيقاف مؤقت" else "تشغيل"
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = { viewModel.skipNext() },
                        enabled = currentVerseIdx + 1 < totalVerses,
                        modifier = Modifier.size(36.dp).testTag("next_audio_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = "الآية التالية"
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f).padding(start = 12.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (isPlaying && !isBuffering) {
                            Text(
                                text = "🔊",
                                fontSize = 11.sp,
                                modifier = Modifier.padding(bottom = 2.dp)
                            )
                        }
                        
                        Text(
                            text = if (activeVerseRaw != null) "${entity.title} • الآية $activeVerseRaw" else entity.title,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.clickable { showReciterDialog = true }
                    ) {
                        Icon(
                            imageVector = Icons.Default.RecordVoiceOver,
                            contentDescription = "تغيير القارئ",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = "القارئ: ${selectedReciter.nameAr}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.testTag("reciter_selector_trigger")
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ReciterSelectionDialog(
    onDismiss: () -> Unit,
    viewModel: MainViewModel
) {
    val reciters = viewModel.availableReciters
    val selected by viewModel.selectedReciter.collectAsState()

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("إغلاق")
            }
        },
        title = {
            Text(
                "اختر القارئ المفضل للتلاوة",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Right
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (reciter in reciters) {
                    val isCurrent = reciter.id == selected.id
                    Surface(
                        onClick = {
                            viewModel.selectReciter(reciter)
                            onDismiss()
                        },
                        shape = RoundedCornerShape(8.dp),
                        color = if (isCurrent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                        modifier = Modifier.fillMaxWidth().testTag("reciter_item_${reciter.id}")
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isCurrent) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "محدد حالياً",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            } else {
                                Box(modifier = Modifier.size(18.dp))
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = reciter.nameAr,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isCurrent) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = reciter.nameEn,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                }
            }
        }
    )
}

@Composable
fun LateNightReadingControlPanel(viewModel: MainViewModel) {
    val isLateNight by viewModel.isLateNightReading.collectAsState()
    val fontSize by viewModel.quranReadingFontSize.collectAsState()
    val tint by viewModel.nightReadingTint.collectAsState()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("late_night_control_panel"),
        colors = CardDefaults.cardColors(
            containerColor = if (isLateNight) {
                Color(0xFF161512)
            } else {
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.12f)
            }
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (isLateNight) {
                when (tint) {
                    "amber" -> Color(0xFFFFBF00).copy(alpha = 0.45f)
                    "sepia" -> Color(0xFFD4C1A8).copy(alpha = 0.45f)
                    "mint" -> Color(0xFF50C878).copy(alpha = 0.45f)
                    "rose" -> Color(0xFFF0A5C0).copy(alpha = 0.45f)
                    else -> Color(0xFFFFBF00).copy(alpha = 0.45f)
                }
            } else {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
            }
        ),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header Row: Toggle & Icon
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (isLateNight) Icons.Default.NightsStay else Icons.Outlined.NightsStay,
                        contentDescription = null,
                        tint = if (isLateNight) {
                            when (tint) {
                                "amber" -> Color(0xFFFFB52D)
                                "sepia" -> Color(0xFFD4C1A8)
                                "mint" -> Color(0xFF50C878)
                                "rose" -> Color(0xFFF5A3B3)
                                else -> Color(0xFFFFB52D)
                            }
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        modifier = Modifier.size(24.dp)
                    )
                    Column {
                        Text(
                            text = "منصة القراءة والتدبر الليلة المريحة 🌙",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isLateNight) Color(0xFFFFDF9B) else MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "تحسين ذكي لحجم الخط وألوان الإضاءة لمنع إجهاد العين ليلاً.",
                            fontSize = 9.sp,
                            color = if (isLateNight) Color.LightGray.copy(alpha = 0.8f) else MaterialTheme.colorScheme.outline
                        )
                    }
                }
                
                Switch(
                    checked = isLateNight,
                    onCheckedChange = { viewModel.isLateNightReading.value = it },
                    modifier = Modifier.testTag("late_night_toggle_switch"),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = when (tint) {
                            "amber" -> Color(0xFFFFB52D)
                            "sepia" -> Color(0xFFD4C1A8)
                            "mint" -> Color(0xFF50C878)
                            "rose" -> Color(0xFFF5A3B3)
                            else -> Color(0xFFFFB52D)
                        },
                        checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            }

            AnimatedVisibility(
                visible = isLateNight,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HorizontalDivider(color = Color.White.copy(alpha = 0.12f))

                    // 1. Font Size Adjuster
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "حجم خط الآيات والمستندات:",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "${fontSize.toInt()} sp",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = when (tint) {
                                "amber" -> Color(0xFFFFB52D)
                                "sepia" -> Color(0xFFD4C1A8)
                                "mint" -> Color(0xFF50C878)
                                "rose" -> Color(0xFFF5A3B3)
                                else -> Color(0xFFFFB52D)
                            }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("A", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Light)
                        Slider(
                            value = fontSize,
                            onValueChange = { viewModel.quranReadingFontSize.value = it },
                            valueRange = 14f..36f,
                            modifier = Modifier.weight(1f).testTag("quran_font_slider"),
                            colors = SliderDefaults.colors(
                                thumbColor = when (tint) {
                                    "amber" -> Color(0xFFFFB52D)
                                    "sepia" -> Color(0xFFD4C1A8)
                                    "mint" -> Color(0xFF50C878)
                                    "rose" -> Color(0xFFF5A3B3)
                                    else -> Color(0xFFFFB52D)
                                },
                                activeTrackColor = when (tint) {
                                    "amber" -> Color(0xFFFFB52D).copy(alpha = 0.7f)
                                    "sepia" -> Color(0xFFD4C1A8).copy(alpha = 0.7f)
                                    "mint" -> Color(0xFF50C878).copy(alpha = 0.7f)
                                    "rose" -> Color(0xFFF5A3B3).copy(alpha = 0.7f)
                                    else -> Color(0xFFFFB52D).copy(alpha = 0.7f)
                                }
                            )
                        )
                        Text("A", fontSize = 21.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }

                    // 2. Tint Selection
                    Text(
                        text = "طيف الإضاءة الليلية لتقليل الإشعاع الأزرق:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val tintChoices = listOf(
                            "amber" to Pair("شموع ذهبية 🕯️", Color(0xFFFFDF9B)),
                            "sepia" to Pair("مخطوطة عتيقة 📜", Color(0xFFE2D6C5)),
                            "mint" to Pair("راحة النعناع 🌿", Color(0xFFC5F7D9)),
                            "rose" to Pair("شفق الغسق 🌸", Color(0xFFFBDCE5))
                        )

                        for ((choiceKey, choiceData) in tintChoices) {
                            val (choiceLabel, previewColor) = choiceData
                            val isSelected = tint == choiceKey
                            Surface(
                                onClick = { viewModel.nightReadingTint.value = choiceKey },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) {
                                    previewColor.copy(alpha = 0.15f)
                                } else {
                                    Color(0xFF222222)
                                },
                                border = BorderStroke(
                                    width = 1.dp,
                                    color = if (isSelected) previewColor else Color(0xFF444444)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("night_tint_chip_$choiceKey")
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .background(previewColor, shape = RoundedCornerShape(50.dp))
                                    )
                                    Text(
                                        text = choiceLabel,
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) previewColor else Color.LightGray,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}


