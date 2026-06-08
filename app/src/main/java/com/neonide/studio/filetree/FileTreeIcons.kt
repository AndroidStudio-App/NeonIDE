package com.neonide.studio.filetree

import com.neonide.studio.R

fun iconForExtension(extension: String): Int = when (extension.lowercase()) {
    "kt" -> R.drawable.ic_filetype_kotlin
    "kts" -> R.drawable.ic_filetype_kts
    "java" -> R.drawable.ic_filetype_java
    "xml" -> R.drawable.ic_filetype_xml
    "gradle" -> R.drawable.ic_filetype_gradle
    "json", "js" -> R.drawable.ic_filetype_json
    "properties", "pro" -> R.drawable.ic_filetype_properties
    "png", "jpg", "jpeg", "webp", "gif" -> R.drawable.ic_filetype_images
    "apk" -> R.drawable.ic_file_apk
    "txt", "log" -> R.drawable.ic_filetype_txt
    "cpp", "c", "h" -> R.drawable.ic_filetype_cpp
    "yml", "yaml" -> R.drawable.ic_filetype_yml
    "toml" -> R.drawable.ic_filetype_toml
    "gitignore" -> R.drawable.ic_filetype_gitignore
    "md" -> R.drawable.ic_filetype_markdown
    "gradlew", "bat", "sh", "bash", "zsh" -> R.drawable.ic_filetype_shell
    else -> R.drawable.ic_filetype_any
}

fun iconForFolder(name: String, isOpen: Boolean): Int = when (name) {
    ".git", ".github" -> R.drawable.ic_folder_git
    "res" -> R.drawable.ic_folder_resource
    else -> if (isOpen) R.drawable.ic_folder_open else R.drawable.ic_folder
}
