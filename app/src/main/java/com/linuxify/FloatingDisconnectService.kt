package com.linuxify

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.*

class FloatingDisconnectService : Service() {
    private var view: View? = null
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onCreate() { super.onCreate(); val wm = getSystemService(WINDOW_SERVICE) as WindowManager; view = LayoutInflater.from(this).inflate(R.layout.floating_button, null).also { v ->
        v.setOnClickListener { startService(Intent(this, ContainerService::class.java).setAction(ContainerService.ACTION_STOP)); stopSelf() }
        val type = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
        wm.addView(v, WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, type, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.END; x = 24; y = 48 })
    } }
    override fun onDestroy() { view?.let { (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(it) }; super.onDestroy() }
}
