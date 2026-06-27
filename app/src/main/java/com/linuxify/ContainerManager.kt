package com.linuxify

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicReference

class ContainerManager(private val context: Context) {
    private val installer = RuntimeAssetInstaller(context)
    private val processRef = AtomicReference<Process?>()

    var state: ContainerState = ContainerState.READY
        private set

    suspend fun start(profile: DeviceProfile): Result<ContainerSession> = withContext(Dispatchers.IO) {
        runCatching {
            if (processRef.get()?.isAlive == true) return@runCatching currentSession(profile, "Already running")
            state = ContainerState.CONNECTING
            val optimization = PerformanceManager(context, profile).prepare()
            val install = installer.install(profile)
            val command = buildCommand(profile, install, optimization)
            val process = command?.let { ProcessBuilder(it).directory(installer.installDir).redirectErrorStream(true).start() }
            if (process != null) processRef.set(process)
            state = ContainerState.CONNECTED
            ContainerSession(
                profile = profile,
                optimization = optimization,
                rootfs = install.rootfs,
                command = command.orEmpty(),
                vncUrl = if (profile.recommendedMode == LinuxMode.TERMINAL) null else "http://127.0.0.1:6080/vnc.html?autoconnect=true&resize=scale",
                message = if (command == null) missingAssetsMessage(profile) else "Linux started with ${profile.containerType} in ${profile.recommendedMode.label} mode."
            )
        }.onFailure { state = ContainerState.ERROR }
    }

    suspend fun stop(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            state = ContainerState.DISCONNECTING
            processRef.getAndSet(null)?.destroy()
            PerformanceManager(context, DeviceDetector.detect(context)).trimCache()
            state = ContainerState.READY
        }.onFailure { state = ContainerState.ERROR }
    }

    fun isRunning(): Boolean = processRef.get()?.isAlive == true

    private fun buildCommand(profile: DeviceProfile, install: InstallReport, optimization: OptimizationReport): List<String>? {
        if (profile.containerType == ContainerType.DROIDSPACES) {
            val droidspaces = File(installer.installDir, "bin/droidspaces")
            if (droidspaces.canExecute() && install.rootfs != null) return listOf(droidspaces.absolutePath, "--rootfs", install.rootfs.absolutePath, "--mode", profile.recommendedMode.name.lowercase())
        }

        val proot = installer.prootBinary(profile) ?: return null
        val rootfs = install.rootfs?.takeIf { it.isDirectory } ?: return null
        val launchScript = writeLaunchScript(profile, optimization)
        return listOf(
            proot.absolutePath,
            "--link2symlink",
            "-0",
            "-r", rootfs.absolutePath,
            "-b", "/dev",
            "-b", "/proc",
            "-b", "/sys",
            "-b", context.filesDir.absolutePath + ":/linuxify",
            "/bin/sh",
            launchScript.absolutePath
        )
    }

    private fun writeLaunchScript(profile: DeviceProfile, optimization: OptimizationReport): File {
        val script = File(installer.installDir, "launch-linuxify.sh")
        val desktop = profile.recommendedMode.desktopCommand
        val body = buildString {
            appendLine("#!/bin/sh")
            appendLine("export HOME=/root")
            appendLine("export USER=root")
            appendLine("export DISPLAY=:1")
            appendLine("export LIBGL_ALWAYS_SOFTWARE=${if (optimization.graphicsMode == GraphicsMode.SOFTWARE) "1" else "0"}")
            appendLine("mkdir -p /tmp /root/.cache")
            if (profile.recommendedMode == LinuxMode.TERMINAL) appendLine("exec /bin/sh") else {
                appendLine("(vncserver :1 -geometry 1280x720 -depth 16 >/tmp/vnc.log 2>&1 || true) &")
                appendLine("(websockify --web=/usr/share/novnc 6080 localhost:5901 >/tmp/novnc.log 2>&1 || true) &")
                appendLine("exec ${desktop.ifEmpty { "startlxde" }}")
            }
        }
        script.writeText(body)
        script.setExecutable(true, true)
        return script
    }

    private fun currentSession(profile: DeviceProfile, message: String): ContainerSession {
        val optimization = PerformanceManager(context, profile).prepare()
        return ContainerSession(profile, optimization, null, emptyList(), "http://127.0.0.1:6080/vnc.html?autoconnect=true&resize=scale", message)
    }

    private fun missingAssetsMessage(profile: DeviceProfile): String = "Runtime assets missing for ${profile.supportedAbis.joinToString()}. Add PRoot/Droidspaces binaries and ${profile.recommendedMode.name.lowercase()} rootfs under assets for a real Linux boot."
}

enum class ContainerState { READY, CONNECTING, CONNECTED, DISCONNECTING, ERROR }

data class ContainerSession(
    val profile: DeviceProfile,
    val optimization: OptimizationReport,
    val rootfs: File?,
    val command: List<String>,
    val vncUrl: String?,
    val message: String
)
