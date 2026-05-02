package com.neonide.studio.app;

import android.content.Context;
import com.termux.app.TermuxApplication;

public class NeonIDEApplication extends TermuxApplication {

    @Override
    public void onCreate() {
        super.onCreate();
        
        Context context = getApplicationContext();
        
        // Init sora-editor file providers
        io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry.getInstance().addFileProvider(new io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver(context.getAssets()));
        io.github.rosemoe.sora.langs.monarch.registry.FileProviderRegistry.INSTANCE.addProvider(new io.github.rosemoe.sora.langs.monarch.registry.provider.AssetsFileResolver(context.getAssets()));
    }
}
