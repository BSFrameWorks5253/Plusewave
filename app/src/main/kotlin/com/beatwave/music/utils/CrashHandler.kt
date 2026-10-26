/**
 * Pulse Wave Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.beatwave.music.utils

import android.content.Context
import android.content.Intent
import android.os.Build
import com.beatwave.music.BuildConfig
import com.beatwave.music.ui.screens.CrashActivity
import timber.log.Timber
import java.io.PrintWriter
import java.io.StringWriter
import kotlin.system.exitProcess

class CrashHandler private constructor(
    private val applicationContext: Context
) : Thread.UncaughtExceptionHandler {

    private val defaultHandler: Thread.UncaughtExceptionHandler? =
        Thread.getDefaultUncaughtExceptionHandler()

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        // media3's MediaNotificationManager calls the platform's (final,
        // un-overridable) Service.startForeground() directly and
        // asynchronously — a posted Handler callback, not on a frame app
        // code controls — so this global handler is the only remaining
        // place to catch a promotion the OS refused (app backgrounded past
        // its foreground-service grace window; confirmed via a real device
        // crash log, Samsung/Android 16, app 1.5.1). The OS already refused
        // the promotion; there's nothing to recover except not killing the
        // process over it — the notification just stays unpromoted until
        // playback next starts in the foreground normally.
        if (Build.VERSION.SDK_INT >= 31 &&
            throwable is android.app.ForegroundServiceStartNotAllowedException
        ) {
            Timber.tag("CrashHandler").w(throwable, "Foreground service start refused by platform — ignoring, not crashing")
            return
        }
        try {
            val crashLog = buildCrashLog(throwable)
            Timber.e(throwable, "App crashed")

            // Persist to disk so it is never lost even if Intent extras fail
            runCatching {
                val file = java.io.File(applicationContext.filesDir, "latest_crash.txt")
                file.writeText(crashLog)
            }

            // Launch crash activity with a size-bounded extra to prevent TransactionTooLargeException
            val safeExtra = if (crashLog.length > 50000) crashLog.take(50000) + "\n...[truncated]" else crashLog
            val intent = Intent(applicationContext, CrashActivity::class.java).apply {
                putExtra(EXTRA_CRASH_LOG, safeExtra)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            applicationContext.startActivity(intent)

            // Allow the ActivityManager binder call to dispatch before terminating the process
            try {
                Thread.sleep(300)
            } catch (e: InterruptedException) {}

            // Kill the current process
            android.os.Process.killProcess(android.os.Process.myPid())
            exitProcess(10)
        } catch (e: Throwable) {
            // If we fail to handle the crash, fall back to default handler
            Timber.e(e, "Error handling crash")
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun buildCrashLog(throwable: Throwable): String {
        val stackTrace = StringWriter().apply {
            throwable.printStackTrace(PrintWriter(this))
        }.toString()

        var rootCause: Throwable = throwable
        while (rootCause.cause != null && rootCause.cause !== rootCause) {
            rootCause = rootCause.cause!!
        }

        return buildString {
            appendLine("Pulse Wave Crash Report")
            appendLine("=".repeat(50))
            appendLine()
            appendLine("Manufacturer: ${Build.MANUFACTURER}")
            appendLine("Device: ${Build.MODEL}")
            appendLine("Android version: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("App version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine()
            if (rootCause !== throwable) {
                appendLine("Root Cause: ${rootCause.javaClass.name}: ${rootCause.message}")
                val rootTrace = StringWriter().apply {
                    rootCause.printStackTrace(PrintWriter(this))
                }.toString()
                appendLine("Root Cause Stacktrace:")
                appendLine(rootTrace.lines().take(25).joinToString("\n"))
                appendLine()
            }
            appendLine("=".repeat(50))
            appendLine("Stacktrace:")
            appendLine("=".repeat(50))
            appendLine()
            append(stackTrace)
        }
    }

    companion object {
        const val EXTRA_CRASH_LOG = "crash_log"

        fun install(context: Context) {
            val handler = CrashHandler(context.applicationContext)
            Thread.setDefaultUncaughtExceptionHandler(handler)
            Timber.d("CrashHandler installed")
        }
    }
}
