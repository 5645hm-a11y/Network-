package com.networkabsorb

import android.app.Application
import android.util.Log
import dagger.hilt.android.HiltAndroidApp
import java.io.File

@HiltAndroidApp
class NetworkAbsorbApp : Application() {

    override fun onCreate() {
        super.onCreate()
        installCrashLogger()
    }

    private fun installCrashLogger() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val logDir = getExternalFilesDir(null) ?: filesDir
                val logFile = File(logDir, "crash.log")
                logFile.writeText(
                    "Thread: ${thread.name}\n" +
                    "Time: ${java.util.Date()}\n\n" +
                    throwable.stackTraceToString()
                )
                Log.e("NetworkAbsorb", "CRASH logged to ${logFile.absolutePath}", throwable)
            } catch (_: Exception) { }
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}
