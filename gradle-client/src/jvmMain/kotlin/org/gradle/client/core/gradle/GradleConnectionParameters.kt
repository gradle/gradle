package org.gradle.client.core.gradle

import kotlinx.serialization.Serializable
import java.io.File

@Serializable
data class GradleConnectionParameters(
    val rootDir: String,
    val javaHomeDir: String?,
    val gradleUserHomeDir: String?,
    val distribution: GradleDistribution,
) {
    companion object {

        fun isValidJavaHome(path: String): Boolean =
            (path.isBlank() && isEnvironmentValidJavaHome()) || isValidJavaHome(File(path))

        private fun isEnvironmentValidJavaHome(): Boolean =
            System.getenv("JAVA_HOME")?.takeIf { it.isNotBlank() }
                ?.let { isValidJavaHome(File(it)) }
                ?: false

        private fun isValidJavaHome(path: File): Boolean =
            path.isDirectory && path.resolve("bin").listFiles { file ->
                file.nameWithoutExtension == "java"
            }?.isNotEmpty() ?: false

        fun isValidGradleUserHome(path: String): Boolean =
            path.isBlank() || File(path).let { !it.exists() || it.isDirectory }

        fun isValidGradleVersion(version: String): Boolean =
            version.isNotBlank()

        fun isValidGradleInstallation(path: String): Boolean =
            path.isNotBlank() && File(path).resolve("bin").listFiles { file ->
                file.nameWithoutExtension == "gradle"
            }?.isNotEmpty() ?: false
    }
}
