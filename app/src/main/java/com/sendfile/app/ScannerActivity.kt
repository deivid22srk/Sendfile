package com.sendfile.app

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.sendfile.app.databinding.ActivityScannerBinding

class ScannerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityScannerBinding
    private var scanned = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScannerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupScanner()
    }

    private fun setupScanner() {
        binding.barcodeScannerView.decodeSingle { result ->
            if (!scanned && result.text != null) {
                scanned = true
                com.journeyapps.barcodescanner.ScanIntentResult.setResult(this, result.text)
                finish()
            }
        }

        binding.btnClose.setOnClickListener {
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        binding.barcodeScannerView.resume()
    }

    override fun onPause() {
        super.onPause()
        binding.barcodeScannerView.pause()
    }
}
