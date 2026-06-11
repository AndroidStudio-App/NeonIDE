package com.neonide.studio.app

import com.neonide.studio.logger.IDEFileLogger
import com.termux.app.TermuxApplication
import com.termux.shared.logger.Logger
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry
import io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver

class NeonIDEApplication : TermuxApplication() {
    override fun onCreate() {
        super.onCreate()

        val context = applicationContext

        FileProviderRegistry.getInstance()
            .addFileProvider(AssetsFileResolver(context.assets))

        Logger.setExternalLogger { priority, tag, message ->
            IDEFileLogger.log(context, "$tag: $message")
        }
    }
}
