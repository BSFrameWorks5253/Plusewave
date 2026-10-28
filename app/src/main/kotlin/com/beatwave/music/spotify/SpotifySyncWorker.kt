/**
 * Pulse Wave Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.beatwave.music.spotify

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import timber.log.Timber
import java.util.concurrent.TimeUnit

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SpotifySyncWorkerEntryPoint {
    fun spotifySyncManager(): SpotifyPlaylistSyncManager
}

class SpotifySyncWorker(
    private val context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Timber.d("SpotifySyncWorker: Starting periodic background sync")
        return try {
            val entryPoint = EntryPointAccessors.fromApplication(
                context.applicationContext,
                SpotifySyncWorkerEntryPoint::class.java
            )
            val spotifySyncManager = entryPoint.spotifySyncManager()
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
