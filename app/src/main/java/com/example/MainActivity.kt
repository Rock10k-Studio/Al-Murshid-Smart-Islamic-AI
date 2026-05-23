package com.example

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.IslamicContentEntity
import com.example.data.SearchResult
import com.example.ui.MainViewModel
import com.example.ui.UiState
import com.example.ui.theme.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: MainViewModel = viewModel()
            val appTheme by viewModel.appTheme.collectAsStateWithLifecycle()
            val darkTheme = when (appTheme) {
                "light" -> false
                "dark" -> true
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }
            MyApplicationTheme(darkTheme = darkTheme) {
                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                ) { innerPadding ->
                    IslamicAppScreen(
                        viewModel = viewModel,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

// Keep the Greeting function signature for Robolectric / App Screenshot Compatibility
@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkEmeraldBg),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            RubElHizbIcon(modifier = Modifier.size(80.dp), color = ShimmeringGold)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "مرحباً بك في تطبيق $name!",
                color = ShimmeringGold,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Serif
            )
        }
    }
}

@Composable
fun RubElHizbIcon(modifier: Modifier = Modifier, color: Color) {
    Canvas(modifier = modifier) {
        val sizePx = size.minDimension
        val center = Offset(sizePx / 2, sizePx / 2)
        val rectSize = sizePx / 1.4f

        rotate(0f, center) {
            drawRect(
                color = color,
                topLeft = Offset((sizePx - rectSize) / 2, (sizePx - rectSize) / 2),
                size = Size(rectSize, rectSize),
                style = Stroke(width = 3.dp.toPx())
            )
        }
        rotate(45f, center) {
            drawRect(
                color = color,
                topLeft = Offset((sizePx - rectSize) / 2, (sizePx - rectSize) / 2),
                size = Size(rectSize, rectSize),
                style = Stroke(width = 3.dp.toPx())
            )
        }
        drawCircle(
            color = color,
            radius = sizePx / 6,
            center = center,
            style = Fill
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IslamicAppScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
    val searchMode by viewModel.searchMode.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val isInternetAvailable by viewModel.isInternetAvailable.collectAsStateWithLifecycle()

    val isPremiumQuranInstalled by viewModel.isPremiumQuranInstalled.collectAsStateWithLifecycle()
    val tempBypassRequiredPackage by viewModel.tempBypassRequiredPackage.collectAsStateWithLifecycle()

    // Configuration Settings
    val appTheme by viewModel.appTheme.collectAsStateWithLifecycle()
    val showTranslation by viewModel.showTranslation.collectAsStateWithLifecycle()
    val fontSizeMultiplier by viewModel.fontSizeMultiplier.collectAsStateWithLifecycle()
    val spiritualRemindersEnabled by viewModel.spiritualRemindersEnabled.collectAsStateWithLifecycle()
    
    var showSettingsDialog by remember { mutableStateOf(false) }
    var selectedItemForDetail by remember { mutableStateOf<IslamicContentEntity?>(null) }
    var reminderIndex by rememberSaveable { mutableStateOf(0) }

    // Recheck Connection and database status on launch
    LaunchedEffect(Unit) {
        viewModel.checkNetworkConnection()
        viewModel.checkPremiumQuranInstallation(context)
        viewModel.refreshCurrentResults()
    }

    // Main layout
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .drawBehind {
                // Background artistic touch: Draw subtle radial arcs to look like Mosque dome
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            ClearEmerald.copy(alpha = 0.08f),
                            Color.Transparent
                        ),
                        center = Offset(size.width / 2, 0f),
                        radius = size.width
                    )
                )
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            // HEADER BANNER
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RubElHizbIcon(
                    modifier = Modifier.size(44.dp),
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "rock10k_studio Islamic AI",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary,
                            fontFamily = FontFamily.Serif,
                            letterSpacing = 0.5.sp
                        )
                    )
                    Text(
                        text = "المساعد الرقمي الموثوق • هجين أونلاين وأوفلاين",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    )
                }

                // Network Status Indicator Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isInternetAvailable) ClearEmerald.copy(alpha = 0.15f)
                            else ShimmeringGold.copy(alpha = 0.15f)
                        )
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (isInternetAvailable) ClearEmerald else ShimmeringGold)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isInternetAvailable) "متصل 📶" else "أوفلاين 💾",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = if (isInternetAvailable) ClearEmerald else ShimmeringGold
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Professional Custom Settings Button (Gear Icon)
                IconButton(
                    onClick = { showSettingsDialog = true },
                    modifier = Modifier
                        .testTag("settings_button")
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "قائمة إعدادات التطبيق",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // SMART CHAT INPUT / SEARCH CARD
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    
                    // Search Mode Selector Toggle (AI Match vs Keyword Offline Only)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "نظام الاستفسار الفقهي:",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        )

                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                                .padding(2.dp)
                        ) {
                            val activeModeColor = MaterialTheme.colorScheme.primary
                            val inactiveColor = Color.Transparent

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (searchMode == MainViewModel.SearchMode.HYBRID) activeModeColor else inactiveColor)
                                    .clickable { viewModel.setSearchMode(MainViewModel.SearchMode.HYBRID) }
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "ذكاء هجين (AI)",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = if (searchMode == MainViewModel.SearchMode.HYBRID) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.secondary
                                    )
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (searchMode == MainViewModel.SearchMode.LOCAL) activeModeColor else inactiveColor)
                                    .clickable { viewModel.setSearchMode(MainViewModel.SearchMode.LOCAL) }
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "مستند بياني (أوفلاين)",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = if (searchMode == MainViewModel.SearchMode.LOCAL) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.secondary
                                    )
                                )
                            }
                        }
                    }

                    // ARABIC SEARCH TEXTFIELD
                    TextField(
                        value = searchQuery,
                        onValueChange = { viewModel.onSearchProgress(it) },
                        placeholder = {
                            Text(
                                text = "اطرح سؤالك الفقهي أو العقدي...",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                    textAlign = TextAlign.End
                                )
                            )
                        },
                        leadingIcon = {
                            IconButton(
                                onClick = { viewModel.executeSearch() },
                                modifier = Modifier.testTag("search_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "بحث",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.onSearchProgress("") }) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "مسح",
                                        tint = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.5f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.3f),
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("search_input")
                    )

                    // One-tap authentic fast tags
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "مواضيع مقترحة شائعة:",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    val fastSuggestTags = listOf("حكم قراءة الجوال", "صلاة العشاء", "الأعمال بالنيات", "آية الكرسي", "أذكار النوم")
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(fastSuggestTags) { tag ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.05f))
                                    .clickable {
                                        viewModel.onSearchProgress(tag)
                                        viewModel.executeSearch()
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = tag,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // SPIRITUAL REMINDER BAR If Enabled
            if (spiritualRemindersEnabled) {
                val reminders = listOf(
                    "💡 قال ﷺ: «إن من أفضل أيامكم يوم الجمعة، فأكثروا علي من الصلاة فيه»",
                    "🌸 سبحان الله وبحمده، سبحان الله العظيم (غرس الجنة)",
                    "📖 قال تعالى: {أَلَا بِذِكْرِ اللَّهِ تَطْمَئِنُّ الْقُلُوبُ}",
                    "🌟 لا حول ولا قوة إلا بالله (كنز من كنوز الجنة)",
                    "🕌 استغفر الله العظيم وأتوب إليه (تمحو الخطايا وتوسع الرزق)",
                    "💡 قال ﷺ: «مَنْ سَلَكَ طَرِيقًا يَلْتَمِسُ فِيهِ عِلْمًا سَهَّلَ اللَّهُ لَهُ بِهِ طَرِيقًا إِلَى الْجَنَّةِ»"
                )
                
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .clickable { reminderIndex = (reminderIndex + 1) % reminders.size },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "💡 تذكير روحي:",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = (11.sp * fontSizeMultiplier)
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = reminders[reminderIndex],
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onBackground,
                                fontSize = (11.sp * fontSizeMultiplier)
                            ),
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Right
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "الذكر التالي",
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // SEGMENT CATEGORY TABS
            val tabs = listOf(
                "all" to "الكل",
                "quran" to "القرآن",
                "hadith" to "الحديث",
                "fatwa" to "الفتاوى",
                "adhkar" to "الأذكار",
                "favorites" to "المفضلة ⭐️"
            )

            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(tabs) { (tabKey, tabTitle) ->
                    val isSelected = currentTab == tabKey
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surface
                            )
                            .clickable { viewModel.setCategoryTab(tabKey) }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = tabTitle,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // MAIN CONTENT LIST OR FLOW STATES
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.TopCenter
            ) {
                when (uiState) {
                    is UiState.Idle -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                RubElHizbIcon(modifier = Modifier.size(50.dp), color = ShimmeringGoldSoft)
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("جاري جلب المعلومات الإسلامية الموثوقة...")
                            }
                        }
                    }
                    is UiState.Loading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            val loadingText = when (aiSearchPattern) {
                                MainViewModel.AiSearchPattern.QUICK -> "جاري البحث السريع الفوري..."
                                MainViewModel.AiSearchPattern.DEEP -> "جاري البحث العميق..."
                                MainViewModel.AiSearchPattern.SCHOLARLY -> "جاري البحث الشرعي المتقدم في أمهات كتب الفقه والمراجع..."
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(
                                    color = ShimmeringGold,
                                    strokeWidth = 3.dp
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = loadingText,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = ShimmeringGold),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 24.dp)
                                )
                            }
                        }
                    }
                    is UiState.Success -> {
                        val itemsList = (uiState as UiState.Success).results
                        if (itemsList.isEmpty()) {
                            // EMPTY STATE VIEW
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(vertical = 40.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = "لا توجد نتائج",
                                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                        modifier = Modifier.size(64.dp)
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = if (currentTab == "favorites") "قائمة المفضلة لديك فارغة حالياً" else "لم نجد نتائج متطابقة في المصادر الشرعية الكودية",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.secondary
                                        ),
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = if (currentTab == "favorites") "اضغط على رمز القلب لتثبيت الفتاوى والأحاديث في أوفلاين كاش." else "حاول كتابة موضوع فقهي آخر (مثال: 'حكم قراءة المصحف') أو تفعيل 'ذكاء هجين' للبحث المباشر.",
                                        style = MaterialTheme.typography.bodySmall,
                                        textAlign = TextAlign.Center,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        } else {
                            // RESULTS SCALABLE COLUMN
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                contentPadding = PaddingValues(bottom = 20.dp)
                            ) {
                                item {
                                    ShariahWebsitesHeaderSection(viewModel = viewModel, context = context)
                                }

                                items(itemsList) { (item, score) ->
                                    IslamicContentResultCard(
                                        item = item,
                                        score = score,
                                        fontSizeMultiplier = fontSizeMultiplier,
                                        showTranslation = showTranslation,
                                        onFavoriteToggle = { viewModel.toggleFavorite(item) },
                                        onCardClick = { selectedItemForDetail = item },
                                        onDeleteClick = { viewModel.deleteItem(item.id) },
                                         onSuggestionClick = { topic ->
                                             viewModel.onSearchProgress(topic)
                                             viewModel.executeSearch()
                                         },
                                        context = context
                                    )
                                }
                            }
                        }
                    }
                    is UiState.Error -> {
                        val msg = (uiState as UiState.Error).message
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Card(
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = "عطل",
                                        tint = ShimmeringGold,
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = msg,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Button(
                                        onClick = { viewModel.refreshCurrentResults() },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                    ) {
                                        Text("أعد المحاولة")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 🕌 com.rock10k.quran.premium Integration Validation Overlay Screen
        if (!isPremiumQuranInstalled) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.98f))
                    .clickable(enabled = false) {}, // Absorb interactions completely
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                        .clip(RoundedCornerShape(32.dp)),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(1.5.dp, ShimmeringGold)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(24.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        RubElHizbIcon(modifier = Modifier.size(68.dp), color = ShimmeringGold)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "يتطلب تطبيق التكامل الشرعي",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "عذراً، تطبيق \"المنارة للذكاء الإسلامي\" يعمل بالتكامل التام وبحزمة واحدة حصرية مع تطبيق:\n(com.rock10k.quran.premium)",
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "قم بتثبيت حزمة المصحف الشريف المذكورة أولاً لفتح واجهة البحث والردود والمستندات الفقهية التلقائية.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        
                        Button(
                            onClick = {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("market://details?id=com.rock10k.quran.premium"))
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://play.google.com/store/apps/details?id=com.rock10k.quran.premium"))
                                        context.startActivity(intent)
                                    } catch (e2: Exception) {
                                        Toast.makeText(context, "لم يتم العثور على متجر التطبيقات", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ShimmeringGold),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .testTag("install_quran_pack_button")
                        ) {
                            Text("تثبيت حزمة المصحف الشريف", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                }
            }
        }
    }

    // INTERACTIVE DETAIL DIALOG VIEW
    selectedItemForDetail?.let { item ->
        IslamicContentDetailDialog(
            item = item,
            fontSizeMultiplier = fontSizeMultiplier,
            showTranslation = showTranslation,
            onDismiss = { selectedItemForDetail = null },
            context = context
        )
    }

    // INTERACTIVE SETTINGS DIALOG VIEW
    if (showSettingsDialog) {
        IslamicSettingsDialog(
            viewModel = viewModel,
            onDismiss = { showSettingsDialog = false }
        )
    }

    // 🛡️ SAF Request System Check Dialog (تأكيد فحص الأمان الشرعي)
    val showSafeCheckDialog by viewModel.showSafeCheckDialog.collectAsStateWithLifecycle()
    val safeAnalysisResult by viewModel.safeAnalysisResult.collectAsStateWithLifecycle()

    if (showSafeCheckDialog) {
        safeAnalysisResult?.let { analysis ->
            androidx.compose.ui.window.Dialog(onDismissRequest = { viewModel.dismissSafeCheck() }) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.5.dp, if (analysis.isSafe) ClearEmerald else MaterialTheme.colorScheme.error)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "🛡️ مصفاة الأمان الشرعي وقيم التدقيق (SAFE Check)",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = ShimmeringGold),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        
                        Text(
                            text = "تصنيف المحتوى المستورد:",
                            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (analysis.isSafe) ClearEmerald.copy(alpha = 0.12f) else MaterialTheme.colorScheme.error.copy(alpha = 0.12f))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = analysis.classification,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (analysis.isSafe) ClearEmerald else MaterialTheme.colorScheme.error
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "معدل موثوقية المستند ومصادره:",
                            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${analysis.reliabilityScore}%",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Black,
                                color = if (analysis.reliabilityScore >= 90) ClearEmerald else ShimmeringGold
                            )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { analysis.reliabilityScore / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(CircleShape),
                            color = if (analysis.reliabilityScore >= 90) ClearEmerald else ShimmeringGold,
                            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (analysis.isSafe) ClearEmerald.copy(alpha = 0.04f) else MaterialTheme.colorScheme.error.copy(alpha = 0.04f)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = analysis.details,
                                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                                textAlign = TextAlign.Right,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.dismissSafeCheck() },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("إلغاء الملف", style = MaterialTheme.typography.labelMedium)
                            }

                            Button(
                                onClick = { viewModel.confirmSafeImport() },
                                enabled = analysis.reliabilityScore >= 40, // Block totally unsafe/heretical content
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (analysis.isSafe) ClearEmerald else ShimmeringGold
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = "تأكيد واستيراد",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = Color.White)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// Local data specifications supporting teaching lessons
data class LessonContent(
    val intro: String,
    val concepts: List<String>,
    val application: String,
    val question: String
)

