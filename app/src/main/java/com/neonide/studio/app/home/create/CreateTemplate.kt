package com.neonide.studio.app.home.create

import android.content.Context
import com.neonide.studio.app.home.create.template.AppManifest
import com.neonide.studio.app.home.create.template.BaseLayoutContentMain
import com.neonide.studio.app.home.create.template.DefaultProguardRules
import com.neonide.studio.app.home.create.template.SimpleMaterial3Theme
import com.neonide.studio.app.home.create.template.basicactivity.BasicActivityJava
import com.neonide.studio.app.home.create.template.basicactivity.BasicActivityKt
import com.neonide.studio.app.home.create.template.basicactivity.xml.BasicActivityLayout
import com.neonide.studio.app.home.create.template.bottomnav.BottomNavActivityJava
import com.neonide.studio.app.home.create.template.bottomnav.BottomNavActivityKt
import com.neonide.studio.app.home.create.template.bottomnav.BottomNavDashboardFragmentJava
import com.neonide.studio.app.home.create.template.bottomnav.BottomNavDashboardFragmentKt
import com.neonide.studio.app.home.create.template.bottomnav.BottomNavDashboardViewModelJava
import com.neonide.studio.app.home.create.template.bottomnav.BottomNavDashboardViewModelKt
import com.neonide.studio.app.home.create.template.bottomnav.BottomNavHomeFragmentJava
import com.neonide.studio.app.home.create.template.bottomnav.BottomNavHomeFragmentKt
import com.neonide.studio.app.home.create.template.bottomnav.BottomNavHomeViewModelJava
import com.neonide.studio.app.home.create.template.bottomnav.BottomNavHomeViewModelKt
import com.neonide.studio.app.home.create.template.bottomnav.BottomNavNotificationsFragmentJava
import com.neonide.studio.app.home.create.template.bottomnav.BottomNavNotificationsFragmentKt
import com.neonide.studio.app.home.create.template.bottomnav.BottomNavNotificationsViewModelJava
import com.neonide.studio.app.home.create.template.bottomnav.BottomNavNotificationsViewModelKt
import com.neonide.studio.app.home.create.template.bottomnav.xml.BottomNavNavigationXml
import com.neonide.studio.app.home.create.template.bottomnav.xml.BottomNavStringsXml
import com.neonide.studio.app.home.create.template.compose.ComposeActivityKt
import com.neonide.studio.app.home.create.template.compose.ComposeColorKt
import com.neonide.studio.app.home.create.template.compose.ComposeThemeKt
import com.neonide.studio.app.home.create.template.compose.ComposeThemesXml
import com.neonide.studio.app.home.create.template.compose.ComposeTypeKt
import com.neonide.studio.app.home.create.template.cppactivity.CppActivityJava
import com.neonide.studio.app.home.create.template.cppactivity.CppActivityKt
import com.neonide.studio.app.home.create.template.cppactivity.jni.AndroidMkFile
import com.neonide.studio.app.home.create.template.cppactivity.jni.ApplicationMkFile
import com.neonide.studio.app.home.create.template.cppactivity.jni.BasicJniCppSource
import com.neonide.studio.app.home.create.template.drawernav.NavDrawerActivityJava
import com.neonide.studio.app.home.create.template.drawernav.NavDrawerActivityKt
import com.neonide.studio.app.home.create.template.drawernav.xml.NavDrawerNavigationXml
import com.neonide.studio.app.home.create.template.drawernav.xml.NavDrawerStringsXml
import com.neonide.studio.app.home.create.template.emptyactivity.EmptyActivityJava
import com.neonide.studio.app.home.create.template.emptyactivity.EmptyActivityKt
import com.neonide.studio.app.home.create.template.gradle.AppBuildGradle
import com.neonide.studio.app.home.create.template.gradle.GradleProperties
import com.neonide.studio.app.home.create.template.gradle.RootBuildGradle
import com.neonide.studio.app.home.create.template.gradle.SettingsGradle
import com.neonide.studio.app.home.create.template.noandroidx.NoAndroidXActivityJava
import com.neonide.studio.app.home.create.template.noandroidx.NoAndroidXActivityKt
import com.neonide.studio.app.home.create.template.noandroidx.xml.NoAndroidXActivityLayout
import com.neonide.studio.app.home.create.template.tabactivity.TabbedActivityJava
import com.neonide.studio.app.home.create.template.tabactivity.TabbedActivityKt
import com.neonide.studio.app.home.create.template.tabactivity.TabbedPageViewModelJava
import com.neonide.studio.app.home.create.template.tabactivity.TabbedPageViewModelKt
import com.neonide.studio.app.home.create.template.tabactivity.TabbedPagerAdapterJava
import com.neonide.studio.app.home.create.template.tabactivity.TabbedPagerAdapterKt
import com.neonide.studio.app.home.create.template.tabactivity.TabbedPlaceholderFragmentJava
import com.neonide.studio.app.home.create.template.tabactivity.TabbedPlaceholderFragmentKt
import com.neonide.studio.app.home.create.template.tabactivity.xml.TabbedStringsXml
import java.io.File

