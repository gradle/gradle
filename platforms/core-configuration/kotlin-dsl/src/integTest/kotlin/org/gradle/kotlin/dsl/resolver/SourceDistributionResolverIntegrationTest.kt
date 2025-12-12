package org.gradle.kotlin.dsl.resolver

import org.gradle.integtests.fixtures.RepoScriptBlockUtil.mavenCentralRepositoryDefinition
import org.gradle.kotlin.dsl.fixtures.AbstractKotlinIntegrationTest
import org.gradle.kotlin.dsl.resolver.internal.GradleDistRepoDescriptorLocator
import org.gradle.test.fixtures.dsl.GradleDsl

import org.junit.Test


class SourceDistributionResolverIntegrationTest : AbstractKotlinIntegrationTest() {

    private fun withCustomGradleProperties(distributionUrl: String) {
        withFile("fake-root/gradle/wrapper/gradle-wrapper.properties", """
        distributionUrl=${distributionUrl}
        """)
    }

    private fun queryGradleDistRepository() =
        "${GradleDistRepoDescriptorLocator::class.qualifiedName}(project, explicitRootProjectDir = file(\"fake-root\")).gradleDistRepository"

    private fun testStandardCustomRepoLayout(distributionFileName: String) {
        withCustomGradleProperties("https://my-host.org/my-path/distributions/$distributionFileName")

        withBuildScript(
            $$"""
            val gradleDistRepository = $${queryGradleDistRepository()}
            require(gradleDistRepository.repoBaseUrl == "https://my-host.org/my-path/distributions") {
                "Unexpected repoBaseUrl in: ${gradleDistRepository}"
            }
            require(gradleDistRepository.artifactPattern == "[module]-[revision](-[classifier])(.[ext])") {
                "Unexpected artifactPattern in: ${gradleDistRepository}"
            }
            """
        )

        build()
    }

    @Test
    fun `test standard layout custom repository release bin`() {
        testStandardCustomRepoLayout("gradle-9.4.0-bin.zip")
    }

    @Test
    fun `test standard layout custom repository release all`() {
        testStandardCustomRepoLayout("gradle-9.4.0-all.zip")
    }

    @Test
    fun `test standard layout custom repository release bin old`() {
        testStandardCustomRepoLayout("gradle-8.14-bin.zip")
    }

    @Test
    fun `test standard layout custom repository rc bin`() {
        testStandardCustomRepoLayout("gradle-9.4.0-rc-1-bin.zip")
    }

    @Test
    fun `test standard layout custom repository milestone bin`() {
        testStandardCustomRepoLayout("gradle-9.4.0-milestone-3-bin.zip")
    }

    @Test
    fun `test standard layout custom repository snapshot bin`() {
        testStandardCustomRepoLayout("gradle-9.4.0-20251207001741+0000-bin.zip")
    }

    @Test
    fun `test explicit custom url repository`() {
        withBuildScript(
            $$"""
            val gradleDistRepository = $${queryGradleDistRepository()}
            require(gradleDistRepository.repoBaseUrl == "https://my-custom-host2:9876/custom-path/deep-path") {
                "Unexpected repoBaseUrl in: ${gradleDistRepository}"
            }
            require(gradleDistRepository.artifactPattern == "[module]-[revision](-[classifier])(.[ext])") {
                "Unexpected artifactPattern in: ${gradleDistRepository}"
            }
            require(gradleDistRepository.credentials == null) {
                "Expected no credentials in ${gradleDistRepository}"
            }
            """
        )

        build("-Porg.gradle.distributions.source.repository.url=https://my-custom-host2:9876/custom-path/deep-path")
    }

    @Test
    fun `test explicit custom url repository with credentials in url`() {
        withBuildScript(
            $$"""
            val gradleDistRepository = $${queryGradleDistRepository()}
            require(gradleDistRepository.repoBaseUrl == "https://my_user:my_pass@my-custom-host2/custom-path/deep-path") {
                "Unexpected repoBaseUrl in: ${gradleDistRepository}"
            }
            require(gradleDistRepository.artifactPattern == "[module]-[revision](-[classifier])(.[ext])") {
                "Unexpected artifactPattern in: ${gradleDistRepository}"
            }
            require(gradleDistRepository.credentials?.username == "my_user") {
                "Unexpected username in ${gradleDistRepository}"
            }
            require(gradleDistRepository.credentials?.password == "my_pass") {
                "Unexpected password in ${gradleDistRepository}"
            }
            """
        )

        build("-Porg.gradle.distributions.source.repository.url=https://my_user:my_pass@my-custom-host2/custom-path/deep-path")
    }

    @Test
    fun `test fully customized repository`() {
        withBuildScript(
            $$"""
            val gradleDistRepository = $${queryGradleDistRepository()}
            require(gradleDistRepository.repoBaseUrl == "https://my-custom-host2/custom-path/deep-path") {
                "Unexpected repoBaseUrl in: ${gradleDistRepository}"
            }
            require(gradleDistRepository.artifactPattern == "company-[module]-[revision]-mod-[classifier].[ext]") {
                "Unexpected artifactPattern in: ${gradleDistRepository}"
            }
            require(gradleDistRepository.credentials?.username == "my_gradle_user") {
                "Unexpected username in ${gradleDistRepository}"
            }
            require(gradleDistRepository.credentials?.password == "my_gradle_pass") {
                "Unexpected password in ${gradleDistRepository}"
            }
            """
        )

        build(
            "-Porg.gradle.distributions.source.repository.url=https://my-custom-host2/custom-path/deep-path",
            "-Porg.gradle.distributions.source.repository.ivyArtifactPattern=company-[module]-[revision]-mod-[classifier].[ext]",
            "-Porg.gradle.distributions.source.repository.credential.username=my_gradle_user",
            "-Porg.gradle.distributions.source.repository.credential.password=my_gradle_pass",
        )
    }

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
