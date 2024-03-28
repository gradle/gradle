package org.gradle.client.logic.gradle

import kotlinx.serialization.Serializable

@Serializable
data class GradleConnectionParameters(
    val rootDir: String,
    val javaHome: String,
    val distribution: GradleDistribution,
)
