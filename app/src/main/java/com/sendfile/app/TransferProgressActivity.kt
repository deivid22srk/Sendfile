package com.sendfile.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.sendfile.app.databinding.ActivityTransferProgressBinding

class TransferProgressActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_TOTAL_SIZE = "extra_total_size"
        const val EXTRA_FILE_COUNT = "extra_file_count"
        const val EXTRA_IS_SENDING = "extra_is_sending"

        fun start(context: Context, totalSize: Long, fileCount: Int, isSending: Boolean) {
            val intent = Intent(context, TransferProgressActivity::class.java).apply {
                putExtra(EXTRA_TOTAL_SIZE, totalSize)
                putExtra(EXTRA_FILE_COUNT, fileCount)
                putExtra(EXTRA_IS_SENDING, isSending)
            }
            context.startActivity(intent)
        }
    }

    private lateinit var binding: ActivityTransferProgressBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTransferProgressBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val totalSize = intent.getLongExtra(EXTRA_TOTAL_SIZE, 0)
        val fileCount = intent.getIntExtra(EXTRA_FILE_COUNT, 0)
        val isSending = intent.getBooleanExtra(EXTRA_IS_SENDING, true)

        binding.tvStatus.text = if (isSending) "Enviando arquivos..." else "Recebendo arquivos..."
        binding.tvFileCount.text = "$fileCount arquivo(s) • ${formatSize(totalSize)}"
    }

    fun updateProgress(bytesTransferred: Long, totalBytes: Long, speedBytesPerSec: Double) {
        val progress = if (totalBytes > 0) (bytesTransferred * 100 / totalBytes).toInt() else 0
        binding.progressBar.progress = progress
        binding.tvProgress.text = "$progress%"
        binding.tvSpeed.text = "${formatSpeed(speedBytesPerSec)}"
    }

    fun transferComplete(success: Boolean, message: String) {
        binding.tvStatus.text = if (success) "Transferência concluída!" else "Erro na transferência"
        binding.tvSpeed.text = message
        binding.btnClose.visibility = android.view.View.VISIBLE
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
            else -> "${bytes / (1024 * 1024 * 1024)} GB"
        }
    }

    private fun formatSpeed(bytesPerSec: Double): String {
        return when {
            bytesPerSec < 1024 -> "${bytesPerSec.toInt()} B/s"
            bytesPerSec < 1024 * 1024 -> "${bytesPerSec / 1024} KB/s"
            else -> "${bytesPerSec / (1024 * 1024)} MB/s"
        }
    }
}
