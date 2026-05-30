package com.neonide.studio.app.home.open

import android.content.Context
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.neonide.studio.R
import com.neonide.studio.app.utils.DisplayNameUtils
import com.neonide.studio.app.utils.SafeFileDeleter
import com.termux.shared.termux.TermuxConstants
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

internal fun showProjectOptionsDialog(
    context: Context,
    project: File,
    onActionComplete: () -> Unit
) {
    val options = arrayOf(
        context.getString(R.string.backup_project),
        context.getString(R.string.delete_project_title_short),
        context.getString(R.string.rename)
    )

    MaterialAlertDialogBuilder(context)
        .setTitle(DisplayNameUtils.safeForUi(project.name))
        .setItems(options) { dialog, which ->
            when (which) {
                0 -> backupProject(context, project, onActionComplete)
                1 -> showDeleteProjectConfirmation(context, project, onActionComplete)
                2 -> showRenameDialog(context, project, onActionComplete)
            }
            dialog.dismiss()
        }
        .show()
}

private fun showDeleteProjectConfirmation(context: Context, project: File, onDeleted: () -> Unit) {
    MaterialAlertDialogBuilder(context)
        .setTitle(context.getString(R.string.delete_project_title))
        .setMessage(
            context.getString(
                R.string.delete_project_message,
                DisplayNameUtils.safeForUi(project.name)
            )
        )
        .setPositiveButton(context.getString(R.string.delete)) { dialog, _ ->
            dialog.dismiss()
            deleteProject(context, project, onDeleted)
        }
        .setNegativeButton(context.getString(R.string.cancel)) { dialog, _ -> dialog.dismiss() }
        .show()
}

