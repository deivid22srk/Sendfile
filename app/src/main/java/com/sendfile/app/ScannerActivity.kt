package com.sendfile.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.journeyapps.barcodescanner.CaptureActivity
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

class ScannerActivity : CaptureActivity() {

    companion object {
        const val REQUEST_CODE = 100
    }

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

        decoratedBarcodeView.decodeSingle { result ->
            if (!scanned && result?.text != null) {
                scanned = true
                val data = Intent().apply {
                    putExtra("SCAN_RESULT", result.text)
                }
                setResult(Activity.RESULT_OK, data)
                finish()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        decoratedBarcodeView.resume()
    }

    override fun onPause() {
        super.onPause()
        decoratedBarcodeView.pause()
    }
}
