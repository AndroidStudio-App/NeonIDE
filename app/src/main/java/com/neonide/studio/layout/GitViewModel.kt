package com.neonide.studio.layout

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.neonide.studio.R
import com.neonide.studio.utils.FileUtil
import com.termux.shared.logger.Logger
import java.io.File
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.ProgressMonitor
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider

private const val TAG = "GitViewModel"

class GitViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application
    private val _uiState = MutableStateFlow(GitLayoutState())
    val uiState: StateFlow<GitLayoutState> = _uiState.asStateFlow()

    // ---- manual edit flag ----
    private var repoNameManuallyEdited = false

    // ---- load persisted preferences ----
    init {
        val prefs = application.getSharedPreferences("acs_clone_prefs", Context.MODE_PRIVATE)
        val lastDest = prefs.getString("dest", null)
            ?: com.termux.shared.termux.TermuxConstants.TERMUX_HOME_DIR_PATH + "/projects"
        val savedUrl = prefs.getString("url", "") ?: ""
        val savedRepoName = if (savedUrl.isNotBlank()) inferRepoName(savedUrl) ?: "" else ""

        _uiState.update {
            it.copy(
                url = savedUrl,
                destination = lastDest,
                repoName = savedRepoName,
                openProjectAfter = prefs.getBoolean("open_after", true),
                shallowClone = prefs.getBoolean("shallow", false),
                depth = prefs.getString("depth", "1") ?: "1",
                useCredentials = prefs.getBoolean("use_creds", false),
                username = prefs.getString("username", "") ?: "",
                branch = prefs.getString("branch", "") ?: "",
                singleBranch = prefs.getBoolean("single_branch", true),
                recurseSubmodules = prefs.getBoolean("submodules", false),
                shallowSubmodules = prefs.getBoolean("shallow_submodules", false)
            )
        }
    }

    // ---- simple update functions ----
    fun updateUrl(v: String) = _uiState.update {
        it.copy(
            url = v,
            urlError = null,
            repoName = if (repoNameManuallyEdited) it.repoName else inferRepoName(v).orEmpty()
        )
    }

    fun updateRepoName(v: String) {
        repoNameManuallyEdited = true
        _uiState.update { it.copy(repoName = v, repoNameError = null) }
    }

    fun updateDestination(v: String) = update { it.copy(destination = v, destinationError = null) }
    fun updateBranch(v: String) = update { it.copy(branch = v) }
    fun setUseCredentials(v: Boolean) = update { it.copy(useCredentials = v) }
    fun updateUsername(v: String) = update { it.copy(username = v, usernameError = null) }
    fun updatePassword(v: String) = update { it.copy(password = v, passwordError = null) }
    fun setShallowClone(v: Boolean) = update { it.copy(shallowClone = v) }
    fun updateDepth(v: String) = update { it.copy(depth = v, depthError = null) }
    fun setSingleBranch(v: Boolean) = update { it.copy(singleBranch = v) }
    fun setRecurseSubmodules(v: Boolean) = update { it.copy(recurseSubmodules = v) }
    fun setShallowSubmodules(v: Boolean) = update { it.copy(shallowSubmodules = v) }
    fun setOpenProjectAfter(v: Boolean) = update { it.copy(openProjectAfter = v) }

    private inline fun update(crossinline block: (GitLayoutState) -> GitLayoutState) {
        _uiState.update(block)
    }

    // ---- SAF directory picker ----
    fun onDirectoryPicked(uri: Uri) {
        val dir = FileUtil.resolveUriToFile(uri)

        if (dir != null && dir.exists() && dir.isDirectory) {
            updateDestination(dir.absolutePath)
        }
    }

    // ---- validation ----
    private fun validate(): Boolean {
        val s = _uiState.value
        var valid = true

        if (s.url.isBlank()) {
            update { it.copy(urlError = app.getString(R.string.url_empty)) }
            valid = false
        } else if (inferRepoName(s.url) == null) {
            update { it.copy(urlError = app.getString(R.string.url_invalid)) }
            valid = false
        }

        if (s.repoName.isBlank()) {
            update { it.copy(repoNameError = app.getString(R.string.name_empty)) }
            valid = false
        } else if (!s.repoName.matches(Regex("[A-Za-z0-9._-]+"))) {
            update { it.copy(repoNameError = app.getString(R.string.name_invalid)) }
            valid = false
        }

        if (s.shallowClone) {
            val d = s.depth.toIntOrNull()
            if (d == null || d < 1) {
                update { it.copy(depthError = app.getString(R.string.depth_invalid)) }
                valid = false
            }
        }

        if (s.useCredentials) {
            if (s.username.isBlank()) {
                update { it.copy(usernameError = app.getString(R.string.username_required)) }
                valid = false
            }
            if (s.password.isBlank()) {
                update { it.copy(passwordError = app.getString(R.string.password_required)) }
                valid = false
            }
        }

        val dest = File(s.destination)
        if (!dest.exists() || !dest.isDirectory) {
            // Attempt to create it
            if (!dest.mkdirs()) {
                update {
                    it.copy(destinationError = app.getString(R.string.dest_not_exist))
                }
                valid = false
            }
        }

        if (valid && File(dest, s.repoName).exists()) {
            update { it.copy(destinationError = app.getString(R.string.dest_already_exists)) }
            valid = false
        }

        return valid
    }

    // ---- clone ----
    fun startClone(context: Context, onSuccess: (File) -> Unit) {
        if (!validate()) return

        val state = _uiState.value
        val targetDir = File(state.destination, state.repoName)

        // persist current settings
        context.getSharedPreferences("acs_clone_prefs", Context.MODE_PRIVATE)
            .edit().apply {
                putString("url", state.url)
                putString("dest", state.destination)
                putBoolean("open_after", state.openProjectAfter)
                putBoolean("shallow", state.shallowClone)
                putString("depth", state.depth)
                putBoolean("use_creds", state.useCredentials)
                putString("username", state.username)
                putString("branch", state.branch)
                putBoolean("single_branch", state.singleBranch)
                putBoolean("submodules", state.recurseSubmodules)
                putBoolean("shallow_submodules", state.shallowSubmodules)
                apply()
            }

        viewModelScope.launch(Dispatchers.IO) {
            performClone(targetDir, onSuccess)
        }
    }

    private suspend fun performClone(targetDir: File, onSuccess: (File) -> Unit) {
        val state = _uiState.value
        _uiState.update {
            it.copy(
                isCloning = true,
                isCancelled = false,
                progressPercent = 0,
                progressText = "",
                statusText = app.getString(R.string.cloning_status),
                urlError = null,
                destinationError = null
            )
        }

        val progressMonitor = createProgressMonitor()

        try {
            val cmd = Git.cloneRepository()
                .setURI(state.url)
                .setDirectory(targetDir)
                .setProgressMonitor(progressMonitor)

            configureCloneCommand(cmd, state)
            try {
                cmd.call().close()
            } catch (closeEx: java.io.IOException) {
                Logger.logError(TAG, "Closing git resource failed: ${closeEx.message}")
            }

            handleCloneResult(targetDir, onSuccess)
        } catch (e: org.eclipse.jgit.api.errors.GitAPIException) {
            handleCloneError(targetDir, e)
        } catch (e: java.io.IOException) {
            handleCloneError(targetDir, e)
        } catch (e: java.lang.IllegalStateException) {
            handleCloneError(targetDir, e)
        }
    }

    private fun createProgressMonitor() = object : ProgressMonitor {
        private var totalWork = 0
        private var completedWork = 0
        private var taskName = ""

        override fun start(totalTasks: Int) { /* no-op */ }
        override fun beginTask(title: String?, totalWork: Int) {
            this.taskName = title ?: ""
            this.totalWork = totalWork
            this.completedWork = 0
            pushProgress()
        }
        override fun update(completed: Int) {
            completedWork += completed
            pushProgress()
        }
        override fun showDuration(show: Boolean) { /* no-op */ }
        override fun endTask() { /* no-op */ }
        override fun isCancelled(): Boolean = _uiState.value.isCancelled

        private fun pushProgress() {
            viewModelScope.launch(Dispatchers.Main) {
                _uiState.update {
                    it.copy(
                        progressText = taskName,
                        progressPercent = if (totalWork >
                            0
                        ) {
                            completedWork * 100 / totalWork
                        } else {
                            0
                        },
                        statusText = if (totalWork >
                            0
                        ) {
                            "$completedWork / $totalWork objects"
                        } else {
                            "$completedWork objects"
                        }
                    )
                }
            }
        }
    }

    private fun configureCloneCommand(
        cmd: org.eclipse.jgit.api.CloneCommand,
        state: GitLayoutState
    ) {
        if (state.branch.isNotBlank()) cmd.setBranch(state.branch)
        if (!state.singleBranch) cmd.setCloneAllBranches(true)
        if (state.shallowClone) cmd.setDepth(state.depth.toIntOrNull() ?: 1)
        if (state.recurseSubmodules) cmd.setCloneSubmodules(true)
        if (state.useCredentials) {
            cmd.setCredentialsProvider(
                UsernamePasswordCredentialsProvider(state.username, state.password)
            )
        }
    }

    private suspend fun handleCloneResult(targetDir: File, onSuccess: (File) -> Unit) {
        withContext(Dispatchers.Main) {
            if (_uiState.value.isCancelled) {
                targetDir.deleteRecursively()
                _uiState.update {
                    it.copy(isCloning = false, statusText = app.getString(R.string.cancelled))
                }
            } else {
                _uiState.update {
                    it.copy(
                        isCloning = false,
                        statusText = app.getString(R.string.done, targetDir.absolutePath)
                    )
                }
                if (_uiState.value.openProjectAfter) {
                    onSuccess(targetDir)
                }
            }
        }
    }

    private suspend fun handleCloneError(targetDir: File, e: Exception) {
        targetDir.deleteRecursively()
        withContext(Dispatchers.Main) {
            _uiState.update {
                it.copy(
                    isCloning = false,
                    statusText = app.getString(R.string.failed),
                    destinationError =
                    e.localizedMessage ?: e.message ?: app.getString(R.string.unknown_error)
                )
            }
        }
    }

    fun cancelClone() {
        _uiState.update {
            it.copy(isCancelled = true, statusText = app.getString(R.string.stopping))
        }
    }

    private fun inferRepoName(url: String): String? = try {
        val trimmed = url.trim().removeSuffix("/")
        val path = URI(trimmed).path ?: trimmed.substringAfterLast(':')
        path.split('/').lastOrNull { it.isNotBlank() }?.removeSuffix(".git")
    } catch (_: Exception) {
        null
    }
}
