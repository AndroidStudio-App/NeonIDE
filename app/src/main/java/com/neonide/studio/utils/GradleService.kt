package com.neonide.studio.utils

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.neonide.studio.R
import com.neonide.studio.editor.bottomsheet.buildoutput.BuildOutputBuffer
import com.neonide.studio.utils.GradleProjectActions.getGradleEnvironment
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
        const val EXTRA_VARIANT = "extra_variant"
        const val EXTRA_EXECUTABLE = "extra_executable"

        fun startBuild(
            context: Context,
            projectDir: File,
            args: List<String>,
            actionLabel: String,
            installOnSuccess: Boolean,
            variant: String = "debug",
            executable: String? = null
        ) {
            val intent = Intent(context, GradleService::class.java).apply {
                action = ACTION_START_BUILD
                putExtra(EXTRA_PROJECT_DIR, projectDir.absolutePath)
                putExtra(EXTRA_ARGS, args.toTypedArray())
                putExtra(EXTRA_ACTION_LABEL, actionLabel)
                putExtra(EXTRA_INSTALL_ON_SUCCESS, installOnSuccess)
                putExtra(EXTRA_VARIANT, variant)
                putExtra(EXTRA_EXECUTABLE, executable)
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
                val variant = intent.getStringExtra(EXTRA_VARIANT) ?: "debug"
                val executable = intent.getStringExtra(EXTRA_EXECUTABLE)

                if (projectDir != null && args != null) {
                    GradleBuildStatus.isRunning = true
                    startForeground(
                        NOTIFICATION_ID,
                        createNotification(
                            getString(R.string.build_project),
                            "$variant ${getString(R.string.build_running)}"
                        )
                    )
                    executeBuild(
                        projectDir,
                        args,
                        actionLabel,
                        installOnSuccess,
                        variant,
                        executable
                    )
                }
            }

            ACTION_STOP_BUILD -> {
                clearNotification()
            }
        }
        return START_NOT_STICKY
    }

    private fun executeBuild(
        projectDir: File,
        args: List<String>,
        actionLabel: String,
        installOnSuccess: Boolean,
        variant: String,
        executable: String? = null
    ) {
        currentJob?.cancel()
        currentJob = serviceScope.launch {
            try {
                val baseEnv = getGradleEnvironment(this@GradleService)
                BuildOutputBuffer.appendLine("$actionLabel")

                val handle = withContext(Dispatchers.IO) {
                    GradleRunner.start(
                        projectDir = projectDir,
                        args = args,
                        envOverrides = baseEnv,
                        onOutputLine = { line -> BuildOutputBuffer.appendLine(line) },
                        executable = executable
                    )
                }
                currentHandle = handle

                val result = withContext(Dispatchers.IO) { handle.waitFor() }

                if (result.isSuccessful && installOnSuccess) {
                    val apkDirs = listOfNotNull(
                        File(projectDir, "app/build/outputs/apk/$variant"),
                        File(projectDir, "build/app/outputs/flutter-apk")
                    )
                    val apk = apkDirs.firstNotNullOfOrNull { dir ->
                        dir.walkTopDown().firstOrNull { it.isFile && it.extension == "apk" }
                    }
                    if (apk != null) {
                        ApkInstallUtils.installApk(this@GradleService, apk)
                    }
                }

                val content = if (result.isSuccessful) {
                    "$variant ${getString(R.string.build_success)}"
                } else {
                    "$variant ${getString(R.string.build_failed)}"
                }
                startForeground(
                    NOTIFICATION_ID,
                    createNotification(getString(R.string.build_project), content)
                )
            } finally {
                GradleBuildStatus.isRunning = false
            }
        }
    }

    private fun clearNotification() {
        currentHandle?.cancel()
        currentJob?.cancel()
        GradleBuildStatus.isRunning = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(Service.STOP_FOREGROUND_REMOVE)
        } else {
            stopForeground(STOP_FOREGROUND_REMOVE)
        }
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NOTIFICATION_ID)
        stopSelf()
    }

    private fun createNotification(title: String, content: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_service_notification)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()

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
        clearNotification()
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}
