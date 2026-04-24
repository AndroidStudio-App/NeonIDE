package com.neonide.studio.app;

import android.app.Application;
import android.content.Context;

import androidx.annotation.Nullable;

import com.neonide.studio.shared.errors.Error;
import com.neonide.studio.shared.logger.Logger;
import com.neonide.studio.shared.termux.TermuxBootstrap;
import com.neonide.studio.shared.termux.TermuxConstants;
import com.neonide.studio.shared.termux.crash.TermuxCrashUtils;
import com.neonide.studio.shared.termux.file.TermuxFileUtils;
import com.neonide.studio.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.neonide.studio.shared.termux.settings.properties.TermuxAppSharedProperties;
import com.neonide.studio.shared.termux.shell.command.environment.TermuxShellEnvironment;
import com.neonide.studio.shared.termux.shell.am.TermuxAmSocketServer;
import com.neonide.studio.shared.termux.shell.TermuxShellManager;

import java.io.File;

public class TermuxApplication extends Application {

    private static final String LOG_TAG = "NeonIDEApplication";

    private static volatile Context sApplicationContext;

    /**
     * Returns the Application context if the Application has been created, otherwise {@code null}.
     * This is intentionally "unsafe" and should only be used for best-effort utilities like file logging.
     */
    @Nullable
    public static Context getApplicationContextUnsafe() {
        return sApplicationContext;
    }

    public void onCreate() {
        super.onCreate();

        Context context = getApplicationContext();
        sApplicationContext = context;

        // Init sora-editor file providers
        io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry.getInstance().addFileProvider(new io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver(context.getAssets()));
        io.github.rosemoe.sora.langs.monarch.registry.FileProviderRegistry.INSTANCE.addProvider(new io.github.rosemoe.sora.langs.monarch.registry.provider.AssetsFileResolver(context.getAssets()));

        // Set crash handler for the app
        TermuxCrashUtils.setDefaultCrashHandler(this);

        // Set log config for the app
        setLogConfig(context);

        Logger.logInfo(LOG_TAG,"Starting Application");

        // Set TermuxBootstrap.TERMUX_APP_PACKAGE_MANAGER and TermuxBootstrap.TERMUX_APP_PACKAGE_VARIANT
        //TermuxBootstrap.setTermuxPackageManagerAndVariant(BuildConfig.TERMUX_PACKAGE_VARIANT);

        // Init app wide SharedProperties loaded from termux.properties
        TermuxAppSharedProperties properties = TermuxAppSharedProperties.init(context);

        // Init app wide shell manager
        TermuxShellManager shellManager = TermuxShellManager.init(context);

        // Set NightMode.APP_NIGHT_MODE

        // Check and create termux files directory. If failed to access it like in case of secondary
        // user or external sd card installation, then don't run files directory related code
        Error error = TermuxFileUtils.isTermuxFilesDirectoryAccessible(this, true, true);
        boolean isTermuxFilesDirectoryAccessible = error == null;

        // Init TermuxShellEnvironment constants and caches after everything has been setup including termux-am-socket server
        TermuxShellEnvironment.init(this);

        if (isTermuxFilesDirectoryAccessible) {
            TermuxShellEnvironment.writeEnvironmentToFile(this);
        }
    }

    public static void setLogConfig(Context context) {
        Logger.setDefaultLogTag(TermuxConstants.TERMUX_APP_NAME);

        // Load the log level from shared preferences and set it to the {@link Logger.CURRENT_LOG_LEVEL}
        TermuxAppSharedPreferences preferences = TermuxAppSharedPreferences.build(context);
        if (preferences == null) return;
        preferences.setLogLevel(null, preferences.getLogLevel());
    }
}
