package com.neonide.studio.editor

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neonide.studio.R
import com.neonide.studio.ui.components.AppButton
import com.neonide.studio.ui.components.AppOutlinedButton
import com.neonide.studio.ui.components.FormTextField
import com.neonide.studio.ui.layout.AppColumn
import com.neonide.studio.ui.layout.AppRow
import com.neonide.studio.utils.PersistedBoolean
import com.termux.shared.termux.TermuxConstants
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun BuildVariantPanel(
    buildVariant: String,
    onBuildVariantChange: (String) -> Unit,
    buildMenuExpanded: Boolean,
    onBuildMenuExpandedChange: (Boolean) -> Unit,
    variantBuildMenuExpanded: Boolean,
    onVariantBuildMenuExpandedChange: (Boolean) -> Unit,
    propertiesDialogEnabled: Boolean,
    onPropertiesDialogChange: (Boolean) -> Unit
) {
    AppRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        Text(
            text = stringResource(R.string.build),
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier
                .clickable { onBuildMenuExpandedChange(!buildMenuExpanded) }
                .padding(end = 4.dp)
        )

        DropdownMenu(
            expanded = buildMenuExpanded,
            onDismissRequest = { onBuildMenuExpandedChange(false) }
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.variant_build)) },
                onClick = {
                    onVariantBuildMenuExpandedChange(true)
                }
            )

            DropdownMenu(
                expanded = variantBuildMenuExpanded,
                onDismissRequest = { onVariantBuildMenuExpandedChange(false) }
            ) {
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(R.string.debug),
                            color = if (buildVariant == "debug") {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                    },
                    onClick = {
                        onBuildVariantChange("debug")
                        onVariantBuildMenuExpandedChange(false)
                        onBuildMenuExpandedChange(false)
                    }
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(R.string.release),
                            color = if (buildVariant == "release") {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                    },
                    onClick = {
                        onBuildVariantChange("release")
                        onVariantBuildMenuExpandedChange(false)
                        onBuildMenuExpandedChange(false)
                    }
                )
            }

            DropdownMenuItem(
                text = { Text(stringResource(R.string.properties)) },
                onClick = {
                    onBuildMenuExpandedChange(false)
                    onPropertiesDialogChange(true)
                }
            )
        }
    }

    if (propertiesDialogEnabled) {
        GlobalPropertiesDialog(
            onDismiss = { onPropertiesDialogChange(false) }
        )
    }
}

@Composable
private fun GlobalPropertiesDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("global_properties", Context.MODE_PRIVATE) }
    var (globalEnabled, setGlobalEnabled) = remember { PersistedBoolean(prefs, "enabled", true) }

    val defaultGradleText = """
        android.aapt2FromMavenOverride=/data/data/com.neonide.studio/files/home/android-sdk/build-tools/36.0.0/aapt2
    """.trimIndent()
    val defaultLocalText = """
        ndk.dir=/data/data/com.neonide.studio/files/home/android-sdk/ndk/29.0.14206865
        cmake.dir=/data/data/com.neonide.studio/files/usr
    """.trimIndent()

    var gradlePropertiesText by remember {
        mutableStateOf(prefs.getString("gradle_text", null) ?: defaultGradleText)
    }
    var localPropertiesText by remember {
        mutableStateOf(prefs.getString("local_text", null) ?: defaultLocalText)
    }
    val scope = rememberCoroutineScope()
    val gradleDir = File(TermuxConstants.TERMUX_HOME_DIR_PATH, ".gradle")
    val gradlePropertiesFile = File(gradleDir, "gradle.properties")
    val localPropertiesFile = File(gradleDir, "local.properties")

    val gradleChanged = gradlePropertiesText != defaultGradleText
    val localChanged = localPropertiesText != defaultLocalText
    val showReset = gradleChanged || localChanged

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.global_properties)) },
        text = {
            AppColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AppRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.enable_global_properties),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Switch(
                        checked = globalEnabled,
                        onCheckedChange = { setGlobalEnabled(it) }
                    )
                }

                FormTextField(
                    value = gradlePropertiesText,
                    onValueChange = { gradlePropertiesText = it },
                    label = "gradle.properties",
                    enabled = globalEnabled,
                    singleLine = false
                )

                FormTextField(
                    value = localPropertiesText,
                    onValueChange = { localPropertiesText = it },
                    label = "local.properties",
                    enabled = globalEnabled,
                    singleLine = false
                )

                LaunchedEffect(gradlePropertiesText, localPropertiesText) {
                    prefs.edit()
                        .putString("gradle_text", gradlePropertiesText)
                        .putString("local_text", localPropertiesText)
                        .apply()
                }
            }
        },
        confirmButton = {
            AppButton(
                text = stringResource(R.string.apply),
                onClick = {
                    scope.launch(Dispatchers.IO) {
                        if (!gradleDir.exists()) gradleDir.mkdirs()
                        if (globalEnabled) {
                            if (gradlePropertiesText.isNotEmpty()) {
                                gradlePropertiesFile.writeText(gradlePropertiesText)
                            }
                            if (localPropertiesText.isNotEmpty()) {
                                localPropertiesFile.writeText(localPropertiesText)
                            }
                        } else {
                            gradlePropertiesFile.delete()
                            localPropertiesFile.delete()
                        }
                    }
                    onDismiss()
                }
            )
        },
        dismissButton = {
            if (showReset) {
                AppOutlinedButton(
                    text = stringResource(R.string.reset),
                    onClick = {
                        gradlePropertiesText = defaultGradleText
                        localPropertiesText = defaultLocalText
                    }
                )
            }
            AppOutlinedButton(
                text = stringResource(R.string.cancel),
                onClick = onDismiss
            )
        }
    )
}
