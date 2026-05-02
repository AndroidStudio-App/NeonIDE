pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "NeonIDE Studio"

include(":app")
include(":terminal-emulator")
include(":terminal-view")
include(":termux-shared")
include(":termux-app")

project(":terminal-emulator").projectDir = file("termux-app/terminal-emulator")
project(":terminal-view").projectDir = file("termux-app/terminal-view")
project(":termux-shared").projectDir = file("termux-app/termux-shared")
project(":termux-app").projectDir = file("termux-app/app")