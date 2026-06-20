package com.neonide.studio.app.home.create.template.gradle

import com.neonide.studio.app.home.create.ProjectTemplate

fun AppBuildGradle(
    appId: String,
    minSdk: Int,
    useKotlin: Boolean,
    useKts: Boolean,
    templateKind: ProjectTemplate.Kind
): String {
    val addAndroidX =
        templateKind != ProjectTemplate.Kind.NO_ANDROIDX_ACTIVITY &&
            templateKind != ProjectTemplate.Kind.COMPOSE_ACTIVITY
    val enableNdkBuild = templateKind == ProjectTemplate.Kind.CPP_ACTIVITY
    val enableCompose = templateKind == ProjectTemplate.Kind.COMPOSE_ACTIVITY

    val deps = mutableListOf<String>()

    fun imp(notation: String): String =
        if (useKts) "implementation(\"$notation\")" else "implementation '$notation'"

    if (useKotlin) {
        deps += imp("androidx.core:core-ktx:1.13.1")
    } else {
        deps += imp("androidx.core:core:1.13.1")
    }
    if (addAndroidX) deps += imp("androidx.appcompat:appcompat:1.6.1")
    deps += imp("com.google.android.material:material:1.12.0")

    when (templateKind) {
        ProjectTemplate.Kind.BOTTOM_NAV_ACTIVITY,
        ProjectTemplate.Kind.NAV_DRAWER_ACTIVITY -> {
            deps += imp("androidx.navigation:navigation-fragment-ktx:2.8.0")
            deps += imp("androidx.navigation:navigation-ui-ktx:2.8.0")
            deps += imp("androidx.lifecycle:lifecycle-livedata-ktx:2.8.4")
            deps += imp("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.4")
        }
        ProjectTemplate.Kind.TABBED_ACTIVITY -> {
            deps += imp("androidx.lifecycle:lifecycle-livedata-ktx:2.8.4")
            deps += imp("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.4")
            deps += imp("androidx.viewpager:viewpager:1.0.0")
        }
        ProjectTemplate.Kind.EMPTY_ACTIVITY,
        ProjectTemplate.Kind.BASIC_ACTIVITY,
        ProjectTemplate.Kind.CPP_ACTIVITY -> {
            deps += imp("androidx.constraintlayout:constraintlayout:2.1.4")
        }
        else -> {}
    }

    if (enableCompose) {
        deps += imp("androidx.activity:activity-compose:1.9.2")
        deps += imp("androidx.compose.material3:material3:1.3.1")
        deps += imp("androidx.compose.ui:ui-tooling-preview:1.7.3")
    }

    val plugins = buildString {
        appendLine("alias(libs.plugins.android.application)")
        if (useKotlin) {
            appendLine("    alias(libs.plugins.kotlin.android)")
            if (enableCompose) appendLine("    alias(libs.plugins.kotlin.compose)")
        }
    }.trimEnd()

    val buildFeatures = if (enableCompose) {
        if (useKts) "buildFeatures { compose = true }" else "buildFeatures { compose true }"
    } else {
        if (useKts) "buildFeatures { viewBinding = true }" else "buildFeatures { viewBinding true }"
    }

    val ndkVersionStr = if (enableNdkBuild) {
        if (useKts) "ndkVersion = \"29.0.14206865\"" else "ndkVersion '29.0.14206865'"
    } else {
        ""
    }

    val ndkBlock = if (enableNdkBuild) {
        if (useKts) {
            "externalNativeBuild { ndkBuild { path = file(\"src/main/jni/Android.mk\") } }"
        } else {
            "externalNativeBuild { ndkBuild { path 'src/main/jni/Android.mk' } }"
        }
    } else {
        ""
    }

    val depsBlock = deps.joinToString("\n    ")
    val kotlinOptionsLine = if (useKotlin) {
        if (useKts) "kotlinOptions { jvmTarget = \"17\" }" else "kotlinOptions { jvmTarget = '17' }"
    } else {
        ""
    }

    return if (useKts) {
        AppBuildGradleKotlin(
            appId,
            minSdk,
            plugins,
            ndkVersionStr,
            buildFeatures,
            kotlinOptionsLine,
            ndkBlock,
            depsBlock
        )
    } else {
        AppBuildGradleGroovy(
            appId,
            minSdk,
            plugins,
            ndkVersionStr,
            buildFeatures,
            kotlinOptionsLine,
            ndkBlock,
            depsBlock
        )
    }
}

fun AppBuildGradleKotlin(
    appId: String,
    minSdk: Int,
    plugins: String,
    ndkVersionStr: String,
    buildFeatures: String,
    kotlinOptionsLine: String,
    ndkBlock: String,
    depsBlock: String
): String = """
plugins {
    $plugins
}

android {
    namespace = "$appId"
    compileSdk = 35
    $ndkVersionStr

    defaultConfig {
        applicationId = "$appId"
        minSdk = $minSdk
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    $buildFeatures

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    $kotlinOptionsLine

    $ndkBlock
}

dependencies {
    $depsBlock
}
""".trimIndent()

fun AppBuildGradleGroovy(
    appId: String,
    minSdk: Int,
    plugins: String,
    ndkVersionStr: String,
    buildFeatures: String,
    kotlinOptionsLine: String,
    ndkBlock: String,
    depsBlock: String
): String = """
plugins {
    $plugins
}

android {
    namespace '$appId'
    compileSdk 35
    $ndkVersionStr

    defaultConfig {
        applicationId '$appId'
        minSdk $minSdk
        targetSdk 35
        versionCode 1
        versionName '1.0'
    }

    $buildFeatures

    compileOptions {
        sourceCompatibility JavaVersion.VERSION_17
        targetCompatibility JavaVersion.VERSION_17
    }

    $kotlinOptionsLine

    $ndkBlock
}

dependencies {
    $depsBlock
}
""".trimIndent()
