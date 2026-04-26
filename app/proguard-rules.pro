# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in android-sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html
-keep class org.eclipse.tm4e.languageconfiguration.internal.model.** { *; }
-keepattributes Signature
-keepattributes *Annotation*

-dontobfuscate
#-renamesourcefileattribute SourceFile
#-keepattributes SourceFile,LineNumberTable

# Temp fix for androidx.window:window:1.0.0-alpha09 imported by termux-shared
# https://issuetracker.google.com/issues/189001730
# https://android-review.googlesource.com/c/platform/frameworks/support/+/1757630
-keep class androidx.window.** { *; }

# --- LSP integration ---
# SoraEditorLspController is loaded via reflection (EditorLspControllerFactory.IMPL_CLASS).
# R8 may strip it in release builds unless we keep it explicitly.
-keep class com.neonide.studio.app.lsp.impl.SoraEditorLspController { <init>(android.content.Context); *; }

# Keep LSP server bridge services (started by Intent and referenced by name in manifest).
-keep class com.neonide.studio.app.lsp.server.** { *; }

# --- JGit & SLF4J fixes for Android ---
# JGit uses GSS-API (Kerberos) which is not available on Android.
-dontwarn org.ietf.jgss.**

# JGit also references these which are missing on Android or in the compilation classpath
-dontwarn java.lang.management.**
-dontwarn javax.management.**
-dontwarn javax.naming.**
-dontwarn java.lang.ProcessHandle

# SLF4J might warn about missing StaticLoggerBinder if no implementation is provided.
-dontwarn org.slf4j.impl.StaticLoggerBinder

# If R8 still complains about missing classes from these packages, we can ignore them all
-dontwarn org.eclipse.jgit.transport.HttpAuthMethod$Negotiate
-dontwarn org.eclipse.jgit.util.GSSManagerFactory$DefaultGSSManagerFactory
-dontwarn org.eclipse.jgit.util.Monitoring
