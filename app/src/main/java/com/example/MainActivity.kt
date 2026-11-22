package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.core.content.IntentCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.system.DefaultFilesAppManager
import com.example.ui.components.DefaultFilesAppSheet
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.TargetsScreen
import com.example.ui.screens.UploadScreen
import com.example.ui.screens.WebBrowserScreen
import com.example.ui.screens.WebBrowserUtils
import com.example.ui.theme.EmeraldAccent
import com.example.ui.theme.FolderPushTheme
import com.example.viewmodel.MainViewModel
import kotlinx.coroutines.launch

const val ROUTE_UPLOAD = "upload"
const val ROUTE_WEB_BROWSER = "web_browser"
const val ROUTE_TARGETS = "targets"
const val ROUTE_HISTORY = "history"

private data class NavTab(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private val appTabs = listOf(
    NavTab(
        route = ROUTE_UPLOAD,
        label = "رفع مجلد",
        selectedIcon = Icons.Filled.CloudUpload,
        unselectedIcon = Icons.Outlined.CloudUpload
    ),
    NavTab(
        route = ROUTE_WEB_BROWSER,
        label = "متصفح الويب",
        selectedIcon = Icons.Filled.Language,
        unselectedIcon = Icons.Outlined.Language
    ),
    NavTab(
        route = ROUTE_TARGETS,
        label = "الوجهات",
        selectedIcon = Icons.Filled.Bookmarks,
        unselectedIcon = Icons.Outlined.Bookmarks
    ),
    NavTab(
        route = ROUTE_HISTORY,
        label = "السجل",
        selectedIcon = Icons.Filled.History,
        unselectedIcon = Icons.Outlined.History
    )
)

class MainActivity : ComponentActivity() {
    private val mainViewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val sharedFiles = extractSharedFileUris(intent)
        val initialSharedUrl = if (sharedFiles.isEmpty()) extractSharedUrl(intent) else null
        if (sharedFiles.isNotEmpty()) {
            mainViewModel.onExtraFilesPicked(sharedFiles, autoNavigateToUpload = true)
        } else if (initialSharedUrl != null) {
            mainViewModel.setBrowserTargetUrl(initialSharedUrl)
        }
        setContent {
            FolderPushTheme {
                FolderPushApp(
                    viewModel = mainViewModel,
                    initialRoute = if (initialSharedUrl != null) ROUTE_WEB_BROWSER else ROUTE_UPLOAD
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val sharedFiles = extractSharedFileUris(intent)
        if (sharedFiles.isNotEmpty()) {
            mainViewModel.onExtraFilesPicked(sharedFiles, autoNavigateToUpload = true)
            return
        }
        val sharedUrl = extractSharedUrl(intent)
        if (sharedUrl != null) {
            mainViewModel.setBrowserTargetUrl(sharedUrl, autoNavigate = true)
        }
    }

    private fun extractSharedUrl(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_SEND) return null
        val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return null
        return WebBrowserUtils.extractFirstHttpUrl(sharedText)
    }

    private fun extractSharedFileUris(intent: Intent?): List<Uri> {
        if (intent == null) return emptyList()
        val uris = LinkedHashSet<Uri>()
        when (intent.action) {
            Intent.ACTION_SEND -> {
                IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                    ?.let { uris.add(it) }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                    ?.let { uris.addAll(it) }
            }
        }
        intent.clipData?.let { clip ->
            for (i in 0 until clip.itemCount) {
                clip.getItemAt(i)?.uri?.let { uris.add(it) }
            }
        }
        return uris.toList()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderPushApp(
    viewModel: MainViewModel = viewModel(),
    initialRoute: String = ROUTE_UPLOAD
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: initialRoute
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var showDefaultFilesSheet by remember { mutableStateOf(false) }

    val defaultFilesStatus by viewModel.defaultFilesStatus.collectAsStateWithLifecycle()

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshDefaultFilesStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val systemPickerTestLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.refreshDefaultFilesStatus()
        val data = result.data
        val pickedCount = when {
            data?.clipData != null -> data.clipData?.itemCount ?: 0
            data?.data != null -> 1
            else -> 0
        }
        if (pickedCount > 0) {
            viewModel.notifyMessage("تمت تجربة منتقي النظام واستلام $pickedCount عنصر بنجاح")
        }
    }

    val snackbarMessage by viewModel.snackbarMessage.collectAsStateWithLifecycle()
    LaunchedEffect(snackbarMessage) {
        val msg = snackbarMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.consumeSnackbar()
        }
    }

    fun navigateToTab(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    val openBrowserSignal by viewModel.openBrowserSignal.collectAsStateWithLifecycle()
    LaunchedEffect(openBrowserSignal) {
        if (openBrowserSignal > 0 && currentRoute != ROUTE_WEB_BROWSER) {
            navigateToTab(ROUTE_WEB_BROWSER)
        }
    }

    val openUploadSignal by viewModel.openUploadSignal.collectAsStateWithLifecycle()
    LaunchedEffect(openUploadSignal) {
        if (openUploadSignal > 0 && currentRoute != ROUTE_UPLOAD) {
            navigateToTab(ROUTE_UPLOAD)
        }
    }

    if (showDefaultFilesSheet) {
        DefaultFilesAppSheet(
            status = defaultFilesStatus,
            onTogglePickerHandler = viewModel::setFilePickerHandlerEnabled,
            onToggleDocumentsProvider = viewModel::setDocumentsProviderEnabled,
            onOpenSystemDefaultSettings = {
                DefaultFilesAppManager.openDefaultAppsSettings(context).onFailure {
                    viewModel.notifyMessage("تعذر فتح إعدادات التطبيقات الافتراضية على هذا الجهاز")
                }
            },
            onTestSystemPicker = {
                runCatching {
                    systemPickerTestLauncher.launch(DefaultFilesAppManager.buildGetContentProbeIntent())
                }.onFailure {
                    viewModel.notifyMessage("تعذر فتح منتقي الملفات في النظام")
                }
            },
            onDismiss = { showDefaultFilesSheet = false }
        )
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = "FolderPush",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                viewModel.refreshDefaultFilesStatus()
                                showDefaultFilesSheet = true
                            },
                            modifier = Modifier.testTag("topbar_default_files_btn")
                        ) {
                            Icon(
                                imageVector = if (defaultFilesStatus.isDefaultGetContentApp) {
                                    Icons.Default.CheckCircle
                                } else {
                                    Icons.Default.AdminPanelSettings
                                },
                                contentDescription = "إعدادات تطبيق الملفات الافتراضي",
                                tint = if (defaultFilesStatus.isDefaultGetContentApp) {
                                    EmeraldAccent
                                } else {
                                    MaterialTheme.colorScheme.primary
                                }
                            )
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            },
            bottomBar = {
                if (!useRail) {
                    NavigationBar {
                        appTabs.forEach { tab ->
                            val selected = currentRoute == tab.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateToTab(tab.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag("nav_tab_${tab.route}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail {
                        appTabs.forEach { tab ->
                            val selected = currentRoute == tab.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateToTab(tab.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag("nav_rail_${tab.route}")
                            )
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = initialRoute,
                    modifier = Modifier.weight(1f)
                ) {
                    composable(ROUTE_UPLOAD) {
                        UploadScreen(
                            viewModel = viewModel,
                            onShowMessage = { msg ->
                                scope.launch { snackbarHostState.showSnackbar(msg) }
                            },
                            onOpenWebBrowser = { navigateToTab(ROUTE_WEB_BROWSER) }
                        )
                    }
                    composable(ROUTE_WEB_BROWSER) {
                        WebBrowserScreen(
                            viewModel = viewModel,
                            onShowMessage = { msg ->
                                scope.launch { snackbarHostState.showSnackbar(msg) }
                            }
                        )
                    }
                    composable(ROUTE_TARGETS) {
                        TargetsScreen(
                            viewModel = viewModel,
                            onTargetLoadedNavigateUpload = { navigateToTab(ROUTE_UPLOAD) }
                        )
                    }
                    composable(ROUTE_HISTORY) {
                        HistoryScreen(
                            viewModel = viewModel,
                            onShowMessage = { msg ->
                                scope.launch { snackbarHostState.showSnackbar(msg) }
                            }
                        )
                    }
                }
            }
        }
    }
}
