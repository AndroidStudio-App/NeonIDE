package com.neonide.studio

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.neonide.studio.app.home.create.CreateProjectBottomSheet
import com.neonide.studio.app.home.open.OpenProjectBottomSheet
import com.neonide.studio.extensions.ExtensionsScreen
import com.neonide.studio.layout.GitLayout
import com.neonide.studio.layout.GitLayoutState
import com.neonide.studio.layout.GitViewModel
import com.neonide.studio.layout.mainLayout
import com.neonide.studio.logger.IDEFileLogger
import com.neonide.studio.ui.components.AppButton
import com.neonide.studio.ui.components.AppCard
import com.neonide.studio.ui.components.AppIcon
import com.neonide.studio.ui.components.AppListItem
import com.neonide.studio.ui.components.AppScaffold
import com.neonide.studio.ui.components.AppSwitch
import com.neonide.studio.ui.components.AppTopBar
import com.neonide.studio.ui.layout.AppBox
import com.neonide.studio.ui.layout.AppColumn
import com.neonide.studio.ui.layout.AppLazyColumn
import com.neonide.studio.ui.layout.AppRow
import com.neonide.studio.ui.theme.AppTheme
import com.termux.app.TermuxActivity
import com.termux.shared.termux.crash.TermuxCrashUtils
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences
import kotlinx.serialization.Serializable

// route for navhost
@Serializable object PermissionRoute

@Serializable object MainLayoutRoute

@Serializable object IdeConfigRoute

@Serializable object GitLayoutRoute

@Serializable object ExtensionsRoute

class MainActivity : ComponentActivity() {

    private var isFilesGranted by mutableStateOf(false)
    private var isInstallGranted by mutableStateOf(false)
    private var isNotificationsGranted by mutableStateOf(false)
    private var isSetupComplete by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initial check on startup
        updatePermissionStates()

        // skip if granted all
        if (isFilesGranted && isInstallGranted && isNotificationsGranted) {
            isSetupComplete = true
        }

