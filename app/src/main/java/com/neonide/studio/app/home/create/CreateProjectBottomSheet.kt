package com.neonide.studio.app.home.create

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.neonide.studio.R
import com.neonide.studio.app.SoraEditorActivityK
import com.neonide.studio.app.home.preferences.WizardPreferences
import com.neonide.studio.shared.termux.TermuxConstants
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateProjectBottomSheet(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Using skipPartiallyExpanded = true for forms prevents jumping when keyboard opens
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    
    var selectedTemplate by remember { mutableStateOf<ProjectTemplate?>(null) }
    var projectName by remember { mutableStateOf("") }
    var packageName by remember { mutableStateOf("") }
    var isPackageNameManuallyEdited by remember { mutableStateOf(false) }
    
    var saveLocation by remember { 
        mutableStateOf(
            WizardPreferences.getLastSaveLocation(context) 
                ?: File(TermuxConstants.TERMUX_HOME_DIR, "projects").absolutePath
        )
    }
    var minSdk by remember { mutableStateOf("21") }
    var language by remember { mutableStateOf("Kotlin") }
    var useKts by remember { mutableStateOf(true) }

    val templates = remember { ProjectTemplateRegistry.all() }

    val ANDROID_DOCS_AUTHORITY = "com.android.externalstorage.documents"
    val TERMUX_DOCS_AUTHORITY = "com.neonide.studio.documents"

    val folderPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data ?: return@rememberLauncherForActivityResult
        val docId = DocumentsContract.getTreeDocumentId(uri)
        val docUri = DocumentsContract.buildDocumentUriUsingTree(uri, docId)
        val authority = docUri.authority
        val documentId = DocumentsContract.getDocumentId(docUri)

        val dirPath: String? = when (authority) {
            TERMUX_DOCS_AUTHORITY -> documentId
            ANDROID_DOCS_AUTHORITY -> {
                val split = documentId.split(':')
                if (split.size >= 2 && split[0] == "primary") {
                    File(Environment.getExternalStorageDirectory(), split[1]).absolutePath
                } else null
            }
            else -> null
        }

        if (dirPath != null) {
            saveLocation = dirPath
        } else {
            Toast.makeText(context, R.string.acs_err_invalid_picked_dir, Toast.LENGTH_SHORT).show()
        }
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

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.fillMaxHeight()
    ) {
        Column(
            modifier = Modifier
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                .fillMaxWidth()
                .navigationBarsPadding() // Space for system nav bar
                .imePadding() // Pushes content up above the keyboard
        ) {
            if (selectedTemplate == null) {
                Text(
                    text = stringResource(id = R.string.choose_template),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Pick a starter template and configure your project",
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
                                val suggestedName = "My" + context.getString(tpl.nameRes).replace(" ", "")
                                projectName = suggestedName
                                isPackageNameManuallyEdited = false
                                updatePackageName(suggestedName, tpl)
                            }
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Image(
                                painter = painterResource(id = selectedTemplate!!.iconRes),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Fit
                            )
                            Column(modifier = Modifier.padding(start = 12.dp)) {
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

                    OutlinedTextField(
                        value = projectName,
                        onValueChange = { 
                            projectName = it
                            if (!isPackageNameManuallyEdited) {
                                updatePackageName(it, selectedTemplate!!)
                            }
                        },
                        label = { Text(stringResource(id = R.string.project_name)) },
                        leadingIcon = { Icon(painter = painterResource(id = R.drawable.ic_add), contentDescription = null) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        singleLine = true
                    )
                    
                    OutlinedTextField(
                        value = packageName,
                        onValueChange = { 
                            packageName = it
                            isPackageNameManuallyEdited = true
                        },
                        label = { Text(stringResource(id = R.string.package_name)) },
                        leadingIcon = { Icon(painter = painterResource(id = R.drawable.ic_language_android), contentDescription = null) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = saveLocation,
                        onValueChange = { saveLocation = it },
                        label = { Text(stringResource(id = R.string.project_location)) },
                        leadingIcon = { Icon(painter = painterResource(id = R.drawable.ic_folder), contentDescription = null) },
                        trailingIcon = {
                            IconButton(onClick = { 
                                folderPickerLauncher.launch(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE))
                            }) {
                                Icon(painter = painterResource(id = R.drawable.ic_folder), contentDescription = stringResource(id = R.string.browse))
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        singleLine = true
                    )

                    DropdownField(
                        label = stringResource(id = R.string.language),
                        options = listOf("Kotlin", "Java"),
                        selectedOption = language,
                        onOptionSelected = { language = it },
                        leadingIconRes = R.drawable.ic_language_kotlin
                    )

                    DropdownField(
                        label = stringResource(id = R.string.minimum_sdk),
                        options = listOf("21", "24", "26", "28", "29", "30", "33"),
                        selectedOption = minSdk,
                        onOptionSelected = { minSdk = it },
                        leadingIconRes = R.drawable.ic_gradle
                    )

                    Row(
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
                        Switch(
                            checked = useKts,
                            onCheckedChange = { useKts = it }
                        )
                    }

                    Row(
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
                        Button(onClick = {
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
private fun TemplateItem(
    template: ProjectTemplate,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .padding(8.dp)
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(id = template.iconRes),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .clip(RoundedCornerShape(8.dp)),
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownField(
    label: String,
    options: List<String>,
    selectedOption: String,
    onOptionSelected: (String) -> Unit,
    leadingIconRes: Int
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
    ) {
        OutlinedTextField(
            value = selectedOption,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            leadingIcon = { Icon(painter = painterResource(id = leadingIconRes), contentDescription = null) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { selectionOption ->
                DropdownMenuItem(
                    text = { Text(selectionOption) },
                    onClick = {
                        onOptionSelected(selectionOption)
                        expanded = false
                    }
                )
            }
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
    if (!ProjectValidators.isValidProjectName(name)) {
        Toast.makeText(context, R.string.create_project_error_invalid_name, Toast.LENGTH_SHORT).show()
        return
    }
    if (!ProjectValidators.isValidPackageName(pkg)) {
        Toast.makeText(context, R.string.create_project_error_invalid_package, Toast.LENGTH_SHORT).show()
        return
    }

    val base = File(baseDir)
    if (!base.exists()) base.mkdirs()
    val projectDir = File(base, name)
    if (projectDir.exists()) {
        Toast.makeText(context, R.string.create_project_error_dir_exists, Toast.LENGTH_SHORT).show()
        return
    }

    try {
        AndroidProjectGenerator.generate(
            context = context,
            template = tpl,
            projectDir = projectDir,
            applicationId = pkg,
            minSdk = minSdk.toIntOrNull() ?: 21,
            language = lang,
            useKts = useKts,
        )
        WizardPreferences.setLastSaveLocation(context, base.absolutePath)
        WizardPreferences.addRecentProject(context, projectDir.absolutePath)
        Toast.makeText(context, context.getString(R.string.create_project_success, projectDir.absolutePath), Toast.LENGTH_LONG).show()
        
        val intent = Intent(context, SoraEditorActivityK::class.java).apply {
            putExtra(SoraEditorActivityK.EXTRA_PROJECT_DIR, projectDir.absolutePath)
        }
        context.startActivity(intent)
        onSuccess()
    } catch (t: Throwable) {
        Toast.makeText(context, t.message ?: "Failed", Toast.LENGTH_LONG).show()
    }
}
