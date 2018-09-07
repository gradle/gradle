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
import org.gradle.integtests.tooling.fixture.ProgressEventsWithStatus
import org.gradle.integtests.tooling.fixture.ToolingApi
import org.gradle.test.fixtures.file.LeaksFileHandles
import org.gradle.test.fixtures.server.http.HttpServer
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

import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

import static org.gradle.test.matchers.UserAgentMatcher.matchesNameAndVersion

@LeaksFileHandles
class ToolingApiRemoteIntegrationTest extends AbstractIntegrationSpec {
    @Rule HttpServer server = new HttpServer()
    final ToolingApi toolingApi = new ToolingApi(distribution, temporaryFolder)

    void setup() {
        assert distribution.binDistribution.exists() : "bin distribution must exist to run this test, you need to run the 'testBinZip' task"
        server.start()
        toolingApi.requireIsolatedUserHome()
    }

    def "downloads distribution with valid user-agent information"() {
        given:
        settingsFile << "";
        buildFile << "task hello { doLast { println hello } }"

        and:
        server.expectGet("/custom-dist.zip", distribution.binDistribution)
        server.expectUserAgent(matchesNameAndVersion("Gradle Tooling API", GradleVersion.current().getVersion()))

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

    def "receives distribution download progress events"() {
        given:
        settingsFile << ""

        and:
        server.expectGet("/custom-dist.zip", distribution.binDistribution)

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
        server.expectGetBroken("/custom-dist.zip")

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
        def e = thrown GradleConnectionException
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
        server.server.setGracefulShutdown(2 * 1000)

        given:
        settingsFile << "";
        buildFile << "task hello { doLast { println hello } }"
        CancellationTokenSource tokenSource = new DefaultCancellationTokenSource()
        CountDownLatch latch = new CountDownLatch(1)
        def distUri = URI.create("http://localhost:${server.port}/cancelled-dist.zip")

        server.expect("/cancelled-dist.zip", false, ['GET'], new SendDataAndCancelAction("/cancelled-dist.zip", distribution.binDistribution, tokenSource, latch))

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
        def e = thrown BuildCancelledException
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

    class SendDataAndCancelAction extends HttpServer.ActionSupport {
        private final String path
        private final File srcFile
        private final CancellationTokenSource tokenSource
        private final CountDownLatch latch

        SendDataAndCancelAction(String path, File srcFile, CancellationTokenSource tokenSource, CountDownLatch latch) {
            super("return contents of $srcFile.name")
            this.srcFile = srcFile
            this.path = path
            this.tokenSource = tokenSource
            this.latch = latch
        }

        void handle(HttpServletRequest request, HttpServletResponse response) {
            def file
            if (request.pathInfo == path) {
                file = srcFile
            } else {
                def relativePath = request.pathInfo.substring(path.length() + 1)
                file = new File(srcFile, relativePath)
            }
            if (file.isFile()) {
                sendFile(response, file, interaction.contentType)
            } else {
                response.sendError(404, "'$request.pathInfo' does not exist")
            }
        }

        private sendFile(HttpServletResponse response, File file, String contentType) {
            response.setContentType(contentType ?: new MimeTypes().getMimeByExtension(file.name).toString())

            def content = file.bytes
            for (int i = 0; i < content.length; i++) {
                response.outputStream.write(content[i])
                if (i == 30000) { // more than one progress tick in output
                    println('call cancel')
                    tokenSource.cancel()
                    println('waiting for test to continue')
                    latch.await(10, TimeUnit.SECONDS)
                    println('test continued, closing connection')
                    break;
                }
            }
            println('server handler done.')
        }
    }
}
