package com.linuxify

import android.content.Context

object LinuxifyRuntime {
    @Volatile private var manager: ContainerManager? = null
    @Volatile var lastSession: ContainerSession? = null
        private set
    @Volatile var lastError: String? = null
        private set

    suspend fun start(context: Context): Result<ContainerSession> {
        val appContext = context.applicationContext
        val profile = DeviceDetector.detect(appContext)
        val activeManager = manager ?: ContainerManager(appContext).also { manager = it }
        val result = activeManager.start(profile)
        result.onSuccess { lastSession = it; lastError = null }.onFailure { lastError = it.message }
        return result
    }

    suspend fun stop(context: Context): Result<Unit> {
        val result = manager?.stop() ?: Result.success(Unit)
        result.onSuccess { manager = null; lastSession = null; lastError = null }.onFailure { lastError = it.message }
        return result
    }
}
