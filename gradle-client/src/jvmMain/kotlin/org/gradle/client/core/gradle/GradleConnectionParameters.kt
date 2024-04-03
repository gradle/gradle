package org.gradle.client.core.gradle

import kotlinx.serialization.Serializable

@Serializable
data class GradleConnectionParameters(
    val rootDir: String,
    val javaHomeDir: String?,
    val gradleUserHomeDir: String?,
    val distribution: GradleDistribution,
)
