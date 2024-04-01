package org.gradle.client.core.files

import org.gradle.client.core.util.DesktopOS
import org.gradle.client.core.util.currentDesktopOS
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import java.io.File

interface AppDirs {

    val configurationDirectory: File
    val dataDirectory: File
    val logDirectory: File
    val cacheDirectory: File
    val temporaryDirectory: File

    fun logApplicationDirectories(level: Level = Level.INFO) {
        LoggerFactory.getLogger(AppDirs::class.java).atLevel(level).log {
            buildString {
                appendLine("Resolved application directories:")
                appendLine("      configuration = $configurationDirectory")
                appendLine("               data = $dataDirectory")
                appendLine("                log = $logDirectory")
                appendLine("              cache = $cacheDirectory")
                append("          temporary = $temporaryDirectory")
            }
        }
    }
}

class RealAppDirs(
    private val appName: String,
    private val installationIdentifier: String,
) : AppDirs {

    override val configurationDirectory: File by lazy {
        persistentDir("conf")
    }

    override val dataDirectory: File by lazy {
        persistentDir("data")
    }

    override val logDirectory: File by lazy {
        transientDir("log")
    }

    override val cacheDirectory: File by lazy {
        transientDir("cache")
    }

    override val temporaryDirectory: File by lazy {
        transientDir("tmp")
    }

    private val userDir: File =
        File((System.getenv("USERPROFILE") ?: System.getProperty("user.home")))

    private fun persistentDir(dirName: String): File =
        when (currentDesktopOS) {
            DesktopOS.Linux -> userDir.resolve(".$appName")
                .resolve(installationIdentifier)
                .resolve(dirName)

            DesktopOS.Mac -> userDir.resolve("Library")
                .resolve("Application Support")
                .resolve(appName)
                .resolve(installationIdentifier)
                .resolve(dirName)

            DesktopOS.Windows -> userDir.resolve("AppData")
                .resolve("Roaming")
                .resolve(appName)
                .resolve(installationIdentifier)
                .resolve(dirName)
        }.probed()

    private fun transientDir(dirName: String): File =
        when (currentDesktopOS) {
            DesktopOS.Linux -> userDir.resolve(".cache")
                .resolve(appName)
                .resolve(installationIdentifier)
                .resolve(dirName)

            DesktopOS.Mac -> userDir.resolve("Library")
                .resolve("Caches")
                .resolve(appName)
                .resolve(installationIdentifier)
                .resolve(dirName)

            DesktopOS.Windows -> userDir.resolve("AppData")
                .resolve("LocalLow")
                .resolve(appName)
                .resolve(installationIdentifier)
                .resolve(dirName)
        }.probed()

    private fun File.probed(): File =
        this.also { dir ->
            if (!dir.isDirectory) {
                require(dir.mkdirs()) { "Unable to create directory '$dir'" }
            }
            val probe = dir.resolve("write.probe")
            val ack = "ACK"
            probe.writeText(ack)
            require(probe.readText() == ack) { "Unable to probe '$dir' for write permissions" }
            require(probe.delete()) { "Unable to probe '$dir' for write permissions" }
        }
}
