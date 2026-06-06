package com.neonide.studio.extensions

import android.content.Context
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ExtensionsManager(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "extensions_sha_prefs"
        private const val KEY_SHA_PREFIX = "sha_"
    }

    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Server directory under filesDir where LSP controller looks for servers.
     * e.g., "java-language-server" -> /data/data/.../files/java-language-server/
     */
    private fun getServerDir(id: String): File = File(context.filesDir, id)

    fun getExtensionDir(id: String): File = getServerDir(id)

    fun isExtensionInstalled(id: String): Boolean = getServerDir(id).exists()

    /**
     * Returns the SHA-256 of the installed extension, or empty string if not tracked.
     */
    fun getInstalledSha256(id: String): String = prefs.getString(KEY_SHA_PREFIX + id, "") ?: ""

    /**
     * Stores the SHA-256 of the installed extension.
     */
    private fun setInstalledSha256(id: String, sha256: String) {
        prefs.edit().putString(KEY_SHA_PREFIX + id, sha256).apply()
    }

    /**
     * Checks if an installed extension needs an update by comparing SHA-256.
     * Returns true if the extension is installed but SHA-256 doesn't match the expected value.
     */
    fun isUpdateAvailable(id: String, expectedSha256: String): Boolean {
        val installedSha = getInstalledSha256(id)
        return isExtensionInstalled(id) && installedSha.isNotEmpty() &&
            !installedSha.equals(expectedSha256, ignoreCase = true)
    }

    /**
     * Returns the size of an extension directory in bytes.
     */
    fun getExtensionSize(id: String): Long {
        val dir = getServerDir(id)
        if (!dir.exists()) {
            return 0L
        }
        val files = dir.walkTopDown().filter { it.isFile }.toList()
        return files.sumOf { it.length() }
    }

    /**
     * Returns a formatted string for a size in bytes.
     */
    fun formatSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${String.format("%.1f", bytes / 1024.0)} KB"
        bytes < 1024 * 1024 * 1024 -> "${String.format("%.1f", bytes / (1024.0 * 1024.0))} MB"
        else -> "${String.format("%.1f", bytes / (1024.0 * 1024.0 * 1024.0))} GB"
    }

    suspend fun installExtension(extension: ExtensionEntry): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val downloadFile = File(context.cacheDir, "${extension.id}.download")
                val extensionDir = getExtensionDir(extension.id)

                java.net.URL(extension.url).openConnection().let { connection ->
                    val httpConnection = connection as java.net.HttpURLConnection
                    httpConnection.connectTimeout = 30000
                    httpConnection.readTimeout = 30000
                    httpConnection.setRequestProperty("User-Agent", "NeonIDE-Extensions/1.0")
                    httpConnection.connect()
                    val responseCode = httpConnection.responseCode
                    if (responseCode != 200) {
                        httpConnection.disconnect()
                        return@withContext Result.failure(
                            Exception("Download failed: HTTP $responseCode")
                        )
                    }
                    httpConnection.inputStream.use { input ->
                        downloadFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    httpConnection.disconnect()
                }

                if (!downloadFile.exists() || downloadFile.length() == 0L) {
                    return@withContext Result.failure(
                        Exception("Download failed: file empty or missing")
                    )
                }

                val actualSha256 = calculateSha256(downloadFile)
                if (!actualSha256.equals(extension.sha256, ignoreCase = true)) {
                    downloadFile.delete()
                    return@withContext Result.failure(Exception("SHA-256 checksum mismatch"))
                }

                val extensionName = extension.url.substringAfterLast('/')
                when {
                    extensionName.endsWith(".zip") -> {
                        unzipFile(downloadFile, extensionDir)
                    }

                    extensionName.endsWith(".jar") -> {
                        val targetJar = File(extensionDir, extensionName)
                        downloadFile.copyTo(targetJar, overwrite = true)
                    }

                    else -> {
                        val targetFile = File(extensionDir, extensionName)
                        downloadFile.copyTo(targetFile, overwrite = true)
                    }
                }

                downloadFile.delete()
                setInstalledSha256(extension.id, actualSha256)

                Result.success(extensionDir.absolutePath)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun uninstallExtension(extension: ExtensionEntry): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val extensionDir = getExtensionDir(extension.id)
                if (extensionDir.exists()) {
                    extensionDir.deleteRecursively()
                }
                prefs.edit().remove(KEY_SHA_PREFIX + extension.id).apply()
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private fun calculateSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun unzipFile(zipFile: File, targetDir: File) {
        java.util.zip.ZipInputStream(zipFile.inputStream()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val outFile = File(targetDir, entry.name)
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    outFile.outputStream().use { output ->
                        zip.copyTo(output)
                    }
                }
                entry = zip.nextEntry
            }
        }
    }
}