private fun deleteProject(context: Context, project: File, onDeleted: () -> Unit) {
    val scope = CoroutineScope(Dispatchers.IO)
    scope.launch {
        val deleted = runCatching { SafeFileDeleter.deleteRecursively(project) }.getOrDefault(false)
        withContext(Dispatchers.Main) {
            if (deleted) {
                Toast.makeText(context, R.string.project_deleted_success, Toast.LENGTH_SHORT).show()
                onDeleted()
            } else {
                Toast.makeText(context, R.string.project_delete_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }
}

private fun showRenameDialog(context: Context, project: File, onComplete: () -> Unit) {
    val inputLayout = TextInputLayout(context).apply {
        boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
        hint = context.getString(R.string.new_project_name)
        val pad = (context.resources.displayMetrics.density * 16).toInt()
        setPadding(pad, 0, pad, 0)
    }

    val input = TextInputEditText(context).apply {
        setText(project.name)
        setSelection(project.name.length)
    }

    inputLayout.addView(input)

    val dialog = MaterialAlertDialogBuilder(context)
        .setTitle(context.getString(R.string.rename_project))
        .setView(inputLayout)
        .setPositiveButton(context.getString(R.string.rename), null)
        .setNegativeButton(context.getString(R.string.cancel)) { d, _ -> d.dismiss() }
        .create()

    dialog.setOnShowListener {
        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val newName = input.text?.toString()?.trim().orEmpty()
            when {
                newName.isBlank() -> {
                    Toast.makeText(context, R.string.error, Toast.LENGTH_SHORT).show()
                }

                newName == project.name -> {
                    dialog.dismiss()
                }

                !newName.matches(Regex("^[a-zA-Z][a-zA-Z0-9_]*$")) -> {
                    MaterialAlertDialogBuilder(context)
                        .setTitle(context.getString(R.string.invalid_name))
                        .setMessage(context.getString(R.string.invalid_project_name_message))
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                }

                else -> {
                    val newDir = File(project.parentFile, newName)
                    if (newDir.exists()) {
                        MaterialAlertDialogBuilder(context)
                            .setTitle(context.getString(R.string.name_already_exists))
                            .setMessage(
                                context.getString(R.string.project_name_exists_message, newName)
                            )
                            .setPositiveButton(android.R.string.ok, null)
                            .show()
                    } else {
                        dialog.dismiss()
                        renameProject(context, project, newDir, onComplete)
                    }
                }
            }
        }
    }

    dialog.show()

    input.requestFocus()
    val imm = context.getSystemService(
        android.content.Context.INPUT_METHOD_SERVICE
    ) as android.view.inputmethod.InputMethodManager
    input.postDelayed({
        imm.showSoftInput(input, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
    }, 100)
}

private fun renameProject(
    context: Context,
    oldProject: File,
    newProject: File,
    onComplete: () -> Unit
) {
    val scope = CoroutineScope(Dispatchers.IO)
    scope.launch {
        val renamed = runCatching { oldProject.renameTo(newProject) }.getOrDefault(false)

        withContext(Dispatchers.Main) {
            if (renamed) {
                val recentProjects = WizardPreferences.getRecentProjects(context).toMutableList()
                val oldIndex = recentProjects.indexOf(oldProject.absolutePath)
                if (oldIndex >= 0) {
                    recentProjects.removeAt(oldIndex)
                    recentProjects.add(oldIndex, newProject.absolutePath)
                    context.getSharedPreferences(
                        "atc_wizard_prefs",
                        android.content.Context.MODE_PRIVATE
                    )
                        .edit()
                        .putString("recent_projects", recentProjects.joinToString(","))
                        .apply()
                } else {
                    WizardPreferences.addRecentProject(context, newProject.absolutePath)
                }

                Toast.makeText(context, R.string.project_renamed_success, Toast.LENGTH_SHORT).show()
                onComplete()
            } else {
                Toast.makeText(context, R.string.project_rename_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }
}

private fun backupProject(context: Context, project: File, onComplete: () -> Unit) {
    val progressBar = android.widget.ProgressBar(context).apply { isIndeterminate = true }
    val message = android.widget.TextView(context).apply {
        text = context.getString(R.string.backup_in_progress_message)
        setPadding(0, 16, 0, 0)
    }
    val container = android.widget.LinearLayout(context).apply {
        orientation = android.widget.LinearLayout.VERTICAL
        val pad = (context.resources.displayMetrics.density * 16).toInt()
        setPadding(pad, pad, pad, pad)
        addView(progressBar)
        addView(message)
    }

    val progressDialog = androidx.appcompat.app.AlertDialog.Builder(context)
        .setTitle(context.getString(R.string.backup_in_progress_title))
        .setView(container)
        .setCancelable(false)
        .create()
    progressDialog.show()

    val scope = CoroutineScope(Dispatchers.IO)
    scope.launch {
        val backupDir = File(TermuxConstants.TERMUX_HOME_DIR, "projects/backed_up_projects").apply {
            mkdirs()
        }
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val backupFileName = "${project.name}_backup_$timestamp.zip"
        val backupFile = File(backupDir, backupFileName)

        val result = runCatching {
            ZipOutputStream(backupFile.outputStream()).use { zipOut ->
                project.walkTopDown().forEach { file ->
                    if (!file.isFile) return@forEach
                    val relativePath = file.relativeTo(project).path
                    if (
                        relativePath.startsWith("build/") ||
                        relativePath.contains("/build/") ||
                        relativePath.startsWith(".androidide/") ||
                        relativePath.startsWith(".gradle/") ||
                        relativePath.contains("/.gradle/") ||
                        relativePath.startsWith(".idea/") ||
                        relativePath.contains("/.idea/")
                    ) {
                        return@forEach
                    }
                    zipOut.putNextEntry(ZipEntry(relativePath))
                    file.inputStream().use { input -> input.copyTo(zipOut) }
                    zipOut.closeEntry()
                }
            }
        }

        withContext(Dispatchers.Main) {
            progressDialog.dismiss()
            result.fold(
                onSuccess = {
                    MaterialAlertDialogBuilder(context)
                        .setTitle(context.getString(R.string.backup_completed_title))
                        .setMessage(
                            context.getString(
                                R.string.backup_completed_message,
                                project.name,
                                backupFile.absolutePath
                            )
                        )
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                    onComplete()
                },
                onFailure = { e ->
                    MaterialAlertDialogBuilder(context)
                        .setTitle(context.getString(R.string.backup_failed_title))
                        .setMessage(
                            context.getString(
                                R.string.backup_failed_message,
                                e.localizedMessage ?: e.toString()
                            )
                        )
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                }
            )
        }
    }
}
