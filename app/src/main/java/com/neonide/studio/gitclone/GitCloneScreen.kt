package com.neonide.studio.gitclone

import android.content.Intent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.neonide.studio.EditorActivity
import com.neonide.studio.R
import com.neonide.studio.ui.components.AppButton
import com.neonide.studio.ui.components.AppIcon
import com.neonide.studio.ui.components.AppSwitch
import com.neonide.studio.ui.components.AppTopBar
import com.neonide.studio.ui.components.FormTextField
import com.neonide.studio.ui.layout.AppColumn
import com.neonide.studio.ui.layout.AppRow
import com.neonide.studio.utils.Divider.horizontalDivider
import com.neonide.studio.utils.rememberDirectoryLauncher

@Composable
fun GitCloneScreen(
    onBack: () -> Unit,
    state: GitLayoutState,
    viewModel: GitViewModel,
    onFinished: () -> Unit
) {
    val context = LocalContext.current

    val dirPickerLauncher = rememberDirectoryLauncher { file ->
        viewModel.updateDestination(file.absolutePath)
    }

    AppColumn(
        modifier = Modifier.fillMaxSize()
    ) {
        AppTopBar(
            title = stringResource(R.string.clone_repository),
            navigationIcon = {
                IconButton(onClick = onBack) {
                    AppIcon(painterResource(R.drawable.ic_chevron_left))
                }
            }
        )

        AppColumn(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AppColumn(modifier = Modifier.padding(horizontal = 16.dp)) {
                // ---- URL ----
                FormTextField(
                    value = state.url,
                    onValueChange = viewModel::updateUrl,
                    label = stringResource(R.string.repository_url),
                    leadingIcon = painterResource(R.drawable.ic_add_link),
                    isError = state.urlError != null,
                    supportingText = state.urlError,
                    enabled = !state.isCloning,
                    trailingIcon = {
                        if (state.url.isNotEmpty()) {
                            IconButton(onClick = { viewModel.updateUrl("") }) {
                                AppIcon(painter = painterResource(id = R.drawable.ic_close))
                            }
                        }
                    }
                )

                // ---- Repo name ----
                FormTextField(
                    value = state.repoName,
                    onValueChange = viewModel::updateRepoName,
                    label = stringResource(R.string.repository_name),
                    leadingIcon = painterResource(R.drawable.ic_files),
                    isError = state.repoNameError != null,
                    supportingText = state.repoNameError,
                    enabled = !state.isCloning
                )

                // ---- Destination ----
                FormTextField(
                    value = state.destination,
                    onValueChange = viewModel::updateDestination,
                    label = stringResource(R.string.destination_path),
                    leadingIcon = painterResource(R.drawable.ic_folder),
                    trailingIcon = {
                        IconButton(onClick = { dirPickerLauncher.launch(null) }) {
                            AppIcon(painterResource(R.drawable.ic_folder_open))
                        }
                    },
                    isError = state.destinationError != null,
                    supportingText = state.destinationError,
                    enabled = !state.isCloning
                )

                // ---- Branch ----
                FormTextField(
                    value = state.branch,
                    onValueChange = viewModel::updateBranch,
                    label = stringResource(R.string.branch_optional),
                    leadingIcon = painterResource(R.drawable.ic_folder_tree),
                    enabled = !state.isCloning
                )

                // ---- Open after clone ----
                AppRow(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.open_project_after_clone),
                        modifier = Modifier.weight(1f)
                    )
                    AppSwitch(
                        checked = state.openProjectAfter,
                        onCheckedChange = viewModel::setOpenProjectAfter,
                        enabled = !state.isCloning
                    )
                }

                // ---- Credentials ----
                AppRow(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.use_credentials), modifier = Modifier.weight(1f))
                    AppSwitch(
                        checked = state.useCredentials,
                        onCheckedChange = viewModel::setUseCredentials,
                        enabled = !state.isCloning
                    )
                }
                if (state.useCredentials) {
                    AppRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FormTextField(
                            value = state.username,
                            onValueChange = viewModel::updateUsername,
                            label = stringResource(R.string.username),
                            leadingIcon = painterResource(R.drawable.ic_user),
                            isError = state.usernameError != null,
                            supportingText = state.usernameError,
                            modifier = Modifier.weight(1f),
                            enabled = !state.isCloning
                        )
                        FormTextField(
                            value = state.password,
                            onValueChange = viewModel::updatePassword,
                            label = stringResource(R.string.password),
                            leadingIcon = painterResource(R.drawable.ic_locked),
                            isError = state.passwordError != null,
                            supportingText = state.passwordError,
                            modifier = Modifier.weight(1f),
                            enabled = !state.isCloning,
                            visualTransformation = PasswordVisualTransformation()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                horizontalDivider(color = Color.Gray)

                // ---- Shallow clone ----
                AppRow(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.shallow_clone), modifier = Modifier.weight(1f))
                    AppSwitch(
                        checked = state.shallowClone,
                        onCheckedChange = viewModel::setShallowClone,
                        enabled = !state.isCloning
                    )
                }
                if (state.shallowClone) {
                    FormTextField(
                        value = state.depth,
                        onValueChange = viewModel::updateDepth,
                        label = stringResource(R.string.depth),
                        leadingIcon = painterResource(R.drawable.ic_chevron_down),
                        isError = state.depthError != null,
                        supportingText = state.depthError,
                        enabled = !state.isCloning
                    )
                }

                // ---- Single branch ----
                AppRow(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.single_branch), modifier = Modifier.weight(1f))
                    AppSwitch(
                        checked = state.singleBranch,
                        onCheckedChange = viewModel::setSingleBranch,
                        enabled = !state.isCloning
                    )
                }

                // ---- Submodules ----
                AppRow(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.recurse_submodules),
                        modifier = Modifier.weight(1f)
                    )
                    AppSwitch(
                        checked = state.recurseSubmodules,
                        onCheckedChange = viewModel::setRecurseSubmodules,
                        enabled = !state.isCloning
                    )
                }
                if (state.recurseSubmodules) {
                    AppRow(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.shallow_submodules),
                            modifier = Modifier.weight(1f)
                        )
                        AppSwitch(
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
                        color = if (state.destinationError != null) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                }

                // ---- Action buttons ----
                AppRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = {
                            if (state.isCloning) {
                                viewModel.cancelClone()
                            } else {
                                onBack()
                            }
                        }
                    ) {
                        Text(
                            if (state.isCloning) {
                                stringResource(
                                    R.string.stop
                                )
                            } else {
                                stringResource(R.string.cancel)
                            }
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    AppButton(
                        onClick = {
                            viewModel.startClone(context) { projectDir ->
                                val intent = Intent(context, EditorActivity::class.java)
                                intent.putExtra(
                                    EditorActivity.EXTRA_PROJECT_DIR,
                                    projectDir.absolutePath
                                )
                                context.startActivity(intent)
                                onFinished()
                            }
                        },
                        enabled = !state.isCloning
                    ) {
                        Text(
                            if (state.isCloning) {
                                stringResource(
                                    R.string.cloning
                                )
                            } else {
                                stringResource(R.string.clone)
                            }
                        )
                    }
                }
            }
        }
    }
}
