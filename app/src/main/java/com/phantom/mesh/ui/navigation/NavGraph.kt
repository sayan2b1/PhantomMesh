package com.phantom.mesh.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.phantom.mesh.ui.auth.AuthScreen
import com.phantom.mesh.ui.auth.AuthViewModel
import com.phantom.mesh.ui.chat.ChatListScreen
import com.phantom.mesh.ui.chat.ChatListViewModel
import com.phantom.mesh.ui.chat.ChatScreen
import com.phantom.mesh.ui.chat.ChatViewModel
import com.phantom.mesh.ui.dropzone.DropZoneScreen
import com.phantom.mesh.ui.dropzone.DropZoneViewModel
import com.phantom.mesh.ui.settings.SettingsScreen
import com.phantom.mesh.ui.settings.SettingsViewModel
import com.phantom.mesh.ui.topology.TopologyScreen
import com.phantom.mesh.ui.topology.TopologyViewModel

object Destinations {
    const val AUTH = "auth"
    const val CHAT_LIST = "chat_list"
    const val CHAT = "chat/{chatId}/{targetId}"
    const val TOPOLOGY = "topology"
    const val DROP_ZONE = "drop_zone"
    const val SETTINGS = "settings"

    fun chatRoute(chatId: String, targetId: String) = "chat/$chatId/$targetId"
}

@Composable
fun PhantomNavGraph(
    navController: NavHostController,
    startDestination: String = Destinations.AUTH
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = {
            fadeIn(animationSpec = tween(280)) + slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.Start,
                animationSpec = tween(280)
            )
        },
        exitTransition = {
            fadeOut(animationSpec = tween(280)) + slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.Start,
                animationSpec = tween(280)
            )
        },
        popEnterTransition = {
            fadeIn(animationSpec = tween(280)) + slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.End,
                animationSpec = tween(280)
            )
        },
        popExitTransition = {
            fadeOut(animationSpec = tween(280)) + slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.End,
                animationSpec = tween(280)
            )
        }
    ) {
        composable(Destinations.AUTH) {
            val viewModel: AuthViewModel = hiltViewModel()
            AuthScreen(
                viewModel = viewModel,
                onAuthSuccess = { isDuress ->
                    navController.navigate(Destinations.CHAT_LIST) {
                        popUpTo(Destinations.AUTH) { inclusive = true }
                    }
                }
            )
        }

        composable(Destinations.CHAT_LIST) {
            val viewModel: ChatListViewModel = hiltViewModel()
            ChatListScreen(
                viewModel = viewModel,
                onChatClick = { chatId, targetId ->
                    navController.navigate(Destinations.chatRoute(chatId, targetId))
                },
                onNavigateTopology = {
                    navController.navigate(Destinations.TOPOLOGY)
                },
                onNavigateDropZone = {
                    navController.navigate(Destinations.DROP_ZONE)
                },
                onNavigateSettings = {
                    navController.navigate(Destinations.SETTINGS)
                }
            )
        }

        composable(
            route = Destinations.CHAT,
            arguments = listOf(
                navArgument("chatId") { type = NavType.StringType },
                navArgument("targetId") { type = NavType.StringType }
            )
        ) {
            val viewModel: ChatViewModel = hiltViewModel()
            ChatScreen(
                viewModel = viewModel,
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(Destinations.TOPOLOGY) {
            val viewModel: TopologyViewModel = hiltViewModel()
            val context = androidx.compose.ui.platform.LocalContext.current
            val sessionManager = androidx.compose.runtime.remember {
                com.phantom.mesh.data.local.preferences.SessionManager(context)
            }
            TopologyScreen(
                viewModel = viewModel,
                onBackClick = { navController.popBackStack() },
                onNodeClick = { node ->
                    val myId = sessionManager.getUsername() ?: sessionManager.getUserId() ?: "anon"
                    val peerTarget = node.displayName.ifBlank { node.userId ?: node.endpointId }
                    val chatId = com.phantom.mesh.data.repository.ChatRepository.generateChatId(myId, peerTarget)
                    navController.navigate(Destinations.chatRoute(chatId, peerTarget))
                }
            )
        }

        composable(Destinations.DROP_ZONE) {
            val viewModel: DropZoneViewModel = hiltViewModel()
            DropZoneScreen(
                viewModel = viewModel,
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(Destinations.SETTINGS) {
            val viewModel: SettingsViewModel = hiltViewModel()
            SettingsScreen(
                viewModel = viewModel,
                onBackClick = { navController.popBackStack() },
                onLogoutOrWiped = {
                    navController.navigate(Destinations.AUTH) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
    }
}