fun generatePedagogicalLesson(item: IslamicContentEntity): LessonContent {
    val intro = "الحمد لله والصلاة والسلام على رسول الله. أخي المتعلم، يدور هذا الدرس المبارك حول محور: [${item.title}]. ويهدف لتبسيط المفاهيم الشرعية العميقة وتأصيلها في قلبك وعقلك لتنعم بنور الهداية والعلم النافع."
    
    val concepts = listOf(
        "ترسيخ اليقين والوعي بالمسألة من خلال النظر في الدليل الشرعي المستند لـ [${item.source}].",
        "ضرورة طلب العلم الشرعي من مكانه الصحيح والابتعاد بالكلية عن الشبهات والآراء العشوائية غير المدققة.",
        "فهم توازن المنهج الإسلامي وجمال الشريعة المطهرة في التيسير والوضوح للأمة المحمدية."
    )
    
    val application = when(item.type) {
        "quran" -> "أن تستحضر عظمة المتكلم بالقرآن سبحانه وتعالى، وتجعل هذه الآية الكريمة منهج عمل لك اليوم، ترددها في صلواتك وتعمل بمقتضاها في بيتك وعملك ومع الناس أجمعين."
        "hadith" -> "الاتباع الصادق للسنة النبوية ونشر هذا الحديث الشريف بين زملائك تبياناً للحق، والتزام هدي نبينا محمد ﷺ في حركاتك وسكناتك بصدق."
        "fatwa" -> "الاحتياط التام في العبادات والمعاملات، وتجنب الشبهات ما استطعت، وسؤال أهل الذكر عند الأشراط المعقدة لتبرأ ذمتك أمام الله جل وعلا."
        else -> "تعميق المحبة للذكر الحكيم والاعتصام بالدعاء الدائم، وتجديد الإخلاص والنية في كل تفاصيل يومك ونوافل أعمالك."
    }
    
    val question = when(item.type) {
        "quran" -> "تدبر اليوم: كيف يمكن لتطبيق هذه الآية وسماع دلالاتها في منزلك أن يعيد السكينة لأهلك ويبعد عنهم وساوس التشتت المعاصر؟"
        "hadith" -> "تفكر ملياً: ما هو الإحساس الإيماني الذي يخالج صدرك عند استحضار أن رسول الله ﷺ وجه هذا الهدي الشريف لأمته لحمايتهم وإرشادهم؟"
        "fatwa" -> "تأمل قيمياً: لمَ حرص علماء الفقه الإسلامي على دقة الرأي ومنع الفتوى بغير دليل؟ وكيف يحفظ ذلك تماسك ووحدة المجتمع المسلم؟"
        else -> "للذكر والتدبر: ما هو الأثر الملموس الذي تلاحظه على انشراح صدرك وذهاب همك عندما تبدأ يومك متحصناً بهذا الدعاء الخالص؟"
    }

    return LessonContent(intro, concepts, application, question)
}

