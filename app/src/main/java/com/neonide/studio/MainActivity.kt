package com.neonide.studio

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.layout.statusBars

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.IconButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

import kotlinx.serialization.Serializable
import com.neonide.studio.layout.mainLayout
import com.neonide.studio.layout.GitLayout
import com.neonide.studio.layout.GitViewModel
import com.neonide.studio.layout.GitLayoutState
import com.neonide.studio.ui.theme.AppTheme
import com.neonide.studio.app.home.create.CreateProjectBottomSheet
import com.neonide.studio.app.home.open.OpenProjectBottomSheet
import com.termux.app.TermuxActivity
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences
import com.termux.shared.termux.crash.TermuxCrashUtils
import com.neonide.studio.logger.IDEFileLogger


//route for navhost
@Serializable object PermissionRoute
@Serializable object MainLayoutRoute
@Serializable object IdeConfigRoute
@Serializable object GitLayoutRoute

class MainActivity : ComponentActivity() {

    private var isFilesGranted by mutableStateOf(false)
    private var isInstallGranted by mutableStateOf(false)
    private var isNotificationsGranted by mutableStateOf(false)
    private var isSetupComplete by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        IDEFileLogger.clearLogFile()
        enableEdgeToEdge()
        window.isNavigationBarContrastEnforced = false
        
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
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets.navigationBars
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = if (isSetupComplete) MainLayoutRoute else PermissionRoute,
                modifier = Modifier.padding(innerPadding)
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
                        onOpenSettings = { navController.navigate(IdeConfigRoute) },
                        onOpenAbout = {
                            Toast.makeText(this@MainActivity, "NeonIDE v1.0", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
                composable<IdeConfigRoute> {
                    ideConfigScreen(onBack = { navController.popBackStack() })
                }
                composable<GitLayoutRoute> {
                   val viewModel: GitViewModel = viewModel()
                   val state by viewModel.uiState.collectAsState()
                   GitLayout(onBack = {navController.popBackStack()}, state = state, viewModel = viewModel)
               }
            }
        }
    }
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun ideConfigScreen(onBack: () -> Unit) {
        val context = LocalContext.current
        val prefs = remember { TermuxAppSharedPreferences.build(context, false) }
        var isLoggingEnabled by remember { mutableStateOf(prefs?.isIdeFileLoggingEnabled ?: false) }

        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text("IDE Configurations") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item {
                    Text(
                        text = "Logging",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                item {
                    ListItem(
                        headlineContent = { Text("Save IDE logs to Documents") },
                        supportingContent = { 
                            Text(if (isLoggingEnabled) {
                                "Writes logs to ${IDEFileLogger.getLogFile()?.absolutePath ?: "/sdcard/Documents/NeonIDE/logs/ide.log"}"
                            } else "Disabled")
                        },
                        trailingContent = {
                            Switch(
                                checked = isLoggingEnabled,
                                onCheckedChange = { enabled ->
                                    isLoggingEnabled = enabled
                                    prefs?.setIdeFileLoggingEnabled(enabled)
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
        } else true

        isInstallGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            packageManager.canRequestPackageInstalls()
        } else true

        isNotificationsGranted = NotificationManagerCompat.from(this).areNotificationsEnabled()
    }

    @Composable
    private fun permissionScreen() {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                permissionContent()
            }
        }
    }

    @Composable
    private fun permissionContent() {
        val context = LocalContext.current
        val allGranted = isFilesGranted && isInstallGranted && isNotificationsGranted

        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Permissions Required To Continue", fontSize = 15.sp, fontWeight = FontWeight.Bold)

            permissionItem(
                icon = Icons.Default.Storage,
                title = "All Files Access",
                description = "Required to manage files on storage",
                isGranted = isFilesGranted
            ) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    context.startActivity(intent)
                }
            }

            permissionItem(
                icon = Icons.Default.InstallMobile,
                title = "Install Unknown Apps",
                description = "Required to install Build APK file",
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
                icon = Icons.Default.Notifications,
                title = "Notifications",
                description = "Receive Notifications from the app",
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

            Button(
                onClick = { isSetupComplete = true },
                enabled = allGranted,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Continue", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }

    @Composable
    private fun permissionItem(
        icon: ImageVector,
        title: String,
        description: String,
        isGranted: Boolean,
        onClick: () -> Unit
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(32.dp))
            
            Spacer(Modifier.width(16.dp))
            
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(description, fontSize = 12.sp)
            }

            if (isGranted) {
                Text("Granted", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            } else {
                Button(
                    onClick = onClick,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Grant", fontSize = 12.sp)
                }
            }
        }
    }
}
