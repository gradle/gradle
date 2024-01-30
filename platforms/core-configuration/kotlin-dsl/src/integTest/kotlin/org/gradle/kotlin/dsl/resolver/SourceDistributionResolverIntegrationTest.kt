package org.gradle.kotlin.dsl.resolver

import org.gradle.integtests.fixtures.RepoScriptBlockUtil.mavenCentralRepositoryDefinition
import org.gradle.kotlin.dsl.fixtures.AbstractKotlinIntegrationTest
import org.gradle.test.fixtures.dsl.GradleDsl

import org.junit.Test


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

            val subprojectSourcePath = "org/gradle/StartParameter.java"
            val subprojectFound = sourceDirs.find { it.resolve(subprojectSourcePath).isFile }
            require(subprojectFound != null) {
                "Source directories do not contain subproject file '${'$'}subprojectSourcePath'. Searched in:\n  " +
                    sourceDirs.joinToString("  \n")
            }

            val platformSourcePath = "org/gradle/api/Action.java"
            val platformFound = sourceDirs.find { it.resolve(platformSourcePath).isFile }
            require(platformFound != null) {
                "Source directories do not contain platform file '${'$'}platformSourcePath'. Searched in:\n  " +
                    sourceDirs.joinToString("\n  ")
            }

            """
        )

        build()
    }

    @Test
    fun `can download source distribution when repositories are declared in settings`() {

        withDefaultSettings().appendText(
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
