package com.neonide.studio.app.home.open

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.neonide.studio.R
import com.neonide.studio.ui.components.FormTextField
import com.neonide.studio.ui.layout.AppColumn
import com.neonide.studio.utils.DisplayNameUtils
import com.neonide.studio.utils.SafeFileDeleter
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ProjectActionsDialog(
    show: Boolean,
    project: File?,
    onDismiss: () -> Unit,
    onActionComplete: () -> Unit
) {
    if (!show || project == null) return

    val context = LocalContext.current
    var step by remember(show, project) { mutableStateOf(Step.Options) }
    var newName by remember(show, project) { mutableStateOf(project.name) }
    var renameError by remember(show, project) { mutableStateOf<String?>(null) }
    var backupStatus by remember(show, project) { mutableStateOf<BackupStatus?>(null) }

    if (step == Step.BackupProcessing) {
        LaunchedEffect(project) {
            backupStatus = performBackup(project)
            step = Step.BackupResult
        }
    }

    when (step) {
        Step.Options -> {
            val options = listOf(
                context.getString(R.string.backup_project),
                context.getString(R.string.delete_project_title_short),
                context.getString(R.string.rename)
            )

            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(DisplayNameUtils.safeForUi(project.name)) },
                text = {
                    AppColumn {
                        options.forEach { option ->
                            Text(
                                text = option,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        when (option) {
                                            options[0] -> step = Step.BackupProcessing
                                            options[1] -> step = Step.Delete
                                            options[2] -> step = Step.Rename
                                        }
                                    }
                                    .padding(vertical = 12.dp, horizontal = 8.dp),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(android.R.string.cancel))
                    }
                }
            )
        }

        Step.Delete -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(stringResource(R.string.delete_project_title)) },
                text = {
                    Text(
                        stringResource(
                            R.string.delete_project_message,
                            DisplayNameUtils.safeForUi(project.name)
                        )
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        deleteProject(context, project, onActionComplete)
                        onDismiss()
                    }) {
                        Text(stringResource(R.string.delete))
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(android.R.string.cancel))
                    }
                }
            )
        }

        Step.Rename -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(stringResource(R.string.rename_project)) },
                text = {
                    AppColumn {
                        FormTextField(
                            value = newName,
                            onValueChange = {
                                newName = it
                                renameError = null
                            },
                            label = stringResource(R.string.new_project_name),
                            singleLine = true,
                            isError = renameError != null,
                            supportingText = renameError,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val trimmed = newName.trim()
                        when {
                            trimmed.isBlank() -> {
                                renameError = context.getString(R.string.error)
                            }
                            trimmed == project.name -> onDismiss()
                            !trimmed.matches(Regex("^[a-zA-Z][a-zA-Z0-9_]*$")) -> {
                                renameError = context.getString(
                                    R.string.invalid_project_name_message
                                )
                            }
                            File(project.parentFile, trimmed).exists() -> {
                                renameError = context.getString(
                                    R.string.project_name_exists_message,
                                    trimmed
                                )
                            }
                            else -> {
                                renameProject(
                                    context,
                                    project,
                                    File(project.parentFile, trimmed),
                                    onActionComplete
                                )
                                onDismiss()
                            }
                        }
                    }) {
                        Text(stringResource(R.string.rename))
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(android.R.string.cancel))
                    }
                }
            )
        }

        Step.BackupProcessing -> {
            AlertDialog(
                onDismissRequest = {},
                title = { Text(stringResource(R.string.backup_in_progress_title)) },
                text = {
                    AppColumn(horizontalAlignment = Alignment.CenterHorizontally) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(16.dp))
                        Text(stringResource(R.string.backup_in_progress_message))
                    }
                },
                confirmButton = {},
                dismissButton = {}
            )
        }

        Step.BackupResult -> {
            when (val status = backupStatus) {
                is BackupStatus.Success -> {
                    AlertDialog(
                        onDismissRequest = {
                            onDismiss()
                            onActionComplete()
                        },
                        title = { Text(stringResource(R.string.backup_completed_title)) },
                        text = {
                            Text(
                                stringResource(
                                    R.string.backup_completed_message,
                                    project.name,
                                    status.path
                                )
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                onDismiss()
                                onActionComplete()
                            }) {
                                Text(stringResource(android.R.string.ok))
                            }
                        }
                    )
                }

                is BackupStatus.Failure -> {
                    AlertDialog(
                        onDismissRequest = {
                            onDismiss()
                            onActionComplete()
                        },
                        title = { Text(stringResource(R.string.backup_failed_title)) },
                        text = {
                            Text(
                                stringResource(
                                    R.string.backup_failed_message,
                                    status.error ?: ""
                                )
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                onDismiss()
                                onActionComplete()
                            }) {
                                Text(stringResource(android.R.string.ok))
                            }
                        }
                    )
                }

                null -> {}
            }
        }
    }
}

