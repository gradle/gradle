package integration

import org.gradle.api.Project

fun Project.sampleDirs() =
    samplesDir().listFiles().filter { it.isDirectory }

fun Project.samplesDir() = file("samples")
