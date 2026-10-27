package com.phantom.mesh.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.phantom.mesh.data.local.backup.LocalChatBackupManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class LocalBackupWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val localChatBackupManager: LocalChatBackupManager
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val result = localChatBackupManager.performBackup()
            if (result.success) {
                Result.success()
            } else {
                Result.retry()
            }
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
