package com.kira.stockscope

import android.app.Application
import java.io.File

/**
 * Installs a default uncaught-exception handler that saves the crash's full
 * stack trace to a file before the process dies. MainActivity checks for that
 * file on the next launch and shows it as copyable text, so a crash can be
 * diagnosed from a chat message instead of requiring adb/Android Studio.
 */
class StockScopeApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                File(filesDir, CRASH_LOG_FILE).writeText(throwable.stackTraceToString())
            }
            previousHandler?.uncaughtException(thread, throwable)
        }
    }

    companion object {
        const val CRASH_LOG_FILE = "last_crash.txt"
    }
}
