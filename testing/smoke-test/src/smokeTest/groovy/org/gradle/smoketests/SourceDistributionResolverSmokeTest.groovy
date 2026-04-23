/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.smoketests

import org.gradle.integtests.fixtures.executer.IntegrationTestBuildContext
import org.gradle.test.fixtures.file.LeaksFileHandles
import org.gradle.test.fixtures.server.http.HttpServer
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.TestExecutionPreconditions

class SourceDistributionResolverSmokeTest extends AbstractSmokeTest {

    @LeaksFileHandles
    @Requires(TestExecutionPreconditions.NotEmbeddedExecutor)
    def "source distribution resolved from fallback when custom repo does not have it"() {
        given:
        def buildContext = IntegrationTestBuildContext.INSTANCE
        assert buildContext.srcDistribution != null: "src distribution must exist to run this test."
        def gradleVersion = buildContext.version
        def artifactFileName = "gradle-${gradleVersion.version}-src.zip"
        def repositoryName = gradleVersion.snapshot ? "distributions-snapshots" : "distributions"

        def primaryServer = new HttpServer()
        primaryServer.start()
        def fallbackServer = new HttpServer()
        fallbackServer.start()

        withWrapperDistributionUrl("${primaryServer.uri}/$repositoryName/gradle-${gradleVersion.version}-bin.zip")
        primaryServer.expectGetMissing("/$repositoryName/")

        def fallbackDir = file("fallback-repo/$repositoryName")
        fallbackDir.mkdirs()
        buildContext.srcDistribution.copyTo(file("fallback-repo/$repositoryName/$artifactFileName"))
        fallbackServer.expectGetDirectoryListing("/$repositoryName/", fallbackDir)
        fallbackServer.expectHead("/$repositoryName/$artifactFileName", buildContext.srcDistribution)
        fallbackServer.expectGet("/$repositoryName/$artifactFileName", buildContext.srcDistribution)
        withSourceResolutionAssertionBuildScript("fallback repository")

        when:
        runnerWithFreshGradleUserHome("help", "-Dorg.gradle.kotlin.dsl.resolver.defaultGradleDistRepoBaseUrl=${fallbackServer.uri}").build()

        then:
        noExceptionThrown()

        cleanup:
        primaryServer.stop()
        fallbackServer.stop()
        primaryServer.resetExpectations()
        fallbackServer.resetExpectations()
    }

    @LeaksFileHandles
    @Requires(TestExecutionPreconditions.NotEmbeddedExecutor)
    def "source distribution resolved from local file when src zip is next to bin zip"() {
        given:
        def buildContext = IntegrationTestBuildContext.INSTANCE
        assert buildContext.srcDistribution != null: "src distribution must exist to run this test."
        assert buildContext.binDistribution != null: "bin distribution must exist to run this test."
        def gradleVersion = buildContext.version.version

        file("local-dist").mkdirs()
        buildContext.binDistribution.copyTo(file("local-dist/gradle-${gradleVersion}-bin.zip"))
        buildContext.srcDistribution.copyTo(file("local-dist/gradle-${gradleVersion}-src.zip"))
        withWrapperDistributionUrl(file("local-dist/gradle-${gradleVersion}-bin.zip").toURI().toASCIIString())
        withSourceResolutionAssertionBuildScript("local file distribution")

        when:
        runnerWithFreshGradleUserHome("help").build()

        then:
        noExceptionThrown()
    }

    @LeaksFileHandles
    @Requires(TestExecutionPreconditions.NotEmbeddedExecutor)
    def "source distribution resolved from fallback when wrapper uses a local file distribution without src zip"() {
        given:
        def buildContext = IntegrationTestBuildContext.INSTANCE
        assert buildContext.srcDistribution != null: "src distribution must exist to run this test."
        assert buildContext.binDistribution != null: "bin distribution must exist to run this test."
        def gradleVersion = buildContext.version
        def artifactFileName = "gradle-${gradleVersion.version}-src.zip"
        def repositoryName = gradleVersion.snapshot ? "distributions-snapshots" : "distributions"

        file("local-dist").mkdirs()
        buildContext.binDistribution.copyTo(file("local-dist/gradle-${gradleVersion.version}-bin.zip"))
        withWrapperDistributionUrl(file("local-dist/gradle-${gradleVersion.version}-bin.zip").toURI().toASCIIString())

        def fallbackServer = new HttpServer()
        fallbackServer.start()

        def fallbackDir = file("fallback-repo/$repositoryName")
        fallbackDir.mkdirs()
        buildContext.srcDistribution.copyTo(file("fallback-repo/$repositoryName/$artifactFileName"))
        fallbackServer.expectGetDirectoryListing("/$repositoryName/", fallbackDir)
        fallbackServer.expectHead("/$repositoryName/$artifactFileName", buildContext.srcDistribution)
        fallbackServer.expectGet("/$repositoryName/$artifactFileName", buildContext.srcDistribution)
        withSourceResolutionAssertionBuildScript("fallback repository")

        when:
        runnerWithFreshGradleUserHome("help", "-Dorg.gradle.kotlin.dsl.resolver.defaultGradleDistRepoBaseUrl=${fallbackServer.uri}").build()

        then:
        noExceptionThrown()

        cleanup:
        fallbackServer.stop()
        fallbackServer.resetExpectations()
    }

    private void withWrapperDistributionUrl(String distributionUrl) {
        file("gradle/wrapper/gradle-wrapper.properties").text = "distributionUrl=${distributionUrl}"
    }

    private void withSourceResolutionAssertionBuildScript(String source) {
        withKotlinBuildFile()
        buildFile.text = """
            val sourceDirs = org.gradle.kotlin.dsl.resolver.SourceDistributionResolver(project).sourceDirs()
            require(sourceDirs.isNotEmpty()) {
                "Expected source dirs to be resolved from the ${source}"
            }
        """
    }
}
