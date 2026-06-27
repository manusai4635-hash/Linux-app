package com.linuxify

import android.content.Context
import java.io.File
import java.util.zip.GZIPInputStream

/**
 * Installs bundled production assets when present. The app is designed to work with
 * real PRoot/rootfs/noVNC payloads packaged in assets/runtime and assets/linux_images.
 */
class RuntimeAssetInstaller(private val context: Context) {
    val installDir: File = File(context.filesDir, "linux-runtime")
    val imageDir: File = File(context.getExternalFilesDir(null), "linux_images")

    fun install(profile: DeviceProfile): InstallReport {
        installDir.mkdirs()
        imageDir.mkdirs()
        val copied = mutableListOf<String>()
        copied += copyAssetDirectory("runtime/common", installDir)
        copied += copyFirstAbiRuntime(profile)
        val rootfs = installRootfs(profile.recommendedMode)
        makeExecutables(installDir)
        return InstallReport(rootfs = rootfs, runtimeDir = installDir, copiedAssets = copied)
    }

    fun prootBinary(profile: DeviceProfile): File? = profile.supportedAbis.asSequence()
        .map { File(installDir, "bin/$it/proot") }
        .firstOrNull { it.canExecute() }
        ?: File(installDir, "bin/proot").takeIf { it.canExecute() }

    fun busyboxBinary(profile: DeviceProfile): File? = profile.supportedAbis.asSequence()
        .map { File(installDir, "bin/$it/busybox") }
        .firstOrNull { it.canExecute() }
        ?: File(installDir, "bin/busybox").takeIf { it.canExecute() }

    private fun copyFirstAbiRuntime(profile: DeviceProfile): List<String> {
        val copied = mutableListOf<String>()
        for (abi in profile.supportedAbis) {
            copied += copyAssetDirectory("runtime/$abi", File(installDir, "bin/$abi"))
        }
        return copied
    }

    private fun installRootfs(mode: LinuxMode): File? {
        val candidates = buildList {
            add("linux_images/${mode.name.lowercase()}.tar.gz")
            add("linux_images/${mode.name.lowercase()}.img")
            if (mode != LinuxMode.TERMINAL) add("linux_images/terminal.tar.gz")
        }
        for (asset in candidates) {
            if (!assetExists(asset)) continue
            if (asset.endsWith(".tar.gz")) {
                val rootDir = File(imageDir, asset.substringAfterLast('/').removeSuffix(".tar.gz"))
                if (!File(rootDir, ".linuxify_extracted").exists()) {
                    rootDir.mkdirs()
                    context.assets.open(asset).use { input -> extractTarGz(input, rootDir) }
                    File(rootDir, ".linuxify_extracted").writeText(System.currentTimeMillis().toString())
                }
                return rootDir
            }
            val target = File(imageDir, asset.substringAfterLast('/'))
            if (!target.exists()) context.assets.open(asset).use { input -> target.outputStream().use(input::copyTo) }
            return target
        }
        return null
    }

    private fun extractTarGz(input: java.io.InputStream, targetDir: File) {
        GZIPInputStream(input).use { gzip ->
            val header = ByteArray(512)
            while (true) {
                val read = gzip.readFullyOrBreak(header)
                if (!read || header.all { it.toInt() == 0 }) break
                val name = header.copyOfRange(0, 100).toString(Charsets.UTF_8).trim('\u0000', ' ')
                val sizeText = header.copyOfRange(124, 136).toString(Charsets.UTF_8).trim('\u0000', ' ')
                val type = header[156].toInt().toChar()
                val size = sizeText.toLongOrNull(8) ?: 0L
                val output = File(targetDir, name).canonicalFile
                require(output.path.startsWith(targetDir.canonicalPath)) { "Unsafe tar path: $name" }
                when (type) {
                    '5' -> output.mkdirs()
                    else -> {
                        output.parentFile?.mkdirs()
                        output.outputStream().use { out -> gzip.copyExactly(out, size) }
                        if (name.endsWith(".sh") || name.endsWith("/proot") || name.endsWith("/busybox")) output.setExecutable(true, true)
                    }
                }
                val padding = (512 - (size % 512)) % 512
                if (padding > 0) gzip.skipFully(padding)
            }
        }
    }

    private fun copyAssetDirectory(assetPath: String, targetDir: File): List<String> {
        val names = runCatching { context.assets.list(assetPath)?.toList().orEmpty() }.getOrDefault(emptyList())
        if (names.isEmpty()) return emptyList()
        targetDir.mkdirs()
        val copied = mutableListOf<String>()
        for (name in names) {
            val childAsset = "$assetPath/$name"
            val childTarget = File(targetDir, name)
            val children = runCatching { context.assets.list(childAsset)?.toList().orEmpty() }.getOrDefault(emptyList())
            if (children.isNotEmpty()) copied += copyAssetDirectory(childAsset, childTarget) else {
                if (!childTarget.exists() || childTarget.length() == 0L) context.assets.open(childAsset).use { input -> childTarget.outputStream().use(input::copyTo) }
                copied += childTarget.absolutePath
            }
        }
        return copied
    }

    private fun makeExecutables(dir: File) {
        dir.walkTopDown().forEach { file ->
            if (file.isFile && (file.name in EXECUTABLE_NAMES || file.extension == "sh")) file.setExecutable(true, true)
        }
    }

    private fun assetExists(path: String): Boolean = runCatching { context.assets.open(path).close(); true }.getOrDefault(false)

    companion object { private val EXECUTABLE_NAMES = setOf("proot", "busybox", "pulseaudio", "websockify", "x11vnc", "vncserver", "droidspaces") }
}

data class InstallReport(val rootfs: File?, val runtimeDir: File, val copiedAssets: List<String>)

private fun java.io.InputStream.readFullyOrBreak(buffer: ByteArray): Boolean {
    var offset = 0
    while (offset < buffer.size) {
        val count = read(buffer, offset, buffer.size - offset)
        if (count == -1) return false
        offset += count
    }
    return true
}

private fun java.io.InputStream.copyExactly(out: java.io.OutputStream, bytes: Long) {
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var remaining = bytes
    while (remaining > 0) {
        val count = read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
        if (count == -1) error("Unexpected EOF in tar entry")
        out.write(buffer, 0, count)
        remaining -= count
    }
}

private fun java.io.InputStream.skipFully(bytes: Long) {
    var remaining = bytes
    while (remaining > 0) {
        val skipped = skip(remaining)
        if (skipped <= 0) {
            if (read() == -1) error("Unexpected EOF while skipping tar padding") else remaining--
        } else remaining -= skipped
    }
}
