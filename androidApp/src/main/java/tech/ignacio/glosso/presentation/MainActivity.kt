package tech.ignacio.glosso.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.koin.android.ext.android.inject
import tech.ignacio.glosso.domain.repository.PreferenceRepository
import tech.ignacio.glosso.presentation.theme.GlossoTheme
import tech.ignacio.glosso.presentation.home.HomeScreen
import tech.ignacio.glosso.presentation.studio.StudioScreen
import tech.ignacio.glosso.presentation.topic.TopicSelectionScreen
import tech.ignacio.glosso.presentation.about.AboutScreen
import androidx.navigation.NavType
import androidx.navigation.navArgument

class MainActivity : ComponentActivity() {
    
    private val prefs: PreferenceRepository by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Decide start destination based on preferences
        val lastLevel = prefs.getLastLevel()
        val startDestination = if (lastLevel >= 0) "studio/$lastLevel" else "home"

        setContent {
            GlossoTheme {
                val navController = rememberNavController()
                Scaffold(
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = startDestination,
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        composable("home") {
                            HomeScreen(
                                onNavigateToStudio = { category ->
                                    navController.navigate("topics/$category")
                                },
                                onNavigateToAbout = {
                                    navController.navigate("about")
                                }
                            )
                        }
                        composable("about") {
                            AboutScreen(onNavigateBack = { navController.popBackStack() })
                        }
                        composable(
                            route = "topics/{levelIndex}",
                            arguments = listOf(navArgument("levelIndex") { type = NavType.IntType })
                        ) { backStackEntry ->
                            val levelIndex = backStackEntry.arguments?.getInt("levelIndex") ?: 0
                            TopicSelectionScreen(
                                levelIndex = levelIndex,
                                onNavigateBack = { navController.popBackStack() },
                                onStartPractice = { level, topics ->
                                    val topicsArg = if (topics.isNotEmpty()) "?topics=${topics.joinToString(",")}" else ""
                                    navController.navigate("studio/$level$topicsArg")
                                }
                            )
                        }
                        composable(
                            route = "studio/{levelIndex}?topics={topics}",
                            arguments = listOf(
                                navArgument("levelIndex") { type = NavType.IntType },
                                navArgument("topics") { 
                                    type = NavType.StringType
                                    nullable = true 
                                    defaultValue = null
                                }
                            )
                        ) { backStackEntry ->
                            val levelIndex = backStackEntry.arguments?.getInt("levelIndex") ?: 0
                            val topics = backStackEntry.arguments?.getString("topics")?.split(",")?.filter { it.isNotBlank() }
                            StudioScreen(
                                category = levelIndex,
                                topics = topics,
                                onNavigateToSettings = { navController.navigate("home") }
                            )
                        }
                    }
                }
            }
        }
    }
}
