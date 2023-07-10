/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.integtests.tooling

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.tooling.fixture.ProgressEvents
import org.gradle.integtests.tooling.fixture.TestResultHandler
import org.gradle.integtests.tooling.fixture.ToolingApi
import org.gradle.test.fixtures.ConcurrentTestUtil
import org.gradle.test.fixtures.file.LeaksFileHandles
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import org.gradle.tooling.BuildCancelledException
import org.gradle.tooling.BuildLauncher
import org.gradle.tooling.GradleConnectionException
import org.gradle.tooling.events.OperationType
import org.gradle.tooling.internal.consumer.DefaultCancellationTokenSource
import org.gradle.util.GradleVersion
import org.junit.Rule
import spock.lang.Issue

import static org.gradle.test.matchers.UserAgentMatcher.matchesNameAndVersion

@LeaksFileHandles
class ToolingApiRemoteIntegrationTest extends AbstractIntegrationSpec {
    @Rule
    BlockingHttpServer server = new BlockingHttpServer()
    final ToolingApi toolingApi = new ToolingApi(distribution, temporaryFolder)
    @Rule
    ConcurrentTestUtil concurrent = new ConcurrentTestUtil()

    def setup() {
        assert distribution.binDistribution.exists(): "bin distribution must exist to run this test; make sure a <test>NormalizedDistribution dependency is defined."
        server.start()
        toolingApi.requireIsolatedUserHome()
        settingsFile.touch()
    }

    @Issue('https://github.com/gradle/gradle-private/issues/1537')
    def "downloads distribution with valid user-agent information"() {
        given:
        server.expect(server.get("/custom-dist.zip").expectUserAgent(matchesNameAndVersion("Gradle Tooling API", GradleVersion.current().getVersion())).sendFile(distribution.binDistribution))

        and:
        toolingApi.withConnector {
            it.useDistribution(URI.create("http://localhost:${server.port}/custom-dist.zip"))
        }

        when:
        toolingApi.withConnection { connection ->
            BuildLauncher launcher = connection.newBuild().forTasks("help")
            launcher.run()
        }

        then:
        toolingApi.gradleUserHomeDir.file("wrapper/dists/custom-dist").assertIsDir().listFiles().size() == 1
    }

    def "loads credentials from gradle.properties"() {
        given:
        server.withBasicAuthentication("username", "password")
        file("gradle.properties") << """
            systemProp.gradle.wrapperUser=username
            systemProp.gradle.wrapperPassword=password
        """.stripIndent()
        server.expect(server.get("/custom-dist.zip").sendFile(distribution.binDistribution))

        and:
        def distUri = URI.create("http://localhost:${server.port}/custom-dist.zip")
        toolingApi.withConnector {
            it.useDistribution(distUri)
        }

        when:
        toolingApi.withConnection {
             it.newBuild().forTasks("help").run()
        }

        then:
        toolingApi.gradleUserHomeDir.file("wrapper/dists/custom-dist").assertIsDir().listFiles().size() == 1
    }

    def "supports project-relative distribution download dir"() {
        given:
        server.expect(server.get("/custom-dist.zip").sendFile(distribution.binDistribution))
        file("gradle/wrapper/gradle-wrapper.properties") << """
            distributionBase=PROJECT
            distributionPath=wrapper/dists
            distributionUrl=http\\://localhost:${server.port}/custom-dist.zip
            zipStoreBase=PROJECT
            zipStorePath=wrapper/dists
        """

        and:
        toolingApi.withConnector {
            it.useBuildDistribution()
        }

        when:
        toolingApi.withConnection { it.newBuild().forTasks("help").run() }

        then:
        file("wrapper/dists/custom-dist").assertIsDir().listFiles().size() == 1
    }

    @Issue('https://github.com/gradle/gradle-private/issues/1537')
    def "receives distribution download progress events"() {
        given:
        server.expect(server.get("/custom-dist.zip").sendFile(distribution.binDistribution))

        and:
        def distUri = URI.create("http://localhost:${server.port}/custom-dist.zip")
        toolingApi.withConnector {
            it.useDistribution(distUri)
        }

        when:
        def events = ProgressEvents.create()
        toolingApi.withConnection {
            it.newBuild()
                .forTasks("help")
                .addProgressListener(events)
                .run()
        }

        then:
        def download = events.buildOperations.first()
        download.assertIsDownload(distUri, distribution.binDistribution.length())
        download.successful

        !download.statusEvents.empty

        download.statusEvents.each { statusEvent ->
            def event = statusEvent.event
            assert event.displayName == "Download $distUri ${event.progress}/${event.total} bytes completed"
            assert event.total == distribution.binDistribution.length()
            assert event.progress <= distribution.binDistribution.length()
        }

        // Build execution is sibling of download
        def build = events.buildOperations.get(1)
        build.descriptor.displayName == "Run build"

        toolingApi.gradleUserHomeDir.file("wrapper/dists/custom-dist").assertIsDir().listFiles().size() == 1
    }

