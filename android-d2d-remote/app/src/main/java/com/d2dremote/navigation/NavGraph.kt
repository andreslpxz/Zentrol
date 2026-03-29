package com.d2dremote.navigation

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.d2dremote.ui.controller.ControllerScreen
import com.d2dremote.ui.home.HomeScreen
import com.d2dremote.ui.target.TargetScreen

object Routes {
    const val HOME = "home"
    const val CONTROLLER = "controller"
    const val TARGET = "target"
}

@Composable
fun AppNavGraph() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        enterTransition = {
            slideInHorizontally(
                initialOffsetX = { it },
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
            ) + fadeIn()
        },
        exitTransition = {
            slideOutHorizontally(
                targetOffsetX = { -it / 3 },
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
            ) + fadeOut()
        },
        popEnterTransition = {
            slideInHorizontally(
                initialOffsetX = { -it / 3 },
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
            ) + fadeIn()
        },
        popExitTransition = {
            slideOutHorizontally(
                targetOffsetX = { it },
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
            ) + fadeOut()
        }
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                onNavigateToController = {
                    navController.navigate(Routes.CONTROLLER)
                },
                onNavigateToTarget = {
                    navController.navigate(Routes.TARGET)
                }
            )
        }

        composable(Routes.CONTROLLER) {
            ControllerScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Routes.TARGET) {
            TargetScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
