package com.neonide.studio.utils

data class OpenFile(
    val path: String,
    val name: String,
    val content: String,
    val isModified: Boolean = false
)
