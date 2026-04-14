package com.sendfile.app

import android.content.Intent
import android.os.Bundle
import com.journeyapps.barcodescanner.CaptureActivity
import com.journeyapps.barcodescanner.ScanOptions

class ScannerActivity : CaptureActivity() {

    private var scanned = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val options = ScanOptions().apply {
            setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            setPrompt("Aponte para o QR Code")
            setCameraId(0)
            setBeepEnabled(true)
            setBarcodeImageEnabled(true)
            setOrientationLocked(true)
        }

        barcodeView.decodeSingle { result ->
            if (!scanned && result.text != null) {
                scanned = true
                val data = Intent().apply {
                    putExtra("SCAN_RESULT", result.text)
                }
                setResult(RESULT_OK, data)
                finish()
            }
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