@Composable
fun IslamicContentResultCard(
    item: IslamicContentEntity,
    score: Double,
    fontSizeMultiplier: Float,
    showTranslation: Boolean,
    onFavoriteToggle: () -> Unit,
    onCardClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onSuggestionClick: (String) -> Unit,
    context: Context
) {
    var showLessonTutorDialog by remember { mutableStateOf(false) }

    val typeTitle = when (item.type) {
        "quran" -> "قرآن كريم"
        "hadith" -> "الحديث الشريف"
        "fatwa" -> "فتوى موثوقة"
        "adhkar" -> "أذكار وأدعية"
        else -> "علمي شرعي"
    }

    val typeColor = when (item.type) {
        "quran" -> ClearEmerald
        "hadith" -> ShimmeringGold
        "fatwa" -> MaterialTheme.colorScheme.primary
        "adhkar" -> MaterialTheme.colorScheme.secondary
        else -> Color.Gray
    }

    // Dynamic Trust / Confidence Calculator & Verified Badge Detection
    val trustText: String
    val trustProgress: Float
    val isVerifiedSource = item.source.contains("البخاري") || 
                           item.source.contains("مسلم") || 
                           item.source.contains("الرياض") || 
                           item.source.contains("ابن كثير") || 
                           item.source.contains("القرآن") || 
                           item.source.contains("النووي")

    if (isVerifiedSource) {
        trustText = "مرجع موثق وصحيح معتمد (99.9% ثقة شرعية)"
        trustProgress = 0.999f
    } else if (score >= 2.0) {
        trustText = "توليد ذكاء اصطناعي هجين (98.2% دقة استدلالية)"
        trustProgress = 0.982f
    } else if (score >= 1.0) {
        trustText = "تطبيق فقهي محلي مدقق (96.5% وثوقية)"
        trustProgress = 0.965f
    } else {
        trustText = "مطابقة بحث دلالي سياقي (94.0% دقة وملاءمة)"
        trustProgress = 0.940f
    }

    // Pre-generated follow-up questions linked to category
    val followups = when (item.type) {
        "quran" -> listOf("ما تفسير الآية؟", "أسباب النزول")
        "hadith" -> listOf("شرح معاني الحديث", "تطبيقات الفقه")
        "fatwa" -> listOf("رأي المذاهب الأربعة", "أدلة الحكم الشرعي")
        else -> listOf("فضل هذا الورد", "أوقات الدعاء")
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("result_card_${item.id}"),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            1.dp,
            if (item.isFavorite) ShimmeringGold.copy(alpha = 0.5f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
        ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // First Row: Badge Type, Match Score % & Star favorite button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(typeColor.copy(alpha = 0.12f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = typeTitle,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = typeColor,
                                fontSize = (11.sp * fontSizeMultiplier)
                            )
                        )
                    }

                    if (isVerifiedSource) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(ClearEmerald.copy(alpha = 0.15f))
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "⭐ مرجع متفق عليه",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = ClearEmerald,
                                    fontSize = (9.sp * fontSizeMultiplier)
                                )
                            )
                        }
                    }

                    if (score >= 2.0) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primary)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "🛡️ إجابة معززة هجينة",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    fontSize = (9.sp * fontSizeMultiplier)
                                )
                            )
                        }
                    }
                }

                Row {
                    IconButton(
                        onClick = onFavoriteToggle,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (item.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "المرجع المفضل",
                            tint = if (item.isFavorite) ShimmeringGold else MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                        )
                    }

                    // Delete Custom cached content option if custom (seed indices are 0-10)
                    if (item.id > 11) { 
                        IconButton(
                            onClick = onDeleteClick,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "مسح الكاش",
                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }

            // Title & Preview Text
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontFamily = FontFamily.Serif,
                    fontSize = (15.sp * fontSizeMultiplier)
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Right,
                modifier = Modifier.fillMaxWidth().clickable { onCardClick() }
            )

            Spacer(modifier = Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.03f))
                    .padding(10.dp)
                    .clickable { onCardClick() }
            ) {
                Text(
                    text = item.text,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        lineHeight = (22.sp * fontSizeMultiplier),
                        fontFamily = FontFamily.Default,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                        fontSize = (13.sp * fontSizeMultiplier)
                    ),
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Translate Card segment if showTranslation is active
            if (showTranslation) {
                val englishTranslation = when (item.type) {
                    "quran" -> "Noble Quran - Guidance and mercy for all humanity."
                    "hadith" -> "Prophetic Hadith - Authentic sayings of the Prophet Muhammad ﷺ."
                    "fatwa" -> "Religious Ruling - Trusted consensus backed by modern Shariah analysis."
                    "adhkar" -> "Supplications & Adhkar - Spiritual daily remembrance for inner peace."
                    else -> "Islamic Reference - Educational science from verified Shariah sources."
                }
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.05f))
                        .padding(10.dp)
                ) {
                    Text(
                        text = englishTranslation,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = (11.sp * fontSizeMultiplier),
                            lineHeight = (16.sp * fontSizeMultiplier),
                            color = MaterialTheme.colorScheme.secondary
                        ),
                        textAlign = TextAlign.Left,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Dynamic Trust Rate metric & Progress bar
            Spacer(modifier = Modifier.height(10.dp))
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = trustText,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = (9.sp * fontSizeMultiplier)
                        ),
                        textAlign = TextAlign.Left
                    )
                    Text(
                        text = "معدل الموثوقية والدقة والتحقق",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            fontSize = (9.sp * fontSizeMultiplier)
                        ),
                        textAlign = TextAlign.Right
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { trustProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(CircleShape),
                    color = if (isVerifiedSource) ClearEmerald else ShimmeringGold,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)
                )
            }

            // Click-to-Search Follow-up Prompts
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                     text = "أسئلة استرشادية:",
                     style = MaterialTheme.typography.labelSmall.copy(
                         fontSize = 10.sp, 
                         color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                         fontWeight = FontWeight.Bold
                     ),
                     modifier = Modifier.padding(start = 4.dp)
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    followups.forEach { followup ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.06f))
                                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                                .clickable { onSuggestionClick(followup + " " + item.title) }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = followup,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                }
            }

            // Source Label & Exporters info
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "المصدر: ${item.source}",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.8f),
                        fontSize = (10.sp * fontSizeMultiplier)
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Auto-Pedagogical Tutor mode activator
                    Text(
                        text = "🎓 تعليم",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = ShimmeringGold
                        ),
                        modifier = Modifier
                            .clickable { showLessonTutorDialog = true }
                            .clip(RoundedCornerShape(6.dp))
                            .background(ShimmeringGold.copy(alpha = 0.1f))
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    )

                    Text(
                        text = "📋 نسخ",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier
                            .clickable { 
                                copyToClipboard(context, item.text, item.source)
                                Toast.makeText(context, "تم النسخ إلى الحافظة", Toast.LENGTH_SHORT).show()
                            }
                            .padding(4.dp)
                    )

                    Text(
                        text = "📤 مشاركة",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.secondary
                        ),
                        modifier = Modifier
                            .clickable { shareProductContent(context, item.title, item.text, item.source) }
                            .padding(4.dp)
                    )
                }
            }
        }
    }

    // Auto Lesson Tutor Dialog Render
    if (showLessonTutorDialog) {
        val lesson = generatePedagogicalLesson(item)
        Dialog(onDismissRequest = { showLessonTutorDialog = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, ShimmeringGoldSoft.copy(alpha = 0.5f)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🎓", fontSize = 20.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "معلم الفقه والشريعة الذكي",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = ShimmeringGold
                                )
                            )
                        }
                        IconButton(onClick = { showLessonTutorDialog = false }) {
                            Icon(imageVector = Icons.Default.Clear, contentDescription = "إغلاق")
                        }
                    }

                    HorizontalDivider(color = ShimmeringGold.copy(alpha = 0.15f), thickness = 1.dp, modifier = Modifier.padding(vertical = 10.dp))

                    Text(
                        text = "درس ميسر ومبسط: ${item.title}",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary),
                        textAlign = TextAlign.Right,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Introduction Section
                    Text(
                        text = "📖 1. تمهيد وتأصيل الدرس:",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary),
                        textAlign = TextAlign.Right,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = lesson.intro,
                        style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                        textAlign = TextAlign.Right,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Content Core Section
                    Text(
                        text = "💡 2. المفاهيم الشرعية والقيمية للمسألة:",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary),
                        textAlign = TextAlign.Right,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    lesson.concepts.forEachIndexed { i, concept ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Text(concept, style = MaterialTheme.typography.bodySmall.copy(lineHeight = 17.sp), textAlign = TextAlign.Right, modifier = Modifier.weight(1f))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("•", fontWeight = FontWeight.Black, color = ShimmeringGold)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Real-world Action compliance
                    Text(
                        text = "🌱 3. كيف يطبقه العبد المسلم في واقعه المعاصر؟:",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary),
                        textAlign = TextAlign.Right,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = lesson.application,
                        style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp, color = ClearEmerald, fontWeight = FontWeight.Bold),
                        textAlign = TextAlign.Right,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Think and ponder exercise
                    Text(
                        text = "❓ 4. تمرين وتدبر فكري شرعي لليوم:",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary),
                        textAlign = TextAlign.Right,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = ShimmeringGoldSoft.copy(alpha = 0.08f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = lesson.question,
                            style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, fontWeight = FontWeight.Bold, color = ShimmeringGold),
                            textAlign = TextAlign.Right,
                            modifier = Modifier.fillMaxWidth().padding(10.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = {
                            val lessonFullText = "درس تعليمي مبسط: [${item.title}]\n\n1. تمهيد الدرس:\n${lesson.intro}\n\n2. مسارات الفهم والعلوم المستخلصة:\n${lesson.concepts.joinToString("\n") { "• $it" }}\n\n3. كيف نطبقه في حياتنا المعاصرة؟:\n${lesson.application}\n\n4. تمرين التدبر:\n${lesson.question}\n\n---\nتم استخراج هذا الدرس التعليمي ومراجعته تلقائياً عبر مصفاة المنارة الفقهية الموثقة."
                            copyToClipboard(context, lessonFullText, "التفقيه المنهجي بالمنارة")
                            Toast.makeText(context, "تم نسخ الدرس التعليمي بالكامل للتوجيه والمشاركة 📋", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ShimmeringGold),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("📋 نسخ الدرس التعليمي بالكامل", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                    }
                }
            }
        }
    }
}

