package com.neonide.studio.app.home.clone

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.neonide.studio.R
import com.neonide.studio.app.SoraEditorActivityK
import com.neonide.studio.app.home.preferences.WizardPreferences
import com.neonide.studio.databinding.DialogCloneRepositoryBinding
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.errors.GitAPIException
import org.eclipse.jgit.lib.ProgressMonitor
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import java.io.File
import java.net.URI

/**
 * Dialog that clones a git repository into Termux $HOME/projects.
 */
class CloneRepositoryDialogFragment : DialogFragment() {

    private var repoNameManuallyEdited = false
    private var lastProgressBytes: Long? = null
    private var lastProgressPercent: Int? = null
    private var lastProgressSpeedBps: Long? = null
    private var startTime: Long = 0

    /** The full target directory for the in-progress clone (if any). Used for cleanup on cancel. */
    private var activeTargetDir: File? = null

    // Persisted UI state
    private val prefs by lazy { requireContext().getSharedPreferences("acs_clone_prefs", Context.MODE_PRIVATE) }
    private fun prefGetBool(key: String, def: Boolean) = prefs.getBoolean(key, def)
    private fun prefGetString(key: String, def: String? = null) = prefs.getString(key, def)
    private fun prefPut(block: (android.content.SharedPreferences.Editor) -> Unit) {
        prefs.edit().also(block).apply()
    }

    private val uiHandler by lazy { android.os.Handler(android.os.Looper.getMainLooper()) }

    companion object {
        private const val ANDROID_DOCS_AUTHORITY = "com.android.externalstorage.documents"
        private const val TERMUX_DOCS_AUTHORITY = "com.neonide.studio.documents"
    }

    private var binding: DialogCloneRepositoryBinding? = null

    private var isCloning: Boolean = false
    private var isCancelled: Boolean = false

