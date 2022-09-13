package org.gradle.kotlin.dsl.resolver

import org.gradle.integtests.fixtures.RepoScriptBlockUtil.mavenCentralRepositoryDefinition
import org.gradle.kotlin.dsl.fixtures.AbstractKotlinIntegrationTest
import org.gradle.test.fixtures.dsl.GradleDsl
import org.junit.Ignore

import org.junit.Test


@Ignore("This test is trying to download a source ranging in (6.8, 6.9.2-snapshot], but we're behind the mainline too far so can't get any 6.9.2-snapshot")
class SourceDistributionResolverIntegrationTest : AbstractKotlinIntegrationTest() {

    @Test
    fun `can download source distribution`() {

        withBuildScript(
            """

            val sourceDirs =
                ${SourceDistributionResolver::class.qualifiedName}(project).run {
                    sourceDirs()
                }
            require(sourceDirs.isNotEmpty()) {
                "Expected source directories but got none"
            }

            """
        )

        build()
    }

    @Test
    fun `can download source distribution when repositories are declared in settings`() {

        withSettings(
            """
            dependencyResolutionManagement {
                repositories {
                    ${mavenCentralRepositoryDefinition(GradleDsl.KOTLIN)}
                }
                repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
            }
            """.trimIndent()
        )

        withBuildScript(
            """

            val sourceDirs =
                ${SourceDistributionResolver::class.qualifiedName}(project).run {
                    sourceDirs()
                }
            require(sourceDirs.isNotEmpty()) {
                "Expected source directories but got none"
            }

            """
        )

        build()
    }
}
