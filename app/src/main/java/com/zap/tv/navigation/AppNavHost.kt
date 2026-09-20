package com.zap.tv.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.zap.tv.presentation.player.PlayerScreen
import com.zap.tv.presentation.setup.SetupScreen
import com.zap.tv.presentation.splash.SplashScreen

object Routes {
    const val SPLASH = "splash"
    const val SETUP = "setup"
    const val PLAYER = "player"
}

@Composable
fun AppNavHost() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Routes.SPLASH) {
        composable(Routes.SPLASH) {
            SplashScreen(
                onSetupNeeded = { nav.navigate(Routes.SETUP){ popUpTo(Routes.SPLASH){inclusive=true} } },
                onReady = { nav.navigate(Routes.PLAYER){ popUpTo(Routes.SPLASH){inclusive=true} } }
            )
        }
        composable(Routes.SETUP) {
            SetupScreen(onComplete = { nav.navigate(Routes.PLAYER){ popUpTo(Routes.SETUP){inclusive=true} } })
        }
        composable(Routes.PLAYER) {
            PlayerScreen()
        }
    }
}
