package org.gradle.kotlin.dsl.resolver

import org.gradle.integtests.fixtures.RepoScriptBlockUtil.mavenCentralRepositoryDefinition
import org.gradle.integtests.fixtures.executer.IntegrationTestBuildContext
import org.gradle.kotlin.dsl.fixtures.AbstractKotlinIntegrationTest
import org.gradle.kotlin.dsl.resolver.internal.GradleDistRepoDescriptorLocator
import org.gradle.test.fixtures.dsl.GradleDsl
import org.gradle.test.fixtures.file.LeaksFileHandles
import org.gradle.test.fixtures.server.http.HttpServer
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.TestExecutionPreconditions
import org.gradle.util.GradleVersion
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.UUID

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
    @Requires(TestExecutionPreconditions.NotEmbeddedExecutor::class, reason = "srcDistribution is only available in forked mode")
    fun `can download source distribution`() {
        val gradleVersion = distribution.version
        val srcDistribution = srcDistributionOrSkip()

        withOwnGradleUserHomeDir("need fresh cache for artifact resolution") {
            val server = HttpServer()
            server.start()
            try {
                server.hostAndExpectRequestFor(gradleVersion, srcDistribution)

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

                build("-Dorg.gradle.kotlin.dsl.resolver.defaultGradleDistRepoBaseUrl=${server.uri}")
            } finally {
                server.stop()
                server.resetExpectations()
            }
        }
    }

    @Test
    @Requires(TestExecutionPreconditions.NotEmbeddedExecutor::class, reason = "srcDistribution is only available in forked mode")
    fun `can download source distribution when repositories are declared in settings`() {
        val gradleVersion = distribution.version
        val srcDistribution = srcDistributionOrSkip()

        withOwnGradleUserHomeDir("need fresh cache for artifact resolution") {
            val server = HttpServer()
            server.start()
            try {
                server.hostAndExpectRequestFor(gradleVersion, srcDistribution)

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

                build("-Dorg.gradle.kotlin.dsl.resolver.defaultGradleDistRepoBaseUrl=${server.uri}")
            } finally {
                server.stop()
                server.resetExpectations()
            }
        }
    }

    @Test
    fun `source distribution available everywhere in a complex build`() {
        val baseUrl = "https://my-host.org/my-path/distributions"
        withCustomGradleProperties("$baseUrl/gradle-9.4.0-bin.zip")
        withSettings(
            """
            rootProject.name = "root"
            include("subproject")
            includeBuild("sub-included-build")
            includeBuild("../flat-included-build")
            """.trimIndent()
        )

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

    @Test
    @LeaksFileHandles
    @Requires(TestExecutionPreconditions.NotEmbeddedExecutor::class, reason = "srcDistribution is only available in forked mode")
    fun `source distribution resolved from wrapper without touching the fallback server`() {
        val srcDistribution = srcDistributionOrSkip()
        val gradleVersion = distribution.version

        withOwnGradleUserHomeDir("need fresh cache for artifact resolution") {
            // Will play the role of the primary, custom Gradle repository, with a source distribution.
            val primaryServer = HttpServer()
            primaryServer.start()
            // Will play the role of `services.gradle.org` (overridden by the system property), should not be involved.
            val fallbackServer = HttpServer()
            fallbackServer.start()

            try {
                val repositoryName = gradleVersion.repositoryName

                withCustomGradleProperties("${primaryServer.uri}/$repositoryName/gradle-${gradleVersion.version}-bin.zip")

                // Primary: repo with the source artifact
                primaryServer.hostAndExpectRequestFor(gradleVersion, srcDistribution)

                withBuildScript(
                    """
                    require(${SourceDistributionResolver::class.qualifiedName}(project).sourceDirs().isNotEmpty()) {
                        "Expected source dirs to be resolved from the primary repository"
                    }
                    """
                )

                build("-Dorg.gradle.kotlin.dsl.resolver.defaultGradleDistRepoBaseUrl=${fallbackServer.uri}")
            } finally {
                primaryServer.stop()
                fallbackServer.stop()
                primaryServer.resetExpectations()
                fallbackServer.resetExpectations()
            }
        }
    }

    @Test
    @LeaksFileHandles
    @Requires(TestExecutionPreconditions.NotEmbeddedExecutor::class, reason = "srcDistribution is only available in forked mode")
    fun `source distribution resolved from fallback when custom repo does not have it`() {
        val srcDistribution = srcDistributionOrSkip()
        val gradleVersion = distribution.version

        withOwnGradleUserHomeDir("need fresh cache for artifact resolution") {
            // Will play the role of the primary, custom Gradle repository, but with a missing source distribution.
            val primaryServer = HttpServer()
            primaryServer.start()
            // Will play the role of `services.gradle.org` (overridden by the system property), with a source distribution.
            val fallbackServer = HttpServer()
            fallbackServer.start()

            try {
                val repositoryName = gradleVersion.repositoryName

                withCustomGradleProperties("${primaryServer.uri}/$repositoryName/gradle-${gradleVersion.version}-bin.zip")

                // Primary: empty repo — source artifact is missing
                primaryServer.expectGetMissing("/$repositoryName/")

                // Fallback: repo with the source artifact
                fallbackServer.hostAndExpectRequestFor(gradleVersion, srcDistribution)

                withBuildScript(
                    """
                    require(${SourceDistributionResolver::class.qualifiedName}(project).sourceDirs().isNotEmpty()) {
                        "Expected source dirs to be resolved from the fallback repository"
                    }
                    """
                )

                build("-Dorg.gradle.kotlin.dsl.resolver.defaultGradleDistRepoBaseUrl=${fallbackServer.uri}")
            } finally {
                primaryServer.stop()
                fallbackServer.stop()
                primaryServer.resetExpectations()
                fallbackServer.resetExpectations()
            }
        }
    }

    @Test
    @LeaksFileHandles
    @Requires(TestExecutionPreconditions.NotEmbeddedExecutor::class, reason = "srcDistribution is only available in forked mode")
    fun `source distribution resolved from local file when src zip is next to bin zip`() {
        val srcDistribution = srcDistributionOrSkip()
        val binDistribution = binDistributionOrSkip()
        val gradleVersion = distribution.version.version

        withOwnGradleUserHomeDir("need fresh cache for artifact resolution") {
            file("local-dist").also { it.mkdirs() }
            binDistribution.copyTo(file("local-dist/gradle-${gradleVersion}-bin.zip"))
            srcDistribution.copyTo(file("local-dist/gradle-${gradleVersion}-src.zip"))
            withCustomGradleProperties(file("local-dist/gradle-${gradleVersion}-bin.zip").toURI().toASCIIString())

            withBuildScript(
                """
                require(${SourceDistributionResolver::class.qualifiedName}(project).sourceDirs().isNotEmpty()) {
                    "Expected source dirs to be resolved from the local file distribution"
                }
                """
            )

            build()
        }
    }

    @Test
    @LeaksFileHandles
    @Requires(TestExecutionPreconditions.NotEmbeddedExecutor::class, reason = "srcDistribution is only available in forked mode")
    fun `source distribution resolved from fallback when wrapper uses a local file distribution without src zip`() {
        val srcDistribution = srcDistributionOrSkip()
        val binDistribution = binDistributionOrSkip()
        val gradleVersion = distribution.version

        withOwnGradleUserHomeDir("need fresh cache for artifact resolution") {
            val fallbackServer = HttpServer()
            fallbackServer.start()

            try {
                // Local file:// distribution without a src zip next to it
                file("local-dist").also { it.mkdirs() }
                binDistribution.copyTo(file("local-dist/gradle-${gradleVersion.version}-bin.zip"))
                withCustomGradleProperties(file("local-dist/gradle-${gradleVersion.version}-bin.zip").toURI().toASCIIString())

                // Fallback: repo with the source artifact
                fallbackServer.hostAndExpectRequestFor(gradleVersion, srcDistribution)

                withBuildScript(
                    """
                    require(${SourceDistributionResolver::class.qualifiedName}(project).sourceDirs().isNotEmpty()) {
                        "Expected source dirs to be resolved from the fallback repository"
                    }
                    """
                )

                build("-Dorg.gradle.kotlin.dsl.resolver.defaultGradleDistRepoBaseUrl=${fallbackServer.uri}")
            } finally {
                fallbackServer.stop()
                fallbackServer.resetExpectations()
            }
        }
    }

    @Test
    @LeaksFileHandles
    @Requires(TestExecutionPreconditions.NotEmbeddedExecutor::class, reason = "srcDistribution is only available in forked mode")
    fun `reasonable log messages when failing to resolve from both repositories`() {
        val gradleVersion = distribution.version
        val repositoryName = gradleVersion.repositoryName

        withOwnGradleUserHomeDir("need fresh cache for artifact resolution") {
            val primaryServer = HttpServer()
            primaryServer.start()
            val fallbackServer = HttpServer()
            fallbackServer.start()

            val fallbackRepoOverride = "-Dorg.gradle.kotlin.dsl.resolver.defaultGradleDistRepoBaseUrl=${fallbackServer.uri}"
            val primaryBaseUrl = primaryServer.uri
            val fallbackBaseUrl = fallbackServer.uri

            try {
                withCustomGradleProperties("${primaryBaseUrl}/$repositoryName/gradle-${gradleVersion.version}-bin.zip")

                withBuildScript(
                    """
                    require(${SourceDistributionResolver::class.qualifiedName}(project).sourceDirs().isEmpty()) {
                        "Did not expect source dirs to be resolved"
                    }
                    """
                )

                primaryServer.expectGetMissing("/${repositoryName}/")
                fallbackServer.expectGetMissing("/${repositoryName}/")

                build(fallbackRepoOverride).apply {
                    assertOutputContains("Could not resolve Gradle distribution sources. See debug logs for details.")
                }

                primaryServer.resetExpectations()
                fallbackServer.resetExpectations()

                primaryServer.stop()
                fallbackServer.stop()

                executer.withStackTraceChecksDisabled()

                build(fallbackRepoOverride, "--no-configuration-cache", "--debug").apply {
                    assertOutputContains("Could not resolve Gradle distribution sources. See debug logs for details.")
                    assertOutputContains(
                        """
                        org.gradle.api.GradleException: Unable to resolve Gradle distribution sources, tried:
                          - $primaryBaseUrl/$repositoryName
                          - $fallbackBaseUrl/$repositoryName
                        """.trimIndent()
                    )
                }
            } finally {
                primaryServer.stop()
                fallbackServer.stop()
            }
        }
    }

    private fun HttpServer.hostAndExpectRequestFor(gradleVersion: GradleVersion, srcDistribution: File) {
        val rand = UUID.randomUUID()
        val repositoryName = gradleVersion.repositoryName
        val artifactFileName = gradleVersion.artifactFileName
        val repoDir = file("$rand/${repositoryName}").also { it.mkdirs() }
        srcDistribution.copyTo(repoDir.resolve(artifactFileName))
        expectGetDirectoryListing("/${repositoryName}/", repoDir)
        expectHead("/$repositoryName/$artifactFileName", srcDistribution)
        expectGet("/$repositoryName/$artifactFileName", srcDistribution)
    }

    private val GradleVersion.repositoryName: String
        get() = if (isSnapshot) "distributions-snapshots" else "distributions"

    private val GradleVersion.artifactFileName: String
        get() = "gradle-${version}-src.zip"

    private fun srcDistributionOrSkip(): File {
        val srcDistribution = IntegrationTestBuildContext.INSTANCE.srcDistribution
        assumeTrue("srcDistribution is not available for this test execution.", srcDistribution != null)
        return srcDistribution!!
    }

    private fun binDistributionOrSkip(): File {
        val binDistribution = IntegrationTestBuildContext.INSTANCE.binDistribution
        assumeTrue("binDistribution is not available for this test execution.", binDistribution != null)
        return binDistribution!!
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
            val gradleDistRepository = $${queryGradleDistRepository()}!!
            require(gradleDistRepository.repoBaseUrl == uri("$$expectedUrl")) {
                "Unexpected repoBaseUrl in: ${gradleDistRepository}"
            }
            require(gradleDistRepository.artifactPattern == "[module]-[revision](-[classifier])(.[ext])") {
                "Unexpected artifactPattern in: ${gradleDistRepository}"
            }
        """.trimIndent()
}

private fun queryGradleDistRepository() =
    "${GradleDistRepoDescriptorLocator::class.qualifiedName}(project).primaryRepository"
