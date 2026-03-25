package me.shirobyte42.glosso.presentation.studio

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import org.koin.androidx.compose.koinViewModel

import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import me.shirobyte42.glosso.domain.model.LetterFeedbackModel
import me.shirobyte42.glosso.domain.model.MatchStatusModel
import me.shirobyte42.glosso.presentation.components.MarkdownText
import me.shirobyte42.glosso.presentation.util.TopicEmojiMap

@Composable
fun ColoredPronunciationText(
    text: String,
    feedback: List<LetterFeedbackModel>? = null,
    style: TextStyle,
    textAlign: TextAlign = TextAlign.Center
) {
    if (feedback == null || feedback.isEmpty()) {
        Text(
            text = text,
            style = style,
            textAlign = textAlign,
            modifier = Modifier.fillMaxWidth()
        )
    } else {
        val annotatedString = buildAnnotatedString {
            feedback.forEach { model ->
                val color = when (model.status) {
                    MatchStatusModel.PERFECT -> Color.Unspecified
                    MatchStatusModel.CLOSE -> Color(0xFFFFA500) // Orange
                    MatchStatusModel.MISSED -> Color.Red
                }
                withStyle(style = SpanStyle(color = color)) {
                    append(model.char)
                }
            }
        }
        Text(
            text = annotatedString,
            style = style,
            textAlign = textAlign,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun StudioTutorialOverlay(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Text(
                "HOW TO READ FEEDBACK", 
                fontWeight = FontWeight.Black, 
                letterSpacing = 1.sp,
                style = MaterialTheme.typography.titleMedium
            ) 
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    "After recording your voice, Glosso Studio analyzes your pronunciation phonetically.",
                    style = MaterialTheme.typography.bodyMedium
                )
                
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    FeedbackGuideItem(
                        color = Color.Black, 
                        label = "CORRECT", 
                        description = "Perfect pronunciation."
                    )
                    FeedbackGuideItem(
                        color = Color(0xFFFFA500), // Orange
                        label = "CLOSE", 
                        description = "Slightly off, keep practicing."
                    )
                    FeedbackGuideItem(
                        color = Color.Red, 
                        label = "MISSED", 
                        description = "Incorrect pronunciation."
                    )
                }

                Divider(color = Color.LightGray.copy(alpha = 0.2f), thickness = 1.dp)

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Star, 
                        contentDescription = null, 
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        "Achieve 85% accuracy or higher to MASTER a sentence and progress in the curriculum.",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("GOT IT", fontWeight = FontWeight.Bold)
            }
        },
        shape = RoundedCornerShape(28.dp)
    )
}

@Composable
fun FeedbackGuideItem(color: Color, label: String, description: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(color, CircleShape)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                label, 
                style = MaterialTheme.typography.labelSmall, 
                fontWeight = FontWeight.Black,
                color = color
            )
            Text(
                description, 
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioScreen(
    category: Int, // levelIndex (0-5)
    topics: List<String>? = null,
    onNavigateToSettings: () -> Unit,
    viewModel: StudioViewModel = koinViewModel { org.koin.core.parameter.parametersOf(category) }
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    BackHandler {
        onNavigateToSettings()
    }

    if (state.isTutorialVisible) {
        StudioTutorialOverlay(onDismiss = { viewModel.dismissTutorial() })
    }
    
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }
val launcher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission(),
    onResult = { granted -> hasPermission = granted }
)

LaunchedEffect(Unit) {
    // Any unit initialization
}

