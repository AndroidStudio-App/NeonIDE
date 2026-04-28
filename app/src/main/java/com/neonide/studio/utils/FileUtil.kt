package com.neonide.studio.utils

import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import com.termux.shared.termux.TermuxConstants
import java.io.File

object FileUtil {
    const val INTERNAL_AUTHORITY = "com.neonide.studio.documents"
    const val EXTERNAL_AUTHORITY = "com.android.externalstorage.documents"

    fun resolveUriToFile(uri: Uri): File? {
        var result: File? = null
        try {
            val treeDocId = DocumentsContract.getTreeDocumentId(uri)
            val docUri = DocumentsContract.buildDocumentUriUsingTree(uri, treeDocId)
            val authority = docUri.authority
            val docId = DocumentsContract.getDocumentId(docUri)

            if (authority == INTERNAL_AUTHORITY) {
                result = if (docId == "/") {
                    TermuxConstants.TERMUX_HOME_DIR
                } else {
                    // docId is relative to home, e.g. "/projects"
                    File(TermuxConstants.TERMUX_HOME_DIR, docId)
                }
            } else if (authority == EXTERNAL_AUTHORITY) {
                val split = docId.split(':')
                if (split.size >= 2) {
                    val type = split[0]
                    val path = split[1]
                    result = if ("primary".equals(type, ignoreCase = true)) {
                        File(Environment.getExternalStorageDirectory(), path)
                    } else {
                        File("/storage/" + type + "/" + path)
                    }
                }
            }
        } catch (e: IllegalArgumentException) {
            // Not a tree URI
        }
        return result
    }
}
