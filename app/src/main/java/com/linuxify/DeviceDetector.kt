package com.linuxify

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import java.io.File
import kotlin.math.max

data class DeviceProfile(
    val ramMb: Long,
    val availableRamMb: Long,
    val cpuCores: Int,
    val androidVersion: Int,
    val rooted: Boolean,
    val lowRamDevice: Boolean,
    val supportedAbis: List<String>
) {
    val recommendedMode: LinuxMode = when {
        ramMb < 1024L || lowRamDevice -> LinuxMode.TERMINAL
        ramMb < 2048L -> LinuxMode.LXDE
        ramMb < 4096L -> LinuxMode.XFCE
        else -> LinuxMode.GNOME
    }

    val containerType: ContainerType = when {
        rooted && ramMb >= 4096L -> ContainerType.DROIDSPACES
        else -> ContainerType.PROOT
    }

    val performanceTier: PerformanceTier = when (recommendedMode) {
        LinuxMode.TERMINAL -> PerformanceTier.VERY_LOW
        LinuxMode.LXDE -> PerformanceTier.LOW
        LinuxMode.XFCE -> PerformanceTier.MEDIUM
        LinuxMode.GNOME -> if (rooted) PerformanceTier.VERY_HIGH else PerformanceTier.HIGH
    }

    fun summary(): String = "RAM: ${ramMb}MB | Free: ${availableRamMb}MB | CPU: $cpuCores cores | Android: $androidVersion | Mode: ${recommendedMode.label}"
}

enum class LinuxMode(val label: String, val desktopCommand: String, val minRamMb: Int) {
    TERMINAL("Terminal", "", 512),
    LXDE("Light/LXDE", "startlxde", 1024),
    XFCE("Medium/XFCE", "startxfce4", 2048),
    GNOME("Full/GNOME", "gnome-session", 4096)
}

enum class ContainerType { PROOT, DROIDSPACES }
enum class PerformanceTier { VERY_LOW, LOW, MEDIUM, HIGH, VERY_HIGH }

object DeviceDetector {
    fun detect(context: Context): DeviceProfile {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)

        return DeviceProfile(
            ramMb = memoryInfo.totalMem / MB,
            availableRamMb = memoryInfo.availMem / MB,
            cpuCores = max(1, Runtime.getRuntime().availableProcessors()),
            androidVersion = Build.VERSION.SDK_INT,
            rooted = isRooted(),
            lowRamDevice = activityManager.isLowRamDevice,
            supportedAbis = Build.SUPPORTED_ABIS.toList()
        )
    }

    private fun isRooted(): Boolean {
        val suPaths = listOf("/system/bin/su", "/system/xbin/su", "/sbin/su", "/su/bin/su", "/magisk/.core/bin/su")
        return suPaths.any { File(it).canExecute() } || runCatching {
            ProcessBuilder("which", "su").redirectErrorStream(true).start().waitFor() == 0
        }.getOrDefault(false)
    }

    private const val MB = 1024L * 1024L
}
