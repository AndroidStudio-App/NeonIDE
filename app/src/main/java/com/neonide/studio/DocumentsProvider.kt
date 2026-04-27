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

    private val queryHelper = QueryHelper()

    companion object {
        private const val MAX_SEARCH_RESULTS = 50
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

    override fun queryRoots(
        projection: Array<out String>?
    ): Cursor = queryHelper.queryRoots(projection, context)

    override fun queryDocument(
        documentId: String, projection: Array<out String>?
    ): Cursor = queryHelper.queryDocument(documentId, projection)

    override fun queryChildDocuments(
        parentDocumentId: String,
        projection: Array<out String>?,
        sortOrder: String?
    ): Cursor = queryHelper.queryChildDocuments(parentDocumentId, projection)

    @Throws(FileNotFoundException::class)
    override fun openDocument(
        documentId: String,
        mode: String,
        signal: CancellationSignal?
    ): ParcelFileDescriptor = queryHelper.openDocument(documentId, mode)

    @Throws(FileNotFoundException::class)
    override fun createDocument(
        parentDocumentId: String,
        mimeType: String,
        displayName: String
    ): String = queryHelper.createDocument(parentDocumentId, mimeType, displayName)

    @Throws(FileNotFoundException::class)
    override fun deleteDocument(documentId: String) = queryHelper.deleteDocument(documentId)

    @Throws(FileNotFoundException::class)
    override fun renameDocument(
        documentId: String, displayName: String
    ): String = queryHelper.renameDocument(documentId, displayName)

    override fun querySearchDocuments(
        rootId: String,
        query: String,
        projection: Array<out String>?
    ): Cursor = queryHelper.querySearchDocuments(query, projection)

    private class QueryHelper(
        private val searchHelper: SearchHelper = SearchHelper(),
        private val fileOpsHelper: FileOpsHelper = FileOpsHelper()
    ) {
        fun queryRoots(projection: Array<out String>?, context: android.content.Context?) =
            fileOpsHelper.queryRoots(projection, context)

        fun queryDocument(documentId: String, projection: Array<out String>?) =
            fileOpsHelper.queryDocument(documentId, projection)

        fun queryChildDocuments(parentDocumentId: String, projection: Array<out String>?) =
            fileOpsHelper.queryChildDocuments(parentDocumentId, projection)

        fun openDocument(documentId: String, mode: String) =
            fileOpsHelper.openDocument(documentId, mode)

        fun createDocument(parentDocumentId: String, mimeType: String, displayName: String) =
            fileOpsHelper.createDocument(parentDocumentId, mimeType, displayName)

        fun deleteDocument(documentId: String) =
            fileOpsHelper.deleteDocument(documentId)

        fun renameDocument(documentId: String, displayName: String) =
            fileOpsHelper.renameDocument(documentId, displayName)

        fun querySearchDocuments(query: String, projection: Array<out String>?) =
            searchHelper.search(query, projection)
    }

    private class FileOpsHelper {
        fun queryRoots(projection: Array<out String>?, context: android.content.Context?): Cursor {
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

        fun queryDocument(documentId: String, projection: Array<out String>?): Cursor {
            val result = MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION)
            includeFile(result, documentId, null)
            return result
        }

        fun queryChildDocuments(
            parentDocumentId: String,
            projection: Array<out String>?
        ): Cursor {
            val result = MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION)
            val parent = getFileForDocId(parentDocumentId)
            parent.listFiles()?.forEach {
                includeFile(result, null, it)
            }
            return result
        }

        fun openDocument(documentId: String, mode: String): ParcelFileDescriptor {
            val file = getFileForDocId(documentId)
            val parseMode = ParcelFileDescriptor.parseMode(mode)
            return ParcelFileDescriptor.open(file, parseMode)
        }

        fun createDocument(parentDocumentId: String, mimeType: String, displayName: String): String {
            val parent = getFileForDocId(parentDocumentId)
            val file = File(parent, displayName)
            val success = if (Document.MIME_TYPE_DIR == mimeType) file.mkdir() else file.createNewFile()
            if (!success) throw FileNotFoundException("Failed to create document: $displayName")
            return getDocIdForFile(file)
        }

        fun deleteDocument(documentId: String) {
            val file = getFileForDocId(documentId)
            if (!file.deleteRecursively()) throw FileNotFoundException("Failed to delete $documentId")
        }

        fun renameDocument(documentId: String, displayName: String): String {
            val file = getFileForDocId(documentId)
            val newFile = File(file.parentFile, displayName)
            if (!file.renameTo(newFile)) throw FileNotFoundException("Failed to rename to $displayName")
            return getDocIdForFile(newFile)
        }

        fun includeFile(result: MatrixCursor, docId: String?, file: File?) {
            val finalFile = file ?: getFileForDocId(docId!!)
            val finalDocId = docId ?: getDocIdForFile(finalFile)
            var flags = Document.FLAG_SUPPORTS_DELETE or
                Document.FLAG_SUPPORTS_RENAME
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
            return MimeTypeMap.getSingleton().getMimeTypeFromExtension(
                file.extension.lowercase()
            ) ?: "application/octet-stream"
        }

        private fun getDocIdForFile(file: File): String {
            val path = file.absolutePath
            val basePath = TermuxConstants.TERMUX_HOME_DIR.absolutePath
            return when {
                path == basePath -> "/"
                path.startsWith(basePath) -> path.substring(basePath.length)
                else -> path
            }
        }

        private fun getFileForDocId(docId: String): File {
            val file = if (docId == "/") {
                TermuxConstants.TERMUX_HOME_DIR
            } else {
                File(TermuxConstants.TERMUX_HOME_DIR, docId)
            }
            if (!file.exists()) throw FileNotFoundException("File not found: $docId")
            return file
        }
    }

    private class SearchHelper(
        private val fileOpsHelper: FileOpsHelper =
            FileOpsHelper()
    ) {
        fun search(
            query: String, projection: Array<out String>?
        ): Cursor {
            val result = MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION)
            TermuxConstants.TERMUX_HOME_DIR.walkTopDown()
                .filter { it.name.contains(query, ignoreCase = true) }
                .take(MAX_SEARCH_RESULTS)
                .forEach {
                    fileOpsHelper.includeFile(
                        result,
                        null,
                        it,
                        ::getDocIdForFile,
                        ::getFileForDocId
                    )
                }
            return result
        }
    }
}