val infiniteTransition = rememberInfiniteTransition(label = "pulse")

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    LaunchedEffect(category, topics) { 
        viewModel.loadTopics(category)
        viewModel.setTopics(category, topics ?: emptyList())
    }

    LaunchedEffect(state.error) {
        state.error?.let { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = { 
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("GLOSSO STUDIO", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                        val levelNames = listOf("Beginner", "Elementary", "Intermediate", "Upper-Int", "Advanced", "Mastery")
                        Text(levelNames.getOrElse(category) { "Level ${category + 1}" }.uppercase(), 
                             style = MaterialTheme.typography.labelSmall, color = Color.Gray, letterSpacing = 0.5.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 8.dp)) {
                        if (state.currentStreak > 0) {
                            Surface(
                                color = Color(0xFFFF5722).copy(alpha = 0.1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Whatshot, contentDescription = null, tint = Color(0xFFFF5722), modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("${state.currentStreak}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black, color = Color(0xFFFF5722))
                                }
                            }
                        }
                        IconButton(onClick = onNavigateToSettings) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.LightGray)
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Main adaptive content area
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top
            ) {
                Spacer(modifier = Modifier.height(24.dp))

                // Professional Practice Card
                state.currentSentence?.let { sentence ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(32.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 32.dp, horizontal = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            if (state.isMastered) {
                                Surface(
                                    color = MaterialTheme.colorScheme.secondary,
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("MASTERED", color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black)
                                    }
                                }
                                Spacer(modifier = Modifier.height(20.dp))
                            }

                            ColoredPronunciationText(
                                text = sentence.text,
                                feedback = state.feedback?.letterFeedback,
                                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } ?: run {
                    if (state.isLoading) {
                        Box(modifier = Modifier.height(150.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(strokeWidth = 3.dp, modifier = Modifier.size(48.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Premium Voice Selector
                Text(
                    "SELECT REFERENCE VOICE",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray,
                    letterSpacing = 1.5.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val voices = listOf("Vivian", "Aiden")
                    val voiceEmojis = listOf("👩", "👨")
                    
                    voices.forEachIndexed { index, voice ->
                        val isSelected = state.selectedVoiceIndex == index
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { viewModel.setVoiceIndex(index, autoPlay = true) },
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(20.dp),
                            border = if (isSelected) null else BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.3f))
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                if (isSelected) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp), tint = Color.White)
                                } else {
                                    Text(text = voiceEmojis[index], style = MaterialTheme.typography.titleMedium, modifier = Modifier.height(20.dp))
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = voice.uppercase(),
                                    color = if (isSelected) Color.White else Color.Gray,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Results Area - Static container to prevent jumping
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp), // Fixed height reserves space
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = state.feedback != null,
                        enter = fadeIn(animationSpec = tween(400)),
                        exit = fadeOut(animationSpec = tween(400))
                    ) {
                        state.feedback?.let { feedback ->
                            val scoreColor = when {
                                feedback.score >= 85 -> MaterialTheme.colorScheme.secondary
                                feedback.score >= 50 -> MaterialTheme.colorScheme.tertiary
                                else -> MaterialTheme.colorScheme.error
                            }
                            
                            Box(contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(
                                    progress = feedback.score / 100f,
                                    modifier = Modifier.size(110.dp),
                                    color = scoreColor,
                                    strokeWidth = 8.dp,
                                    trackColor = scoreColor.copy(alpha = 0.1f)
                                )
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("${feedback.score}%", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                                    Text("ACCURACY", style = MaterialTheme.typography.labelSmall, color = scoreColor, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(48.dp))
            }

            // Fixed Control Bar at the bottom
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                if (state.isRecording) {
                    Box(modifier = Modifier.size(140.dp).scale(pulseScale).clip(CircleShape).background(Color.Red.copy(alpha = 0.08f)))
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier.size(56.dp).clip(CircleShape).clickable(enabled = state.hasRecordedVoice) { viewModel.playRecordedVoice() },
                        color = if (state.hasRecordedVoice) MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f) else Color.Transparent,
                        border = if (state.hasRecordedVoice) null else BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.2f))
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.PlayArrow, 
                                contentDescription = "Play Recording", 
                                modifier = Modifier.size(26.dp), 
                                tint = if (state.hasRecordedVoice) MaterialTheme.colorScheme.secondary else Color.LightGray.copy(alpha = 0.4f)
                            )
                        }
                    }

                    Box(contentAlignment = Alignment.Center) {
                        if (state.isAnalyzing) {
                            CircularProgressIndicator(modifier = Modifier.size(100.dp), strokeWidth = 4.dp, color = MaterialTheme.colorScheme.primary)
                        }
                        FloatingActionButton(
                            onClick = { if (hasPermission) viewModel.toggleRecording() else launcher.launch(Manifest.permission.RECORD_AUDIO) },
                            containerColor = if (state.isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            contentColor = Color.White,
                            shape = CircleShape,
                            modifier = Modifier.size(84.dp)
                        ) {
                            Icon(if (state.isRecording) Icons.Default.Stop else Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(38.dp))
                        }
                    }

                    Surface(
                        modifier = Modifier.size(56.dp).clip(CircleShape).clickable { viewModel.loadSample(category) },
                        color = Color.Transparent,
                        border = BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.2f))
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.ArrowForward, contentDescription = "Next Sentence", modifier = Modifier.size(26.dp), tint = Color.Gray)
                        }
                    }
                }
            }
        }
    }
}
