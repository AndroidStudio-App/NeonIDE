package com.neonide.studio.extensions

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.neonide.studio.R
import com.neonide.studio.ui.components.AppButton
import com.neonide.studio.ui.components.AppCard
import com.neonide.studio.ui.components.AppIcon
import com.neonide.studio.ui.components.AppOutlinedButton
import com.neonide.studio.ui.components.AppScaffold
import com.neonide.studio.ui.components.AppTopBar
import com.neonide.studio.ui.layout.AppBox
import com.neonide.studio.ui.layout.AppColumn
import com.neonide.studio.ui.layout.AppLazyColumn
import com.neonide.studio.ui.layout.AppRow
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

@Serializable
data class ExtensionsJson(
    @SerialName("extensions")
    val extensions: List<ExtensionEntry>
)

@Serializable
data class ExtensionEntry(
    @SerialName("id")
    val id: String,
    @SerialName("name")
    val name: String,
    @SerialName("description")
    val description: String,
    @SerialName("url")
    val url: String,
    @SerialName("sha256")
    val sha256: String,
    @SerialName("version")
    val version: String,
    @SerialName("size")
    val size: Long = 0
)

@Composable
fun ExtensionsScreen(context: Context, onBack: () -> Unit) {
    val manager = remember { ExtensionsManager(context) }
    val scope = rememberCoroutineScope()

    var extensions by remember { mutableStateOf<List<ExtensionEntry>>(emptyList()) }
    var installedExtensions by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var installingExtensionId by remember { mutableStateOf<String?>(null) }
    var installationError by remember { mutableStateOf<String?>(null) }

    fun loadExtensions() {
        scope.launch {
            try {
                val url =
                    URL(
                        "https://raw.githubusercontent.com/AndroidStudio-App/NeonIDE-Extension/main/extensions.json"
                    )
                val jsonText = withContext(Dispatchers.IO) {
                    val connection = url.openConnection() as HttpURLConnection
                    connection.connectTimeout = 5000
                    connection.readTimeout = 5000
                    connection.requestMethod = "GET"
                    try {
                        connection.connect()
                        connection.inputStream.bufferedReader().use { it.readText() }
                    } finally {
                        connection.disconnect()
                    }
                }
                val parsed = Json.decodeFromString<ExtensionsJson>(jsonText)
                extensions = parsed.extensions
                installedExtensions = parsed.extensions.filter {
                    manager.isExtensionInstalled(it.id)
                }.map { it.id }.toSet()
            } catch (e: Exception) {
                error = "Failed to load: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        loadExtensions()
    }

    fun installExtension(extension: ExtensionEntry) {
        installingExtensionId = extension.id
        installationError = null

        scope.launch {
            val result = manager.installExtension(extension)
            if (result.isSuccess) {
                installedExtensions = installedExtensions + extension.id
            } else {
                installationError = result.exceptionOrNull()?.message ?: "Install failed"
            }
            installingExtensionId = null
        }
    }

    fun uninstallExtension(extension: ExtensionEntry) {
        scope.launch {
            val result = manager.uninstallExtension(extension)
            if (result.isSuccess) {
                installedExtensions = installedExtensions - extension.id
            }
        }
    }

    AppScaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.extensions),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        AppIcon(painterResource(R.drawable.ic_chevron_left))
                    }
                }
            )
        }
    ) { padding ->
        when {
            isLoading -> {
                AppBox(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            error != null -> {
                AppBox(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    AppColumn(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Error: $error,Make sure you're connected to internet")
                        Spacer(modifier = Modifier.height(16.dp))
                        AppButton(
                            text = stringResource(R.string.retry),
                            onClick = {
                                error = null
                                isLoading = true
                                loadExtensions()
                            }
                        )
                    }
                }
            }

            else -> {
                AppLazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        if (installationError != null) {
                            AppCard(
                                modifier = Modifier.fillMaxWidth(),
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            ) {
                                AppRow(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = "Installation error: $installationError,Make sure you're connected to internet",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }

                    items(extensions) { extension ->
                        val displaySize = if (extension.size > 0) {
                            manager.formatSize(extension.size)
                        } else {
                            null
                        }
                        ExtensionCard(
                            extension = extension,
                            isInstalled = installedExtensions.contains(extension.id),
                            isUpdateAvailable = manager.isUpdateAvailable(
                                extension.id,
                                extension.sha256
                            ),
                            isInstalling = installingExtensionId == extension.id,
                            displaySize = displaySize,
                            onInstall = { installExtension(extension) },
                            onUninstall = { uninstallExtension(extension) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExtensionCard(
    extension: ExtensionEntry,
    isInstalled: Boolean,
    isUpdateAvailable: Boolean,
    isInstalling: Boolean,
    displaySize: String?,
    onInstall: () -> Unit,
    onUninstall: () -> Unit
) {
    AppCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        AppColumn(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = extension.name,
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = extension.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            AppRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "v${extension.version}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (displaySize != null) {
                        Text(
                            text = displaySize,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (isInstalling) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else if (isInstalled) {
                    AppRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AppOutlinedButton(onClick = onUninstall) {
                            Text(stringResource(R.string.remove))
                        }
                        if (isUpdateAvailable) {
                            AppButton(onClick = onInstall) {
                                Text(stringResource(R.string.update))
                            }
                        }
                    }
                } else {
                    AppButton(onClick = onInstall) {
                        Text(stringResource(R.string.install))
                    }
                }
            }
        }
    }
}
