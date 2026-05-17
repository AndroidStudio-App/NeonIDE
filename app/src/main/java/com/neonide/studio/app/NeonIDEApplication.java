package com.neonide.studio.app;

import android.content.Context;
import com.termux.app.TermuxApplication;
import com.neonide.studio.logger.IDEFileLogger;
import com.termux.shared.logger.Logger;

public class NeonIDEApplication extends TermuxApplication {

    @Override
    public void onCreate() {
        super.onCreate();
        
        Context context = getApplicationContext();
        
        // Init sora-editor file providers
        io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry.getInstance().addFileProvider(new io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver(context.getAssets()));

        // Register IDEFileLogger to capture all logs sent via Logger class
        Logger.setExternalLogger((priority, tag, message) -> {
            IDEFileLogger.log(context, tag + ": " + message);
        });
    }
}
