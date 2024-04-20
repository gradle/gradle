package org.gradle.client.core.database

import org.gradle.client.core.gradle.GradleDistribution
import org.gradle.client.core.util.generateIdentity
import java.io.File

data class Build(
    val id: String = generateIdentity(),
    val rootDir: File,
    val javaHomeDir: File? = null,
    val gradleUserHomeDir: File? = null,
    val gradleDistribution: GradleDistribution = GradleDistribution.Default,
)
