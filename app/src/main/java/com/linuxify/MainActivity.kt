package com.linuxify

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var profile: DeviceProfile
    private lateinit var statusText: TextView

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        statusText = findViewById(R.id.statusText)
        profile = DeviceDetector.detect(this)
        findViewById<TextView>(R.id.deviceInfoText).text = profile.summary()
        findViewById<MaterialButton>(R.id.connectButton).setOnClickListener { connect() }
        findViewById<MaterialButton>(R.id.disconnectButton).setOnClickListener { disconnect() }
        if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun connect() {
        statusText.text = "Connecting..."
        startForegroundServiceCompat(Intent(this, ContainerService::class.java).setAction(ContainerService.ACTION_START))
        if (Settings.canDrawOverlays(this)) startService(Intent(this, FloatingDisconnectService::class.java))
        lifecycleScope.launch {
            repeat(20) {
                delay(250)
                LinuxifyRuntime.lastSession?.let { session ->
                    statusText.text = "Connected"
                    Toast.makeText(this@MainActivity, session.message, Toast.LENGTH_LONG).show()
                    startActivity(Intent(this@MainActivity, ContainerActivity::class.java))
                    return@launch
                }
            }
            val error = LinuxifyRuntime.lastError
            statusText.text = if (error == null) "Connected" else "Ready"
            Toast.makeText(this@MainActivity, error ?: "Linuxify is starting in background", Toast.LENGTH_LONG).show()
        }
    }

    private fun disconnect() {
        statusText.text = "Disconnecting..."
        startService(Intent(this, ContainerService::class.java).setAction(ContainerService.ACTION_STOP))
        stopService(Intent(this, FloatingDisconnectService::class.java))
        statusText.text = "Ready"
        Toast.makeText(this, "Linux disconnected", Toast.LENGTH_SHORT).show()
    }

    private fun startForegroundServiceCompat(intent: Intent) {
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent) else startService(intent)
    }
}
