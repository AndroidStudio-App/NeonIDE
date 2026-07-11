package com.neonide.studio.app

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.svg.SvgDecoder
import com.neonide.studio.logger.IDEFileLogger
import com.termux.app.TermuxApplication
import com.termux.shared.logger.Logger
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry
import io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver

class NeonIDEApplication :
    TermuxApplication(),
    SingletonImageLoader.Factory {
    override fun onCreate() {
        super.onCreate()

        val context = applicationContext

        FileProviderRegistry.getInstance()
            .addFileProvider(AssetsFileResolver(context.assets))

        Logger.setExternalLogger { priority, tag, message ->
            IDEFileLogger.log(context, "$tag: $message")
        }
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory())
                add(SvgDecoder.Factory())
            }
            .build()
}
