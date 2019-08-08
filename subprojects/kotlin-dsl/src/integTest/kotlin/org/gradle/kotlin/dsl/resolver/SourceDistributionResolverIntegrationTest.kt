package org.gradle.kotlin.dsl.resolver

import org.gradle.kotlin.dsl.fixtures.AbstractKotlinIntegrationTest

import org.gradle.util.GradleVersion

import org.junit.Test


class SourceDistributionResolverIntegrationTest : AbstractKotlinIntegrationTest() {

    @Test
    fun `can download source distribution`() {

        withBuildScript("""

            val resolver = ${SourceDistributionResolver::class.qualifiedName}(project).apply {
                minimumVersion = ${minimumGradleVersionToAccept()}
            }
            val sourceDirs = resolver.sourceDirs()
            require(sourceDirs.isNotEmpty()) {
                "Expected source directories but got none"
            }

        """)

        build()
    }

    private
    fun minimumGradleVersionToAccept(): String? {
        val (major, minor) = GradleVersion.current().baseVersion.version.split('.')
        return when (minor) {
            "0" -> {
                // When testing against a `major.0` version we need to take into account
                // that source distributions matching the major version might not have
                // been published yet. In that case we adjust the test to also resolve
                // source distributions from the previous major version.
                "\"${Integer.valueOf(major) - 1}.0\""
            }
            else -> null // no need to adjust the minimum version
        }
    }
}
