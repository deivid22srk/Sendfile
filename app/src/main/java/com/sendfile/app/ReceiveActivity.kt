package com.sendfile.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.sendfile.app.databinding.ActivityReceiveBinding
import android.graphics.Bitmap
import java.io.File
import java.net.ServerSocket

class ReceiveActivity : AppCompatActivity() {

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, ReceiveActivity::class.java)
            context.startActivity(intent)
        }
    }

    private lateinit var binding: ActivityReceiveBinding
    private var serverThread: Thread? = null
    private var isRunning = false
    private var selectedFolder: Uri? = null

    private val folderPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            selectedFolder = it
            contentResolver.takePersistableUriPermission(
                it,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            binding.tvSelectedFolder.text = "Pasta selecionada: ${getFolderName(it)}"
            Toast.makeText(this, "Pasta selecionada!", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReceiveBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        startServer()
    }

    private fun setupUI() {
        binding.btnClose.setOnClickListener {
            stopServer()
            finish()
        }

        binding.btnSelectFolder.setOnClickListener {
            folderPickerLauncher.launch(null)
        }
    }

    private fun getFolderName(uri: Uri): String {
        val treeId = uri.lastPathSegment?.split(":")?.lastOrNull()
        return treeId ?: uri.toString()
    }

    private fun startServer() {
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
        val ip = android.text.format.Formatter.formatIpAddress(wifiManager.connectionInfo.ipAddress)
        
        if (ip == "0.0.0.0") {
            binding.tvStatus.text = "Erro: Conecte-se a uma rede WiFi primeiro"
            return
        }

        val port = 8888
        val connectionInfo = "$ip:$port"
        
        binding.tvStatus.text = "Aguardando conexão...\nIP: $ip\nPorta: $port"
        
        // Generate QR Code
        val qrBitmap = generateQRCode(connectionInfo)
        binding.qrCodeView.setImageBitmap(qrBitmap)
        
        // Start server thread
        isRunning = true
        serverThread = Thread {
            try {
                val serverSocket = ServerSocket(port)
                runOnUiThread {
                    binding.tvStatus.text = "Servidor iniciado!\nAguardando envio..."
                }
                
                val socket = serverSocket.accept()
                runOnUiThread {
                    binding.tvStatus.text = "Conectado! Recebendo arquivos..."
                    TransferProgressActivity.start(this, 0, 0, false)
                }
                
                // Get save location
                val saveDir = if (selectedFolder != null) {
                    val docFile = android.documentfile.DocumentFile.fromTreeUri(this, selectedFolder!!)
                    File(docFile?.uri?.toString() ?: Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath)
                } else {
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                }
                
                receiveFiles(socket, saveDir)
                
                serverSocket.close()
            } catch (e: Exception) {
                runOnUiThread {
                    binding.tvStatus.text = "Erro: ${e.message}"
                    Toast.makeText(this@ReceiveActivity, "Erro: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
        serverThread?.start()
    }

    private fun receiveFiles(socket: java.net.Socket, saveDir: File) {
        try {
            socket.soTimeout = 120000
            socket.sendBufferSize = 1024 * 1024
            socket.receiveBufferSize = 1024 * 1024
            
            val input = java.io.DataInputStream(socket.getInputStream())
            
            // Read file count
            val fileCount = input.readInt()
            
            var totalBytes = 0L
            var transferredBytes = 0L
            val startTime = System.currentTimeMillis()
            
            // Read file metadata first to calculate total size
            val fileInfos = mutableListOf<Triple<String, Long, File>>()
            for (i in 0 until fileCount) {
                val fileName = input.readUTF()
                val fileSize = input.readLong()
                val outputFile = File(saveDir, fileName)
                fileInfos.add(Triple(fileName, fileSize, outputFile))
                totalBytes += fileSize
            }
            
            runOnUiThread {
                TransferProgressActivity.start(this, totalBytes, fileCount, false)
            }
            
            // Receive files
            for ((fileName, fileSize, outputFile) in fileInfos) {
                val output = java.io.FileOutputStream(outputFile)
                
                val buffer = ByteArray(1024 * 1024) // 1MB buffer
                var remaining = fileSize
                var read: Int
                
                while (remaining > 0) {
                    val toRead = minOf(remaining.toInt(), buffer.size)
                    read = input.read(buffer, 0, toRead)
                    if (read == -1) break
                    
                    output.write(buffer, 0, read)
                    transferredBytes += read
                    remaining -= read
                    
                    val elapsed = (System.currentTimeMillis() - startTime) / 1000.0
                    val speed = if (elapsed > 0) transferredBytes / elapsed else 0.0
                    
                    runOnUiThread {
                        // Update progress activity if it exists
                    }
                }
                output.close()
            }
            
            socket.close()
            
            runOnUiThread {
                binding.tvStatus.text = "Transferência concluída!\nArquivos salvos em: ${saveDir.absolutePath}"
                Toast.makeText(this, "Arquivos recebidos com sucesso!", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            runOnUiThread {
                binding.tvStatus.text = "Erro na transferência: ${e.message}"
                Toast.makeText(this@ReceiveActivity, "Erro: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun generateQRCode(text: String): Bitmap? {
        return try {
            val size = 512
            val bitMatrix = com.google.zxing.qrcode.QRCodeWriter().encode(
                text,
                com.google.zxing.BarcodeFormat.QR_CODE,
                size,
                size
            )
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
            for (x in 0 until size) {
                for (y in 0 until size) {
                    bitmap.setPixel(x, y, if (bitMatrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
                }
            }
            bitmap
        } catch (e: Exception) {
            null
        }
    }

    private fun stopServer() {
        isRunning = false
        serverThread?.interrupt()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopServer()
    }
}
