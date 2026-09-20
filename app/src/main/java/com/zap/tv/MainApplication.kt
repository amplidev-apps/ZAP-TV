package com.zap.tv

import android.app.Application
import android.content.Intent
import dagger.hilt.android.HiltAndroidApp
import java.io.File

@HiltAndroidApp
class MainApplication : Application() {
    override fun attachBaseContext(base: android.content.Context?) {
        super.attachBaseContext(base)
        Thread.setDefaultUncaughtExceptionHandler { _, e ->
            try {
                val dir = base?.filesDir ?: filesDir
                val log = File(dir, "crash.log")
                log.writeText("Crash ${e::class.java.name}: ${e.message}\n${e.stackTrace.take(30).joinToString("\n")}\nCause: ${e.cause}")
            } catch (_: Exception) {}
        }
    }
    override fun onCreate() {
        super.onCreate()
        Thread.setDefaultUncaughtExceptionHandler { _, e ->
            try {
                val log = File(filesDir, "crash.log")
                log.writeText("Crash ${e::class.java.name}: ${e.message}\n${e.stackTrace.take(30).joinToString("\n")}\nCause: ${e.cause}")
            } catch (_: Exception) {}
            try {
                val intent = Intent(this, CrashActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    putExtra("error", "${e::class.java.simpleName}: ${e.message}\n${e.stackTrace.take(12).joinToString("\n")}")
                }
                startActivity(intent)
            } catch (_: Exception) {}
            try { Thread.sleep(800) } catch (_: Exception) {}
        }
    }
}
