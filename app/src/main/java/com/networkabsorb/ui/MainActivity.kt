package com.networkabsorb.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.networkabsorb.ui.screens.CacheBrowserScreen
import com.networkabsorb.ui.screens.DashboardScreen
import com.networkabsorb.ui.screens.SettingsScreen
import com.networkabsorb.ui.screens.TrafficMonitorScreen
import com.networkabsorb.ui.theme.NetworkAbsorbTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NetworkAbsorbTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    NetworkAbsorbApp()
                }
            }
        }
    }
}

@Composable
fun NetworkAbsorbApp() {
    val navController = rememberNavController()
    val viewModel: MainViewModel = hiltViewModel()

    NavHost(navController = navController, startDestination = "dashboard") {
        composable("dashboard") {
            DashboardScreen(
                viewModel     = viewModel,
                onNavigateTo  = { route -> navController.navigate(route) }
            )
        }
        composable("traffic") {
            TrafficMonitorScreen(
                viewModel = viewModel,
                onBack    = { navController.popBackStack() }
            )
        }
        composable("cache") {
            CacheBrowserScreen(
                viewModel = viewModel,
                onBack    = { navController.popBackStack() }
            )
        }
        composable("settings") {
            SettingsScreen(
                viewModel = viewModel,
                onBack    = { navController.popBackStack() }
            )
        }
    }
}
