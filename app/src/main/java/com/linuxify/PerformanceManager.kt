package com.linuxify

import android.content.Context
import android.os.BatteryManager
import java.io.File

class PerformanceManager(private val context: Context, private val profile: DeviceProfile) {
    fun prepare(): OptimizationReport {
        val warnings = mutableListOf<String>()
        val swapFile = if (profile.ramMb < 2048L) createSwapFile(256) else null
        if (availableStorageMb() < 1024L) warnings += "Storage low: keep at least 1GB free for rootfs extraction."
        if (batteryPercent() in 1..15) warnings += "Battery low: connect charger before long desktop sessions."
        trimCache()
        return OptimizationReport(
            swapFile = swapFile,
            cpuLimitPercent = cpuLimitPercent(),
            graphicsMode = graphicsMode(),
            dashboardEnabled = profile.ramMb >= 2048L,
            audioEnabled = profile.ramMb >= 1024L,
            warnings = warnings
        )
    }

    fun cpuLimitPercent(): Int = when (profile.performanceTier) {
        PerformanceTier.VERY_LOW -> 30
        PerformanceTier.LOW -> 50
        PerformanceTier.MEDIUM -> 70
        PerformanceTier.HIGH -> 90
        PerformanceTier.VERY_HIGH -> 100
    }

    fun graphicsMode(): GraphicsMode = if (profile.ramMb < 2048L || profile.cpuCores <= 2) GraphicsMode.SOFTWARE else GraphicsMode.HARDWARE

    fun trimCache() {
        context.cacheDir.walkBottomUp().filter { it.isFile }.forEach { it.delete() }
    }

    private fun createSwapFile(sizeMb: Int): File {
        val swap = File(context.filesDir, "linuxify.swap")
        if (!swap.exists() || swap.length() != sizeMb * MB) swap.setLength(sizeMb * MB)
        return swap
    }

    private fun availableStorageMb(): Long = context.filesDir.usableSpace / MB

    private fun batteryPercent(): Int {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }

    companion object { private const val MB = 1024L * 1024L }
}

enum class GraphicsMode { SOFTWARE, HARDWARE }

data class OptimizationReport(
    val swapFile: File?,
    val cpuLimitPercent: Int,
    val graphicsMode: GraphicsMode,
    val dashboardEnabled: Boolean,
    val audioEnabled: Boolean,
    val warnings: List<String>
)
