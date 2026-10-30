package com.foxus.app.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import com.foxus.app.ui.screens.ProfileScreen
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.foxus.app.PendingInterruptDeepLink
import com.foxus.app.ui.components.ImmersiveInterruptModal
import com.foxus.app.ui.screens.AgentDebugScreen
import com.foxus.app.ui.screens.ChallengeScreen
import com.foxus.app.ui.screens.DeviceScreen
import com.foxus.app.ui.screens.FoxBzzScreen
import com.foxus.app.ui.screens.HomeScreen
import com.foxus.app.ui.screens.OnboardingScreen
import com.foxus.app.ui.screens.RewardsScreen
import com.foxus.app.ui.screens.SettingsScreen
import com.foxus.app.ui.screens.TasksScreen
import com.foxus.app.ui.theme.FoxusTokens
import com.foxus.app.viewmodel.FoxusMainViewModel

object FoxusRoutes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val TASKS = "tasks"
    const val CHALLENGE = "challenge"
    const val FOX_BZZ = "fox_bzz"
    const val PROFILE = "profile"
    const val REWARDS = "rewards"
    const val DEVICE = "device"
    const val AGENT_DEBUG = "agent_debug"
    const val SETTINGS = "settings"
}

private data class BottomNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector
)

private val bottomItems = listOf(
    BottomNavItem(FoxusRoutes.HOME, "Inicio", Icons.Filled.Home),
    BottomNavItem(FoxusRoutes.TASKS, "Tareas", Icons.Filled.CheckCircle),
    BottomNavItem(FoxusRoutes.CHALLENGE, "Reto", Icons.Filled.Bolt),
    BottomNavItem(FoxusRoutes.PROFILE, "Perfil", Icons.Filled.Person),
    BottomNavItem(FoxusRoutes.DEVICE, "FoxusWatch", Icons.Filled.Watch)
)