private enum class Step {
    Options,
    Delete,
    Rename,
    BackupProcessing,
    BackupResult
}

private sealed class BackupStatus {
    data class Success(val path: String) : BackupStatus()
    data class Failure(val error: String?) : BackupStatus()
}

private fun deleteProject(context: Context, project: File, onDeleted: () -> Unit) {
    val scope = CoroutineScope(Dispatchers.IO)
    scope.launch {
        val deleted = runCatching { SafeFileDeleter.deleteRecursively(project) }
            .getOrDefault(false)
        withContext(Dispatchers.Main) {
            if (deleted) {
                Toast.makeText(
                    context,
                    R.string.project_deleted_success,
                    Toast.LENGTH_SHORT
                ).show()
                onDeleted()
            } else {
                Toast.makeText(
                    context,
                    R.string.project_delete_failed,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
}

private fun renameProject(
    context: Context,
    oldProject: File,
    newProject: File,
    onComplete: () -> Unit
) {
    CoroutineScope(Dispatchers.IO).launch {
        val renamed = runCatching { oldProject.renameTo(newProject) }.getOrDefault(false)
        withContext(Dispatchers.Main) {
            if (!renamed) {
                Toast.makeText(
                    context,
                    R.string.project_rename_failed,
                    Toast.LENGTH_SHORT
                ).show()
                return@withContext
            }

            WizardPreferences.replaceRecentProject(
                context,
                oldProject.absolutePath,
                newProject.absolutePath
            )
            Toast.makeText(
                context,
                R.string.project_renamed_success,
                Toast.LENGTH_SHORT
            ).show()
            onComplete()
        }
    }
}

private val EXCLUDED_SEGMENTS = setOf("build", ".gradle")

private fun shouldExclude(relativePath: String): Boolean = EXCLUDED_SEGMENTS.any { segment ->
    relativePath == segment ||
        relativePath.startsWith("$segment/") ||
        relativePath.contains("/$segment/")
}

private suspend fun performBackup(project: File): BackupStatus = withContext(Dispatchers.IO) {
    runCatching {
        val backupDir = File("/sdcard/Documents/NeonIDE/backups").apply { mkdirs() }
        val timestamp = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
        val backupFile = File(backupDir, "${project.name}_backup_$timestamp.zip")

        ZipOutputStream(backupFile.outputStream()).use { zipOut ->
            project.walkTopDown()
                .filter { it.isFile && !shouldExclude(it.relativeTo(project).path) }
                .forEach { file ->
                    val relativePath = file.relativeTo(project).path
                    zipOut.putNextEntry(ZipEntry(relativePath))
                    file.inputStream().use { input -> input.copyTo(zipOut) }
                    zipOut.closeEntry()
                }
        }

        BackupStatus.Success(backupFile.absolutePath)
    }.getOrElse { e -> BackupStatus.Failure(e.localizedMessage ?: e.toString()) }
}
