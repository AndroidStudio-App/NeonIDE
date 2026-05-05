package com.neonide.studio.app

import android.content.Context
import com.neonide.studio.R
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

class EditorFileManager(private val context: Context) {

    fun readFileText(absolutePath: String): String {
        return try {
            BufferedReader(InputStreamReader(FileInputStream(absolutePath), StandardCharsets.UTF_8)).use {
                it.readText()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    fun saveFile(file: File, content: String): Boolean {
        return try {
            file.parentFile?.mkdirs()
            file.writeText(content)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
