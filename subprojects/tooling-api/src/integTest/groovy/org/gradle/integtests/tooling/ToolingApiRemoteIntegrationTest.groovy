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

import com.sun.net.httpserver.HttpExchange
import org.gradle.api.Action
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.tooling.fixture.ProgressEventsWithStatus
import org.gradle.integtests.tooling.fixture.ToolingApi
import org.gradle.internal.Actions
import org.gradle.test.fixtures.ConcurrentTestUtil
import org.gradle.test.fixtures.file.LeaksFileHandles
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.gradle.tooling.BuildCancelledException
import org.gradle.tooling.BuildLauncher
import org.gradle.tooling.CancellationTokenSource
import org.gradle.tooling.GradleConnectionException
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.internal.consumer.DefaultCancellationTokenSource
import org.gradle.util.GradleVersion
import org.junit.Rule
import org.mortbay.jetty.MimeTypes
import spock.lang.Issue

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

import static org.gradle.test.fixtures.server.http.BlockingHttpServer.broken
import static org.gradle.test.fixtures.server.http.BlockingHttpServer.sendFile
import static org.gradle.test.fixtures.server.http.BlockingHttpServer.userAgent
import static org.gradle.test.matchers.UserAgentMatcher.matchesNameAndVersion

@LeaksFileHandles
class ToolingApiRemoteIntegrationTest extends AbstractIntegrationSpec {
    @Rule BlockingHttpServer server = new BlockingHttpServer()
    final ToolingApi toolingApi = new ToolingApi(distribution, temporaryFolder)
    @Rule ConcurrentTestUtil concurrent = new ConcurrentTestUtil()

    def setup() {
        assert distribution.binDistribution.exists() : "bin distribution must exist to run this test, you need to run the 'binZip' task"
        server.start()
        toolingApi.requireIsolatedUserHome()
    }

    @Issue('https://github.com/gradle/gradle-private/issues/1537')
    def "downloads distribution with valid user-agent information"() {
        given:
        settingsFile << "";
        buildFile << "task hello { doLast { println hello } }"

        and:
        server.expect(server.get("/custom-dist.zip", Actions.composite(userAgent(matchesNameAndVersion("Gradle Tooling API", GradleVersion.current().getVersion())), sendFile(distribution.binDistribution))))

        and:
        toolingApi.withConnector { GradleConnector connector ->
            connector.useDistribution(URI.create("http://localhost:${server.port}/custom-dist.zip"))
        }

        when:
        ByteArrayOutputStream buildOutput = new ByteArrayOutputStream()

        toolingApi.withConnection { connection ->
            BuildLauncher launcher = connection.newBuild().forTasks("hello")
            launcher.standardOutput = buildOutput;
            launcher.run()
        }

        then:
        buildOutput.toString().contains('hello')
        toolingApi.gradleUserHomeDir.file("wrapper/dists/custom-dist").assertIsDir().listFiles().size() == 1
    }

    @Issue('https://github.com/gradle/gradle-private/issues/1537')
    def "receives distribution download progress events"() {
        given:
        settingsFile << ""

        and:
        server.expect(server.file("/custom-dist.zip", distribution.binDistribution))

        and:
        def distUri = URI.create("http://localhost:${server.port}/custom-dist.zip")
        toolingApi.withConnector { GradleConnector connector ->
            connector.useDistribution(distUri)
        }

        when:
        def events = new ProgressEventsWithStatus()
        toolingApi.withConnection { ProjectConnection connection ->
            connection.newBuild()
                .forTasks("help")
                .addProgressListener(events)
                .run()
        }

        then:
        def download = events.buildOperations.first()
        download.successful
        download.descriptor.displayName == "Download " + distUri

        !download.statusEvents.empty

        download.statusEvents.each { statusEvent ->
            assert statusEvent.displayName == "Download $distUri $statusEvent.progress/$statusEvent.total bytes downloaded"
            assert statusEvent.total == distribution.binDistribution.length()
            assert statusEvent.progress <= distribution.binDistribution.length()
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
        server.expect(server.get("/custom-dist.zip", broken()))

        and:
        def distUri = URI.create("http://localhost:${server.port}/custom-dist.zip")
        toolingApi.withConnector { GradleConnector connector ->
            connector.useDistribution(distUri)
        }

        when:
        def events = new ProgressEventsWithStatus()
        toolingApi.withConnection { ProjectConnection connection ->
            connection.newBuild()
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
        !download.successful
        download.descriptor.displayName == "Download " + distUri
        download.failures.size() == 1
        download.failures.first().message == "Server returned HTTP response code: 500 for URL: ${distUri}"
    }

    def "can cancel distribution download"() {
        def userHomeDir = file("user-home-dir")

        given:
        settingsFile << "";
        buildFile << "task hello { doLast { println hello } }"
        CancellationTokenSource tokenSource = new DefaultCancellationTokenSource()
        CountDownLatch latch = new CountDownLatch(1)
        def distUri = URI.create("http://localhost:${server.port}/cancelled-dist.zip")

        server.expect(server.get("/cancelled-dist.zip", sendDataAndCancel(distribution.binDistribution, tokenSource, latch)))

        and:
        toolingApi.withConnector { GradleConnector connector ->
            connector.useDistribution(distUri)
            connector.useGradleUserHomeDir(userHomeDir)
        }

        when:
        def events = new ProgressEventsWithStatus()
        toolingApi.withConnection { ProjectConnection connection ->
            connection.newBuild()
                .forTasks("hello")
                .withCancellationToken(tokenSource.token())
                .addProgressListener(events)
                .run()
        }

        then:
        BuildCancelledException e = thrown()
        e.message.contains('Distribution download cancelled.')

        and:
        events.buildOperations.size() == 1

        def download = events.buildOperations.first()
        !download.successful
        download.descriptor.displayName == "Download " + distUri
        download.failures.size() == 1

        cleanup:
        latch.countDown()
    }

    static Action<? extends HttpExchange> sendDataAndCancel(TestFile file, CancellationTokenSource tokenSource, CountDownLatch latch) {
        return { HttpExchange e ->
            e.responseHeaders.put("Content-Type", [new MimeTypes().getMimeByExtension(file.name).toString()])
            e.sendResponseHeaders(200, file.size())

            def content = file.bytes
            e.responseBody.write(content[0..30000].toArray(new byte[30000])) // more than one progress tick in output
            println('call cancel')
            tokenSource.cancel()
            println('waiting for test to continue')
            latch.await(10, TimeUnit.SECONDS)
            println('test continued, closing connection')
        }
    }
}
