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
import androidx.core.app.NotificationCompat
import com.sendfile.app.MainActivity
import com.sendfile.app.R
import com.sendfile.app.TransferProgressActivity
import kotlinx.coroutines.*
import java.io.*
import java.net.ServerSocket
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

        fun startReceiving(context: Context, socket: Socket, saveLocation: Uri) {
            // This is called from the activity directly, not via service
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
                
                startForeground(NOTIFICATION_ID, createNotification("Enviando arquivos..."))
                sendFiles(ip, port, files)
            }
            "ACTION_RECEIVE" -> {
                startForeground(NOTIFICATION_ID, createNotification("Recebendo arquivos..."))
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

    private fun createNotification(text: String): androidx.core.app.NotificationCompat.Builder {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SendFile")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
    }

    private fun sendFiles(ip: String, port: Int, files: List<Uri>) {
        serviceScope.launch {
            try {
                val socket = Socket(ip, port)
                socket.soTimeout = 60000
                socket.sendBufferSize = BUFFER_SIZE
                socket.receiveBufferSize = BUFFER_SIZE
                
                // Send file count
                val output = DataOutputStream(socket.getOutputStream())
                output.writeInt(files.size)
                output.flush()

                var totalBytes = 0L
                var transferredBytes = 0L

                // First pass: calculate total size
                for (uri in files) {
                    val size = getFileSize(uri)
                    totalBytes += size
                }

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
                        
                        val speed = calculateSpeed(transferredBytes, totalBytes)
                        withContext(Dispatchers.Main) {
                            listener?.onProgress(transferredBytes, totalBytes, speed)
                        }
                        updateNotification(transferredBytes, totalBytes)
                    }
                    inputStream?.close()
                }

                output.writeUTF("DONE")
                output.flush()
                
                socket.close()

                withContext(Dispatchers.Main) {
                    listener?.onComplete(true, "Transferência concluída!")
                    stopSelf()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    listener?.onComplete(false, "Erro: ${e.message}")
                    stopSelf()
                }
            }
        }
    }

    private fun receiveFiles(socket: Socket, saveDir: File) {
        serviceScope.launch {
            try {
                socket.soTimeout = 60000
                socket.sendBufferSize = BUFFER_SIZE
                socket.receiveBufferSize = BUFFER_SIZE
                
                val input = DataInputStream(socket.getInputStream())
                
                // Read file count
                val fileCount = input.readInt()
                
                var totalBytes = 0L
                var transferredBytes = 0L
                
                // First pass: read all file sizes
                val fileSizes = mutableListOf<Pair<String, Long>>()
                for (i in 0 until fileCount) {
                    val fileName = input.readUTF()
                    val fileSize = input.readLong()
                    fileSizes.add(fileName to fileSize)
                    totalBytes += fileSize
                }
                
                // Receive files
                for ((fileName, fileSize) in fileSizes) {
                    val outputFile = File(saveDir, fileName)
                    val output = FileOutputStream(outputFile)
                    
                    val buffer = ByteArray(BUFFER_SIZE)
                    var remaining = fileSize
                    var read: Int
                    
                    while (remaining > 0) {
                        val toRead = minOf(remaining.toLong(), BUFFER_SIZE.toLong()).toInt()
                        read = input.read(buffer, 0, toRead)
                        if (read == -1) break
                        
                        output.write(buffer, 0, read)
                        transferredBytes += read
                        remaining -= read
                        
                        val speed = calculateSpeed(transferredBytes, totalBytes)
                        withContext(Dispatchers.Main) {
                            listener?.onProgress(transferredBytes, totalBytes, speed)
                        }
                        updateNotification(transferredBytes, totalBytes)
                    }
                    output.close()
                }
                
                // Read DONE marker
                val done = input.readUTF()
                
                socket.close()
                
                withContext(Dispatchers.Main) {
                    listener?.onComplete(true, "Transferência concluída!")
                    stopSelf()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    listener?.onComplete(false, "Erro: ${e.message}")
                    stopSelf()
                }
            }
        }
    }

    private fun calculateSpeed(transferred: Long, total: Long): Double {
        // Simplified speed calculation
        return transferred.toDouble() / (System.currentTimeMillis() / 1000.0)
    }

    private fun getFileSize(uri: Uri): Long {
        return try {
            contentResolver.openInputStream(uri)?.available()?.toLong() ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    private fun getFileName(uri: Uri): String {
        var name = uri.lastPathSegment ?: "unknown"
        val cursor = contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val displayNameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (displayNameIndex != -1) {
                    name = it.getString(displayNameIndex)
                }
            }
        }
        return name
    }

    private fun updateNotification(transferred: Long, total: Long) {
        val progress = if (total > 0) (transferred * 100 / total).toInt() else 0
        val notification = createNotification("Transferindo: $progress%")
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification.build())
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
