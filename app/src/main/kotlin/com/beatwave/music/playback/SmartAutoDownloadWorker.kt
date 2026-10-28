/**
 * Pulse Wave Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.beatwave.music.playback

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.beatwave.music.db.MusicDatabase
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.TimeUnit

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AutoDownloadWorkerEntryPoint {
    fun database(): MusicDatabase
    fun downloadUtil(): DownloadUtil
}

/**
 * Smart Auto-Download Worker:
 * Automatically downloads and caches the user's top 20 most played songs
 * in the background when connected to Wi-Fi (unmetered network) and charging/battery not low.
 */
@UnstableApi
class SmartAutoDownloadWorker(
    private val context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Timber.d("SmartAutoDownloadWorker: Starting top 20 auto-cache run")
        try {
            val entryPoint = EntryPointAccessors.fromApplication(
                context.applicationContext,
                AutoDownloadWorkerEntryPoint::class.java
            )
            val database = entryPoint.database()
            val downloadUtil = entryPoint.downloadUtil()

            // 1. Fetch top 20 most played songs (all time)
            val topSongs = database.mostPlayedSongs(0L, 20).first()
            if (topSongs.isEmpty()) {
                Timber.d("SmartAutoDownloadWorker: No played songs found in database")
                return@withContext Result.success()
            }

            // 2. Identify which songs need downloading
            val currentDownloads = downloadUtil.downloads.value
            val targets = topSongs.map { DownloadTarget(it.id, it.song.title) }
            val missingTargets = targets.filter { shouldQueueDownload(currentDownloads[it.id]?.state) }

            if (missingTargets.isEmpty()) {
                Timber.d("SmartAutoDownloadWorker: All top 20 songs are already cached offline")
                return@withContext Result.success()
            }

            Timber.i("SmartAutoDownloadWorker: Queuing ${missingTargets.size} songs from top 20 for auto-download")
            downloadSongs(context, missingTargets, currentDownloads)

            Result.success()
        } catch (e: Exception) {
            Timber.e(e, "SmartAutoDownloadWorker failed during execution")
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "smart_auto_download_top_songs_worker"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.UNMETERED) // Wi-Fi only
                .setRequiresBatteryNotLow(true)
                .build()

            val request = PeriodicWorkRequestBuilder<SmartAutoDownloadWorker>(12, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
            Timber.d("SmartAutoDownloadWorker: Scheduled periodic Wi-Fi auto-cache every 12 hours")
        }
    }
}
