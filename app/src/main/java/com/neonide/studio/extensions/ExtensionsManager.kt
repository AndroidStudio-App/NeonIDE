package com.neonide.studio.extensions

import android.content.Context
import com.termux.shared.logger.Logger
import com.termux.shared.termux.TermuxConstants
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Server directory under filesDir where LSP controller looks for servers.
 * e.g., "java-language-server" -> /data/data/.../files/java-language-server/
 */
class ExtensionsManager(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "extensions_sha_prefs"
        private const val KEY_SHA_PREFIX = "sha_"
        private const val TAG = "ExtensionsManager"
    }

    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun getServerDir(id: String): File = File(context.filesDir, id)

    fun getExtensionDir(id: String): File = getServerDir(id)

    fun isExtensionInstalled(id: String): Boolean = getServerDir(id).exists()

    fun isExtensionInstalled(extension: ExtensionEntry): Boolean {
        if (isExtensionInstalled(extension.id)) return true

        extension.checkPaths?.forEach { path ->
            val file = if (path.startsWith("/")) {
                File(TermuxConstants.TERMUX_PREFIX_DIR_PATH, path.substring(1))
            } else {
                File(TermuxConstants.TERMUX_PREFIX_DIR_PATH, path)
            }
            if (file.exists()) return true
        }

        return false
    }

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
    fun isUpdateAvailable(extension: ExtensionEntry): Boolean {
        val installedSha = getInstalledSha256(extension.id)
        return isExtensionInstalled(extension) &&
            installedSha.isNotEmpty() &&
            !installedSha.equals(extension.sha256, ignoreCase = true)
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
                val extensionDir = if (extension.type == "usr") {
                    File(TermuxConstants.TERMUX_PREFIX_DIR_PATH)
                } else {
                    getExtensionDir(extension.id)
                }

                URL(extension.url).openConnection().let { connection ->
                    val httpConnection = connection as HttpURLConnection
                    httpConnection.connectTimeout = 10000
                    httpConnection.readTimeout = 10000
                    httpConnection.setRequestProperty("User-Agent", "NeonIDE-Extensions/1.0")
                    httpConnection.connect()
                    httpConnection.inputStream.use { input ->
                        downloadFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    httpConnection.disconnect()
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
            } catch (e: java.io.IOException) {
                Result.failure(e)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                Result.failure(e)
            } catch (e: java.util.concurrent.TimeoutException) {
                Result.failure(e)
            }
        }

    suspend fun uninstallExtension(extension: ExtensionEntry): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                if (extension.type == "usr") {
                    extension.checkPaths?.forEach { path ->
                        val file = if (path.startsWith("/")) {
                            File(TermuxConstants.TERMUX_PREFIX_DIR_PATH, path.substring(1))
                        } else {
                            File(TermuxConstants.TERMUX_PREFIX_DIR_PATH, path)
                        }
                        if (file.exists()) {
                            file.deleteRecursively()
                        }
                    }
                } else {
                    val extensionDir = getExtensionDir(extension.id)
                    if (extensionDir.exists()) {
                        extensionDir.deleteRecursively()
                    }
                }
                prefs.edit().remove(KEY_SHA_PREFIX + extension.id).apply()
                Result.success(Unit)
            } catch (e: java.io.IOException) {
                Result.failure(e)
            } catch (e: java.io.FileNotFoundException) {
                Result.failure(e)
            } catch (e: IllegalArgumentException) {
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
        try {
            val process = ProcessBuilder(
                "unzip",
                "-oq",
                zipFile.absolutePath,
                "-d",
                targetDir.absolutePath
            ).start()
            if (process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS) &&
                process.exitValue() == 0
            ) {
                return
            }
            process.destroyForcibly()
        } catch (e: java.io.IOException) {
            Logger.logError(TAG, "unzip via unzip binary failed: ${e.message}")
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            Logger.logError(TAG, "unzip via unzip binary interrupted: ${e.message}")
        } catch (e: java.util.concurrent.TimeoutException) {
            Logger.logError(TAG, "unzip via unzip binary timed out: ${e.message}")
        }
        val canonicalTargetDir = targetDir.canonicalPath
        java.util.zip.ZipInputStream(zipFile.inputStream()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val outFile = File(targetDir, entry.name)
                if (!outFile.canonicalPath.startsWith(canonicalTargetDir)) {
                    throw SecurityException("Invalid zip entry: ${entry.name}")
                }
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
