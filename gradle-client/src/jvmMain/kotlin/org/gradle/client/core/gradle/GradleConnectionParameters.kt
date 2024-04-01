package org.gradle.client.core.gradle

import kotlinx.serialization.Serializable

@Serializable
data class GradleConnectionParameters(
    val rootDir: String,
    val javaHome: String,
    val distribution: GradleDistribution,
)
