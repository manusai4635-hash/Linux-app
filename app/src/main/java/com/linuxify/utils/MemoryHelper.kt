package com.linuxify.utils

import android.app.ActivityManager
import android.content.Context

object MemoryHelper { fun availableMb(context: Context): Long { val i = ActivityManager.MemoryInfo(); (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(i); return i.availMem / 1024 / 1024 } }
