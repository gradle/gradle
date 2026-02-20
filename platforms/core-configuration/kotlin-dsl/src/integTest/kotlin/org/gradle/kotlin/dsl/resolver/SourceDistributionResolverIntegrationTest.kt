package org.gradle.kotlin.dsl.resolver

import org.gradle.integtests.fixtures.RepoScriptBlockUtil.mavenCentralRepositoryDefinition
import org.gradle.kotlin.dsl.fixtures.AbstractKotlinIntegrationTest
import org.gradle.kotlin.dsl.resolver.internal.GradleDistRepoDescriptorLocator
import org.gradle.test.fixtures.dsl.GradleDsl
import org.junit.Test

class SourceDistributionResolverIntegrationTest : AbstractKotlinIntegrationTest() {

    @Test
    fun `standard layout custom repository release bin`() {
        testStandardCustomRepoLayout("gradle-9.4.0-bin.zip")
    }

    @Test
    fun `standard layout custom repository release all`() {
        testStandardCustomRepoLayout("gradle-9.4.0-all.zip")
    }

    @Test
    fun `standard layout custom repository release bin old`() {
        testStandardCustomRepoLayout("gradle-8.14-bin.zip")
    }

    @Test
    fun `standard layout custom repository rc bin`() {
        testStandardCustomRepoLayout("gradle-9.4.0-rc-1-bin.zip")
    }

    @Test
    fun `standard layout custom repository milestone bin`() {
        testStandardCustomRepoLayout("gradle-9.4.0-milestone-3-bin.zip")
    }

    @Test
    fun `standard layout custom repository snapshot bin`() {
        testStandardCustomRepoLayout("gradle-9.4.0-20251207001741+0000-bin.zip")
    }

    @Test
    fun `standard layout with credentials in url`() {
        withCustomGradleProperties("https://my_custom_user:my_custom_pass@my-host.org/my-path/custom-dists/gradle-9.4.0-bin.zip")

        withBuildScript(
            $$"""
            $${buildScriptAssertingGradleDistRepository("https://my-host.org/my-path/custom-dists")}
            $${verifyPasswordCredentials("my_custom_user", "my_custom_pass")}
            """
        )

        build()
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

    @Test
    fun `source distribution available everywhere in a complex build`() {
        val baseUrl = "https://my-host.org/my-path/distributions"
        withCustomGradleProperties("$baseUrl/gradle-9.4.0-bin.zip")
        withSettings("""
            rootProject.name = "root"
            include("subproject")
            includeBuild("sub-included-build")
            includeBuild("../flat-included-build")
        """.trimIndent())

        // rootProject
        withBuildScript(buildScriptAssertingGradleDistRepository(baseUrl))
        // subproject
        withFile("subproject/build.gradle.kts", buildScriptAssertingGradleDistRepository(baseUrl))
        // buildSrc
        withFile("buildSrc/build.gradle.kts", buildScriptAssertingGradleDistRepository(baseUrl))
        // included build
        withFile("sub-included-build/settings.gradle.kts", """rootProject.name = "included-build" """)
        withFile("sub-included-build/settings.gradle.kts", """rootProject.name = "included-build" """)
        withFile("../flat-included-build/settings.gradle.kts", """rootProject.name = "included-build" """)
        withFile("../flat-included-build/build.gradle.kts", buildScriptAssertingGradleDistRepository(baseUrl))

        build()
    }

    private fun testStandardCustomRepoLayout(distributionFileName: String) {
        val baseUrl = "https://my-host.org/my-path/distributions"
        withCustomGradleProperties("$baseUrl/$distributionFileName")
        withBuildScript(buildScriptAssertingGradleDistRepository(baseUrl))
        build()
    }

    private fun withCustomGradleProperties(distributionUrl: String) {
        withFile("gradle/wrapper/gradle-wrapper.properties", "distributionUrl=${distributionUrl}")
    }

    private fun verifyPasswordCredentials(expectedUser: String, expectedPassword: String): String {
        return $$"""
            var receivedUser: String? = null
            var receivedPassword: String? = null
            repositories {
                maven {
                    gradleDistRepository.credentialsApplier(this)
                    receivedUser = credentials.username
                    receivedPassword = credentials.password
                }
            }
            require(receivedUser == "$$expectedUser") {
                "Unexpected username ${receivedUser} in ${gradleDistRepository}"
            }
            require(receivedPassword == "$$expectedPassword") {
                "Unexpected password ${receivedPassword} in ${gradleDistRepository}"
            }
        """.trimIndent()
    }

    private fun buildScriptAssertingGradleDistRepository(expectedUrl: String) =
        $$"""
            val gradleDistRepository = $${queryGradleDistRepository()}
            require(gradleDistRepository.repoBaseUrl == uri("$$expectedUrl")) {
                "Unexpected repoBaseUrl in: ${gradleDistRepository}"
            }
            require(gradleDistRepository.artifactPattern == "[module]-[revision](-[classifier])(.[ext])") {
                "Unexpected artifactPattern in: ${gradleDistRepository}"
            }
        """.trimIndent()
}

private fun queryGradleDistRepository() =
    "${GradleDistRepoDescriptorLocator::class.qualifiedName}(project).gradleDistRepository"
