package com.neonide.studio.utils

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import java.io.File

/**
 * Remembers a SAF directory picker launcher.
 * Resolves the picked URI to a [File] and passes it to [onPicked].
 */
@Composable
fun rememberDirectoryLauncher(onPicked: (File) -> Unit): ActivityResultLauncher<Uri?> =
    rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            FileUtil.resolveUriToFile(it)?.let { file ->
                onPicked(file)
            }
        }
    }
