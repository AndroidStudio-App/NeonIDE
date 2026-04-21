import java.math.BigInteger
import java.security.MessageDigest

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.neonide.studio"
    compileSdk = 36
    ndkVersion = "29.0.14033849"

    defaultConfig {
        applicationId = "com.neonide.studio"
        minSdk = 21
        targetSdk = 28
        versionCode = 1
        versionName = "0.1"

        manifestPlaceholders += mapOf(
            "TERMUX_PACKAGE_NAME" to "com.neonide.studio",
            "TERMUX_APP_NAME" to "NeonIDE Studio",
            "TERMUX_API_APP_NAME" to "NeonIDE Studio:API",
            "TERMUX_BOOT_APP_NAME" to "NeonIDE Studio:Boot",
            "TERMUX_FLOAT_APP_NAME" to "NeonIDE Studio:Float",
            "TERMUX_STYLING_APP_NAME" to "NeonIDE Studio:Styling",
            "TERMUX_TASKER_APP_NAME" to "NeonIDE Studio:Tasker",
            "TERMUX_WIDGET_APP_NAME" to "NeonIDE Studio:Widget"
        )
        
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
    }

    signingConfigs {
        getByName("debug") {
            storeFile = file("testkey_untrusted.jks")
            keyAlias = "alias"
            storePassword = "xrj45yWGLbsO7W0v"
            keyPassword = "xrj45yWGLbsO7W0v"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    externalNativeBuild {
        ndkBuild {
            path("src/main/cpp/Android.mk")
        }
    }

    lint {
        disable += "ProtectedPermissions"
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {
    implementation(libs.androidx.core)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.collection)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.drawerlayout)
    implementation(libs.androidx.preference)
    implementation(libs.androidx.viewpager)
    implementation(libs.androidx.viewpager2)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.window)

    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.guava) {
        exclude(group = "org.codehaus.mojo", module = "animal-sniffer-annotations")
    }
    implementation(libs.commons.io)
    implementation(libs.gson)
    implementation(libs.moshi)

    implementation(libs.bundles.markwon)
    implementation(libs.bundles.monarch)
    implementation(libs.bundles.regex)
    implementation(libs.bundles.treesitter)

    implementation(libs.hiddenapibypass)
    implementation(libs.termux.am.library)
    implementation(libs.jhighlight)
    implementation(libs.jcodings)
    implementation(libs.joni)
    implementation(libs.snakeyaml)
    implementation(libs.jdt.annotation)
    implementation(libs.lsp4j)

    annotationProcessor(files("libs/annotation-processors.jar", "libs/annotations.jar"))
    annotationProcessor(libs.javapoet)

    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"), "exclude" to listOf("annotation-processors.jar", "annotations.jar"))))

    coreLibraryDesugaring(libs.desugar.jdk.libs)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.core.testing)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.espresso.core)
}

fun sha256Of(f: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    f.inputStream().use { stream ->
        val buffer = ByteArray(8192)
        var bytesRead: Int
        while (stream.read(buffer).also { bytesRead = it } != -1) {
            digest.update(buffer, 0, bytesRead)
        }
    }
    return BigInteger(1, digest.digest()).toString(16).padStart(64, '0')
}

val bootstrapZipFile = file("$projectDir/src/main/cpp/bootstrap-aarch64.zip")
val bootstrapStampFile = file("$projectDir/src/main/cpp/generated/bootstrap-stamp.S")
val bootstrapZipSha256 = if (bootstrapZipFile.exists()) sha256Of(bootstrapZipFile) else "missing"
val bootstrapZipSize = if (bootstrapZipFile.exists()) bootstrapZipFile.length() else -1L

tasks.register("generateBootstrapStamp") {
    inputs.file(bootstrapZipFile)
    outputs.file(bootstrapStampFile)
    doLast {
        val sha = if (bootstrapZipFile.exists()) sha256Of(bootstrapZipFile) else "missing"
        val size = if (bootstrapZipFile.exists()) bootstrapZipFile.length() else -1L
        bootstrapStampFile.parentFile.mkdirs()
        bootstrapStampFile.writeText(
        """
        // Generated by Gradle. Do not edit.
        .section .rodata
        .globl BOOTSTRAP_SHA256
        .type BOOTSTRAP_SHA256, @object
        BOOTSTRAP_SHA256:
           .asciz "${sha}"

        .globl BOOTSTRAP_SIZE
        .type BOOTSTRAP_SIZE, @object
        BOOTSTRAP_SIZE:
            .quad ${size}
        """.trimIndent()
    )
    }
}

tasks.register("cleanNativeIfBootstrapChanged") {
    val stateFile = file("${layout.buildDirectory.asFile.get()}/bootstrap-embedded.sha256")
    inputs.file(bootstrapZipFile)
    outputs.file(stateFile)

    doLast {
        val currentSha = if (bootstrapZipFile.exists()) sha256Of(bootstrapZipFile) else "missing"
        val previousSha = if (stateFile.exists()) stateFile.readText().trim() else null

        if (previousSha == null || previousSha != currentSha) {
            logger.lifecycle("Bootstrap zip changed (old=${previousSha}, new=${currentSha}). Cleaning native intermediates...")
            delete(
                file("${layout.buildDirectory.asFile.get()}/intermediates/ndkBuild"),
                file("${layout.buildDirectory.asFile.get()}/intermediates/cxx"),
                file("${layout.buildDirectory.asFile.get()}/intermediates/merged_native_libs"),
                file("${layout.buildDirectory.asFile.get()}/intermediates/stripped_native_libs")
            )
        }

        stateFile.parentFile.mkdirs()
        stateFile.writeText("$currentSha\n")
    }
}

afterEvaluate {
    val zip = file("$projectDir/src/main/cpp/bootstrap-aarch64.zip")
    val stamp = file("$projectDir/src/main/cpp/generated/bootstrap-stamp.S")

    tasks.matching { 
        it.name.startsWith("externalNativeBuild") || 
        it.name.startsWith("configureNdkBuild") || 
        it.name.startsWith("buildNdkBuild") 
    }.configureEach {
        dependsOn(tasks.named("generateBootstrapStamp"))
        inputs.file(zip)
        inputs.file(stamp)
    }

    tasks.matching { it.name == "packageDebug" || it.name == "packageRelease" }.configureEach {
        doFirst {
            outputs.files.files.forEach { f ->
                if (f.exists() && f.isFile) {
                    logger.lifecycle("Deleting stale APK output before packaging: ${f}")
                    f.delete()
                }
            }
        }
    }
}

tasks.named("preBuild") {
    dependsOn(
        tasks.named("generateBootstrapStamp"),
        tasks.named("cleanNativeIfBootstrapChanged")
    )
}