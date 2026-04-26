package com.neonide.studio.layout

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Password
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

import com.neonide.studio.app.SoraEditorActivityK

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GitLayout(
    onBack: () -> Unit,
    state: GitLayoutState,
    viewModel: GitViewModel
) {
    val context = LocalContext.current

    // SAF directory picker
    val dirPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let { viewModel.onDirectoryPicked(context, it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Clone Git Repository") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
                .animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ---- URL ----
            OutlinedTextField(
                value = state.url,
                onValueChange = viewModel::updateUrl,
                label = { Text("Repository URL") },
                leadingIcon = { Icon(Icons.Filled.Link, null) },
                isError = state.urlError != null,
                supportingText = { state.urlError?.let { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isCloning,
                shape = MaterialTheme.shapes.small
            )

            // ---- Repo name ----
            OutlinedTextField(
                value = state.repoName,
                onValueChange = viewModel::updateRepoName,
                label = { Text("Repository Name") },
                leadingIcon = { Icon(Icons.Filled.Description, null) },
                isError = state.repoNameError != null,
                supportingText = { state.repoNameError?.let { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isCloning,
                shape = MaterialTheme.shapes.small
            )

            // ---- Destination ----
            OutlinedTextField(
                value = state.destination,
                onValueChange = viewModel::updateDestination,
                label = { Text("Destination Path") },
                leadingIcon = { Icon(Icons.Filled.Folder, null) },
                trailingIcon = {
                    IconButton(onClick = { dirPickerLauncher.launch(null) }) {
                        Icon(Icons.Filled.FolderOpen, "Choose folder")
                    }
                },
                isError = state.destinationError != null,
                supportingText = { state.destinationError?.let { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isCloning,
                shape = MaterialTheme.shapes.small
            )

            // ---- Branch ----
            OutlinedTextField(
                value = state.branch,
                onValueChange = viewModel::updateBranch,
                label = { Text("Branch (optional)") },
                leadingIcon = { Icon(Icons.Filled.AccountTree, null) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isCloning,
                shape = MaterialTheme.shapes.small
            )

            // ---- Open after clone ----
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Open project after clone", modifier = Modifier.weight(1f))
                Switch(
                    checked = state.openProjectAfter,
                    onCheckedChange = viewModel::setOpenProjectAfter,
                    enabled = !state.isCloning
                )
            }

            // ---- Credentials ----
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Use credentials", modifier = Modifier.weight(1f))
                Switch(
                    checked = state.useCredentials,
                    onCheckedChange = viewModel::setUseCredentials,
                    enabled = !state.isCloning
                )
            }
            if (state.useCredentials) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = state.username,
                        onValueChange = viewModel::updateUsername,
                        label = { Text("Username") },
                        leadingIcon = { Icon(Icons.Filled.AccountCircle, null) },
                        isError = state.usernameError != null,
                        supportingText = { state.usernameError?.let { Text(it) } },
                        modifier = Modifier.weight(1f),
                        enabled = !state.isCloning,
                        shape = MaterialTheme.shapes.small
                    )
                    OutlinedTextField(
                        value = state.password,
                        onValueChange = viewModel::updatePassword,
                        label = { Text("Password") },
                        leadingIcon = { Icon(Icons.Filled.Password, null) },
                        isError = state.passwordError != null,
                        supportingText = { state.passwordError?.let { Text(it) } },
                        modifier = Modifier.weight(1f),
                        enabled = !state.isCloning,
                        shape = MaterialTheme.shapes.small,
                        visualTransformation = PasswordVisualTransformation()
                    )
                }
            }

            HorizontalDivider()

            // ---- Shallow clone ----
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Shallow clone", modifier = Modifier.weight(1f))
                Switch(
                    checked = state.shallowClone,
                    onCheckedChange = viewModel::setShallowClone,
                    enabled = !state.isCloning
                )
            }
            if (state.shallowClone) {
                OutlinedTextField(
                    value = state.depth,
                    onValueChange = viewModel::updateDepth,
                    label = { Text("Depth") },
                    leadingIcon = { Icon(Icons.Filled.ArrowDownward, null) },
                    isError = state.depthError != null,
                    supportingText = { state.depthError?.let { Text(it) } },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isCloning,
                    shape = MaterialTheme.shapes.small
                )
            }

            // ---- Single branch ----
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Single branch", modifier = Modifier.weight(1f))
                Switch(
                    checked = state.singleBranch,
                    onCheckedChange = viewModel::setSingleBranch,
                    enabled = !state.isCloning
                )
            }

            // ---- Submodules ----
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Recurse submodules", modifier = Modifier.weight(1f))
                Switch(
                    checked = state.recurseSubmodules,
                    onCheckedChange = viewModel::setRecurseSubmodules,
                    enabled = !state.isCloning
                )
            }
            if (state.recurseSubmodules) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Shallow submodules", modifier = Modifier.weight(1f))
                    Switch(
                        checked = state.shallowSubmodules,
                        onCheckedChange = viewModel::setShallowSubmodules,
                        enabled = !state.isCloning
                    )
                }
            }

            // ---- Progress & status ----
            if (state.isCloning || state.statusText.isNotBlank()) {
                if (state.isCloning) {
                    LinearProgressIndicator(
                        progress = { state.progressPercent / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = state.progressText,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Text(
                    text = state.statusText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (state.destinationError != null)
                        MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurface
                )
            }

            // ---- Action buttons ----
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = {
                        if (state.isCloning) viewModel.cancelClone()
                        else onBack()
                    }
                ) {
                    Text(if (state.isCloning) "Stop" else "Cancel")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        viewModel.startClone(context) { projectDir ->
                            val intent = Intent(context, SoraEditorActivityK::class.java)
                            intent.putExtra(SoraEditorActivityK.EXTRA_PROJECT_DIR, projectDir.absolutePath)
                            context.startActivity(intent)
                        }
                    },
                    enabled = !state.isCloning
                ) {
                    Text(if (state.isCloning) "Cloning…" else "Clone")
                }
            }

            // bottom spacer for scroll
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
