package com.sendfile.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Binder
import android.os.IBinder
import android.provider.OpenableColumns
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.sendfile.app.MainActivity
import com.sendfile.app.R
import kotlinx.coroutines.*
import java.io.DataOutputStream
import java.net.Socket

class TransferService : Service() {

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "transfer_channel"
        private const val BUFFER_SIZE = 1024 * 1024 // 1MB buffer for faster transfers

        private var serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        private var listener: TransferListener? = null

        fun startSending(
            context: Context,
            ip: String,
            port: Int,
            files: List<Uri>
        ) {
            val intent = Intent(context, TransferService::class.java).apply {
                action = "ACTION_SEND"
                putExtra("ip", ip)
                putExtra("port", port)
                putParcelableArrayListExtra("files", ArrayList(files))
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun setListener(l: TransferListener?) {
            listener = l
        }
    }

    interface TransferListener {
        fun onProgress(bytesTransferred: Long, totalBytes: Long, speedBytesPerSec: Double)
        fun onComplete(success: Boolean, message: String)
    }

    inner class TransferBinder : Binder() {
        fun getService(): TransferService = this@TransferService
    }

    override fun onBind(intent: Intent?): IBinder {
        return TransferBinder()
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "ACTION_SEND" -> {
                val ip = intent.getStringExtra("ip") ?: return START_NOT_STICKY
                val port = intent.getIntExtra("port", 8888)
                val files = intent.getParcelableArrayListExtra<Uri>("files") ?: return START_NOT_STICKY
                
                startForeground(NOTIFICATION_ID, createNotification("Enviando arquivos...").build())
                sendFiles(ip, port, files)
            }
        }
        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Transferência de Arquivos",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    private fun createNotification(text: String): NotificationCompat.Builder {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SendFile")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_upload)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
    }

    private fun sendFiles(ip: String, port: Int, files: List<Uri>) {
        serviceScope.launch {
            var socket: Socket? = null
            try {
                socket = Socket(ip, port)
                socket.soTimeout = 120000
                socket.sendBufferSize = BUFFER_SIZE
                socket.receiveBufferSize = BUFFER_SIZE
                
                val output = DataOutputStream(socket.getOutputStream())
                
                // Send file count
                output.writeInt(files.size)
                output.flush()

                var totalBytes = 0L
                var transferredBytes = 0L

                // First pass: calculate total size
                for (uri in files) {
                    val size = getFileSize(uri)
                    totalBytes += size
                }

                val startTime = System.currentTimeMillis()

                // Send files
                for (uri in files) {
                    val fileName = getFileName(uri)
                    val fileSize = getFileSize(uri)
                    
                    // Send file metadata
                    output.writeUTF(fileName)
                    output.writeLong(fileSize)
                    output.flush()

                    // Send file content
                    val inputStream = contentResolver.openInputStream(uri)
                    val buffer = ByteArray(BUFFER_SIZE)
                    var read: Int
                    
                    while (inputStream?.read(buffer).also { read = it ?: -1 } != -1) {
                        output.write(buffer, 0, read)
                        transferredBytes += read
                        
                        val elapsed = (System.currentTimeMillis() - startTime) / 1000.0
                        val speed = if (elapsed > 0) transferredBytes / elapsed else 0.0
                        
                        val progress = if (totalBytes > 0) (transferredBytes * 100 / totalBytes).toInt() else 0
                        updateNotification("Transferindo: $progress%")
                    }
                    inputStream?.close()
                }

                output.writeUTF("DONE")
                output.flush()
                
                socket.close()

                withContext(Dispatchers.Main) {
                    updateNotification("Transferência concluída!")
                    listener?.onComplete(true, "Transferência concluída!")
                    stopSelf()
                }
            } catch (e: Exception) {
                socket?.close()
                withContext(Dispatchers.Main) {
                    listener?.onComplete(false, "Erro: ${e.message}")
                    stopSelf()
                }
            }
        }
    }

    private fun getFileSize(uri: Uri): Long {
        var size: Long = 0
        val cursor = contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val sizeIndex = it.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex != -1) {
                    size = it.getLong(sizeIndex)
                }
            }
        }
        return size
    }

    private fun getFileName(uri: Uri): String {
        var name = uri.lastPathSegment ?: "unknown"
        val cursor = contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val displayNameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (displayNameIndex != -1) {
                    name = it.getString(displayNameIndex)
                }
            }
        }
        return name
    }

    private fun updateNotification(text: String) {
        val notification = createNotification(text).build()
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
