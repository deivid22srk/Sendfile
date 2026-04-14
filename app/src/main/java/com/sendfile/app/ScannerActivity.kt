package com.sendfile.app

import android.os.Bundle
import android.content.Intent
import com.journeyapps.barcodescanner.CaptureActivity
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.sendfile.app.databinding.ActivityScannerBinding

class ScannerActivity : CaptureActivity() {

    private lateinit var binding: ActivityScannerBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScannerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initBarcodeScanner()
    }

    private fun initBarcodeScanner() {
        val options = ScanOptions().apply {
            setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            setPrompt("Aponte para o QR Code")
            setCameraId(0)
            setBeepEnabled(true)
            setBarcodeImageEnabled(true)
            setOrientationLocked(true)
        }
        barcodeView.decodeSingle { result ->
            result.text?.let { text ->
                val data = Intent().apply {
                    putExtra("SCAN_RESULT", text)
                }
                setResult(RESULT_OK, data)
                finish()
            }
        }

        binding.btnClose.setOnClickListener {
            setResult(RESULT_CANCELED)
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        barcodeView.resume()
    }

    override fun onPause() {
        super.onPause()
        barcodeView.pause()
    }
}
