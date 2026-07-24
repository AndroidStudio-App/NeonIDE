# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in android-sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

-keep class org.joni.** { *; }
-dontwarn org.joni.**

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

# Keep Okio internal classes that JGit accesses via reflection
# JGit uses okio.-SegmentedByteString and other internal Okio classes via reflection
-keep class okio.** { *; }
-keep class okio.internal.** { *; }
-keep class okio.-* { *; }
-keepattributes *Annotation*,Signature,InnerClasses

# Prevent R8 from optimizing/minifying Okio classes - needed for JGit compatibility with Okio 3.x
-optimizations !class/merging/*,!class/unboxing/*,!method/propagation/*,!method/inlining/*
-keep class okio.** { *; }
-keepclassmembers class okio.** { *; }

# Specifically keep the internal SegmentedByteString class that JGit accesses via reflection
-keep class okio.-SegmentedByteString { *; }
-keepclassmembers class okio.-SegmentedByteString { <init>(...); }

# Keep Kotlin stdlib classes that JGit might access via reflection
-keep class kotlin.ranges.** { *; }
-keep class kotlin.collections.** { *; }
-keep class kotlin.text.** { *; }
-keep class kotlin.jvm.internal.** { *; }
-keep class kotlin.reflect.** { *; }
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Prevent R8 from removing/optimizing Kotlin stdlib classes
-keep class kotlin.** { *; }
-keepclassmembers class kotlin.** { *; }

# Keep ALL JGit classes - JGit uses extensive reflection internally
-keep class org.eclipse.jgit.** { *; }
-keepclassmembers class org.eclipse.jgit.** { *; }
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Keep jcodings classes that JGit uses via reflection
-keep class org.jcodings.** { *; }
-keep class org.jruby.** { *; }

# Keep jcodings EncodingList and related classes
-keep class org.jcodings.EncodingList { *; }
-keep class org.jcodings.Encoding { *; }
-keep class org.jcodings.specific.** { *; }

# Keep resource bundles
-keep class * implements java.util.ResourceBundle { *; }

# --- Sora Editor TextMate Theme / Color Scheme ---
-keep class io.github.rosemoe.sora.langs.textmate.TextMateColorScheme { *; }
-keep class io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry { *; }
-keep class io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel { *; }
-keep class io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry { *; }
-keep class io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry { *; }
-keep class io.github.rosemoe.sora.langs.textmate.** { *; }

# Eclipse TM4E core — theme source parsing
-keep class org.eclipse.tm4e.** { *; }
-dontwarn org.eclipse.tm4e.**

# Keep all TextMate registry internals
-keep class io.github.rosemoe.sora.langs.textmate.registry.** { *; }
