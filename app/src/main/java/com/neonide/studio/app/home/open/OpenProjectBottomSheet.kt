package com.neonide.studio.app.home.open

import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
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
import com.neonide.studio.EditorActivity
import com.neonide.studio.R
import com.neonide.studio.app.home.preferences.WizardPreferences
import com.neonide.studio.app.utils.DisplayNameUtils
import com.neonide.studio.app.utils.SafeDirLister
import com.neonide.studio.ui.components.FormTextField
import com.neonide.studio.utils.FileUtil
import com.termux.shared.termux.TermuxConstants
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun OpenProjectBottomSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    var projects by remember { mutableStateOf(emptyList<File>()) }
    var searchQuery by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }

    // Load projects initially
    LaunchedEffect(Unit) {
        projects = withContext(Dispatchers.IO) { loadProjectsInternal(context) }
        isLoading = false
    }

    val startForResult =
        rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val uri = result.data?.data ?: return@rememberLauncherForActivityResult

            val dir = FileUtil.resolveUriToFile(uri)

            if (dir != null) {
                if (!dir.exists() || !dir.isDirectory) {
                    Toast.makeText(
                        context,
                        R.string.err_invalid_picked_dir,
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    scope.launch {
                        sheetState.hide()
                        onDismiss()
                    }
                    openProject(context, dir)
                }
            } else {
                Toast.makeText(
                    context,
                    R.string.err_invalid_picked_dir,
                    Toast.LENGTH_SHORT
                ).show()
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
        modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars)
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
            FormTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                placeholder = stringResource(id = R.string.search_projects_hint),
                leadingIcon = {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_search),
                        contentDescription = null
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_close),
                                contentDescription = null
                            )
                        }
                    }
                }
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
                        text = if (searchQuery.isEmpty()) {
                            stringResource(id = R.string.no_projects_found)
                        } else {
                            stringResource(id = R.string.no_projects_match_search)
                        },
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
                                        projects =
                                            withContext(Dispatchers.IO) {
                                                loadProjectsInternal(context)
                                            }
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
private fun ProjectItem(project: File, onClick: () -> Unit, onLongClick: () -> Unit) {
    val context = LocalContext.current
    val recentRank =
        remember(project) { WizardPreferences.getRecentProjectRank(context, project.absolutePath) }
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
    val hasSettings =
        File(dir, "settings.gradle").exists() || File(dir, "settings.gradle.kts").exists()
    val hasGradlew = File(dir, "gradlew").exists() || File(dir, "gradlew.bat").exists()
    val hasWrapper = File(dir, "gradle/wrapper/gradle-wrapper.properties").exists()
    val hasAppModuleBuild =
        File(dir, "app/build.gradle").exists() || File(dir, "app/build.gradle.kts").exists()
    return hasRootBuild || hasSettings || hasGradlew || hasWrapper || hasAppModuleBuild
}

private fun openProject(context: Context, root: File) {
    WizardPreferences.addRecentProject(context, root.absolutePath)
    val intent = Intent(context, EditorActivity::class.java)
    intent.putExtra(EditorActivity.EXTRA_PROJECT_DIR, root.absolutePath)
    context.startActivity(intent)
}
