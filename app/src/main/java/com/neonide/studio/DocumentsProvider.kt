package com.neonide.studio

import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import android.provider.DocumentsProvider
import android.webkit.MimeTypeMap
import com.termux.shared.termux.TermuxConstants
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

/** Saf implementation 
This give permission to 3rd party software 
to modify NeonIDE files in "home" dir**/

class DocumentsProvider : DocumentsProvider() {

    companion object {
        private val DEFAULT_ROOT_PROJECTION = arrayOf(
            Root.COLUMN_ROOT_ID,
            Root.COLUMN_MIME_TYPES,
            Root.COLUMN_FLAGS,
            Root.COLUMN_ICON,
            Root.COLUMN_TITLE,
            Root.COLUMN_SUMMARY,
            Root.COLUMN_DOCUMENT_ID,
            Root.COLUMN_AVAILABLE_BYTES
        )

        private val DEFAULT_DOCUMENT_PROJECTION = arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_LAST_MODIFIED,
            Document.COLUMN_FLAGS,
            Document.COLUMN_SIZE
        )
    }

    override fun onCreate(): Boolean = true

    override fun queryRoots(projection: Array<out String>?): Cursor {
        val result = MatrixCursor(projection ?: DEFAULT_ROOT_PROJECTION)
        val homeDir = TermuxConstants.TERMUX_HOME_DIR

        result.newRow().apply {
            add(Root.COLUMN_ROOT_ID, "home")
            add(Root.COLUMN_DOCUMENT_ID, getDocIdForFile(homeDir))
            add(Root.COLUMN_TITLE, context?.getString(R.string.app_name) ?: "NeonIDE")
            add(Root.COLUMN_SUMMARY, "Internal IDE Files")
            add(Root.COLUMN_FLAGS, Root.FLAG_SUPPORTS_CREATE or 
            Root.FLAG_SUPPORTS_IS_CHILD or 
            Root.FLAG_SUPPORTS_SEARCH)
            add(Root.COLUMN_ICON, R.mipmap.ic_launcher)
            add(Root.COLUMN_AVAILABLE_BYTES, homeDir.freeSpace)
        }
        return result
    }

    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor {
        val result = MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION)
        includeFile(result, documentId, null)
        return result
    }

    override fun queryChildDocuments(
        parentDocumentId: String,
        projection: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        val result = MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION)
        val parent = getFileForDocId(parentDocumentId)
        parent.listFiles()?.forEach { file ->
            includeFile(result, null, file)
        }
        return result
    }

    @Throws(FileNotFoundException::class)
    override fun openDocument(
        documentId: String,
        mode: String,
        signal: CancellationSignal?
    ): ParcelFileDescriptor {
        val file = getFileForDocId(documentId)
        val accessMode = ParcelFileDescriptor.parseMode(mode)
        return ParcelFileDescriptor.open(file, accessMode)
    }

    @Throws(FileNotFoundException::class)
    override fun createDocument(
        parentDocumentId: String,
        mimeType: String,
        displayName: String
    ): String {
        val parent = getFileForDocId(parentDocumentId)
        val file = File(parent, displayName)
        
        try {
            if (Document.MIME_TYPE_DIR == mimeType) {
                if (!file.mkdir()) throw IOException("Failed to create directory")
            } else {
                if (!file.createNewFile()) throw IOException("Failed to create file")
            }
        } catch (e: IOException) {
            throw FileNotFoundException("Failed to create document: ${e.message}")
        }
        
        return getDocIdForFile(file)
    }

    @Throws(FileNotFoundException::class)
    override fun deleteDocument(documentId: String) {
        val file = getFileForDocId(documentId)
        if (!file.deleteRecursively()) {
            throw FileNotFoundException("Failed to delete $documentId")
        }
    }

    @Throws(FileNotFoundException::class)
    override fun renameDocument(documentId: String, displayName: String): String {
        val file = getFileForDocId(documentId)
        val newFile = File(file.parentFile, displayName)
        if (!file.renameTo(newFile)) {
            throw FileNotFoundException("Failed to rename to $displayName")
        }
        return getDocIdForFile(newFile)
    }

    override fun querySearchDocuments(
        rootId: String,
        query: String,
        projection: Array<out String>?
    ): Cursor {
        val result = MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION)
        val homeDir = TermuxConstants.TERMUX_HOME_DIR
        
        homeDir.walkTopDown()
            .filter { it.name.contains(query, ignoreCase = true) }
            .take(50)
            .forEach { includeFile(result, null, it) }
            
        return result
    }

    // --- Helpers ---

    private fun getDocIdForFile(file: File): String {
        val path = file.absolutePath
        val basePath = TermuxConstants.TERMUX_HOME_DIR.absolutePath
        return when {
            path == basePath -> "/"
            path.startsWith(basePath) -> path.substring(basePath.length)
            else -> path
        }
    }

    @Throws(FileNotFoundException::class)
    private fun getFileForDocId(docId: String): File {
        val file = if (docId == "/") {
            TermuxConstants.TERMUX_HOME_DIR
        } else {
            File(TermuxConstants.TERMUX_HOME_DIR, docId)
        }
        if (!file.exists()) throw FileNotFoundException("File not found: $docId")
        return file
    }

    private fun includeFile(result: MatrixCursor, docId: String?, file: File?) {
        val finalFile = file ?: getFileForDocId(docId!!)
        val finalDocId = docId ?: getDocIdForFile(finalFile)

        var flags = Document.FLAG_SUPPORTS_DELETE or Document.FLAG_SUPPORTS_RENAME
        
        if (finalFile.isDirectory) {
            flags = flags or Document.FLAG_DIR_SUPPORTS_CREATE
        } else {
            flags = flags or Document.FLAG_SUPPORTS_WRITE
        }

        result.newRow().apply {
            add(Document.COLUMN_DOCUMENT_ID, finalDocId)
            add(Document.COLUMN_DISPLAY_NAME, finalFile.name)
            add(Document.COLUMN_SIZE, if (finalFile.isDirectory) null else finalFile.length())
            add(Document.COLUMN_MIME_TYPE, getTypeForFile(finalFile))
            add(Document.COLUMN_LAST_MODIFIED, finalFile.lastModified())
            add(Document.COLUMN_FLAGS, flags)
        }
    }

    private fun getTypeForFile(file: File): String {
        if (file.isDirectory) return Document.MIME_TYPE_DIR
        val extension = file.extension.lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) 
            ?: "application/octet-stream"
    }
}
