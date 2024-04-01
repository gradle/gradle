package org.gradle.client.ui.fixtures

import org.gradle.client.logic.files.AppDirs
import java.io.File

class TestAppDirs(
    private val rootDir: File
) : AppDirs {

    override val configurationDirectory = sub("config")
    override val dataDirectory = sub("data")
    override val logDirectory = sub("log")
    override val cacheDirectory = sub("cache")
    override val temporaryDirectory = sub("tmp")

    private fun sub(name: String): File =
        rootDir.resolve(name).also { it.mkdirs() }
}