package com.linuxify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class ContainerService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createChannel()
        startForeground(1, notification("Linuxify is preparing Linux..."))
        when (intent?.action) {
            ACTION_STOP -> scope.launch { LinuxifyRuntime.stop(this@ContainerService); stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() }
            else -> scope.launch { LinuxifyRuntime.start(this@ContainerService); updateNotification("Linuxify container is active") }
        }
        return START_STICKY
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_RUNNING_LOW) Runtime.getRuntime().gc()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun updateNotification(text: String) {
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(1, notification(text))
    }

    private fun notification(text: String) = NotificationCompat.Builder(this, CHANNEL)
        .setSmallIcon(R.drawable.ic_launcher)
        .setContentTitle("Linuxify running")
        .setContentText(text)
        .setOngoing(true)
        .build()

    private fun createChannel() {
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL, "Linuxify", NotificationManager.IMPORTANCE_LOW))
        }
    }

    companion object {
        const val ACTION_START = "com.linuxify.START"
        const val ACTION_STOP = "com.linuxify.STOP"
        private const val CHANNEL = "linuxify_container"
    }
}
