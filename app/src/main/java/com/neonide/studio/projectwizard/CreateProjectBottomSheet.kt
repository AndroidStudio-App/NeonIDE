package com.neonide.studio.projectwizard

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.neonide.studio.EditorActivity
import com.neonide.studio.R
import com.neonide.studio.ui.components.AppButton
import com.neonide.studio.ui.components.AppCard
import com.neonide.studio.ui.components.AppIcon
import com.neonide.studio.ui.components.AppSwitch
import com.neonide.studio.ui.components.DropdownField
import com.neonide.studio.ui.components.FormTextField
import com.neonide.studio.ui.layout.AppColumn
import com.neonide.studio.ui.layout.AppRow
import com.neonide.studio.utils.rememberDirectoryLauncher
import com.termux.shared.termux.TermuxConstants
import java.io.File
import kotlinx.coroutines.launch

@Composable
fun CreateProjectBottomSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var selectedTemplate by remember { mutableStateOf<ProjectTemplate?>(null) }
    var projectName by remember { mutableStateOf("") }
    var packageName by remember { mutableStateOf("") }
    var isPackageNameManuallyEdited by remember { mutableStateOf(false) }

    var saveLocation by remember {
        val saved = context.getSharedPreferences("atc_wizard_prefs", Context.MODE_PRIVATE)
            .getString("last_save_location", null)
        mutableStateOf(saved ?: File(TermuxConstants.TERMUX_HOME_DIR, "projects").absolutePath)
    }
    var minSdk by remember { mutableStateOf("21") }
    var language by remember { mutableStateOf("Kotlin") }
    var useKts by remember { mutableStateOf(true) }

    var projectNameError by remember { mutableStateOf<String?>(null) }
    var packageNameError by remember { mutableStateOf<String?>(null) }
    var saveLocationError by remember { mutableStateOf<String?>(null) }
    var languageError by remember { mutableStateOf<String?>(null) }

    val templates = remember { ProjectTemplateRegistry.all() }

    val folderPickerLauncher = rememberDirectoryLauncher { file ->
        saveLocation = file.absolutePath
        saveLocationError = null
    }

    fun updatePackageName(name: String, template: ProjectTemplate) {
        val templateRaw = context.getString(template.nameRes).lowercase()
            .replace("project", "")
            .replace("activity", "")
            .trim()

        val templateSanitized = templateRaw.replace(Regex("[^a-z0-9]"), "")
        val projectSanitized = name.lowercase().replace(Regex("[^a-z0-9]"), "")
        val prefix = if (templateSanitized.isNotEmpty()) "com.$templateSanitized" else "com.example"

        packageName = if (projectSanitized.isNotEmpty()) "$prefix.$projectSanitized" else prefix
    }

    fun validate(): Boolean {
        var valid = true

        if (!ProjectValidators.isValidProjectName(projectName)) {
            projectNameError = context.getString(R.string.create_project_error_invalid_name)
            valid = false
        } else {
            projectNameError = null
        }

        if (!ProjectValidators.isValidPackageName(packageName)) {
            packageNameError = context.getString(R.string.create_project_error_invalid_package)
            valid = false
        } else {
            packageNameError = null
        }

        val base = File(saveLocation)
        val projectDir = File(base, projectName)
        if (projectDir.exists()) {
            saveLocationError = context.getString(R.string.create_project_error_dir_exists)
            valid = false
        } else {
            saveLocationError = null
        }
        if (selectedTemplate?.kind == ProjectTemplate.Kind.COMPOSE_ACTIVITY &&
            !language.equals("Kotlin", ignoreCase = true)
        ) {
            languageError = context.getString(R.string.compose_requires_kotlin)
            valid = false
        } else {
            languageError = null
        }

        return valid
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
            if (selectedTemplate == null) {
                Text(
                    text = stringResource(id = R.string.choose_template),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(id = R.string.pick_template_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )

                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                ) {
                    items(templates) { tpl ->
                        TemplateItem(
                            template = tpl,
                            onClick = {
                                selectedTemplate = tpl
                                val suggestedName =
                                    "My" + context.getString(tpl.nameRes).replace(" ", "")
                                projectName = suggestedName
                                isPackageNameManuallyEdited = false
                                updatePackageName(suggestedName, tpl)
                            }
                        )
                    }
                }
            } else {
                AppColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    AppCard(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        AppRow(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Image(
                                painter = painterResource(id = selectedTemplate!!.iconRes),
                                contentDescription = null,
                                modifier = Modifier.size(150.dp)
                            )
                            AppColumn(modifier = Modifier.padding(start = 12.dp)) {
                                Text(
                                    text = stringResource(id = selectedTemplate!!.nameRes),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = stringResource(id = selectedTemplate!!.descriptionRes),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Text(
                        text = stringResource(id = R.string.project_configuration),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 16.dp)
                    )

                    FormTextField(
                        value = projectName,
                        onValueChange = {
                            projectName = it
                            projectNameError = null
                            if (!isPackageNameManuallyEdited) {
                                updatePackageName(it, selectedTemplate!!)
                            }
                        },
                        label = stringResource(id = R.string.project_name),
                        leadingIcon = painterResource(id = R.drawable.ic_add),
                        isError = projectNameError != null,
                        supportingText = projectNameError,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                    )

                    FormTextField(
                        value = packageName,
                        onValueChange = {
                            packageName = it
                            packageNameError = null
                            isPackageNameManuallyEdited = true
                        },
                        label = stringResource(id = R.string.package_name),
                        leadingIcon = painterResource(id = R.drawable.ic_language_android),
                        isError = packageNameError != null,
                        supportingText = packageNameError,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                    )

                    FormTextField(
                        value = saveLocation,
                        onValueChange = {
                            saveLocation = it
                            saveLocationError = null
                        },
                        label = stringResource(id = R.string.project_location),
                        leadingIcon = painterResource(id = R.drawable.ic_folder),
                        trailingIcon = {
                            IconButton(onClick = {
                                folderPickerLauncher.launch(null)
                            }) {
                                AppIcon(painter = painterResource(id = R.drawable.ic_folder))
                            }
                        },
                        isError = saveLocationError != null,
                        supportingText = saveLocationError,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                    )

                    DropdownField(
                        label = stringResource(id = R.string.language),
                        options = listOf("Kotlin", "Java"),
                        selectedOption = language,
                        onOptionSelected = {
                            language = it
                            useKts = it.equals("Kotlin")
                        },
                        leadingIcon = if (language == "Java") {
                            painterResource(id = R.drawable.ic_filetype_java)
                        } else {
                            painterResource(id = R.drawable.ic_filetype_kotlin)
                        },
                        isError = languageError != null,
                        supportingText = languageError,
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                    )

                    DropdownField(
                        label = stringResource(id = R.string.minimum_sdk),
                        options = listOf("21", "24", "26", "28", "29", "30", "33"),
                        selectedOption = minSdk,
                        onOptionSelected = { minSdk = it },
                        leadingIcon = painterResource(id = R.drawable.ic_filetype_gradle),
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                    )

                    AppRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(id = R.string.use_gradle_kotlin_dsl),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )
                        AppSwitch(
                            checked = useKts,
                            onCheckedChange = { useKts = it }
                        )
                    }

                    AppRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 20.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text(stringResource(id = R.string.cancel))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(onClick = { selectedTemplate = null }) {
                            Text(stringResource(id = R.string.back))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        AppButton(onClick = {
                            if (validate()) {
                                createProject(
                                    context = context,
                                    tpl = selectedTemplate!!,
                                    name = projectName,
                                    pkg = packageName,
                                    baseDir = saveLocation,
                                    minSdk = minSdk,
                                    lang = language,
                                    useKts = useKts,
                                    onSuccess = {
                                        scope.launch {
                                            sheetState.hide()
                                            onDismiss()
                                        }
                                    }
                                )
                            }
                        }) {
                            Text(stringResource(id = R.string.create))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TemplateItem(template: ProjectTemplate, onClick: () -> Unit) {
    AppCard(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .padding(8.dp)
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        AppColumn(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(id = template.iconRes),
                contentDescription = null,
                modifier = Modifier.size(150.dp),
                contentScale = ContentScale.Crop
            )
            Text(
                text = stringResource(id = template.nameRes),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                modifier = Modifier.padding(top = 10.dp)
            )
        }
    }
}

private fun createProject(
    context: Context,
    tpl: ProjectTemplate,
    name: String,
    pkg: String,
    baseDir: String,
    minSdk: String,
    lang: String,
    useKts: Boolean,
    onSuccess: () -> Unit
) {
    val base = File(baseDir)
    if (!base.exists()) base.mkdirs()
    val projectDir = File(base, name)

    try {
        CreateTemplate(
            context = context,
            template = tpl,
            projectDir = projectDir,
            appId = pkg,
            minSdk = minSdk.toIntOrNull() ?: 21,
            language = lang,
            useKts = useKts
        )
        context.getSharedPreferences("atc_wizard_prefs", Context.MODE_PRIVATE)
            .edit().putString("last_save_location", base.absolutePath).apply()
        Toast.makeText(
            context,
            context.getString(R.string.create_project_success, projectDir.absolutePath),
            Toast.LENGTH_LONG
        ).show()

        val intent = Intent(context, EditorActivity::class.java).apply {
            putExtra(EditorActivity.EXTRA_PROJECT_DIR, projectDir.absolutePath)
        }
        context.startActivity(intent)
        onSuccess()
    } catch (e: android.content.ActivityNotFoundException) {
        Toast.makeText(
            context,
            e.message ?: context.getString(R.string.failed),
            Toast.LENGTH_LONG
        ).show()
    } catch (e: java.io.IOException) {
        Toast.makeText(
            context,
            e.message ?: context.getString(R.string.failed),
            Toast.LENGTH_LONG
        ).show()
    }
}
