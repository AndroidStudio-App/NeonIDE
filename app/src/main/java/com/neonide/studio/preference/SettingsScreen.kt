package com.neonide.studio.preference

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.neonide.studio.R
import com.neonide.studio.logger.IDEFileLogger
import com.neonide.studio.ui.components.AppIcon
import com.neonide.studio.ui.components.AppListItem
import com.neonide.studio.ui.components.AppSwitch
import com.neonide.studio.ui.components.AppTopBar
import com.neonide.studio.ui.layout.AppColumn
import com.neonide.studio.ui.layout.AppLazyColumn
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onAppSettings: (String) -> Unit,
    onEditorSettings: (String) -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { TermuxAppSharedPreferences.build(context, false) }
    var isLoggingEnabled by remember { mutableStateOf(prefs?.isIdeFileLoggingEnabled ?: false) }

    AppColumn(modifier = Modifier.fillMaxSize()) {
        AppTopBar(
            title = "Settings",
            navigationIcon = {
                IconButton(onClick = onBack) {
                    AppIcon(painterResource(R.drawable.ic_chevron_left))
                }
            }
        )
        AppLazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                AppListItem(
                    headlineText = stringResource(R.string.app_settings),
                    onClick = onAppSettings
                )
            }
            item {
                AppListItem(
                    headlineText = "Editor",
                    onClick = onEditorSettings
                )
            }
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
                    headlineText = stringResource(R.string.save_ide_logs_to_documents),
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
