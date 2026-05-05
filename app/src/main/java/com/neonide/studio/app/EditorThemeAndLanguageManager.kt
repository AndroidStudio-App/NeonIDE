package com.neonide.studio.app

import android.content.res.Configuration
import io.github.rosemoe.sora.langs.monarch.registry.MonarchGrammarRegistry
import io.github.rosemoe.sora.langs.monarch.registry.ThemeRegistry as MonarchThemeRegistry
import io.github.rosemoe.sora.langs.monarch.registry.dsl.monarchLanguages
import io.github.rosemoe.sora.langs.monarch.registry.model.ThemeSource
import io.github.rosemoe.sora.langs.monarch.registry.model.ThemeModel as MonarchThemeModel
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import io.github.rosemoe.sora.widget.schemes.SchemeDarcula
import org.eclipse.tm4e.core.registry.IThemeSource

import io.github.dingyi222666.monarch.languages.JavaLanguage
import io.github.dingyi222666.monarch.languages.KotlinLanguage
import io.github.dingyi222666.monarch.languages.PythonLanguage
import io.github.dingyi222666.monarch.languages.TypescriptLanguage

class EditorThemeAndLanguageManager(private val editor: CodeEditor) {

    fun setupTextmate() {
        GrammarRegistry.getInstance().loadGrammars("textmate/languages.json")
        val themes = arrayOf("darcula", "ayu-dark", "quietlight", "solarized_dark")
        themes.forEach { name ->
            val path = "textmate/$name.json"
            val inputStream = FileProviderRegistry.getInstance().tryGetInputStream(path)
            ThemeRegistry.getInstance().loadTheme(
                ThemeModel(IThemeSource.fromInputStream(inputStream, path, null), name).apply {
                    if (name != "quietlight") isDark = true
                }
            )
        }
        ThemeRegistry.getInstance().setTheme("quietlight")
    }

    fun setupMonarch() {
        val themes = arrayOf("darcula", "ayu-dark", "quietlight", "solarized_dark")
        themes.forEach { name ->
            MonarchThemeRegistry.loadTheme(
                MonarchThemeModel(ThemeSource("textmate/$name.json", name)).apply {
                    if (name != "quietlight") isDark = true
                }, false
            )
        }
        MonarchThemeRegistry.setTheme("quietlight")
        // Try calling loadGrammars on MonarchGrammarRegistry (likely a Kotlin object)
        MonarchGrammarRegistry.INSTANCE.loadGrammars(monarchLanguages {
            language("java") { 
                monarchLanguage = JavaLanguage
                defaultScopeName()
                languageConfiguration = "textmate/java/language-configuration.json" 
            }
            language("kotlin") { 
                monarchLanguage = KotlinLanguage
                defaultScopeName()
                languageConfiguration = "textmate/kotlin/language-configuration.json" 
            }
            language("python") { 
                monarchLanguage = PythonLanguage
                defaultScopeName()
                languageConfiguration = "textmate/python/language-configuration.json" 
            }
            language("typescript") { 
                monarchLanguage = TypescriptLanguage
                defaultScopeName() 
            }
        })
    }

    fun switchTheme(night: Boolean) {
        val themeName = if (night) "darcula" else "quietlight"
        ThemeRegistry.getInstance().setTheme(themeName)
        MonarchThemeRegistry.setTheme(themeName)
        
        if (editor.colorScheme !is TextMateColorScheme) {
            editor.colorScheme = if (night) SchemeDarcula() else EditorColorScheme()
        }
        editor.invalidate()
    }
}
