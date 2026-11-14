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
 * Abgleich im Hintergrund: holt etwa alle [EVERY_MINUTES] Minuten den Stand aus dem Space, damit
 * neue Tasks der anderen samt Erinnerung ankommen, auch wenn die App nicht geöffnet ist.
 *
 * Android erlaubt wiederkehrende Arbeit frühestens alle 15 Minuten. Für den kürzeren Takt stellt
 * sich deshalb jeder Lauf seinen Nachfolger selbst ([LOOP]); der 15-Minuten-Auftrag bleibt als
 * Netz darunter und setzt die Kette wieder in Gang, falls sie einmal abreißt. Ruht das Handy,
 * streckt Android die Abstände trotzdem.
 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        Store.init(applicationContext)
        val loop = LOOP in tags
        // ein Lauf der Kette hängt den nächsten hinter sich; jeder andere startet sie nur, wenn sie fehlt
        next(applicationContext, if (loop) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.KEEP)
        if (!Settings.config.value.complete) return Result.success()
        return if (Store.sync() || loop || runAttemptCount >= 3) Result.success() else Result.retry()
    }

    companion object {
        /** So oft wird im Hintergrund abgeglichen. */
        const val EVERY_MINUTES = 5L
        private const val LOOP = "sync-loop"
        private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "sync", ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES).setConstraints(online).build(),
            )
            next(context, ExistingWorkPolicy.KEEP)
        }

        private fun next(context: Context, policy: ExistingWorkPolicy) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                LOOP, policy,
                OneTimeWorkRequestBuilder<SyncWorker>().setInitialDelay(EVERY_MINUTES, TimeUnit.MINUTES)
                    .setConstraints(online).addTag(LOOP).build(),
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