fun CreateTemplate(
    context: Context,
    template: ProjectTemplate,
    projectDir: File,
    applicationId: String,
    minSdk: Int,
    language: String,
    useKts: Boolean = false
) {
    val useKotlin = language.equals("Kotlin", ignoreCase = true)
    val name = projectDir.name
    val ext = if (useKts) "kts" else ""
    val srcType = if (useKotlin) "kotlin" else "java"

    val appDir = File(projectDir, "app")
    val srcMain = File(appDir, "src/main")
    val resDir = File(srcMain, "res")
    val codeDir = File(srcMain, srcType)
    val valuesDir = File(resDir, "values")
    val valuesNightDir = File(resDir, "values-night")

    listOf(codeDir, valuesDir, valuesNightDir).forEach { it.mkdirs() }

    writeText(File(projectDir, "settings.gradle.$ext"), SettingsGradle(name))
    writeText(File(projectDir, "build.gradle.$ext"), RootBuildGradle(useKts))
    writeText(
        File(projectDir, "gradle.properties"),
        GradleProperties(
            addAndroidX = template.kind != ProjectTemplate.Kind.NO_ANDROIDX_ACTIVITY
        )
    )
    writeText(File(projectDir, "proguard-rules.pro"), DefaultProguardRules())

    copyLibsVersionsToml(context, projectDir)
    copyGradleWrapper(context, projectDir)
    copyAsset(context, "acs-templates/base/root/gitignore", File(projectDir, ".gitignore"))
    copyAssetsDir(context, "atc/resources", resDir)

    writeText(File(valuesDir, "strings.xml"), buildStringsXml(name))
    writeText(File(valuesDir, "themes.xml"), SimpleMaterial3Theme("AppTheme", false))
    writeText(File(valuesNightDir, "themes.xml"), SimpleMaterial3Theme("AppTheme", false))
    writeText(
        File(appDir, "build.gradle.$ext"),
        AppBuildGradle(
            appId = applicationId,
            minSdk = minSdk,
            useKotlin = useKotlin,
            useKts = useKts,
            templateKind = template.kind
        )
    )
    writeText(File(srcMain, "AndroidManifest.xml"), AppManifest(applicationId))

    val pkgDir = File(codeDir, applicationId.replace('.', '/')).apply { mkdirs() }

    when (template.kind) {
        ProjectTemplate.Kind.NO_ACTIVITY -> {}

        ProjectTemplate.Kind.EMPTY_ACTIVITY -> {
            writeText(File(resDir, "layout/activity_main.xml"), BaseLayoutContentMain())
            writeText(
                File(pkgDir, if (useKotlin) "MainActivity.kt" else "MainActivity.java"),
                if (useKotlin) EmptyActivityKt(applicationId) else EmptyActivityJava(applicationId)
            )
        }
        ProjectTemplate.Kind.CPP_ACTIVITY -> {
            writeText(File(resDir, "layout/activity_main.xml"), BaseLayoutContentMain())
            writeText(
                File(pkgDir, if (useKotlin) "MainActivity.kt" else "MainActivity.java"),
                if (useKotlin) CppActivityKt(applicationId) else CppActivityJava(applicationId)
            )
            val jniDir = File(appDir, "src/main/jni").apply { mkdirs() }
            writeText(File(jniDir, "tomaslib.cpp"), BasicJniCppSource(applicationId))
            writeText(File(jniDir, "Android.mk"), AndroidMkFile())
            writeText(File(jniDir, "Application.mk"), ApplicationMkFile())
        }
        ProjectTemplate.Kind.BASIC_ACTIVITY -> {
            writeText(File(resDir, "layout/activity_main.xml"), BasicActivityLayout())
            writeText(File(resDir, "layout/content_main.xml"), BaseLayoutContentMain())
            writeText(
                File(pkgDir, if (useKotlin) "MainActivity.kt" else "MainActivity.java"),
                if (useKotlin) BasicActivityKt(applicationId) else BasicActivityJava(applicationId)
            )
        }
        ProjectTemplate.Kind.NAV_DRAWER_ACTIVITY -> {
            copyAssetsDir(context, "templates/navDrawer/res", resDir)
            writeText(
                File(resDir, "navigation/mobile_navigation.xml"),
                NavDrawerNavigationXml(applicationId, "mobile_navigation")
            )
            mergeStringsXml(valuesDir, NavDrawerStringsXml())
            writeText(
                File(pkgDir, if (useKotlin) "MainActivity.kt" else "MainActivity.java"),
                if (useKotlin) {
                    NavDrawerActivityKt(
                        applicationId
                    )
                } else {
                    NavDrawerActivityJava(applicationId)
                }
            )
            val pkg = applicationId
            val bind = { name: String, from: String, to: String -> name.replace(from, to) }
            if (useKotlin) {
                writeKotlin(
                    codeDir,
                    pkg,
                    "ui/home",
                    "HomeViewModel.kt",
                    BottomNavHomeViewModelKt(pkg)
                )
                writeKotlin(
                    codeDir,
                    pkg,
                    "ui/home",
                    "HomeFragment.kt",
                    BottomNavHomeFragmentKt(pkg)
                )
                writeKotlin(
                    codeDir,
                    pkg,
                    "ui/gallery",
                    "GalleryViewModel.kt",
                    BottomNavDashboardViewModelKt(
                        pkg
                    ).replace(
                        "ui.dashboard",
                        "ui.gallery"
                    ).replace(
                        "DashboardViewModel",
                        "GalleryViewModel"
                    ).replace("dashboard", "gallery")
                )
                writeKotlin(
                    codeDir,
                    pkg,
                    "ui/gallery",
                    "GalleryFragment.kt",
                    BottomNavDashboardFragmentKt(
                        pkg
                    ).replace(
                        "ui.dashboard",
                        "ui.gallery"
                    ).replace(
                        "DashboardFragment",
                        "GalleryFragment"
                    ).replace(
                        "FragmentDashboardBinding",
                        "FragmentGalleryBinding"
                    ).replace(
                        "textDashboard",
                        "textGallery"
                    ).replace("DashboardViewModel", "GalleryViewModel")
                )
                writeKotlin(
                    codeDir,
                    pkg,
                    "ui/slideshow",
                    "SlideshowViewModel.kt",
                    BottomNavDashboardViewModelKt(
                        pkg
                    ).replace(
                        "ui.dashboard",
                        "ui.slideshow"
                    ).replace(
                        "DashboardViewModel",
                        "SlideshowViewModel"
                    ).replace("dashboard", "slideshow")
                )
                writeKotlin(
                    codeDir,
                    pkg,
                    "ui/slideshow",
                    "SlideshowFragment.kt",
                    BottomNavDashboardFragmentKt(
                        pkg
                    ).replace(
                        "ui.dashboard",
                        "ui.slideshow"
                    ).replace(
                        "DashboardFragment",
                        "SlideshowFragment"
                    ).replace(
                        "FragmentDashboardBinding",
                        "FragmentSlideshowBinding"
                    ).replace(
                        "textDashboard",
                        "textSlideshow"
                    ).replace("DashboardViewModel", "SlideshowViewModel")
                )
            } else {
                writeJava(
                    codeDir,
                    pkg,
                    "ui/home",
                    "HomeViewModel.java",
                    BottomNavHomeViewModelJava(pkg)
                )
                writeJava(
                    codeDir,
                    pkg,
                    "ui/home",
                    "HomeFragment.java",
                    BottomNavHomeFragmentJava(pkg)
                )
                writeJava(
                    codeDir,
                    pkg,
                    "ui/gallery",
                    "GalleryViewModel.java",
                    BottomNavDashboardViewModelJava(
                        pkg
                    ).replace(
                        "ui.dashboard",
                        "ui.gallery"
                    ).replace(
                        "DashboardViewModel",
                        "GalleryViewModel"
                    ).replace("dashboard", "gallery")
                )
                writeJava(
                    codeDir,
                    pkg,
                    "ui/gallery",
                    "GalleryFragment.java",
                    BottomNavDashboardFragmentJava(
                        pkg
                    ).replace(
                        "ui.dashboard",
                        "ui.gallery"
                    ).replace(
                        "DashboardFragment",
                        "GalleryFragment"
                    ).replace(
                        "FragmentDashboardBinding",
                        "FragmentGalleryBinding"
                    ).replace(
                        "textDashboard",
                        "textGallery"
                    ).replace("DashboardViewModel", "GalleryViewModel")
                )
                writeJava(
                    codeDir,
                    pkg,
                    "ui/slideshow",
                    "SlideshowViewModel.java",
                    BottomNavDashboardViewModelJava(
                        pkg
                    ).replace(
                        "ui.dashboard",
                        "ui.slideshow"
                    ).replace(
                        "DashboardViewModel",
                        "SlideshowViewModel"
                    ).replace("dashboard", "slideshow")
                )
                writeJava(
                    codeDir,
                    pkg,
                    "ui/slideshow",
                    "SlideshowFragment.java",
                    BottomNavDashboardFragmentJava(
                        pkg
                    ).replace(
                        "ui.dashboard",
                        "ui.slideshow"
                    ).replace(
                        "DashboardFragment",
                        "SlideshowFragment"
                    ).replace(
                        "FragmentDashboardBinding",
                        "FragmentSlideshowBinding"
                    ).replace(
                        "textDashboard",
                        "textSlideshow"
                    ).replace("DashboardViewModel", "SlideshowViewModel")
                )
            }
        }
        ProjectTemplate.Kind.BOTTOM_NAV_ACTIVITY -> {
            copyAssetsDir(context, "templates/bottomNav/res", resDir)
            writeText(
                File(resDir, "navigation/mobile_navigation.xml"),
                BottomNavNavigationXml(applicationId, "mobile_navigation")
            )
            mergeStringsXml(valuesDir, BottomNavStringsXml())
            writeText(
                File(pkgDir, if (useKotlin) "MainActivity.kt" else "MainActivity.java"),
                if (useKotlin) {
                    BottomNavActivityKt(
                        applicationId
                    )
                } else {
                    BottomNavActivityJava(applicationId)
                }
            )
            val pkg = applicationId
            if (useKotlin) {
                writeKotlin(
                    codeDir,
                    pkg,
                    "ui/home",
                    "HomeViewModel.kt",
                    BottomNavHomeViewModelKt(pkg)
                )
                writeKotlin(
                    codeDir,
                    pkg,
                    "ui/home",
                    "HomeFragment.kt",
                    BottomNavHomeFragmentKt(pkg)
                )
                writeKotlin(
                    codeDir,
                    pkg,
                    "ui/dashboard",
                    "DashboardViewModel.kt",
                    BottomNavDashboardViewModelKt(pkg)
                )
                writeKotlin(
                    codeDir,
                    pkg,
                    "ui/dashboard",
                    "DashboardFragment.kt",
                    BottomNavDashboardFragmentKt(pkg)
                )
                writeKotlin(
                    codeDir,
                    pkg,
                    "ui/notifications",
                    "NotificationsViewModel.kt",
                    BottomNavNotificationsViewModelKt(pkg)
                )
                writeKotlin(
                    codeDir,
                    pkg,
                    "ui/notifications",
                    "NotificationsFragment.kt",
                    BottomNavNotificationsFragmentKt(pkg)
                )
            } else {
                writeJava(
                    codeDir,
                    pkg,
                    "ui/home",
                    "HomeViewModel.java",
                    BottomNavHomeViewModelJava(pkg)
                )
                writeJava(
                    codeDir,
                    pkg,
                    "ui/home",
                    "HomeFragment.java",
                    BottomNavHomeFragmentJava(pkg)
                )
                writeJava(
                    codeDir,
                    pkg,
                    "ui/dashboard",
                    "DashboardViewModel.java",
                    BottomNavDashboardViewModelJava(pkg)
                )
                writeJava(
                    codeDir,
                    pkg,
                    "ui/dashboard",
                    "DashboardFragment.java",
                    BottomNavDashboardFragmentJava(pkg)
                )
                writeJava(
                    codeDir,
                    pkg,
                    "ui/notifications",
                    "NotificationsViewModel.java",
                    BottomNavNotificationsViewModelJava(pkg)
                )
                writeJava(
                    codeDir,
                    pkg,
                    "ui/notifications",
                    "NotificationsFragment.java",
                    BottomNavNotificationsFragmentJava(pkg)
                )
            }
        }
        ProjectTemplate.Kind.TABBED_ACTIVITY -> {
            copyAssetsDir(context, "templates/tabbed/res", resDir)
            mergeStringsXml(valuesDir, TabbedStringsXml())
            writeText(
                File(pkgDir, if (useKotlin) "MainActivity.kt" else "MainActivity.java"),
                if (useKotlin) {
                    TabbedActivityKt(
                        applicationId
                    )
                } else {
                    TabbedActivityJava(applicationId)
                }
            )
            val pkg = applicationId
            if (useKotlin) {
                writeKotlin(
                    codeDir,
                    pkg,
                    "ui/main",
                    "SectionsPagerAdapter.kt",
                    TabbedPagerAdapterKt(pkg)
                )
                writeKotlin(codeDir, pkg, "ui/main", "PageViewModel.kt", TabbedPageViewModelKt(pkg))
                writeKotlin(
                    codeDir,
                    pkg,
                    "ui/main",
                    "PlaceholderFragment.kt",
                    TabbedPlaceholderFragmentKt(pkg)
                )
            } else {
                writeJava(
                    codeDir,
                    pkg,
                    "ui/main",
                    "SectionsPagerAdapter.java",
                    TabbedPagerAdapterJava(pkg)
                )
                writeJava(
                    codeDir,
                    pkg,
                    "ui/main",
                    "PageViewModel.java",
                    TabbedPageViewModelJava(pkg)
                )
                writeJava(
                    codeDir,
                    pkg,
                    "ui/main",
                    "PlaceholderFragment.java",
                    TabbedPlaceholderFragmentJava(pkg)
                )
            }
        }
        ProjectTemplate.Kind.NO_ANDROIDX_ACTIVITY -> {
            writeText(File(resDir, "layout/activity_main.xml"), NoAndroidXActivityLayout())
            writeText(
                File(pkgDir, if (useKotlin) "MainActivity.kt" else "MainActivity.java"),
                if (useKotlin) {
                    NoAndroidXActivityKt(
                        applicationId
                    )
                } else {
                    NoAndroidXActivityJava(applicationId)
                }
            )
        }
        ProjectTemplate.Kind.COMPOSE_ACTIVITY -> {
            writeText(File(valuesDir, "themes.xml"), ComposeThemesXml())
            writeKotlin(
                codeDir,
                applicationId,
                "",
                "MainActivity.kt",
                ComposeActivityKt(applicationId)
            )
            writeKotlin(
                codeDir,
                applicationId,
                "ui/theme",
                "Color.kt",
                ComposeColorKt(applicationId)
            )
            writeKotlin(
                codeDir,
                applicationId,
                "ui/theme",
                "Theme.kt",
                ComposeThemeKt(applicationId)
            )
            writeKotlin(codeDir, applicationId, "ui/theme", "Type.kt", ComposeTypeKt(applicationId))
        }
    }
}

