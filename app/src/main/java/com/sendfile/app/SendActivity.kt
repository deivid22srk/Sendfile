package com.sendfile.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.sendfile.app.databinding.ActivitySendBinding
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.Socket
import java.io.DataOutputStream

class SendActivity : AppCompatActivity() {

    companion object {
        private const val MAX_FILE_PICK = 50
        fun start(context: Context) {
            val intent = Intent(context, SendActivity::class.java)
            context.startActivity(intent)
        }
    }

    private lateinit var binding: ActivitySendBinding
    private val selectedFiles = mutableListOf<Uri>()

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            selectedFiles.clear()
            selectedFiles.addAll(uris.take(MAX_FILE_PICK))
            updateFileList()
        }
    }

    private val scannerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val data = result.data
            val scanResult = data?.getStringExtra("SCAN_RESULT")
                ?: data?.getStringExtra("SCAN_RESULT")
            if (scanResult != null) {
                startTransfer(scanResult)
            } else {
                Toast.makeText(this, "Falha ao ler QR Code", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySendBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupClickListeners()
    }

    private fun setupClickListeners() {
        binding.btnSelectFiles.setOnClickListener {
            openFilePicker()
        }

        binding.btnScanQR.setOnClickListener {
            if (selectedFiles.isEmpty()) {
                Toast.makeText(this, "Selecione arquivos primeiro", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            openScanner()
        }

        binding.btnSend.setOnClickListener {
            if (selectedFiles.isEmpty()) {
                Toast.makeText(this, "Selecione arquivos primeiro", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            openScanner()
        }

        binding.btnClose.setOnClickListener {
            finish()
        }
    }

    private fun openFilePicker() {
        filePickerLauncher.launch("*/*")
    }

    private fun openScanner() {
        val scanIntent = Intent(this, ScannerActivity::class.java)
        scannerLauncher.launch(scanIntent)
    }

    private fun updateFileList() {
        binding.tvFileCount.text = "${selectedFiles.size} arquivo(s) selecionado(s)"
        
        val fileListText = selectedFiles.joinToString("\n") { uri ->
            val name = getFileName(uri)
            val size = getFileSize(uri)
            "• $name (${formatSize(size)})"
        }
        binding.tvFileList.text = fileListText
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

    private fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
            else -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        }
    }

    private fun startTransfer(connectionInfo: String) {
        val parts = connectionInfo.split(":")
        if (parts.size != 2) {
            Toast.makeText(this, "QR Code inválido", Toast.LENGTH_SHORT).show()
            return
        }

        val ip = parts[0]
        val port = parts[1].toIntOrNull() ?: 8888

        // Calculate total size
        var totalSize = 0L
        for (uri in selectedFiles) {
            totalSize += getFileSize(uri)
        }

        // Start transfer in background
        Thread {
            try {
                sendFiles(ip, port, selectedFiles, totalSize)
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@SendActivity, "Erro: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()

        // Show progress activity
        TransferProgressActivity.start(this, totalSize, selectedFiles.size, true)
    }

    private fun sendFiles(ip: String, port: Int, files: List<Uri>, totalSize: Long) {
        var socket: Socket? = null
        try {
            socket = Socket(ip, port)
            socket.soTimeout = 120000
            socket.sendBufferSize = 1024 * 1024 // 1MB buffer for faster transfer
            socket.receiveBufferSize = 1024 * 1024
            
            val output = DataOutputStream(socket.getOutputStream())
            
            // Send file count
            output.writeInt(files.size)
            output.flush()

            var transferredBytes = 0L
            val startTime = System.currentTimeMillis()

            // Send each file
            for (uri in files) {
                val fileName = getFileName(uri)
                val fileSize = getFileSize(uri)
                
                // Send file metadata
                output.writeUTF(fileName)
                output.writeLong(fileSize)
                output.flush()

                // Send file content
                val inputStream = contentResolver.openInputStream(uri)
                val buffer = ByteArray(1024 * 1024) // 1MB buffer
                var read: Int
                
                while (inputStream?.read(buffer).also { read = it ?: -1 } != -1) {
                    output.write(buffer, 0, read)
                    transferredBytes += read
                    
                    val elapsed = (System.currentTimeMillis() - startTime) / 1000.0
                    val speed = if (elapsed > 0) transferredBytes / elapsed else 0.0
                    
                    runOnUiThread {
                        // Progress update would be handled by the activity
                    }
                }
                inputStream?.close()
            }

            // Send done marker
            output.writeUTF("DONE")
            output.flush()
            
            socket.close()

            runOnUiThread {
                Toast.makeText(this, "Transferência concluída!", Toast.LENGTH_LONG).show()
                finish()
            }
        } catch (e: Exception) {
            socket?.close()
            runOnUiThread {
                Toast.makeText(this, "Erro na transferência: ${e.message}", Toast.LENGTH_LONG).show()
            }
            throw e
        }
    }
}
