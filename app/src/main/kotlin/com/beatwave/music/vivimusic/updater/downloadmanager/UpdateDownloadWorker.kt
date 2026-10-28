package com.beatwave.music.vivimusic.updater.downloadmanager

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.beatwave.music.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

class UpdateDownloadWorker(private val context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val apkUrl = inputData.getString("apk_url") ?: return@withContext Result.failure()
        val version = inputData.getString("version") ?: "unknown"
        val fileSize = inputData.getString("file_size") ?: ""

        DownloadNotificationManager.showDownloadStarting(version, fileSize)

        try {
            val client = OkHttpClient.Builder()
                .followRedirects(true)
                .followSslRedirects(true)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build()

            val request = Request.Builder()
                .url(apkUrl)
                .header("User-Agent", "PulseWave/$version (Android)")
                .header("Accept", "application/octet-stream, application/vnd.android.package-archive, */*")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                DownloadNotificationManager.showDownloadFailed(
                    version,
                    context.getString(R.string.server_error, response.code)
                )
                return@withContext Result.failure()
            }

            val body = response.body ?: run {
                DownloadNotificationManager.showDownloadFailed(version, "Empty response from server")
                return@withContext Result.failure()
            }

            val fileLength = body.contentLength()
            val inputStream = body.byteStream()

            // Save in internal cacheDir which is completely accessible to FileProvider without Scoped Storage blocks
            val downloadDir = File(context.cacheDir, "pulsewave_updates")
            if (!downloadDir.exists()) {
                downloadDir.mkdirs()
            }

            val isZip = apkUrl.contains("nightly.link") || apkUrl.endsWith(".zip")
            val downloadFile = if (isZip) File(downloadDir, "pulsewave_temp.zip") else File(downloadDir, "PulseWave-update.apk")
            if (downloadFile.exists()) {
                downloadFile.delete()
            }

            val outputStream = FileOutputStream(downloadFile)
            val buffer = ByteArray(16384)
            var bytesRead: Int
            var totalBytesRead: Long = 0

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                if (isStopped) {
                    outputStream.close()
                    inputStream.close()
                    response.close()
                    if (downloadFile.exists()) {
                        downloadFile.delete()
                    }
                    return@withContext Result.retry()
                }

                outputStream.write(buffer, 0, bytesRead)
                totalBytesRead += bytesRead

                if (fileLength > 0) {
                    val progress = (totalBytesRead.toFloat() / fileLength.toFloat() * 100).toInt()
                    DownloadNotificationManager.updateDownloadProgress(progress, version)
                    setProgress(workDataOf("progress" to progress.toFloat() / 100f))
                }
            }

            outputStream.flush()
            outputStream.close()
            inputStream.close()
            response.close()

            val finalFile = if (isZip) {
                val targetApkFile = File(downloadDir, "PulseWave-update.apk")
                if (targetApkFile.exists()) targetApkFile.delete()
                var extracted = false
                try {
                    ZipInputStream(downloadFile.inputStream()).use { zis ->
                        var entry = zis.nextEntry
                        while (entry != null) {
                            if (!entry.isDirectory && entry.name.endsWith(".apk")) {
                                FileOutputStream(targetApkFile).use { fos ->
                                    zis.copyTo(fos)
                                }
                                extracted = true
                                break
                            }
                            entry = zis.nextEntry
                        }
                    }
                } catch (e: Exception) {
                    if (downloadFile.exists()) downloadFile.delete()
                    DownloadNotificationManager.showDownloadFailed(
                        version,
                        e.message ?: "Failed to extract zip file"
                    )
                    return@withContext Result.failure()
                } finally {
                    if (downloadFile.exists()) {
                        downloadFile.delete()
                    }
                }
                if (!extracted) {
                    DownloadNotificationManager.showDownloadFailed(
                        version,
                        "Could not find APK in zip"
                    )
                    return@withContext Result.failure()
                }
                targetApkFile
            } else {
                downloadFile
            }

            // Verify downloaded APK integrity
            val pkgInfo = context.packageManager.getPackageArchiveInfo(finalFile.absolutePath, 0)
            if (pkgInfo == null) {
                if (finalFile.exists()) finalFile.delete()
                DownloadNotificationManager.showDownloadFailed(
                    version,
                    "Downloaded APK was corrupted or incomplete"
                )
                return@withContext Result.failure()
            }

            DownloadNotificationManager.showDownloadComplete(version, finalFile.absolutePath)
            Result.success(workDataOf("file_path" to finalFile.absolutePath))
        } catch (e: Exception) {
            DownloadNotificationManager.showDownloadFailed(
                version,
                e.message ?: context.getString(R.string.download_failed)
            )
            Result.failure()
        }
    }
}
