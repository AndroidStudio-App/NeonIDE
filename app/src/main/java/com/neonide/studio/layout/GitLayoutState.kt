package com.neonide.studio.layout

import androidx.compose.runtime.Immutable

@Immutable
data class GitLayoutState(
    // form fields
    val url: String = "",
    val repoName: String = "",
    val destination: String = "",
    val branch: String = "",
    val useCredentials: Boolean = false,
    val username: String = "",
    val password: String = "",
    val shallowClone: Boolean = false,
    val depth: String = "1",
    val singleBranch: Boolean = true,
    val recurseSubmodules: Boolean = false,
    val shallowSubmodules: Boolean = false,
    val openProjectAfter: Boolean = false,

    // validation errors
    val urlError: String? = null,
    val repoNameError: String? = null,
    val destinationError: String? = null,
    val depthError: String? = null,
    val usernameError: String? = null,
    val passwordError: String? = null,

    // cloning state
    val isCloning: Boolean = false,
    val progressPercent: Int = 0,       // 0..100
    val progressText: String = "",      // "Receiving objects" etc.
    val statusText: String = "",        // "Cloning…", "Done", "Failed: …"
    val isCancelled: Boolean = false    // internal flag
)