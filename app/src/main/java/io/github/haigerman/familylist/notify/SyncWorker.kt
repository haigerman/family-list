package io.github.haigerman.familylist.notify

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.haigerman.familylist.data.Settings
import io.github.haigerman.familylist.data.Store
import java.util.concurrent.TimeUnit

/**
 * Abgleich im Hintergrund: holt etwa alle 15 Minuten den Stand aus dem Space, damit neue Tasks
 * der anderen samt Erinnerung ankommen, auch wenn die App nicht geöffnet ist.
 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        Store.init(applicationContext)
        if (!Settings.config.value.complete) return Result.success()
        return if (Store.sync() || runAttemptCount >= 3) Result.success() else Result.retry()
    }

    companion object {
        private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "sync", ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES).setConstraints(online).build(),
            )
        }

        /** Einmal abgleichen, sobald wieder Netz da ist. */
        fun kick(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                "sync-once", ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<SyncWorker>().setConstraints(online).build(),
            )
        }
    }
}