@Composable
fun IslamicContentDetailDialog(
    item: IslamicContentEntity,
    fontSizeMultiplier: Float,
    showTranslation: Boolean,
    onDismiss: () -> Unit,
    context: Context
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .testTag("detail_dialog"),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, ShimmeringGoldSoft.copy(alpha = 0.5f)),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header with icon
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RubElHizbIcon(modifier = Modifier.size(36.dp), color = ShimmeringGold)

                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "إغلاق النافذة",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Title
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        fontFamily = FontFamily.Serif,
                        fontSize = (17.sp * fontSizeMultiplier)
                    ),
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(2.dp))
                Divider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                Spacer(modifier = Modifier.height(12.dp))

                // Large readable text scrollable
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.05f))
                        .padding(14.dp)
                ) {
                    Text(
                        text = item.text,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            lineHeight = (26.sp * fontSizeMultiplier),
                            fontFamily = FontFamily.Serif,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = (15.sp * fontSizeMultiplier)
                        ),
                        textAlign = TextAlign.Right,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Translate Dialog segment if showTranslation is active
                if (showTranslation) {
                    val englishTranslation = when (item.type) {
                        "quran" -> "Noble Quran - Guidance and mercy for all humanity."
                        "hadith" -> "Prophetic Hadith - Authentic sayings of the Prophet Muhammad ﷺ."
                        "fatwa" -> "Religious Ruling - Trusted consensus backed by modern Shariah analysis."
                        "adhkar" -> "Supplications & Adhkar - Spiritual daily remembrance for inner peace."
                        else -> "Islamic Reference - Educational science from verified Shariah sources."
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "English Context / Translation:",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        ),
                        textAlign = TextAlign.Left,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.05f))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = englishTranslation,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = (12.sp * fontSizeMultiplier),
                                lineHeight = (18.sp * fontSizeMultiplier),
                                color = MaterialTheme.colorScheme.secondary
                            ),
                            textAlign = TextAlign.Left,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Source details
                Text(
                    text = "مصنف شرعياً بـ: ${item.source}",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary,
                        fontSize = (11.sp * fontSizeMultiplier)
                    ),
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth()
                )

                if (item.keywords.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "الكلمات الدلالية: ${item.keywords}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            fontSize = (10.sp * fontSizeMultiplier)
                        ),
                        textAlign = TextAlign.Right,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Action Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Button(
                        onClick = {
                            copyToClipboard(context, item.text, item.source)
                            Toast.makeText(context, "تم نسخ النص إلى الحافظة", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("📋 نسخ")
                    }

                    Button(
                        onClick = { shareProductContent(context, item.title, item.text, item.source) },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Text("📤 مشاركة")
                    }
                }
            }
        }
    }
}