    def "receives distribution download progress events when download fails"() {
        given:
        settingsFile << ""

        and:
        server.expect(server.get("/custom-dist.zip").broken())

        and:
        def distUri = URI.create("http://localhost:${server.port}/custom-dist.zip")
        toolingApi.withConnector {
            it.useDistribution(distUri)
        }

        when:
        def events = ProgressEvents.create()
        toolingApi.withConnection {
            it.newBuild()
                .forTasks("help")
                .addProgressListener(events)
                .run()
        }

        then:
        GradleConnectionException e = thrown()
        e.message == "Could not install Gradle distribution from '$distUri'."

        and:
        events.buildOperations.size() == 1

        def download = events.buildOperations.first()
        download.assertIsDownload(distUri, 0)
        !download.successful
        download.failures.size() == 1
        download.failures.first().message == "Server returned HTTP response code: 500 for URL: ${distUri}"
    }

    def "does not receive distribution download progress events when not requested"() {
        given:
        server.expect(server.get("/custom-dist.zip").sendFile(distribution.binDistribution))

        and:
        def distUri = URI.create("http://localhost:${server.port}/custom-dist.zip")
        toolingApi.withConnector {
            it.useDistribution(distUri)
        }

        when:
        def events = ProgressEvents.create()
        toolingApi.withConnection {
            it.newBuild()
                .forTasks("help")
                .addProgressListener(events, EnumSet.complementOf(EnumSet.of(OperationType.FILE_DOWNLOAD)))
                .run()
        }

        then:
        !events.operations.any { it.download }
    }

    def "can cancel distribution download"() {
        def userHomeDir = file("user-home-dir")

        given:
        def tokenSource = new DefaultCancellationTokenSource()
        def distUri = server.uri("cancelled-dist.zip")

        BufferedReader reader = new BufferedReader(new FileReader(distribution.binDistribution))
        def content = new byte[30000] // more than one progress tick in output
        for (i in 0..<content.length) {
            content[i++] = reader.read() as byte
        }

        def downloadHandle = server.get("cancelled-dist.zip").sendSomeAndBlock(content)
        server.expect(downloadHandle)

        and:
        toolingApi.withConnector {
            it.useDistribution(distUri)
            it.useGradleUserHomeDir(userHomeDir)
        }

        when:
        def events = ProgressEvents.create()
        def handler = new TestResultHandler()
        toolingApi.withConnection {
            it.newBuild()
                .forTasks("help")
                .withCancellationToken(tokenSource.token())
                .addProgressListener(events)
                .run(handler)
            downloadHandle.waitUntilBlocked()
            tokenSource.cancel()
            handler.finished()
            downloadHandle.release()
        }

        then:
        handler.assertFailedWith(BuildCancelledException)
        handler.failure.message.contains('Distribution download cancelled.')

        and:
        events.buildOperations.size() == 1

        def download = events.buildOperations.first()
        !download.successful
        download.descriptor.displayName == "Download " + distUri
        download.failures.size() == 1
    }

    @Issue('https://github.com/gradle/gradle/issues/15405')
    def "calling disconnect on uninitialized connection does not trigger wrapper download"() {
        when:
        def connector = toolingApi.connector()
        connector.useDistribution(URI.create("http://localhost:${server.port}/custom-dist.zip"))
        connector.connect()
        connector.disconnect()

        then:
        !toolingApi.gradleUserHomeDir.file("wrapper/dists/custom-dist").exists()
    }

    @Issue('https://github.com/gradle/gradle/issues/15405')
    @Requires(UnitTestPreconditions.NotWindows)
    // cannot delete files when daemon is running
    def "calling disconnect on existing connection does not re-trigger wrapper download"() {
        setup:
        server.expect(server.get("/custom-dist.zip").expectUserAgent(matchesNameAndVersion("Gradle Tooling API", GradleVersion.current().getVersion())).sendFile(distribution.binDistribution))
        def connector = toolingApi.connector()
        connector.useDistribution(URI.create("http://localhost:${server.port}/custom-dist.zip"))
        def connection = connector.connect()
        connection.newBuild().forTasks("help").run()

        expect:
        toolingApi.gradleUserHomeDir.file("wrapper/dists/custom-dist").exists()

        when:
        toolingApi.gradleUserHomeDir.file("wrapper/dists/custom-dist").deleteDir()
        connector.disconnect()

        then:
        !toolingApi.gradleUserHomeDir.file("wrapper/dists/custom-dist").exists()
    }
}
