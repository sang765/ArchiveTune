/*
 * ArchiveTune Project Original (2026)
 * Kòi Natsuko (github.com/koiverse)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package moe.koiverse.archivetune.utils

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

sealed class DownloadState {
    object Idle : DownloadState()
    data class Downloading(
        val progress: Int,
        val bytesDownloaded: Long,
        val totalBytes: Long,
        val speed: String
    ) : DownloadState()
    object Paused : DownloadState()
    data class Completed(val filePath: String) : DownloadState()
    data class Failed(val error: String) : DownloadState()
    object Cancelled : DownloadState()
}

object UpdateDownloadManager {
    private val _downloadStateFlow = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadStateFlow: StateFlow<DownloadState> = _downloadStateFlow.asStateFlow()

    private var currentDownloadId: Long = -1L
    private var downloadManager: DownloadManager? = null
    private var progressJob: Job? = null
    private var downloadCompleteReceiver: BroadcastReceiver? = null
    private var lastBytesDownloaded = 0L
    private var lastUpdateTime = 0L

    fun startDownload(context: Context, url: String, fileName: String = "ArchiveTune.apk"): Long {
        downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

        // Cancel any existing download
        if (currentDownloadId != -1L) {
            cancelDownload()
        }

        // Generate unique file name if file already exists
        val finalFileName = generateUniqueFileName(fileName)

        val request = DownloadManager.Request(Uri.parse(url)).apply {
            setTitle("ArchiveTune ${moe.koiverse.archivetune.BuildConfig.VERSION_NAME}")
            setDescription("Downloading update...")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, finalFileName)
            setMimeType("application/vnd.android.package-archive")
            setAllowedOverMetered(true)
            setAllowedOverRoaming(true)
        }

        currentDownloadId = downloadManager!!.enqueue(request)
        startProgressMonitoring(context)
        registerDownloadCompleteReceiver(context)

        return currentDownloadId
    }

    private fun generateUniqueFileName(baseName: String): String {
        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        var fileName = baseName
        var counter = 1

        val nameWithoutExt = baseName.substringBeforeLast(".")
        val extension = baseName.substringAfterLast(".")

        while (File(downloadDir, fileName).exists()) {
            fileName = "$nameWithoutExt-$counter.$extension"
            counter++
        }

        return fileName
    }

    fun cancelDownload() {
        progressJob?.cancel()
        progressJob = null

        if (currentDownloadId != -1L && downloadManager != null) {
            downloadManager?.remove(currentDownloadId)
            currentDownloadId = -1L
            _downloadStateFlow.value = DownloadState.Cancelled
        }
    }

    fun queryDownloadProgress(): DownloadState {
        if (currentDownloadId == -1L || downloadManager == null) {
            return DownloadState.Idle
        }

        val query = DownloadManager.Query().setFilterById(currentDownloadId)
        val cursor: Cursor? = downloadManager?.query(query)

        cursor?.use {
            if (it.moveToFirst()) {
                val statusIndex = it.getColumnIndex(DownloadManager.COLUMN_STATUS)
                val bytesDownloadedIndex = it.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                val totalBytesIndex = it.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                val reasonIndex = it.getColumnIndex(DownloadManager.COLUMN_REASON)

                if (statusIndex >= 0 && bytesDownloadedIndex >= 0 && totalBytesIndex >= 0) {
                    val status = it.getInt(statusIndex)
                    val bytesDownloaded = it.getLong(bytesDownloadedIndex)
                    val totalBytes = it.getLong(totalBytesIndex)

                    return when (status) {
                        DownloadManager.STATUS_RUNNING, DownloadManager.STATUS_PENDING -> {
                            val progress = if (totalBytes > 0) {
                                ((bytesDownloaded * 100) / totalBytes).toInt()
                            } else 0

                            val speed = calculateSpeed(bytesDownloaded)

                            DownloadState.Downloading(progress, bytesDownloaded, totalBytes, speed)
                        }
                        DownloadManager.STATUS_SUCCESSFUL -> {
                            val uriIndex = it.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                            val filePath = if (uriIndex >= 0) it.getString(uriIndex) ?: "" else ""
                            DownloadState.Completed(filePath)
                        }
                        DownloadManager.STATUS_PAUSED -> DownloadState.Paused
                        DownloadManager.STATUS_FAILED -> {
                            val reason = if (reasonIndex >= 0) it.getInt(reasonIndex) else -1
                            val errorMessage = getErrorMessage(reason)
                            DownloadState.Failed(errorMessage)
                        }
                        else -> DownloadState.Idle
                    }
                }
            }
        }

        return DownloadState.Idle
    }

    private fun calculateSpeed(currentBytes: Long): String {
        val currentTime = System.currentTimeMillis()
        
        if (lastUpdateTime == 0L) {
            lastUpdateTime = currentTime
            lastBytesDownloaded = currentBytes
            return "0 KB/s"
        }

        val timeDiff = (currentTime - lastUpdateTime) / 1000.0 // seconds
        if (timeDiff < 0.5) return "..." // Wait for more data

        val bytesDiff = currentBytes - lastBytesDownloaded
        val bytesPerSecond = (bytesDiff / timeDiff).toLong()

        lastUpdateTime = currentTime
        lastBytesDownloaded = currentBytes

        return when {
            bytesPerSecond >= 1024 * 1024 -> "%.1f MB/s".format(bytesPerSecond / (1024.0 * 1024.0))
            bytesPerSecond >= 1024 -> "%.1f KB/s".format(bytesPerSecond / 1024.0)
            else -> "$bytesPerSecond B/s"
        }
    }

    private fun getErrorMessage(reason: Int): String {
        return when (reason) {
            DownloadManager.ERROR_CANNOT_RESUME -> "Cannot resume download"
            DownloadManager.ERROR_DEVICE_NOT_FOUND -> "No external storage device found"
            DownloadManager.ERROR_FILE_ALREADY_EXISTS -> "File already exists"
            DownloadManager.ERROR_FILE_ERROR -> "Storage issue"
            DownloadManager.ERROR_HTTP_DATA_ERROR -> "HTTP data error"
            DownloadManager.ERROR_INSUFFICIENT_SPACE -> "Insufficient storage space"
            DownloadManager.ERROR_TOO_MANY_REDIRECTS -> "Too many redirects"
            DownloadManager.ERROR_UNHANDLED_HTTP_CODE -> "Unhandled HTTP response"
            DownloadManager.ERROR_UNKNOWN -> "Unknown error"
            else -> "Download failed"
        }
    }

    private fun startProgressMonitoring(context: Context) {
        progressJob?.cancel()
        lastUpdateTime = 0L
        lastBytesDownloaded = 0L

        progressJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                val state = queryDownloadProgress()
                _downloadStateFlow.value = state

                when (state) {
                    is DownloadState.Completed, is DownloadState.Failed, is DownloadState.Cancelled -> {
                        break
                    }
                    else -> {}
                }

                delay(500)
            }
        }
    }

    private fun registerDownloadCompleteReceiver(context: Context) {
        unregisterDownloadCompleteReceiver(context)

        downloadCompleteReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id == currentDownloadId) {
                    val state = queryDownloadProgress()
                    _downloadStateFlow.value = state

                    if (state is DownloadState.Completed) {
                        installApk(context, state.filePath)
                    }

                    unregisterDownloadCompleteReceiver(context)
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(
                downloadCompleteReceiver,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            context.registerReceiver(
                downloadCompleteReceiver,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
            )
        }
    }

    private fun unregisterDownloadCompleteReceiver(context: Context) {
        downloadCompleteReceiver?.let {
            try {
                context.unregisterReceiver(it)
            } catch (e: Exception) {
                // Receiver not registered
            }
            downloadCompleteReceiver = null
        }
    }

    private fun installApk(context: Context, uriString: String) {
        try {
            val file = if (uriString.startsWith("file://")) {
                File(Uri.parse(uriString).path!!)
            } else {
                File(uriString)
            }

            if (!file.exists()) {
                _downloadStateFlow.value = DownloadState.Failed("APK file not found")
                return
            }

            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.FileProvider",
                file
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(intent)
        } catch (e: Exception) {
            _downloadStateFlow.value = DownloadState.Failed("Failed to install: ${e.message}")
        }
    }

    fun cleanup(context: Context) {
        progressJob?.cancel()
        unregisterDownloadCompleteReceiver(context)
    }

    fun formatBytes(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
            bytes >= 1024 -> "%.1f KB".format(bytes / 1024.0)
            else -> "$bytes B"
        }
    }
}

