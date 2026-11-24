package com.example.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.automirrored.outlined.FactCheck
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.R
import com.example.data.model.AttendanceRecord
import com.example.data.model.UiState
import com.example.data.model.Worker
import com.example.ui.auth.signOutUser
import com.example.ui.components.AccountDialog
import com.example.ui.components.AttendanceProofSheet
import com.example.ui.components.SiteSettingsSheet
import com.example.ui.components.WorkerFormSheet
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.TodayScreen
import com.example.ui.screens.WorkersScreen
import com.example.ui.viewmodel.AttendanceViewModel

const val ROUTE_TODAY = "today"
const val ROUTE_HISTORY = "history"
const val ROUTE_WORKERS = "workers"

private data class NavTabItem(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String,
)

private val NAV_TABS = listOf(
    NavTabItem(
        route = ROUTE_TODAY,
        labelRes = R.string.nav_today,
        selectedIcon = Icons.AutoMirrored.Filled.FactCheck,
        unselectedIcon = Icons.AutoMirrored.Outlined.FactCheck,
        testTag = "nav_tab_today"
    ),
    NavTabItem(
        route = ROUTE_HISTORY,
        labelRes = R.string.nav_history,
        selectedIcon = Icons.Filled.History,
        unselectedIcon = Icons.Outlined.History,
        testTag = "nav_tab_history"
    ),
    NavTabItem(
        route = ROUTE_WORKERS,
        labelRes = R.string.nav_workers,
        selectedIcon = Icons.Filled.Groups,
        unselectedIcon = Icons.Outlined.Groups,
        testTag = "nav_tab_workers"
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScaffold(
    viewModel: AttendanceViewModel,
    userDisplayName: String?,
    userEmail: String?,
    onSignedOut: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }

    val profileState by viewModel.profileState.collectAsStateWithLifecycle()
    val workersState by viewModel.workersState.collectAsStateWithLifecycle()
    val recordsState by viewModel.recordsState.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
    val feedbackMessage by viewModel.feedbackMessage.collectAsStateWithLifecycle()
    val verifiedLocation by viewModel.verifiedLocationSummary.collectAsStateWithLifecycle()
    val isFetchingLocation by viewModel.isFetchingLocation.collectAsStateWithLifecycle()

    var proofTarget by remember { mutableStateOf<Pair<Worker, AttendanceRecord?>?>(null) }
    var workerFormTarget by remember { mutableStateOf<Pair<Boolean, Worker?>?>(null) }
    var showSiteSettings by remember { mutableStateOf(false) }
    var showAccountDialog by remember { mutableStateOf(false) }

    LaunchedEffect(feedbackMessage) {
        val msg = feedbackMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearFeedbackMessage()
        }
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: ROUTE_TODAY
    val profile = (profileState as? UiState.Success)?.data
    val defaultSite = profile?.projectName ?: stringResource(R.string.default_project_name)

    fun navigateToTab(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavRail = maxWidth >= 600.dp

        Scaffold(
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    actions = {
                        IconButton(
                            onClick = { showSiteSettings = true },
                            modifier = Modifier.testTag("site_settings_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Settings,
                                contentDescription = "إعدادات الموقع"
                            )
                        }
                        IconButton(
                            onClick = { showAccountDialog = true },
                            modifier = Modifier.testTag("account_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AccountCircle,
                                contentDescription = "حساب المشرف"
                            )
                        }
                    }
                )
            },
            bottomBar = {
                if (!useNavRail) {
                    NavigationBar(windowInsets = WindowInsets.navigationBars) {
                        NAV_TABS.forEach { tab ->
                            val selected = currentRoute == tab.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateToTab(tab.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = stringResource(tab.labelRes)
                                    )
                                },
                                label = { Text(stringResource(tab.labelRes)) },
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }
            },
            floatingActionButton = {
                if (currentRoute == ROUTE_WORKERS) {
                    ExtendedFloatingActionButton(
                        onClick = { workerFormTarget = true to null },
                        icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                        text = { Text("إضافة عامل") },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.testTag("add_worker_fab")
                    )
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavRail) {
                    NavigationRail(modifier = Modifier.fillMaxHeight()) {
                        Spacer(modifier = Modifier.weight(0.1f))
                        NAV_TABS.forEach { tab ->
                            val selected = currentRoute == tab.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateToTab(tab.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = stringResource(tab.labelRes)
                                    )
                                },
                                label = { Text(stringResource(tab.labelRes)) },
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }

                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    NavHost(
                        navController = navController,
                        startDestination = ROUTE_TODAY,
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 760.dp)
                    ) {
                        composable(ROUTE_TODAY) {
                            TodayScreen(
                                profileState = profileState,
                                workersState = workersState,
                                recordsState = recordsState,
                                verifiedLocation = verifiedLocation,
                                isFetchingLocation = isFetchingLocation,
                                isBusy = isBusy,
                                onRequestLocation = { viewModel.captureSiteLocation(context) },
                                onQuickCheckIn = { worker ->
                                    viewModel.quickCheckInWorker(worker, profile)
                                },
                                onOpenProofSheet = { worker, existingRecord ->
                                    proofTarget = worker to existingRecord
                                },
                                onCheckOut = { record ->
                                    viewModel.checkOutWorker(record)
                                },
                                onAddWorkerClick = { workerFormTarget = true to null }
                            )
                        }

                        composable(ROUTE_HISTORY) {
                            HistoryScreen(
                                profileState = profileState,
                                recordsState = recordsState,
                                onDeleteRecord = { recordId ->
                                    viewModel.deleteAttendanceRecord(recordId)
                                },
                                buildReportText = { dateLabel, projectName, records ->
                                    viewModel.buildReportText(dateLabel, projectName, records)
                                }
                            )
                        }

                        composable(ROUTE_WORKERS) {
                            WorkersScreen(
                                workersState = workersState,
                                recordsState = recordsState,
                                onAddWorkerClick = { workerFormTarget = true to null },
                                onEditWorkerClick = { worker ->
                                    workerFormTarget = true to worker
                                },
                                onToggleActive = { worker ->
                                    viewModel.toggleWorkerActive(worker)
                                },
                                onDeleteWorker = { workerId ->
                                    viewModel.deleteWorker(workerId)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (proofTarget != null) {
        val (worker, existingRecord) = proofTarget!!
        AttendanceProofSheet(
            worker = worker,
            existingRecord = existingRecord,
            suggestedStatus = viewModel.suggestCheckInStatus(profile),
            currentVerifiedLocation = verifiedLocation,
            isFetchingLocation = isFetchingLocation,
            isBusy = isBusy,
            onRequestLocation = { viewModel.captureSiteLocation(context) },
            onSave = { status, timeLabel, proofMethod, locationSummary, photoUri, notes ->
                viewModel.submitAttendanceProof(
                    worker = worker,
                    dateKey = existingRecord?.dateKey ?: AttendanceViewModel.todayDateKey(),
                    status = status,
                    checkInTimeLabel = timeLabel,
                    proofMethod = proofMethod,
                    locationSummary = locationSummary,
                    photoUriString = photoUri,
                    notes = notes,
                    existingRecordId = existingRecord?.id,
                    onComplete = { proofTarget = null }
                )
            },
            onDismiss = { proofTarget = null }
        )
    }

    if (workerFormTarget?.first == true) {
        val editingWorker = workerFormTarget?.second
        WorkerFormSheet(
            existingWorker = editingWorker,
            defaultSiteName = defaultSite,
            isBusy = isBusy,
            onSave = { fullName, roleTitle, siteName, badgeCode, phone, active ->
                viewModel.saveWorker(
                    existingWorkerId = editingWorker?.id,
                    fullName = fullName,
                    roleTitle = roleTitle,
                    siteName = siteName,
                    badgeCode = badgeCode,
                    phoneNumber = phone,
                    active = active,
                    onComplete = { workerFormTarget = null }
                )
            },
            onDismiss = { workerFormTarget = null }
        )
    }

    if (showSiteSettings) {
        SiteSettingsSheet(
            profile = profile,
            isBusy = isBusy,
            onSave = { displayName, projectName, startHour, startMinute ->
                viewModel.saveSiteProfile(
                    displayName = displayName,
                    projectName = projectName,
                    shiftStartHour = startHour,
                    shiftStartMinute = startMinute,
                    onComplete = { showSiteSettings = false }
                )
            },
            onDismiss = { showSiteSettings = false }
        )
    }

    if (showAccountDialog) {
        AccountDialog(
            displayName = userDisplayName ?: profile?.displayName,
            email = userEmail,
            onSignOut = {
                showAccountDialog = false
                signOutUser(
                    credentialManager = credentialManager,
                    scope = scope,
                    onSignOutComplete = onSignedOut
                )
            },
            onDismiss = { showAccountDialog = false }
        )
    }
}