fun writeKotlin(codeDir: File, pkg: String, sub: String, name: String, src: String) {
    val dir = File(codeDir, pkg.replace('.', '/') + if (sub.isNotBlank()) "/$sub" else "")
    dir.mkdirs()
    writeText(File(dir, name), src.trim() + "\n")
}

fun writeJava(codeDir: File, pkg: String, sub: String, name: String, src: String) {
    val dir = File(codeDir, pkg.replace('.', '/') + if (sub.isNotBlank()) "/$sub" else "")
    dir.mkdirs()
    writeText(File(dir, name), src.trim() + "\n")
}

fun mergeStringsXml(valuesDir: File, additionalStringsXml: String) {
    val stringsFile = File(valuesDir, "strings.xml")
    if (!stringsFile.exists()) {
        writeText(
            stringsFile,
            """<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<resources>\n</resources>\n"""
        )
    }
    val base = stringsFile.readText()
    val baseNames = Regex("""<string\s+name="([^"]+)"""").findAll(base).map {
        it.groupValues[1]
    }.toSet()
    val additionalNodes = Regex(
        """<string\s+name="([^"]+)"[^>]*>.*?</string>""",
        RegexOption.DOT_MATCHES_ALL
    )
        .findAll(additionalStringsXml).map { it.value.trim() }
        .filter { node ->
            val n = Regex("""<string\s+name="([^"]+)"""").find(node)?.groupValues?.get(1)
            n !=
                null &&
                n !in baseNames
        }
        .toList()
    if (additionalNodes.isEmpty()) return
    val insertion = additionalNodes.joinToString("\n") { "    $it" } + "\n"
    val merged = if (base.contains("</resources>")) {
        base.replace(
            "</resources>",
            insertion + "</resources>"
        )
    } else {
        base + "\n" + insertion
    }
    stringsFile.writeText(merged)
}

fun buildStringsXml(name: String) = """
    <?xml version="1.0" encoding="utf-8"?>
    <resources>
        <string name="app_name">$name</string>
    </resources>
""".trimIndent()

fun writeText(file: File, content: String) {
    file.parentFile?.mkdirs()
    file.writeText(content)
}

fun copyAsset(context: Context, assetPath: String, dst: File) {
    dst.parentFile?.mkdirs()
    context.assets.open(assetPath).use { it.copyTo(dst.outputStream()) }
}

fun copyGradleWrapper(context: Context, projectDir: File) {
    val wrapperDir = File(projectDir, "gradle/wrapper").apply { mkdirs() }
    copyAsset(context, "gradle/wrapper/gradle-wrapper.jar", File(wrapperDir, "gradle-wrapper.jar"))
    copyAsset(
        context,
        "gradle/wrapper/gradle-wrapper.properties",
        File(wrapperDir, "gradle-wrapper.properties")
    )
    val gradlew = File(projectDir, "gradlew")
    copyAsset(context, "gradle/gradlew", gradlew)
    gradlew.setExecutable(true)
    copyAsset(context, "gradle/gradlew.bat", File(projectDir, "gradlew.bat"))
}

fun copyLibsVersionsToml(context: Context, projectDir: File) {
    val toml = File(projectDir, "gradle").apply { mkdirs() }.let { File(it, "libs.versions.toml") }
    runCatching { copyAsset(context, "acs-templates/libs.versions.toml", toml) }
}

fun copyAssetsDir(context: Context, assetRoot: String, outRoot: File) {
    fun copyDir(path: String) {
        val entries = context.assets.list(path)?.toList() ?: return
        for (name in entries) {
            val child = if (path.isBlank()) name else "$path/$name"
            val children = context.assets.list(child)?.toList() ?: emptyList()
            if (children.isNotEmpty()) {
                copyDir(child)
            } else {
                val out = File(outRoot, child.removePrefix("$assetRoot/"))
                out.parentFile?.mkdirs()
                context.assets.open(child).use { it.copyTo(out.outputStream()) }
            }
        }
    }
    copyDir(assetRoot)
}