@Composable
fun FoxusNavHost(
    viewModel: FoxusMainViewModel,
    pendingDeepLink: PendingInterruptDeepLink? = null,
    onConsumeDeepLink: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    LaunchedEffect(uiState.statusBanner) {
        val msg = uiState.statusBanner
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearBanner()
        }
    }

    LaunchedEffect(pendingDeepLink?.nonce) {
        if (pendingDeepLink != null) {
            viewModel.handleIncomingInterruptIntent(
                action = pendingDeepLink.action,
                taskId = pendingDeepLink.taskId,
                hookMode = pendingDeepLink.hookMode,
                onNavigateToChallenge = {
                    navController.navigate(FoxusRoutes.CHALLENGE) {
                        launchSingleTop = true
                    }
                }
            )
            onConsumeDeepLink()
        }
    }

    val activeInterruptMission = uiState.activeMission
    if (uiState.showImmersiveInterruptModal && activeInterruptMission != null) {
        ImmersiveInterruptModal(
            mission = activeInterruptMission,
            urgencyReason = uiState.interruptUrgencyReason,
            urgencyScore = uiState.interruptUrgencyScore,
            onPlayMinigameHit = { completed ->
                viewModel.playMinigameHitSound(completed)
            },
            onAcceptStart = {
                viewModel.startChallengeTimer {
                    navController.navigate(FoxusRoutes.CHALLENGE) {
                        launchSingleTop = true
                    }
                }
            },
            onPostpone = {
                viewModel.postponeOrStopChallenge(reason = "postponed")
            },
            onSilence = {
                viewModel.postponeOrStopChallenge(reason = "silenced")
            }
        )
    }

    val startDestination = if (uiState.settings.onboardingCompleted) {
        FoxusRoutes.HOME
    } else {
        FoxusRoutes.ONBOARDING
    }

    Scaffold(
        containerColor = FoxusTokens.DarkSurface,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (currentRoute != null && currentRoute != FoxusRoutes.ONBOARDING) {
                NavigationBar(containerColor = FoxusTokens.SurfaceCard) {
                    bottomItems.forEach { item ->
                        val selected = currentRoute == item.route ||
                            (item.route == FoxusRoutes.CHALLENGE && currentRoute == FoxusRoutes.FOX_BZZ)
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = FoxusTokens.FoxOrange,
                                selectedTextColor = FoxusTokens.FoxOrange,
                                indicatorColor = FoxusTokens.DeepNavy,
                                unselectedIconColor = FoxusTokens.TextSecondary,
                                unselectedTextColor = FoxusTokens.TextSecondary
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            enterTransition = {
                fadeIn(animationSpec = tween(220)) +
                    slideInVertically(animationSpec = tween(220), initialOffsetY = { it / 18 })
            },
            exitTransition = { fadeOut(animationSpec = tween(160)) },
            popEnterTransition = { fadeIn(animationSpec = tween(200)) },
            popExitTransition = { fadeOut(animationSpec = tween(160)) },
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(FoxusRoutes.ONBOARDING) {
                OnboardingScreen(
                    onFinishOnboarding = { chosenLevel ->
                        viewModel.completeOnboarding(chosenLevel)
                        navController.navigate(FoxusRoutes.HOME) {
                            popUpTo(FoxusRoutes.ONBOARDING) { inclusive = true }
                        }
                    }
                )
            }
            composable(FoxusRoutes.HOME) {
                HomeScreen(
                    uiState = uiState,
                    onLaunchChallengeForTask = { taskId, title, mode ->
                        viewModel.requestMissionForTask(taskId, title, mode) { resolvedHook ->
                            val dest = if (resolvedHook == "fox_bzz") {
                                FoxusRoutes.FOX_BZZ
                            } else {
                                FoxusRoutes.CHALLENGE
                            }
                            navController.navigate(dest)
                        }
                    },
                    onGoToTasks = { navController.navigate(FoxusRoutes.TASKS) },
                    onGoToActiveMission = {
                        val dest = if (uiState.activeMission?.hook?.hookType == "fox_bzz") {
                            FoxusRoutes.FOX_BZZ
                        } else {
                            FoxusRoutes.CHALLENGE
                        }
                        navController.navigate(dest)
                    },
                    onGoToRewards = { navController.navigate(FoxusRoutes.PROFILE) },
                    onGoToTraces = { navController.navigate(FoxusRoutes.AGENT_DEBUG) },
                    onGoToSettings = { navController.navigate(FoxusRoutes.SETTINGS) },
                    onTriggerImmersiveNow = { viewModel.triggerImmersiveInterruptNow() },
                    onScheduleInterrupt5s = { viewModel.scheduleBackgroundInterruptIn5s() },
                    onUpdateSmartPressure = { sched, overlay ->
                        viewModel.updateSmartPressureSettings(sched, overlay)
                    },
                    onRefreshOverlayPermission = { viewModel.refreshOverlayPermissionStatus() },
                    onRefresh = { viewModel.refreshAllManual() }
                )
            }
            composable(FoxusRoutes.TASKS) {
                TasksScreen(
                    uiState = uiState,
                    onCreateTask = { title, desc, pressure, deadline ->
                        viewModel.createNewTask(title, desc, pressure, deadline)
                    },
                    onToggleTaskPressure = { task -> viewModel.toggleTaskPressure(task) },
                    onGenerateMission = { taskId, title, mode ->
                        viewModel.requestMissionForTask(taskId, title, mode) { resolvedHook ->
                            val dest = if (resolvedHook == "fox_bzz") {
                                FoxusRoutes.FOX_BZZ
                            } else {
                                FoxusRoutes.CHALLENGE
                            }
                            navController.navigate(dest)
                        }
                    },
                    onOpenGoogleOAuth = { openUrl -> viewModel.requestGoogleOAuthUrl(openUrl) },
                    onConnectGoogleIcal = { url -> viewModel.connectGoogleCalendarIcal(url) },
                    onConnectGoogleToken = { tok, ical -> viewModel.connectGoogleWithToken(tok, ical) },
                    onSyncGoogleNow = { viewModel.syncGoogleNow() },
                    onDisconnectGoogle = { viewModel.disconnectGoogle() }
                )
            }
            composable(FoxusRoutes.CHALLENGE) {
                ChallengeScreen(
                    uiState = uiState,
                    onOpenProfile = { navController.navigate(FoxusRoutes.PROFILE) },
                    onStartTimer = { viewModel.startChallengeTimer() },
                    onBulletCheckedCheer = { checkedCount, totalCount ->
                        viewModel.onBulletStepChecked(checkedCount, totalCount)
                    },
                    onTriggerPepTalk = { viewModel.triggerCoachPepTalk() },
                    onToggleFocusMusic = { viewModel.toggleFocusMusicInline() },
                    onToggleVoiceCoach = { viewModel.toggleVoiceCoachInline() },
                    onOpenEvidenceStage = { viewModel.openEvidenceVerificationStage() },
                    onSubmitPhotoEvidence = { jpegBase64, checkedCount ->
                        viewModel.submitEvidenceForVerification(
                            evidenceType = "photo",
                            imageBase64 = jpegBase64,
                            checkedStepsCount = checkedCount
                        )
                    },
                    onSubmitVoiceEvidence = { transcript, checkedCount ->
                        viewModel.submitEvidenceForVerification(
                            evidenceType = "voice",
                            voiceTranscript = transcript,
                            checkedStepsCount = checkedCount
                        )
                    },
                    onCompleteChallengeWithoutEvidence = { continuity ->
                        viewModel.completeActiveChallenge(continuity)
                    },
                    onPostpone = {
                        viewModel.postponeOrStopChallenge(reason = "postponed") {
                            navController.navigate(FoxusRoutes.HOME)
                        }
                    },
                    onOpenTraces = { navController.navigate(FoxusRoutes.AGENT_DEBUG) },
                    onPickTaskFirst = { navController.navigate(FoxusRoutes.TASKS) }
                )
            }
            composable(FoxusRoutes.FOX_BZZ) {
                FoxBzzScreen(
                    uiState = uiState,
                    onAcceptBzzMission = {
                        viewModel.startChallengeTimer {
                            navController.navigate(FoxusRoutes.CHALLENGE)
                        }
                    },
                    onPostponeBzz = {
                        viewModel.postponeOrStopChallenge(reason = "postponed") {
                            navController.navigate(FoxusRoutes.HOME)
                        }
                    },
                    onSilenceFly = {
                        viewModel.postponeOrStopChallenge(reason = "silenced") {
                            navController.navigate(FoxusRoutes.HOME)
                        }
                    },
                    onReplayDiscreetBuzz = { viewModel.replayDiscreetBuzz() },
                    onGenerateNewBzz = { taskId, title ->
                        viewModel.requestMissionForTask(taskId, title, "fox_bzz")
                    },
                    onPickTaskFirst = { navController.navigate(FoxusRoutes.TASKS) },
                    onOpenTraces = { navController.navigate(FoxusRoutes.AGENT_DEBUG) }
                )
            }
            composable(FoxusRoutes.PROFILE) {
                ProfileScreen(uiState, { viewModel.refreshMomentum() },
                    { id, target, minutes, mode -> viewModel.continueMomentum(id, target, minutes, mode) {
                        navController.navigate(FoxusRoutes.CHALLENGE) { launchSingleTop = true }
                    } },
                    { session, kind, caption, image, mime, media, units, unit -> viewModel.saveMoment(session, kind, caption, image, mime, media, units, unit) },
                    { id, ready -> viewModel.openMomentMedia(id, ready) },
                    { navController.navigate(FoxusRoutes.REWARDS) })
            }
            composable(FoxusRoutes.REWARDS) {
                RewardsScreen(
                    uiState = uiState,
                    onRefreshRewards = { viewModel.refreshRewardsAndHistory() }
                )
            }
            composable(FoxusRoutes.DEVICE) {
                DeviceScreen(
                    uiState = uiState,
                    onRefreshDevices = { viewModel.refreshDevices() },
                    onPollNextCommand = { viewModel.pollNextDeviceCommand() },
                    onSelectBleExperimental = { viewModel.setBleExperimentalMode(it) },
                    onSimulateButton = { eventType -> viewModel.simulateDeviceButton(eventType) }
                )
            }
            composable(FoxusRoutes.AGENT_DEBUG) {
                AgentDebugScreen(uiState = uiState)
            }
            composable(FoxusRoutes.SETTINGS) {
                SettingsScreen(
                    uiState = uiState,
                    onSaveConnection = { url, demoTok, devTok ->
                        viewModel.saveConnectionSettings(url, demoTok, devTok)
                    },
                    onUpdatePreferences = { level, audio, music ->
                        viewModel.updatePreferences(level, audio, music)
                    }
                )
            }
        }
    }
}
