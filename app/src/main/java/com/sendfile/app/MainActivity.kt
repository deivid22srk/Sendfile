package com.sendfile.app

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.sendfile.app.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            showModeSelection()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupClickListeners()
    }

    private fun setupClickListeners() {
        binding.btnSend.setOnClickListener {
            requestPermissions()
        }

        binding.btnReceive.setOnClickListener {
            requestPermissions()
        }
    }

    private fun requestPermissions() {
        val permissions = arrayOf(
            android.Manifest.permission.CAMERA,
            android.Manifest.permission.READ_EXTERNAL_STORAGE,
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
            android.Manifest.permission.ACCESS_WIFI_STATE,
            android.Manifest.permission.ACCESS_COARSE_LOCATION,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        )
        requestPermissionLauncher.launch(permissions)
    }

    private fun showModeSelection() {
        // This is handled by the button clicks directly
    }

    fun onSendClicked() {
        SendActivity.start(this)
    }

    fun onReceiveClicked() {
        ReceiveActivity.start(this)
    }
}
