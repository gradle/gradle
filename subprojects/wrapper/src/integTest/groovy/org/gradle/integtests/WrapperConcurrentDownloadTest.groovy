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
package org.gradle.integtests

import org.apache.commons.io.IOUtils
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.file.TestFile
import org.junit.Rule
import org.junit.rules.ExternalResource
import org.mortbay.jetty.Connector
import org.mortbay.jetty.Server
import org.mortbay.jetty.bio.SocketConnector
import org.mortbay.jetty.handler.AbstractHandler
import spock.lang.Issue

import javax.servlet.ServletException
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

class WrapperConcurrentDownloadTest extends AbstractIntegrationSpec {
    @Rule BlockingDownloadHttpServer server = new BlockingDownloadHttpServer(distribution.binDistribution)

    def setup() {
        executer.beforeExecute(new WrapperSetup())
    }

    @Issue("http://issues.gradle.org/browse/GRADLE-2699")
    def "concurrent downloads do not stomp over each other"() {
        given:
        buildFile << """
    import org.gradle.api.tasks.wrapper.Wrapper
    task wrapper(type: Wrapper) {
        distributionUrl = '${server.distUri}'
    }
"""

        succeeds('wrapper')

        when:
        def build1 = executer.usingExecutable("gradlew").start()
        def build2 = executer.usingExecutable("gradlew").start()
        def build3 = executer.usingExecutable("gradlew").start()
        def build4 = executer.usingExecutable("gradlew").start()
        def builds = [build1, build2, build3, build4]
        builds.each { it.waitForFinish() }

        then:
        builds.findAll { it.standardOutput.contains("Downloading") }.size() == 1
    }

    static class BlockingDownloadHttpServer extends ExternalResource {
        private final Server server = new Server()
        private final TestFile binZip

        BlockingDownloadHttpServer(TestFile binZip) {
            this.binZip = binZip
        }

        URI getDistUri() {
            return new URI("http://localhost:${server.connectors[0].localPort}/gradle-bin.zip")
        }

        @Override
        protected void before() throws Throwable {
            server.connectors = [new SocketConnector()] as Connector[]
            server.addHandler(new AbstractHandler() {
                void handle(String target, HttpServletRequest request, HttpServletResponse response, int dispatch) throws IOException, ServletException {
                    binZip.withInputStream { instr ->
                        IOUtils.copy(instr, response.outputStream)
                    }
                    request.handled = true
                }
            })
            server.start()
        }

        @Override
        protected void after() {
            server.stop()
        }
    }
}
