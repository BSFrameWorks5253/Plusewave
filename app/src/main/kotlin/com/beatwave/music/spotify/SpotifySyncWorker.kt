/**
 * Pulse Wave Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.beatwave.music.spotify

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber
import java.util.concurrent.TimeUnit

@HiltWorker
class SpotifySyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val spotifySyncManager: SpotifyPlaylistSyncManager,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Timber.d("SpotifySyncWorker: Starting periodic background sync")
        return try {
            val count = spotifySyncManager.syncAllAutoSyncPlaylists()
            Timber.i("SpotifySyncWorker: Completed successfully with $count new track(s)")
            Result.success()
        } catch (e: Exception) {
            Timber.e(e, "SpotifySyncWorker: Background sync failed")
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "spotify_periodic_sync_worker"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<SpotifySyncWorker>(6, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
            Timber.d("SpotifySyncWorker: Scheduled periodic work every 6 hours")
        }
    }
}
