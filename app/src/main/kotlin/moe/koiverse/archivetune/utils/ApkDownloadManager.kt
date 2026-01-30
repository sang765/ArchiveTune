package moe.koiverse.archivetune.utils

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.FileProvider
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

data class DownloadProgress(
    val downloadId: Long = -1,
    val progress: Float = 0f,
    val isDownloading: Boolean = false,
    val isCompleted: Boolean = false,
    val error: String? = null
)

class ApkDownloadManager(
    private val context: Context,
    lifecycleOwner: LifecycleOwner? = null
) : DefaultLifecycleObserver {
    private val _downloadProgress = MutableStateFlow(DownloadProgress())
    val downloadProgress: StateFlow<DownloadProgress> = _downloadProgress
    
    private var downloadManager: DownloadManager? = null
    private var downloadReceiver: BroadcastReceiver? = null
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var progressMonitoringJob: Job? = null
    
    init {
        downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        lifecycleOwner?.lifecycle?.addObserver(this)
    }
    
    fun startDownload(downloadUrl: String, fileName: String = "ArchiveTune_update.apk") {
        try {
            val destinationFile = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)
            
            val request = DownloadManager.Request(Uri.parse(downloadUrl)).apply {
                setTitle("ArchiveTune Update")
                setDescription("Downloading latest version...")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationUri(Uri.fromFile(destinationFile))
                setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE)
                setAllowedOverRoaming(false)
            }
            
            val downloadId = downloadManager?.enqueue(request) ?: -1
            
            if (downloadId != -1L) {
                coroutineScope.launch {
                    withContext(Dispatchers.Main) {
                        _downloadProgress.value = DownloadProgress(
                            downloadId = downloadId,
                            isDownloading = true
                        )
                    }
                }
                
                // Register receiver to listen for download completion
                downloadReceiver = object : BroadcastReceiver() {
                    override fun onReceive(context: Context?, intent: Intent?) {
                        val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                        if (id == downloadId) {
                            handleDownloadComplete(downloadId, fileName)
                            context?.unregisterReceiver(this)
                            downloadReceiver = null
                        }
                    }
                }
                
                context.registerReceiver(
                    downloadReceiver,
                    IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                    Context.RECEIVER_NOT_EXPORTED
                )
                
                // Start progress monitoring
                monitorDownloadProgress(downloadId)
            } else {
                coroutineScope.launch {
                    withContext(Dispatchers.Main) {
                        _downloadProgress.value = DownloadProgress(error = "Failed to start download")
                    }
                }
            }
        } catch (e: Exception) {
            coroutineScope.launch {
                withContext(Dispatchers.Main) {
                    _downloadProgress.value = DownloadProgress(error = e.message)
                }
            }
        }
    }
    
    private fun monitorDownloadProgress(downloadId: Long) {
        progressMonitoringJob = coroutineScope.launch {
            while (isActive) {
                val query = DownloadManager.Query().setFilterById(downloadId)
                val cursor = downloadManager?.query(query)
                cursor?.use {
                    if (it.moveToFirst()) {
                        val bytesDownloaded = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                        val bytesTotal = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                        val status = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                        
                        if (bytesTotal > 0) {
                            val progress = (bytesDownloaded.toFloat() / bytesTotal.toFloat())
                            withContext(Dispatchers.Main) {
                                _downloadProgress.value = _downloadProgress.value.copy(progress = progress)
                            }
                        }
                        
                        if (status != DownloadManager.STATUS_RUNNING) {
                            break
                        }
                    }
                }
                delay(500)
            }
        }
    }
    
    private fun handleDownloadComplete(downloadId: Long, fileName: String) {
        val query = DownloadManager.Query().setFilterById(downloadId)
        val cursor = downloadManager?.query(query)
        cursor?.use {
            if (it.moveToFirst()) {
                val status = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                if (status == DownloadManager.STATUS_SUCCESSFUL) {
                    coroutineScope.launch {
                        withContext(Dispatchers.Main) {
                            _downloadProgress.value = DownloadProgress(isCompleted = true)
                        }
                    }
                    installApk(fileName)
                } else {
                    coroutineScope.launch {
                        withContext(Dispatchers.Main) {
                            _downloadProgress.value = DownloadProgress(error = "Download failed")
                        }
                    }
                }
            }
        }
    }
    
    private fun installApk(fileName: String) {
        try {
            val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)
            if (file.exists()) {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        val uri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.FileProvider",
                            file
                        )
                        setDataAndType(uri, "application/vnd.android.package-archive")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    } else {
                        setDataAndType(Uri.fromFile(file), "application/vnd.android.package-archive")
                    }
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
        } catch (e: Exception) {
            coroutineScope.launch {
                withContext(Dispatchers.Main) {
                    _downloadProgress.value = DownloadProgress(error = "Failed to install APK: ${e.message}")
                }
            }
        }
    }
    
    override fun onDestroy(owner: LifecycleOwner) {
        cleanup()
    }
    
    fun cleanup() {
        progressMonitoringJob?.cancel()
        progressMonitoringJob = null
        
        downloadReceiver?.let { receiver ->
            try {
                context.unregisterReceiver(receiver)
            } catch (e: Exception) {
                // Receiver might already be unregistered
            }
            downloadReceiver = null
        }
        
        coroutineScope.cancel()
    }
}