        setContent {
            AppTheme {
                mainNavigation()
            }
        }
    }

    @Composable
    private fun mainNavigation() {
        val navController = rememberNavController()
        AppScaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background
        ) {
            NavHost(
                navController = navController,
                startDestination = if (isSetupComplete) MainLayoutRoute else PermissionRoute,
                modifier = Modifier.fillMaxSize()
            ) {
                composable<PermissionRoute> {
                    permissionScreen()
                }
                composable<MainLayoutRoute> {
                    val showOpenProject = remember { mutableStateOf(false) }
                    val showCreateProject = remember { mutableStateOf(false) }

                    if (showOpenProject.value) {
                        OpenProjectBottomSheet(
                            onDismiss = { showOpenProject.value = false }
                        )
                    }

                    if (showCreateProject.value) {
                        CreateProjectBottomSheet(
                            onDismiss = { showCreateProject.value = false }
                        )
                    }

                    mainLayout(
                        onSetupDevKit = { DevKitSetup.startSetup(this@MainActivity) },
                        onCreateProject = { showCreateProject.value = true },
                        onOpenProject = { showOpenProject.value = true },
                        onCloneRepo = { navController.navigate(GitLayoutRoute) },
                        onOpenTerminal = {
                            startActivity(Intent(this@MainActivity, TermuxActivity::class.java))
                        },
                        onOpenExtensions = { navController.navigate(ExtensionsRoute) },
                        onOpenSettings = { navController.navigate(IdeConfigRoute) },
                        onOpenAbout = {
                            Toast.makeText(
                                this@MainActivity,
                                "NeonIDE v1.0",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    )
                }
                composable<IdeConfigRoute> {
                    ideConfigScreen(onBack = { navController.popBackStack() })
                }
                composable<GitLayoutRoute> {
                    val viewModel: GitViewModel = viewModel()
                    val state by viewModel.uiState.collectAsState()
                    GitLayout(
                        onBack = { navController.popBackStack() },
                        state = state,
                        viewModel = viewModel,
                        onFinished = { navController.popBackStack() }
                    )
                }
                composable<ExtensionsRoute> {
                    ExtensionsScreen(context = this@MainActivity, onBack = {
                        navController.popBackStack()
                    })
                }
            }
        }
    }

    @Composable
    private fun ideConfigScreen(onBack: () -> Unit) {
        val context = LocalContext.current
        val prefs = remember { TermuxAppSharedPreferences.build(context, false) }
        var isLoggingEnabled by remember { mutableStateOf(prefs?.isIdeFileLoggingEnabled ?: false) }

        AppColumn(modifier = Modifier.fillMaxSize()) {
            AppTopBar(
                title = "IDE Configurations",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        AppIcon(painterResource(R.drawable.ic_chevron_left))
                    }
                }
            )
            AppLazyColumn(modifier = Modifier.fillMaxSize()) {
                item {
                    Text(
                        text = "Logging",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                item {
                    AppListItem(
                        headlineContent = { Text("Save IDE logs to Documents") },
                        supportingContent = {
                            Text(
                                if (isLoggingEnabled) {
                                    "Writes logs to ${IDEFileLogger.getLogFile()?.absolutePath ?: "/sdcard/Documents/NeonIDE/logs/ide.log"}"
                                } else {
                                    "Disabled"
                                }
                            )
                        },
                        trailingContent = {
                            AppSwitch(
                                checked = isLoggingEnabled,
                                onCheckedChange = { enabled ->
                                    isLoggingEnabled = enabled
                                    prefs?.setIdeFileLoggingEnabled(enabled)
                                    if (!enabled) {
                                        IDEFileLogger.clearLogFile()
                                    }
                                }
                            )
                        }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStates()
        TermuxCrashUtils.notifyAppCrashFromCrashLogFile(this, "MainActivity")
    }

    private fun updatePermissionStates() {
        isFilesGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }

        isInstallGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            packageManager.canRequestPackageInstalls()
        } else {
            true
        }

        isNotificationsGranted = NotificationManagerCompat.from(this).areNotificationsEnabled()
    }

    @Composable
    private fun permissionScreen() {
        AppBox(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            AppCard(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                permissionContent()
            }
        }
    }

    @Composable
    private fun permissionContent() {
        val context = LocalContext.current
        val allGranted = isFilesGranted && isInstallGranted && isNotificationsGranted

        AppColumn(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                stringResource(R.string.permissions_required_to_continue),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )

            permissionItem(
                icon = painterResource(id = R.drawable.ic_files),
                title = stringResource(R.string.all_files_access),
                description = stringResource(R.string.all_files_access_desc),
                isGranted = isFilesGranted
            ) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION
                    ).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    context.startActivity(intent)
                }
            }

            permissionItem(
                icon = painterResource(id = R.drawable.ic_install),
                title = stringResource(R.string.install_unknown_apps),
                description = stringResource(R.string.install_unknown_apps_desc),
                isGranted = isInstallGranted
            ) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    context.startActivity(intent)
                }
            }

            permissionItem(
                icon = painterResource(id = R.drawable.ic_notification),
                title = stringResource(R.string.notifications),
                description = stringResource(R.string.notifications_desc),
                isGranted = isNotificationsGranted
            ) {
                val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    }
                } else {
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                }
                context.startActivity(intent)
            }

            AppButton(
                text = stringResource(R.string.continue_button),
                onClick = { isSetupComplete = true },
                enabled = allGranted,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                shape = RoundedCornerShape(12.dp)
            )
        }
    }

    @Composable
    private fun permissionItem(
        icon: Painter,
        title: String,
        description: String,
        isGranted: Boolean,
        onClick: () -> Unit
    ) {
        AppRow(Modifier.fillMaxWidth()) {
            AppIcon(icon, size = 32.dp)

            Spacer(Modifier.width(16.dp))

            AppColumn(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(description, fontSize = 12.sp)
            }

            if (isGranted) {
                Text(
                    stringResource(R.string.granted),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            } else {
                AppButton(
                    text = stringResource(R.string.grant),
                    onClick = onClick,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    shape = RoundedCornerShape(8.dp)
                )
            }
        }
    }
}
