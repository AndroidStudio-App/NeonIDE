package com.neonide.studio.app.gradle

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.neonide.studio.R
import com.neonide.studio.app.buildoutput.BuildOutputBuffer
import kotlinx.coroutines.*
import java.io.File

/**
 * A foreground service that executes Gradle builds.
 * This ensures that builds (and network downloads) continue even when the app is in the background.
 */
class GradleService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var currentJob: Job? = null
    private var currentHandle: GradleRunner.Handle? = null

    companion object {
        private const val NOTIFICATION_ID = 2001
        private const val CHANNEL_ID = "gradle_build_channel"
        
        const val ACTION_START_BUILD = "com.neonide.studio.ACTION_START_BUILD"
        const val ACTION_STOP_BUILD = "com.neonide.studio.ACTION_STOP_BUILD"
        
        const val EXTRA_PROJECT_DIR = "extra_project_dir"
        const val EXTRA_ARGS = "extra_args"
        const val EXTRA_ACTION_LABEL = "extra_action_label"
        const val EXTRA_INSTALL_ON_SUCCESS = "extra_install_on_success"
        const val EXTRA_LOG_FILE_PATH = "extra_log_file_path"

        fun startBuild(
            context: Context,
            projectDir: File,
            args: List<String>,
            actionLabel: String,
            installOnSuccess: Boolean,
            logFilePath: String? = null
        ) {
            val intent = Intent(context, GradleService::class.java).apply {
                action = ACTION_START_BUILD
                putExtra(EXTRA_PROJECT_DIR, projectDir.absolutePath)
                putExtra(EXTRA_ARGS, args.toTypedArray())
                putExtra(EXTRA_ACTION_LABEL, actionLabel)
                putExtra(EXTRA_INSTALL_ON_SUCCESS, installOnSuccess)
                putExtra(EXTRA_LOG_FILE_PATH, logFilePath)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopBuild(context: Context) {
            val intent = Intent(context, GradleService::class.java).apply {
                action = ACTION_STOP_BUILD
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_BUILD -> {
                val projectDir = intent.getStringExtra(EXTRA_PROJECT_DIR)?.let { File(it) }
                val args = intent.getStringArrayExtra(EXTRA_ARGS)?.toList()
                val actionLabel = intent.getStringExtra(EXTRA_ACTION_LABEL) ?: "Build"
                val installOnSuccess = intent.getBooleanExtra(EXTRA_INSTALL_ON_SUCCESS, false)
                val logFilePath = intent.getStringExtra(EXTRA_LOG_FILE_PATH)

                if (projectDir != null && args != null) {
                    GradleBuildStatus.isRunning = true
                    startForeground(NOTIFICATION_ID, createNotification(actionLabel, "Starting..."))
                    executeBuild(projectDir, args, actionLabel, installOnSuccess, logFilePath)
                } else {
                    stopSelf()
                }
            }
            ACTION_STOP_BUILD -> {
                currentHandle?.cancel()
                currentJob?.cancel()
                GradleBuildStatus.isRunning = false
                stopSelf()
            }
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun executeBuild(
        projectDir: File,
        args: List<String>,
        actionLabel: String,
        installOnSuccess: Boolean,
        logFilePath: String?
    ) {
        currentJob?.cancel()
        currentJob = serviceScope.launch {
            try {
                val logFile = logFilePath?.let { File(it) }
                
                val baseEnv = GradleProjectActions.getGradleEnvironment(this@GradleService)
                val sdk = AndroidSdkUtils.configureForProject(
                    context = this@GradleService,
                    projectDir = projectDir,
                    baseEnv = baseEnv,
                )

                val envOverrides = sdk?.env ?: emptyMap()
                val finalArgs = if (sdk?.aapt2Path != null) {
                    val override = "-Pandroid.aapt2FromMavenOverride=${sdk.aapt2Path}"
                    args.toMutableList().apply { add(0, override) }
                } else args

                val handle = withContext(Dispatchers.IO) {
                    GradleRunner.start(
                        context = this@GradleService,
                        projectDir = projectDir,
                        args = finalArgs,
                        envOverrides = envOverrides,
                    ) { line ->
                        logFile?.appendText(line + "\n")
                        BuildOutputBuffer.appendLine(line)
                    }
                }
                currentHandle = handle

                updateNotification(actionLabel, "Running...")

                val result = withContext(Dispatchers.IO) { handle.waitFor() }

                if (result.isSuccessful && installOnSuccess) {
                    updateNotification(actionLabel, "Build successful, installing...")
                    
                    val debugApk = File(projectDir, "app/build/outputs/apk/debug/app-debug.apk")
                    val apk = if (debugApk.exists()) {
                        debugApk
                    } else {
                        val apkDir = File(projectDir, "app/build/outputs/apk")
                        val apks = apkDir.walkTopDown().filter { it.isFile && it.extension == "apk" }.toList()
                        apks.firstOrNull { it.path.contains("debug", ignoreCase = true) } ?: apks.firstOrNull()
                    }

                    if (apk != null) {
                        ApkInstallUtils.installApk(this@GradleService, apk)
                    }
                }
                
            } catch (t: Throwable) {
                BuildOutputBuffer.appendLine("ERROR: ${t.message}")
            } finally {
                GradleBuildStatus.isRunning = false
                stopSelf()
            }
        }
    }

    private fun createNotification(title: String, content: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_service_notification)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(title: String, content: String) {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, createNotification(title, content))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Gradle Builds",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        currentHandle?.cancel()
        currentJob?.cancel()
        GradleBuildStatus.isRunning = false
        stopSelf()
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}
