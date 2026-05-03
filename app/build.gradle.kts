import java.math.BigInteger
import java.security.MessageDigest

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    alias(libs.plugins.kotlin.serialization)
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
        
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
        compose = true
    }

    signingConfigs {
        getByName("debug") {
            storeFile = file("testkey_untrusted.jks")
            keyAlias = "alias"
            storePassword = "xrj45yWGLbsO7W0v"
            keyPassword = "xrj45yWGLbsO7W0v"
        }

        create("release") {
            storeFile = file("release.jks")
            storePassword = project.findProperty("KEYSTORE_PASSWORD")?.toString()
                ?: System.getenv("KEYSTORE_PASSWORD")
            keyAlias = "my-key"
            keyPassword = project.findProperty("KEY_PASSWORD")?.toString()
                ?: System.getenv("KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
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

    lint {
        disable += "ProtectedPermissions"
        disable += "ExpiredTargetSdkVersion"
        abortOnError = false
        checkReleaseBuilds = false
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
    implementation(project(":termux-app"))
    implementation(project(":termux-shared"))
    implementation(project(":bonsai-core"))
    implementation(project(":bonsai-file-system"))
    
    implementation(libs.androidx.core)
    implementation(libs.androidx.appcompat)
    implementation(platform(libs.androidx.compose.bom))
    implementation(platform(libs.rosemoe.editor.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
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
    implementation(libs.bundles.rosemoe)

    implementation(libs.hiddenapibypass)
    implementation(libs.termux.am.library)
    implementation(libs.jhighlight)
    implementation(libs.jcodings)
    implementation(libs.joni)
    implementation(libs.snakeyaml)
    implementation(libs.jdt.annotation)
    implementation(libs.lsp4j)
    implementation(libs.jgit)

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
