package com.neonide.studio.preference

import android.content.Context.MODE_PRIVATE
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.neonide.studio.R
import com.neonide.studio.ui.components.AppIcon
import com.neonide.studio.ui.components.AppListItem
import com.neonide.studio.ui.components.AppTopBar
import com.neonide.studio.ui.components.RadioListDialog
import com.neonide.studio.ui.layout.AppColumn
import com.neonide.studio.ui.layout.AppLazyColumn
import com.neonide.studio.ui.theme.ColorSchemeMode
import com.neonide.studio.utils.PersistedString

@Composable
fun AppSettingsScreen(title: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("app_settings", MODE_PRIVATE)
    var modeKey by remember {
        PersistedString(prefs, "color_scheme_mode", ColorSchemeMode.SYSTEM.key)
    }
    val selectedMode = ColorSchemeMode.fromKey(modeKey)
    var showDialog by remember { mutableStateOf(false) }

    AppColumn(modifier = Modifier.fillMaxSize()) {
        AppTopBar(
            title = title,
            navigationIcon = {
                IconButton(onClick = onBack) {
                    AppIcon(painterResource(R.drawable.ic_chevron_left))
                }
            }
        )
        AppLazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                AppListItem(
                    modifier = Modifier.clickable { showDialog = true },
                    headlineText = stringResource(R.string.color_scheme),
                    trailingText = stringResource(selectedMode.labelRes)
                )
            }
        }
    }

    if (showDialog) {
        RadioListDialog(
            title = stringResource(R.string.change_color_scheme),
            items = ColorSchemeMode.entries.map { stringResource(it.labelRes) },
            selectedIndex = ColorSchemeMode.entries.indexOf(selectedMode),
            onItemClick = { index ->
                modeKey = ColorSchemeMode.entries[index].key
                showDialog = false
            },
            onDismissRequest = { showDialog = false }
        )
    }
}
