package org.gradle.client.logic.files

import org.gradle.client.logic.util.DesktopOS
import org.gradle.client.logic.util.currentDesktopOS
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import java.io.File

class AppDirs(
    private val appName: String,
    private val installationIdentifier: String,
) {

    val configurationDirectory: File by lazy {
        persistentDir("conf")
    }

    val dataDirectory: File by lazy {
        persistentDir("data")
    }

    val logDirectory: File by lazy {
        transientDir("log")
    }

    val cacheDirectory: File by lazy {
        transientDir("cache")
    }

    val temporaryDirectory: File by lazy {
        transientDir("tmp")
    }

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
