package org.gradle.client.core.gradle

import kotlinx.serialization.Serializable

@Serializable
sealed interface GradleDistribution {

    @Serializable
    data object Default : GradleDistribution

    @Serializable
    data class Version(val version: String) : GradleDistribution

    @Serializable
    data class Local(val installDir: String) : GradleDistribution
}
