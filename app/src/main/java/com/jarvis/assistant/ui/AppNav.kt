package com.jarvis.assistant.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.firebase.auth.FirebaseAuth
import com.jarvis.assistant.ui.auth.AuthScreen
import com.jarvis.assistant.ui.chat.ChatScreen
import com.jarvis.assistant.ui.charging.ChargingScreen
import com.jarvis.assistant.ui.home.HomeScreen
import com.jarvis.assistant.ui.mic.MicScreen
import com.jarvis.assistant.ui.permissions.PermissionsScreen
import com.jarvis.assistant.ui.settings.SettingsScreen

@Composable
fun AppNav(routeExtra: String? = null) {
    val nav = rememberNavController()
    val hasUser = runCatching { FirebaseAuth.getInstance().currentUser != null }.getOrDefault(true)

    LaunchedEffect(routeExtra) {
        if (routeExtra == "chat") nav.navigate("chat")
    }

    NavHost(navController = nav, startDestination = if (hasUser) "home" else "auth") {
        composable("auth") {
            AuthScreen(onSuccess = {
                nav.navigate("home") { popUpTo("auth") { inclusive = true } }
            })
        }
        composable("home") { HomeScreen(nav) }
        composable("chat") { ChatScreen(nav) }
        composable("permissions") { PermissionsScreen(nav) }
        composable("charging") { ChargingScreen(nav) }
        composable("mic") { MicScreen(nav) }
        composable("settings") { SettingsScreen(nav) }
    }
}
