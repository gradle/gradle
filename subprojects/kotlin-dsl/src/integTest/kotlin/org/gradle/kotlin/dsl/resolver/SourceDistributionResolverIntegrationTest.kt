package org.gradle.kotlin.dsl.resolver

import org.gradle.kotlin.dsl.fixtures.AbstractKotlinIntegrationTest

import org.junit.Test


class SourceDistributionResolverIntegrationTest : AbstractKotlinIntegrationTest() {

    @Test
    fun `can download source distribution`() {

        withBuildScript("""

            val resolver = ${SourceDistributionResolver::class.qualifiedName}(project)
            val sourceDirs = resolver.sourceDirs()
            require(sourceDirs.isNotEmpty()) {
                "Expected source directories but got none"
            }

        """)

        build()
    }
}
