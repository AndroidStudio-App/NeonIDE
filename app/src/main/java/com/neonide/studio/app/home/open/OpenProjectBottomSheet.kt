package com.neonide.studio.app.home.open

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import com.neonide.studio.ui.components.AppCard
import com.neonide.studio.ui.components.AppIcon
import com.neonide.studio.ui.components.AppIconButton
import com.neonide.studio.ui.components.AppSurface
import com.neonide.studio.ui.components.FormTextField
import com.neonide.studio.ui.layout.AppBox
import com.neonide.studio.ui.layout.AppColumn
import com.neonide.studio.ui.layout.AppLazyColumn
import com.neonide.studio.ui.layout.AppRow
import com.neonide.studio.utils.DisplayNameUtils
import com.neonide.studio.utils.SafeDirLister
import com.neonide.studio.utils.rememberDirectoryLauncher
import com.termux.shared.termux.TermuxConstants
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object WizardPreferences {

    private const val PREFS_NAME = "atc_wizard_prefs"
    private const val KEY_RECENT_PROJECTS = "recent_projects"
    private const val MAX_RECENT = 5

    fun addRecentProject(context: Context, projectPath: String) {
        val recents = getRecentProjects(context).toMutableList()
        recents.remove(projectPath)
        recents.add(0, projectPath)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_RECENT_PROJECTS, recents.take(MAX_RECENT).joinToString(","))
            .apply()
    }

    fun getRecentProjects(context: Context): List<String> {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_RECENT_PROJECTS, "") ?: ""
        if (raw.isBlank()) return emptyList()
        return raw.split(',').filter { it.isNotBlank() }
            .mapNotNull { path ->
                path.takeIf { File(it).let { f -> f.exists() && f.isDirectory } }
            }
    }
}

@Composable
fun OpenProjectBottomSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var projects by remember { mutableStateOf(emptyList<File>()) }
    var searchQuery by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        projects = withContext(Dispatchers.IO) { loadProjectsInternal(context) }
        isLoading = false
    }

    val startForResult = rememberDirectoryLauncher { dir ->
        if (dir.exists() && dir.isDirectory) {
            scope.launch {
                sheetState.hide()
                onDismiss()
            }
            openProject(context, dir)
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
        contentWindowInsets = { WindowInsets(0.dp) }
    ) {
        AppColumn(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AppRow(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(id = R.string.open_project)
                )

                AppIconButton(
                    onClick = { startForResult.launch(null) }
                ) {
                    AppIcon(painter = painterResource(id = R.drawable.ic_folder))
                }
            }
            FormTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                placeholder = stringResource(id = R.string.search_projects_hint),
                leadingIcon = painterResource(id = R.drawable.ic_search),
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            AppIcon(painter = painterResource(id = R.drawable.ic_close))
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (filteredProjects.isEmpty() && !isLoading) {
                AppBox(
                    modifier = Modifier.fillMaxWidth().weight(1f),
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
                AppLazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
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

@Composable
private fun ProjectItem(project: File, onClick: () -> Unit, onLongClick: () -> Unit) {
    val context = LocalContext.current
    val recentRank =
        remember(project) {
            WizardPreferences.getRecentProjects(context).indexOf(project.absolutePath)
        }
    val isRecent = recentRank in 0..2

    AppCard(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        AppRow(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppBox(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        MaterialTheme.colorScheme.primaryContainer,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                AppIcon(
                    painter = painterResource(id = R.drawable.ic_folder),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    size = 24.dp
                )
            }

            AppColumn(
                modifier = Modifier.weight(1f).padding(start = 12.dp)
            ) {
                AppRow(
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
                        AppSurface(
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

    val recentProjectPaths = WizardPreferences.getRecentProjects(context)
    val recentProjectFiles = recentProjectPaths.mapNotNull { path ->
        val f = File(path)
        if (f.exists() && f.isDirectory) f else null
    }

    val allProjectsMap = mutableMapOf<String, File>()
    recentProjectFiles.forEach { allProjectsMap[it.absolutePath] = it }
    allProjectDirs.forEach { allProjectsMap[it.absolutePath] = it }

    return allProjectsMap.values
        .toList()
        .sortedWith(
            compareBy<File> { project ->
                val idx = recentProjectPaths.indexOf(project.absolutePath)
                if (idx >= 0) idx else Int.MAX_VALUE
            }.thenByDescending { it.lastModified() }
        )
}

private fun openProject(context: Context, root: File) {
    WizardPreferences.addRecentProject(context, root.absolutePath)
    val intent = Intent(context, EditorActivity::class.java)
    intent.putExtra(EditorActivity.EXTRA_PROJECT_DIR, root.absolutePath)
    context.startActivity(intent)
}