    private val startForResult = registerForActivityResult(StartActivityForResult()) { result ->
        val uri = result?.data?.data ?: return@registerForActivityResult
        val ctx = requireContext()

        val pickedDir = DocumentFile.fromTreeUri(ctx, uri)
        if (pickedDir == null || !pickedDir.exists()) {
            Toast.makeText(ctx, R.string.acs_err_invalid_picked_dir, Toast.LENGTH_SHORT).show()
            return@registerForActivityResult
        }

        val treeDocId = DocumentsContract.getTreeDocumentId(uri)
        val docUri = DocumentsContract.buildDocumentUriUsingTree(uri, treeDocId)
        val docId = DocumentsContract.getDocumentId(docUri)
        val authority = docUri.authority

        val dir: File = when (authority) {
            TERMUX_DOCS_AUTHORITY -> File(docId)
            ANDROID_DOCS_AUTHORITY -> {
                val split = docId.split(':')
                if (split.size < 2 || split[0] != "primary") {
                    Toast.makeText(ctx, R.string.acs_err_select_primary_storage, Toast.LENGTH_LONG).show()
                    return@registerForActivityResult
                }
                File(Environment.getExternalStorageDirectory(), split[1])
            }
            else -> {
                Toast.makeText(ctx, getString(R.string.acs_err_authority_not_allowed, authority), Toast.LENGTH_LONG).show()
                return@registerForActivityResult
            }
        }

        if (!dir.exists() || !dir.isDirectory) {
            Toast.makeText(ctx, R.string.acs_err_invalid_picked_dir, Toast.LENGTH_SHORT).show()
            return@registerForActivityResult
        }

        binding?.destinationEditText?.setText(dir.absolutePath)
        binding?.destinationLayout?.error = null
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val ctx = requireContext()
        val inflater = LayoutInflater.from(ctx)
        binding = DialogCloneRepositoryBinding.inflate(inflater)

        // Use a sensible default if no previous location exists
        val defaultProjectsDir = File(Environment.getExternalStorageDirectory(), "Documents/NeonIDE/projects").absolutePath
        val lastDir = WizardPreferences.getLastSaveLocation(ctx)

        // Restore previous values
        binding!!.urlEditText.setText(prefGetString("url", "") ?: "")
        binding!!.destinationEditText.setText(prefGetString("dest", lastDir ?: defaultProjectsDir) ?: (lastDir ?: defaultProjectsDir))
        binding!!.openAfterCloneCheckBox.isChecked = prefGetBool("open_after", true)
        binding!!.shallowCloneSwitch.isChecked = prefGetBool("shallow", false)
        binding!!.depthEditText.setText(prefGetString("depth", "1") ?: "1")

        // Enable depth input only when shallow clone is enabled
        binding!!.depthLayout.isEnabled = binding!!.shallowCloneSwitch.isChecked
        binding!!.shallowCloneSwitch.setOnCheckedChangeListener { _, enabled ->
            binding!!.depthLayout.isEnabled = enabled
            if (!enabled) {
                binding!!.depthLayout.error = null
            }
        }

        // Credentials section
        binding!!.useCredentialsSwitch.isChecked = prefGetBool("use_creds", false)
        binding!!.credentialsContainer.visibility = if (binding!!.useCredentialsSwitch.isChecked) View.VISIBLE else View.GONE
        binding!!.usernameEditText.setText(prefGetString("username", "") ?: "")
        binding!!.passwordEditText.setText("")

        binding!!.useCredentialsSwitch.setOnCheckedChangeListener { _, isChecked ->
            binding!!.credentialsContainer.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        // Advanced defaults
        binding!!.singleBranchSwitch.isChecked = prefGetBool("single_branch", true)
        binding!!.recurseSubmodulesSwitch.isChecked = prefGetBool("submodules", false)
        binding!!.shallowSubmodulesSwitch.isChecked = prefGetBool("shallow_submodules", false)
        binding!!.shallowSubmodulesSwitch.isEnabled = false
        binding!!.recurseSubmodulesSwitch.setOnCheckedChangeListener { _, enabled ->
            binding!!.shallowSubmodulesSwitch.isEnabled = enabled
            if (!enabled) binding!!.shallowSubmodulesSwitch.isChecked = false
        }

        // Clear previous errors
        binding!!.urlEditText.addTextChangedListener(clearErrorWatcher { binding!!.urlLayout.error = null })
        binding!!.destinationEditText.addTextChangedListener(clearErrorWatcher { binding!!.destinationLayout.error = null })
        binding!!.usernameEditText.addTextChangedListener(clearErrorWatcher { binding!!.usernameLayout.error = null })
        binding!!.passwordEditText.addTextChangedListener(clearErrorWatcher { binding!!.passwordLayout.error = null })
        binding!!.repoNameEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (binding!!.repoNameEditText.hasFocus()) repoNameManuallyEdited = true
                binding!!.repoNameLayout.error = null
            }
        })

        binding!!.urlEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (repoNameManuallyEdited) return
                val inferred = inferRepoName(s?.toString().orEmpty()) ?: return
                binding!!.repoNameEditText.setText(inferred)
            }
        })

        binding!!.destinationLayout.setEndIconOnClickListener { pickDirectory() }
        binding!!.repoNameEditText.setText(inferRepoName(binding!!.urlEditText.text?.toString().orEmpty()) ?: "")
        binding!!.branchEditText.setText(prefGetString("branch", "") ?: "")

        val dialog = MaterialAlertDialogBuilder(ctx)
            .setView(binding!!.root)
            .setPositiveButton(R.string.acs_clone_action, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        dialog.setOnShowListener {
            val positive = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
            val negative = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEGATIVE)

            positive.setOnClickListener {
                if (isCloning) return@setOnClickListener
                startClone()
            }

            negative.setOnClickListener {
                if (isCloning) {
                    stopClone()
                } else {
                    dismiss()
                }
            }
        }

        return dialog
    }

    private fun pickDirectory() {
        try {
            startForResult.launch(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE))
        } catch (e: Exception) {
            Toast.makeText(requireContext(), getString(R.string.acs_dir_picker_failed, e.message), Toast.LENGTH_LONG).show()
        }
    }

    private fun startClone() {
        val ctx = requireContext().applicationContext
        val rawUrl = binding!!.urlEditText.text?.toString()?.trim().orEmpty()
        val destBase = binding!!.destinationEditText.text?.toString()?.trim().orEmpty()
        val repoNameInput = binding!!.repoNameEditText.text?.toString()?.trim().orEmpty()

        prefPut {
            it.putString("url", rawUrl)
            it.putString("dest", destBase)
            it.putBoolean("open_after", binding!!.openAfterCloneCheckBox.isChecked)
            it.putBoolean("shallow", binding!!.shallowCloneSwitch.isChecked)
            it.putString("depth", binding!!.depthEditText.text?.toString()?.trim().orEmpty())
            it.putBoolean("use_creds", binding!!.useCredentialsSwitch.isChecked)
            it.putString("username", binding!!.usernameEditText.text?.toString()?.trim().orEmpty())
            it.putString("branch", binding!!.branchEditText.text?.toString()?.trim().orEmpty())
            it.putBoolean("single_branch", binding!!.singleBranchSwitch.isChecked)
            it.putBoolean("submodules", binding!!.recurseSubmodulesSwitch.isChecked)
            it.putBoolean("shallow_submodules", binding!!.shallowSubmodulesSwitch.isChecked)
        }

        if (rawUrl.isBlank()) {
            binding!!.urlLayout.error = getString(R.string.acs_clone_error_empty_url)
            return
        }

        val inferred = inferRepoName(rawUrl)
        if (inferred == null) {
            binding!!.urlLayout.error = getString(R.string.acs_clone_error_invalid_url)
            return
        }

        val repoName = repoNameInput.ifBlank { inferred }
        if (!repoName.matches(Regex("[A-Za-z0-9._-]+"))) {
            binding!!.repoNameLayout.error = getString(R.string.invalid_name)
            return
        }

        val baseDir = File(destBase)
        if (!baseDir.exists()) baseDir.mkdirs()
        if (!baseDir.exists() || !baseDir.isDirectory) {
            binding!!.destinationLayout.error = getString(R.string.acs_err_invalid_picked_dir)
            return
        }
        if (!baseDir.canWrite()) {
            binding!!.destinationLayout.error = getString(R.string.acs_clone_error_destination_not_writable)
            return
        }

        val targetDir = File(baseDir, repoName)
        if (targetDir.exists()) {
            binding!!.destinationLayout.error = getString(R.string.acs_clone_error_destination_exists)
            return
        }

        activeTargetDir = targetDir
        isCloning = true
        isCancelled = false
        startTime = System.currentTimeMillis()
        setUiBusy(true, getString(R.string.acs_clone_in_progress))

        Thread {
            try {
                val cloneCommand = Git.cloneRepository()
                    .setURI(rawUrl)
                    .setDirectory(targetDir)
                    .setProgressMonitor(JGitProgressMonitor())

                // Branch/Single branch
                val branch = binding!!.branchEditText.text?.toString()?.trim().orEmpty()
                if (branch.isNotBlank()) {
                    cloneCommand.setBranch(branch)
                }
                cloneCommand.setCloneAllBranches(!binding!!.singleBranchSwitch.isChecked)

                // Shallow clone
                if (binding!!.shallowCloneSwitch.isChecked) {
                    val depth = binding!!.depthEditText.text?.toString()?.toIntOrNull() ?: 1
                    cloneCommand.setDepth(depth)
                }

                // Submodules
                if (binding!!.recurseSubmodulesSwitch.isChecked) {
                    cloneCommand.setCloneSubmodules(true)
                }

                // Credentials
                if (binding!!.useCredentialsSwitch.isChecked) {
                    val user = binding!!.usernameEditText.text?.toString().orEmpty()
                    val pass = binding!!.passwordEditText.text?.toString().orEmpty()
                    cloneCommand.setCredentialsProvider(UsernamePasswordCredentialsProvider(user, pass))
                }

                cloneCommand.call().use { git ->
                    uiHandler.post { onCloneSuccess(ctx, targetDir) }
                }
            } catch (e: Exception) {
                uiHandler.post { onCloneError(ctx, e) }
            } finally {
                uiHandler.post { 
                    isCloning = false
                    setUiBusy(false, null)
                }
            }
        }.start()
    }

    private fun stopClone() {
        isCancelled = true
        Toast.makeText(requireContext(), R.string.acs_clone_stop, Toast.LENGTH_SHORT).show()
    }

    private fun onCloneSuccess(ctx: Context, projectDir: File) {
        if (isCancelled) {
            cleanupTargetDir()
            return
        }
        Toast.makeText(ctx, R.string.acs_clone_success, Toast.LENGTH_SHORT).show()
        WizardPreferences.setLastSaveLocation(ctx, projectDir.parentFile?.absolutePath ?: "")
        WizardPreferences.addRecentProject(ctx, projectDir.absolutePath)

        if (binding?.openAfterCloneCheckBox?.isChecked == true) {
            if (!looksLikeAndroidProject(projectDir)) {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.acs_clone_git_repository)
                    .setMessage(R.string.acs_clone_not_android_project)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        openProject(projectDir)
                        dismissAllowingStateLoss()
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
                return
            }
            openProject(projectDir)
        }
        dismissAllowingStateLoss()
    }

    private fun onCloneError(ctx: Context, e: Exception) {
        if (isCancelled) {
            cleanupTargetDir()
            return
        }
        val msg = e.localizedMessage ?: e.message ?: "Unknown error"
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.error)
            .setMessage(getString(R.string.acs_clone_error_failed) + "\n\n" + msg)
            .setPositiveButton(android.R.string.ok, null)
            .show()
        cleanupTargetDir()
    }

    private fun cleanupTargetDir() {
        activeTargetDir?.let {
            if (it.exists()) {
                runCatching { it.deleteRecursively() }
            }
        }
        activeTargetDir = null
    }

    private inner class JGitProgressMonitor : ProgressMonitor {
        private var totalWork = 0
        private var completedWork = 0
        private var currentTask: String? = null

        override fun start(totalTasks: Int) {}

        override fun beginTask(title: String?, totalWork: Int) {
            this.currentTask = title
            this.totalWork = totalWork
            this.completedWork = 0
            updateUi()
        }

        override fun update(completed: Int) {
            completedWork += completed
            updateUi()
        }

        override fun showDuration(show: Boolean) {}

        override fun endTask() {}

        override fun isCancelled(): Boolean = this@CloneRepositoryDialogFragment.isCancelled

        private fun updateUi() {
            uiHandler.post {
                if (totalWork > 0) {
                    lastProgressPercent = (completedWork * 100) / totalWork
                }
                // JGit monitors objects, not bytes, by default. 
                // We'll show progress based on work units (objects).
                binding?.apply {
                    statusText.text = currentTask ?: getString(R.string.acs_clone_in_progress)
                    progress.isIndeterminate = totalWork <= 0
                    if (totalWork > 0) {
                        progress.progress = lastProgressPercent ?: 0
                    }
                    progressDetailsText.text = if (totalWork > 0) "$completedWork / $totalWork" else "$completedWork"
                }
            }
        }
    }

    private fun setUiBusy(busy: Boolean, status: String?) {
        val dialog = dialog as? androidx.appcompat.app.AlertDialog
        dialog?.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)?.isEnabled = !busy
        dialog?.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEGATIVE)?.apply {
            text = if (busy) context.getString(R.string.acs_clone_stop) else context.getString(android.R.string.cancel)
        }

        binding?.apply {
            urlLayout.isEnabled = !busy
            repoNameLayout.isEnabled = !busy
            destinationLayout.isEnabled = !busy
            useCredentialsSwitch.isEnabled = !busy
            credentialsContainer.isEnabled = !busy
            branchLayout.isEnabled = !busy
            singleBranchSwitch.isEnabled = !busy
            recurseSubmodulesSwitch.isEnabled = !busy
            shallowSubmodulesSwitch.isEnabled = !busy && recurseSubmodulesSwitch.isChecked
            shallowCloneSwitch.isEnabled = !busy
            openAfterCloneCheckBox.isEnabled = !busy

            progress.visibility = if (busy) View.VISIBLE else View.GONE
            progressInfo.visibility = if (busy) View.VISIBLE else View.GONE
            statusText.text = status ?: ""
        }
    }

    private fun clearErrorWatcher(onChange: () -> Unit): TextWatcher {
        return object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) = onChange()
        }
    }

    private fun inferRepoName(url: String): String? {
        val trimmed = url.trim().removeSuffix("/")
        if (trimmed.isBlank()) return null
        val path = runCatching { URI(trimmed) }.getOrNull()?.path ?: trimmed.substringAfterLast(':')
        val last = path.split('/').lastOrNull { it.isNotBlank() } ?: return null
        return last.removeSuffix(".git").takeIf { it.matches(Regex("[A-Za-z0-9._-]+")) }
    }

    private fun openProject(projectDir: File) {
        val ctx = requireContext()
        val intent = Intent(ctx, SoraEditorActivityK::class.java).apply {
            putExtra(SoraEditorActivityK.EXTRA_PROJECT_DIR, projectDir.absolutePath)
        }
        startActivity(intent)
    }

    private fun looksLikeAndroidProject(dir: File): Boolean {
        return File(dir, "settings.gradle").exists() || 
               File(dir, "settings.gradle.kts").exists() ||
               File(dir, "build.gradle").exists() ||
               File(dir, "build.gradle.kts").exists()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }
}