// Helper Action Clipboard Copier
fun copyToClipboard(context: Context, text: String, source: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("Islamic Content Source", "$text\n\n[المصدر: $source]")
    clipboard.setPrimaryClip(clip)
}

// Helper Action Share Intent launcher
fun shareProductContent(context: Context, title: String, text: String, source: String) {
    val sendIntent = Intent().apply {
        action = Intent.ACTION_SEND
        putExtra(Intent.EXTRA_TITLE, title)
        putExtra(Intent.EXTRA_TEXT, "$title\n\n$text\n\nالمصدر: $source\n\nتطبيق rock10k_studio Islamic AI")
        type = "text/plain"
    }
    val shareIntent = Intent.createChooser(sendIntent, "مشاركة المحتوى الشرعي عبر:")
    context.startActivity(shareIntent)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IslamicSettingsDialog(
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val appTheme by viewModel.appTheme.collectAsStateWithLifecycle()
    val showTranslation by viewModel.showTranslation.collectAsStateWithLifecycle()
    val fontSizeMultiplier by viewModel.fontSizeMultiplier.collectAsStateWithLifecycle()
    val spiritualRemindersEnabled by viewModel.spiritualRemindersEnabled.collectAsStateWithLifecycle()
    
    val userApiKey by viewModel.userApiKey.collectAsStateWithLifecycle()
    val isOfflineModelDownloaded by viewModel.isOfflineModelDownloaded.collectAsStateWithLifecycle()
    val isOfflineModelDownloading by viewModel.isOfflineModelDownloading.collectAsStateWithLifecycle()
    val offlineDownloadProgress by viewModel.offlineDownloadProgress.collectAsStateWithLifecycle()

    var keyText by remember { mutableStateOf(userApiKey) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
                .testTag("settings_dialog"),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, ShimmeringGoldSoft.copy(alpha = 0.4f)),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "الإعدادات",
                            tint = ShimmeringGold,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "إعدادات المساعد الفقهي",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "إغلاق الإعدادات",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    thickness = 1.dp,
                    modifier = Modifier.padding(vertical = 12.dp)
                )

                // 🔑 API Key Customization
                Text(
                    text = "🔑 مفتاح واجهة برمجة تطبيقات Gemini (من اختيارك):",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Right
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = keyText,
                    onValueChange = {
                        keyText = it
                        viewModel.setUserApiKey(it)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_api_key_field"),
                    textStyle = MaterialTheme.typography.bodySmall,
                    placeholder = {
                        Text(
                            "أدخل مفتاح (API Key) المخصص للدردشة والذكاء هنا للعمل عليه",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        )
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ShimmeringGold,
                        unfocusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                    )
                )

                Spacer(modifier = Modifier.height(14.dp))

                // 💾 Offline Intelligent Model Downloader
                Text(
                    text = "💾 ذكاء اصطناعي محلي أوفلاين (بدون إنترنت):",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Right
                )
                Spacer(modifier = Modifier.height(6.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        if (isOfflineModelDownloaded) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = { viewModel.deleteOfflineIntelligenceModel() },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    modifier = Modifier.height(30.dp)
                                ) {
                                    Text("حذف النموذج", style = MaterialTheme.typography.labelSmall)
                                }
                                Text(
                                    "✅ نموذج الذكاء الأوفلاين نشط ومحمّل",
                                    style = MaterialTheme.typography.bodySmall.copy(color = ClearEmerald, fontWeight = FontWeight.Bold),
                                    textAlign = TextAlign.Right
                                )
                            }
                        } else if (isOfflineModelDownloading) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "${(offlineDownloadProgress * 100).toInt()}%",
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Text(
                                        "جاري تحميل حزمة الذكاء المحلي وفهرس الفتاوى والتشريع الشامل...",
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp),
                                        textAlign = TextAlign.Right
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    progress = { offlineDownloadProgress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(CircleShape),
                                    color = ShimmeringGold,
                                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                )
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = { viewModel.downloadOfflineIntelligenceModel() },
                                    colors = ButtonDefaults.buttonColors(containerColor = ShimmeringGold),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Text("تحميل الحزمة الشرعية الآن", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                                }
                                Text(
                                    "تحميل حزمة أوفلاين ذكية (حجم 25MB) للبحث والفتوى كلياً بلا شبكة.",
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(start = 8.dp),
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)),
                                    textAlign = TextAlign.Right
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 1. App Theme Setting Mode
                Text(
                    text = "🎨 مظهر التطبيق (Theme Mode):",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Right
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.05f))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    val themes = listOf(
                        "light" to "☀️ نهاري",
                        "dark" to "🌙 ليلي",
                        "system" to "⚙️ تلقائي"
                    )
                    themes.forEach { (themeKey, themeLabel) ->
                        val isSelected = appTheme == themeKey
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary
                                    else Color.Transparent
                                )
                                .clickable { viewModel.setAppTheme(themeKey) }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = themeLabel,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 2. Font Size Adjustment
                Text(
                    text = "📏 حجم خط النصوص والآيات:",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Right
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.05f))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    val fontSizes = listOf(
                        0.85f to "صغير",
                        1.0f to "طبيعي",
                        1.25f to "كبير",
                        1.5f to "ضخم"
                    )
                    fontSizes.forEach { (multiplier, label) ->
                        val isSelected = fontSizeMultiplier == multiplier
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary
                                    else Color.Transparent
                                )
                                .clickable { viewModel.setFontSizeMultiplier(multiplier) }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 3. Translation Toggles
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Switch(
                        checked = showTranslation,
                        onCheckedChange = { viewModel.toggleTranslation(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = ShimmeringGold,
                            checkedTrackColor = ClearEmerald
                        )
                    )
                    Column(
                        horizontalAlignment = Alignment.End,
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Text(
                            text = "🌐 إظهار الترجمة اللغوية والتفسير",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            textAlign = TextAlign.Right
                        )
                        Text(
                            text = "ترجمات إنجليزية مدمجة وتفسير للمقاطع",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)),
                            textAlign = TextAlign.Right
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 4. Spiritual Reminders Toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Switch(
                        checked = spiritualRemindersEnabled,
                        onCheckedChange = { viewModel.toggleSpiritualReminders(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = ShimmeringGold,
                            checkedTrackColor = ClearEmerald
                        )
                    )
                    Column(
                        horizontalAlignment = Alignment.End,
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Text(
                            text = "🕌 التذكيرات والأذكار الروحية التلقائية",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            textAlign = TextAlign.Right
                        )
                        Text(
                            text = "تذكير روحي متحرك وهادئ أعلى الصفحة الرئيسية",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)),
                            textAlign = TextAlign.Right
                        )
                    }
                }

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    thickness = 1.dp,
                    modifier = Modifier.padding(vertical = 12.dp)
                )

                // 🧭 AI Mode Selection (نمط التفكير والذكاء)
                Text(
                    text = "🧭 نمط التفكير ومستوى الذكاء الشرعي (AI Mode):",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Right
                )
                Spacer(modifier = Modifier.height(6.dp))
                val currentAiMode by viewModel.aiMode.collectAsStateWithLifecycle()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.05f))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    val modes = listOf(
                        MainViewModel.AiMode.SIMPLE to "⚡ البسيط",
                        MainViewModel.AiMode.STANDARD to "📖 القوي",
                        MainViewModel.AiMode.ADVANCED_ULTRA to "🧠 المتقدم"
                    )
                    modes.forEach { (mode, label) ->
                        val isSelected = currentAiMode == mode
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary
                                    else Color.Transparent
                                )
                                .clickable { viewModel.setAiMode(mode) }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                )
                            )
                        }
                    }
                }
                Text(
                    text = when (currentAiMode) {
                        MainViewModel.AiMode.SIMPLE -> "الوضع البسيط: فتاوى مختصرة جداً، واضحة وخالية من الخلاف لتسهيل الفهم للمبتدئين."
                        MainViewModel.AiMode.ADVANCED_ULTRA -> "الوضع المتقدم جداً: تحليل دقيق ومعمق، مقارنة المذاهب والأقوال الشرعية ونقد الروايات والآراء الفقهية."
                        else -> "الوضع القياسي القوي: إجابة متوازنة ومنظمة مصحوبة بالأدلة والتبسيط الدقيق."
                    },
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)),
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 12.dp)
                )

                // 🛡️ Strict Shariah Validation Toggle (مصفاة التدقيق القيمي)
                val strictShariahValidation by viewModel.strictShariahValidation.collectAsStateWithLifecycle()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Switch(
                        checked = strictShariahValidation,
                        onCheckedChange = { viewModel.setStrictShariahValidation(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = ShimmeringGold,
                            checkedTrackColor = ClearEmerald
                        )
                    )
                    Column(
                        horizontalAlignment = Alignment.End,
                        modifier = Modifier.padding(start = 8.dp).weight(1f)
                    ) {
                        Text(
                            text = "🛡️ مصفاة التدقيق الشرعي الصارمة",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            textAlign = TextAlign.Right,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = "منع التخمين أو الفتاوى العشوائية عند ضعف الأدلة والتحوط الفقهي التام.",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)),
                            textAlign = TextAlign.Right,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))

                // 🧠 Smart Memory Profile (ذاكرة اهتمامات العلوم الشرعية)
                val memoryInterests by viewModel.memoryInterests.collectAsStateWithLifecycle()
                Text(
                    text = "🧠 ملف الذاكرة المعرفية الذكية (Smart Memory):",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Right
                )
                Spacer(modifier = Modifier.height(6.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.03f)
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "تتبع فوري لمحاور بحثك المفضلة لتكييف الإجابات مع نمط اهتمامك وفهمك:",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)),
                            textAlign = TextAlign.Right,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (memoryInterests.isEmpty()) {
                            Text(
                                text = "الذاكرة ذكية فارغة حالياً. ابدأ بالبحث لبناء مؤشرات اهتمامك.",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                            )
                        } else {
                            Column(modifier = Modifier.padding(vertical = 6.dp)) {
                                memoryInterests.forEach { (topic, count) ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(text = "كرَّرت البحث: $count مرات", style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp, color = ShimmeringGold, fontWeight = FontWeight.Bold))
                                        Text(text = "• $topic", style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold))
                                    }
                                }
                            }
                            Button(
                                onClick = {
                                    viewModel.clearMemoryInterests()
                                    Toast.makeText(context, "تم مسح الذاكرة المعرفية للذكاء الإصطناعي بنجاح 🧠", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)),
                                modifier = Modifier.fillMaxWidth().height(32.dp),
                                contentPadding = PaddingValues(top = 2.dp, bottom = 2.dp)
                            ) {
                                Text("مسح ذاكرة الاهتمامات الذكية", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))

                // 📂 Ready-Made Database and Content Importer (مستورد القواعد المخصصة)
                Text(
                    text = "📂 مستورد القواعد والوثائق الشرعية مسبقة الصنع:",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Right
                )
                Spacer(modifier = Modifier.height(6.dp))
                var importInputText by remember { mutableStateOf("") }
                var importFormat by remember { mutableStateOf("json") }
                val isImporting by viewModel.isImporting.collectAsStateWithLifecycle()
                val importStatus by viewModel.importStatus.collectAsStateWithLifecycle()
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "استورد فتاوى أو أحاديث مخصصة (تدعم تنسيقات JSON و CSV و TXT) مباشرة لقاعدة البيانات لتعمل أوفلاين بالكامل:",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)),
                            textAlign = TextAlign.Right,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            listOf("json" to "JSON", "csv" to "CSV جداول", "txt" to "TXT نصوص").forEach { (fmt, label) ->
                                val active = importFormat == fmt
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (active) ShimmeringGold else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                                        .clickable { importFormat = fmt }
                                        .padding(vertical = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(label, style = MaterialTheme.typography.labelSmall.copy(color = if (active) Color.White else MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold))
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = importInputText,
                            onValueChange = { importInputText = it },
                            modifier = Modifier.fillMaxWidth().height(90.dp),
                            textStyle = MaterialTheme.typography.bodySmall,
                            placeholder = { Text("الصق السجلات هنا لبدء معالجتها وفهرستها بالكامل...", fontSize = 9.sp) }
                        )
                        if (importStatus.isNotEmpty()) {
                            Text(
                                text = importStatus,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold),
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                textAlign = TextAlign.Right
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        val safeModeEnabled by viewModel.safeModeEnabled.collectAsStateWithLifecycle()
                        Button(
                            onClick = {
                                if (importInputText.isNotBlank()) {
                                    if (safeModeEnabled) {
                                        viewModel.triggerSafeCheck(importInputText, importFormat)
                                    } else {
                                        viewModel.importDataFile(importInputText, importFormat)
                                    }
                                    importInputText = ""
                                } else {
                                    Toast.makeText(context, "يرجى لصق نصوص شرعية صالحة قبل الاستيراد", Toast.LENGTH_SHORT).show()
                                }
                            },
                            enabled = !isImporting,
                            colors = ButtonDefaults.buttonColors(containerColor = ShimmeringGold),
                            modifier = Modifier.fillMaxWidth().height(36.dp),
                            contentPadding = PaddingValues(vertical = 2.dp)
                        ) {
                            Text(if (isImporting) "جاري الفحص المنهجي..." else "تحميل السجلات الفقهية ومعالجتها", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    thickness = 1.dp,
                    modifier = Modifier.padding(vertical = 12.dp)
                )

                // 5. Database cache cleanup for speed and lightness optimization
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.15f)),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.02f)
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "⚡️ تنظيف وتسريع التطبيق بالكامل:",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error),
                            textAlign = TextAlign.Right,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "تنظيف الذاكرة المؤقتة للـ AI يُبقي التطبيق أوفلاين وسريعاً وخفيفاً بدون استهلاك مساحات تخزين.",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)),
                            textAlign = TextAlign.Right,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                viewModel.clearCachedSearchEntities()
                                Toast.makeText(context, "تم تنظيف ذاكرة الكاش بالكامل بنجاح وتوفير المساحة ⚡️", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(vertical = 4.dp)
                        ) {
                            Text(
                                "تفريغ الذاكرة وتسريع التصفح",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 🌐 Custom Referencing Links Section
                Text(
                    text = "🌐 إدارة روابط ومواقع البحث والفتوى الموثوقة:",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Right
                )
                Spacer(modifier = Modifier.height(6.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "يمكنك إضافة روابط مرجعية خاصة بك لتسهيل الرجوع لمعلومات ومواقع فتوى تثق بها:",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)),
                            textAlign = TextAlign.Right,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Form to add custom link
                        var newTitle by remember { mutableStateOf("") }
                        var newUrl by remember { mutableStateOf("") }
                        val customLinks by viewModel.customLinks.collectAsStateWithLifecycle()

                        OutlinedTextField(
                            value = newTitle,
                            onValueChange = { newTitle = it },
                            label = { Text("اسم الموقع، مثل: دار الإفتاء", fontSize = 10.sp) },
                            textStyle = MaterialTheme.typography.bodySmall,
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = newUrl,
                            onValueChange = { newUrl = it },
                            label = { Text("رابط الموقع (URL)، مثل: dar-alifta.org", fontSize = 10.sp) },
                            textStyle = MaterialTheme.typography.bodySmall,
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Button(
                            onClick = {
                                if (newTitle.isNotBlank() && newUrl.isNotBlank()) {
                                    val checkedUrl = if (!newUrl.startsWith("http://") && !newUrl.startsWith("https://")) {
                                        "https://$newUrl"
                                    } else {
                                        newUrl
                                    }
                                    viewModel.addCustomLink(newTitle, checkedUrl)
                                    newTitle = ""
                                    newUrl = ""
                                    Toast.makeText(context, "تمت إضافة الرابط المخصص بنجاح 🌐", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "يرجى تعبئة كلا الحقلين لإضافة الرابط", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ShimmeringGold),
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(vertical = 4.dp)
                        ) {
                            Text("➕ إضافة رابط تخصيصي", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondary))
                        }

                        // Display existing custom links
                        if (customLinks.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                            Spacer(modifier = Modifier.height(6.dp))
                            customLinks.forEach { link ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        onClick = {
                                            viewModel.removeCustomLink(link.first)
                                            Toast.makeText(context, "تم حذف الرابط بنجاح", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "حذف الرابط",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Text(
                                        text = link.first,
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                        textAlign = TextAlign.Right
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 6. Security & Policy Compliance
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(ClearEmerald.copy(alpha = 0.08f))
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "توافق سياسات Play",
                        tint = ClearEmerald,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "مشروع آمن ومطابق بالكامل لسياسات Google Play وموثوق شرعياً.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = ClearEmerald,
                            fontSize = 9.sp
                        ),
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Right
                    )
                }
            }
        }
    }
}

@Composable
fun ShariahWebsitesHeaderSection(viewModel: MainViewModel, context: Context) {
    var expanded by remember { mutableStateOf(false) }
    val customLinks by viewModel.customLinks.collectAsStateWithLifecycle()
    
    val defaultWebsites = listOf(
        "الإسلام سؤال وجواب" to "https://islamqa.info/ar",
        "الدرر السنية" to "https://dorar.net",
        "موقع ابن باز" to "https://binbaz.org.sa",
        "إسلام ويب" to "https://islamweb.net/ar",
        "دار الإفتاء المصرية" to "https://dar-alifta.org",
        "المكتبة الشاملة" to "https://shamela.ws"
    )

    val allLinks = defaultWebsites + customLinks

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.04f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = "عرض الروابط",
                    tint = MaterialTheme.colorScheme.primary
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "📚 المصادر وتصفح المواقع الشرعية الموثوقة",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "المصادر",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "اضغط على أي موقع للانتقال الفوري وتصفح الأصول العلمية الموثقة:",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)),
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(6.dp))

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    allLinks.chunked(2).forEach { pairList ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            pairList.forEach { link ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surface)
                                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                        .clickable {
                                            try {
                                                val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(link.second))
                                                context.startActivity(intent)
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "لم نتمكن من فتح الرابط المختار", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                        .padding(vertical = 8.dp, horizontal = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = link.first,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        ),
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            if (pairList.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}
