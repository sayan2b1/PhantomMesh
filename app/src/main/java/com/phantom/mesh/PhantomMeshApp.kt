package com.phantom.mesh

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import java.io.File
import javax.inject.Inject

/**
 * PhantomMesh Application class — Hilt entry point.
 */
@HiltAndroidApp
class PhantomMeshApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()

        // Global crash guard to capture and prevent fatal crashes from killing UI without trace
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("PhantomMeshApp", "FATAL CRASH on thread ${thread.name}: ${throwable.message}", throwable)
            try {
                val file = File(filesDir, "crash_log.txt")
                file.writeText("Crash on ${thread.name}: ${throwable.message}\n${throwable.stackTraceToString()}")
            } catch (ignored: Exception) {}
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}
