package com.neonide.studio.app.home.open

import android.content.Context
import android.content.Intent
import android.os.Environment
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.neonide.studio.R
import com.neonide.studio.app.SoraEditorActivityK
import com.neonide.studio.app.home.preferences.WizardPreferences
import com.neonide.studio.app.utils.DisplayNameUtils
import com.neonide.studio.app.utils.SafeDirLister
import com.neonide.studio.app.utils.SafeFileDeleter
import com.neonide.studio.shared.termux.TermuxConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun OpenProjectBottomSheet(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    var projects by remember { mutableStateOf(emptyList<File>()) }
    var searchQuery by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }

    // Constants from original Fragment
    val ANDROID_DOCS_AUTHORITY = "com.android.externalstorage.documents"
    val TERMUX_DOCS_AUTHORITY = "com.neonide.studio.documents"

    // Load projects initially
    LaunchedEffect(Unit) {
        projects = withContext(Dispatchers.IO) { loadProjectsInternal(context) }
        isLoading = false
    }

    val startForResult = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data ?: return@rememberLauncherForActivityResult
        
        val pickedDir = DocumentFile.fromTreeUri(context, uri)
        if (pickedDir == null || !pickedDir.exists()) {
            Toast.makeText(context, R.string.acs_err_invalid_picked_dir, Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }

        val treeDocId = DocumentsContract.getTreeDocumentId(uri)
        val docUri = DocumentsContract.buildDocumentUriUsingTree(uri, treeDocId)
        val docId = DocumentsContract.getDocumentId(docUri)
        val authority = docUri.authority

        val dir: File? = when (authority) {
            TERMUX_DOCS_AUTHORITY -> File(docId)
            ANDROID_DOCS_AUTHORITY -> {
                val split = docId.split(':')
                if (split.size < 2 || split[0] != "primary") {
                    Toast.makeText(context, R.string.acs_err_select_primary_storage, Toast.LENGTH_LONG).show()
                    null
                } else {
                    File(Environment.getExternalStorageDirectory(), split[1])
                }
            }
            else -> {
                Toast.makeText(context, context.getString(R.string.acs_err_authority_not_allowed, authority), Toast.LENGTH_LONG).show()
                null
            }
        }

        if (dir != null) {
            if (!dir.exists() || !dir.isDirectory) {
                Toast.makeText(context, R.string.acs_err_invalid_picked_dir, Toast.LENGTH_SHORT).show()
            } else {
                scope.launch {
                    sheetState.hide()
                    onDismiss()
                }
                openProject(context, dir)
            }
        }
    }

    val filteredProjects = if (searchQuery.isBlank()) {
        projects
    } else {
        projects.filter { p ->
            p.name.contains(searchQuery, ignoreCase = true) ||
                    p.absolutePath.contains(searchQuery, ignoreCase = true)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.fillMaxHeight()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(id = R.string.open_project),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                
                OutlinedButton(
                    onClick = { startForResult.launch(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)) },
                    modifier = Modifier.size(48.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_folder),
                        contentDescription = stringResource(id = R.string.browse_other_location)
                    )
                }
            }

            // Search
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                placeholder = { Text(stringResource(id = R.string.search_projects_hint)) },
                leadingIcon = { Icon(painter = painterResource(id = R.drawable.ic_search), contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(painter = painterResource(id = R.drawable.ic_close_24), contentDescription = null)
                        }
                    }
                },
                singleLine = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (filteredProjects.isEmpty() && !isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (searchQuery.isEmpty()) 
                            stringResource(id = R.string.no_projects_found)
                        else 
                            stringResource(id = R.string.no_projects_match_search),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.secondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(24.dp)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredProjects) { project ->
                        ProjectItem(
                            project = project,
                            onClick = {
                                WizardPreferences.addRecentProject(context, project.absolutePath)
                                scope.launch {
                                    sheetState.hide()
                                    onDismiss()
                                }
                                openProject(context, project)
                            },
                            onLongClick = {
                                showProjectOptionsDialog(context, project) {
                                    scope.launch {
                                        projects = withContext(Dispatchers.IO) { loadProjectsInternal(context) }
                                    }
                                }
                            }
                        )
                    }
                    
                    item {
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProjectItem(
    project: File,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val context = LocalContext.current
    val recentRank = remember(project) { WizardPreferences.getRecentProjectRank(context, project.absolutePath) }
    val isRecent = recentRank in 0..2

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        MaterialTheme.colorScheme.primaryContainer,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_folder),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(24.dp)
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = DisplayNameUtils.safeForUi(project.name),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    if (isRecent) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 8.dp)
                        ) {
                            Text(
                                text = stringResource(id = R.string.recent_badge),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                Text(
                    text = DisplayNameUtils.safeForUi(project.absolutePath, 260),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun loadProjectsInternal(context: Context): List<File> {
    val projectsDir = File(TermuxConstants.TERMUX_HOME_DIR, "projects")
    val allProjectDirs = SafeDirLister.listDirs(projectsDir)
    val projectDirProjects = allProjectDirs.filter { isValidAndroidProjectInternal(it) }

    val recentProjectPaths = WizardPreferences.getRecentProjects(context)
    val recentProjectFiles = recentProjectPaths.mapNotNull { path ->
        val f = File(path)
        if (f.exists() && f.isDirectory && isValidAndroidProjectInternal(f)) f else null
    }

    val allProjectsMap = mutableMapOf<String, File>()
    recentProjectFiles.forEach { allProjectsMap[it.absolutePath] = it }
    projectDirProjects.forEach { allProjectsMap[it.absolutePath] = it }

    return allProjectsMap.values
        .toList()
        .sortedWith(
            compareBy<File> { project ->
                val idx = recentProjectPaths.indexOf(project.absolutePath)
                if (idx >= 0) idx else Int.MAX_VALUE
            }.thenByDescending { it.lastModified() }
        )
}

private fun isValidAndroidProjectInternal(dir: File): Boolean {
    if (!dir.isDirectory) return false
    val hasRootBuild = File(dir, "build.gradle").exists() || File(dir, "build.gradle.kts").exists()
    val hasSettings = File(dir, "settings.gradle").exists() || File(dir, "settings.gradle.kts").exists()
    val hasGradlew = File(dir, "gradlew").exists() || File(dir, "gradlew.bat").exists()
    val hasWrapper = File(dir, "gradle/wrapper/gradle-wrapper.properties").exists()
    val hasAppModuleBuild = File(dir, "app/build.gradle").exists() || File(dir, "app/build.gradle.kts").exists()
    return hasRootBuild || hasSettings || hasGradlew || hasWrapper || hasAppModuleBuild
}

private fun openProject(context: Context, root: File) {
    WizardPreferences.addRecentProject(context, root.absolutePath)
    val intent = Intent(context, SoraEditorActivityK::class.java)
    intent.putExtra(SoraEditorActivityK.EXTRA_PROJECT_DIR, root.absolutePath)
    context.startActivity(intent)
}

private fun showProjectOptionsDialog(context: Context, project: File, onActionComplete: () -> Unit) {
    val options = arrayOf(
        context.getString(R.string.backup_project),
        context.getString(R.string.delete_project_title_short),
        context.getString(R.string.rename),
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
        .setMessage(context.getString(R.string.delete_project_message, DisplayNameUtils.safeForUi(project.name)))
        .setPositiveButton(context.getString(R.string.delete)) { dialog, _ ->
            dialog.dismiss()
            deleteProject(context, project, onDeleted)
        }
        .setNegativeButton(context.getString(R.string.cancel)) { dialog, _ -> dialog.dismiss() }
        .show()
}

private fun deleteProject(context: Context, project: File, onDeleted: () -> Unit) {
    val scope = kotlinx.coroutines.CoroutineScope(Dispatchers.IO)
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
                            .setMessage(context.getString(R.string.project_name_exists_message, newName))
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
    val imm = context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
    input.postDelayed({
        imm.showSoftInput(input, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
    }, 100)
}

private fun renameProject(context: Context, oldProject: File, newProject: File, onComplete: () -> Unit) {
    val scope = kotlinx.coroutines.CoroutineScope(Dispatchers.IO)
    scope.launch {
        val renamed = runCatching { oldProject.renameTo(newProject) }.getOrDefault(false)

        withContext(Dispatchers.Main) {
            if (renamed) {
                val recentProjects = WizardPreferences.getRecentProjects(context).toMutableList()
                val oldIndex = recentProjects.indexOf(oldProject.absolutePath)
                if (oldIndex >= 0) {
                    recentProjects.removeAt(oldIndex)
                    recentProjects.add(oldIndex, newProject.absolutePath)
                    context.getSharedPreferences("atc_wizard_prefs", android.content.Context.MODE_PRIVATE)
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

    val scope = kotlinx.coroutines.CoroutineScope(Dispatchers.IO)
    scope.launch {
        val backupDir = File(TermuxConstants.TERMUX_HOME_DIR, "projects/backed_up_projects").apply { mkdirs() }
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
                        .setMessage(context.getString(R.string.backup_completed_message, project.name, backupFile.absolutePath))
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                    onComplete()
                },
                onFailure = { e ->
                    MaterialAlertDialogBuilder(context)
                        .setTitle(context.getString(R.string.backup_failed_title))
                        .setMessage(context.getString(R.string.backup_failed_message, e.localizedMessage ?: e.toString()))
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                },
            )
        }
    }
}
