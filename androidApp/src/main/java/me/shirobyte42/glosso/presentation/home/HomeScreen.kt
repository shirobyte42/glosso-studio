package me.shirobyte42.glosso.presentation.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToStudio: (Int) -> Unit,
    onNavigateToAbout: () -> Unit,
    viewModel: HomeViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var showResetDialog by remember { mutableStateOf(false) }

    if (state.isInitialSetupRequired) {
        AlertDialog(
            onDismissRequest = { },
            properties = androidx.compose.ui.window.DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
            title = { Text("Initial Setup", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Glosso Studio needs to download the acoustic model (approx. 45MB) to perform phonetic recognition.")
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "WARNING: If you are using mobile data, costs may apply. We recommend using a Wi-Fi connection.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.startInitialSetup() },
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("SET UP NOW")
                }
            }
        )
    }

    if (state.isDownloadRequired) {
        val levelsNames = listOf("Beginner", "Elementary", "Intermediate", "Upper-Int", "Advanced", "Mastery")
        val levelName = state.pendingLevelIndex?.let { levelsNames.getOrNull(it) } ?: "this level"
        AlertDialog(
            onDismissRequest = { viewModel.refreshStats() },
            properties = androidx.compose.ui.window.DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true),
            title = { Text("Setup Required", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Glosso Studio requires a one-time download for the $levelName curriculum (approx. 50-100MB).")
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "WARNING: If you are using mobile data, significant costs may apply. We recommend using a Wi-Fi connection.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.startDownload() },
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("DOWNLOAD")
                }
            }
        )
    }

    if (state.isDownloading) {
        val levelsNames = listOf("Beginner", "Elementary", "Intermediate", "Upper-Int", "Advanced", "Mastery")
        val levelName = state.pendingLevelIndex?.let { levelsNames.getOrNull(it) }
        val titleText = if (levelName != null) "Downloading $levelName" else "Initial Setup"
        
        AlertDialog(
            onDismissRequest = { },
            properties = androidx.compose.ui.window.DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
            title = { Text(titleText, fontWeight = FontWeight.Bold) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text("Preparing your curriculum...", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(24.dp))
                    LinearProgressIndicator(
                        progress = state.downloadProgress,
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("${(state.downloadProgress * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
            },
            confirmButton = { }
        )
    }

    if (state.downloadError != null) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Download Failed") },
            text = { Text(state.downloadError ?: "Unknown error occurred during setup.") },
            confirmButton = {
                Button(onClick = { viewModel.refreshStats() }) {
                    Text("RETRY")
                }
            }
        )
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset Progress?") },
            text = { Text("This will clear your mastery data and reset the curriculum. This cannot be undone.") },
            confirmButton = {
                Button(onClick = {
                    viewModel.resetProgress()
                    showResetDialog = false
                }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                    Text("RESET")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("CANCEL")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("GLOSSO", fontWeight = FontWeight.Black, letterSpacing = 2.sp) },
                navigationIcon = {
                    IconButton(onClick = onNavigateToAbout) {
                        Icon(Icons.Default.Info, contentDescription = "About", tint = Color.LightGray)
                    }
                },
                actions = {
                    IconButton(onClick = { showResetDialog = true }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reset Progress", tint = Color.LightGray)
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
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                StatCard(
                    modifier = Modifier.weight(1f),
                    label = "Consistency",
                    value = "${state.streak} Days",
                    icon = Icons.Default.Favorite,
                    color = MaterialTheme.colorScheme.tertiary
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    label = "Mastery Score",
                    value = "${state.masteryScore} Phr.",
                    icon = Icons.Default.Star,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            Spacer(modifier = Modifier.height(40.dp))

            Text(
                text = "CURRICULUM PROGRESS",
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray,
                letterSpacing = 1.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(20.dp))

            val levels = listOf("Level 1", "Level 2", "Level 3", "Level 4", "Level 5", "Level 6")
            val subtitles = listOf("Beginner", "Elementary", "Intermediate", "Upper-Int", "Advanced", "Mastery")

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 150.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                itemsIndexed(levels) { index, level ->
                    val stat = state.levelStats.getOrElse(index) { LevelStat(0, 10, 0f) }
                    LevelCard(
                        title = subtitles[index],
                        code = level,
                        progress = stat.progress,
                        masteredCount = stat.mastered,
                        totalCount = stat.total,
                        isDownloaded = stat.isDownloaded,
                        onClick = { viewModel.onLevelClick(index, onNavigateToStudio) }
                    )
                }
            }
        }
    }
}

@Composable
fun StatCard(modifier: Modifier = Modifier, label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.05f)),
        shape = RoundedCornerShape(24.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.1f))
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(color = color.copy(alpha = 0.1f), shape = CircleShape, modifier = Modifier.size(40.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = Color.Gray, textAlign = TextAlign.Center)
            Text(text = value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LevelCard(
    title: String, 
    code: String, 
    progress: Float, 
    masteredCount: Int,
    totalCount: Int,
    isDownloaded: Boolean,
    onClick: () -> Unit
) {
    val completeColor = MaterialTheme.colorScheme.secondary
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.height(140.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(20.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = code, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
            Text(text = title, style = MaterialTheme.typography.labelSmall, color = Color.Gray, textAlign = TextAlign.Center)
            
            Spacer(modifier = Modifier.height(16.dp))
            
            if (isDownloaded) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    LinearProgressIndicator(
                        progress = progress,
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                        color = if (progress >= 1f) completeColor else MaterialTheme.colorScheme.primary,
                        trackColor = Color.LightGray.copy(alpha = 0.2f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (progress >= 1f) "COMPLETE" else "$masteredCount / $totalCount",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (progress >= 1f) completeColor else Color.Gray,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    shape = CircleShape,
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Text(
                        text = "TAP TO SETUP",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }
    }
}
