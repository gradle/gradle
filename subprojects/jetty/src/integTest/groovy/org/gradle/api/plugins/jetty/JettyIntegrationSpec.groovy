/*
 * Copyright 2016 the original author or authors.
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

/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.plugins.jetty

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.server.http.CyclicBarrierHttpServer
import org.gradle.util.ports.ReleasingPortAllocator
import org.junit.Rule
import spock.lang.Timeout
import spock.util.concurrent.PollingConditions

@Timeout(15)
class JettyIntegrationSpec extends AbstractIntegrationSpec {

    private static final CONTEXT_PATH = 'testContext'
    private static final String JSP_CONTENT = 'Test'

    @Rule
    CyclicBarrierHttpServer server = new CyclicBarrierHttpServer()
    @Rule
    ReleasingPortAllocator portAllocator = new ReleasingPortAllocator()

    int httpPort = portAllocator.assignPort()
    int stopPort = portAllocator.assignPort()
    PollingConditions pollingConditions = new PollingConditions(timeout: 10, initialDelay: 0.1, factor: 1)

    def setup() {
        file("src/main/webapp/test.jsp") << JSP_CONTENT
    }

    def 'Jetty Run starts jetty in daemon mode'() {
        given:
        jettyBuildScript(daemon: true, """
            task block doLast {
              new URL("$server.uri").text
            }
        """.stripIndent())

        when:
        def handle = executer.withTasks('jettyRun', 'block').start()

        then:
        server.waitFor()
        jettyIsUp()

        when:
        server.release()

        then:
        handle.waitForFinish()
        assertJettyStopped()
    }

    def 'Jetty Run starts blocking jetty'() {
        given:
        jettyBuildScript(daemon: false, """
            jettyRun.doFirst {
              new URL("$server.uri").text
            }
        """.stripIndent())

        when:
        def handle = executer.withTasks('jettyRun').start()
        server.sync()

        then:
        pollingConditions.eventually {
            jettyIsUp()
        }

        when:
        handle.abort()

        then:
        handle.waitForExit()
        assertJettyStopped()
    }

    private void jettyIsUp() {
        try {
            new URL("http://localhost:${httpPort}/${CONTEXT_PATH}/test.jsp").text == "Test"
        } catch (ConnectException e) {
            assert 'Jetty is still runnning', e != null
        }
    }

    private void assertJettyStopped() {
        new ServerSocket(httpPort).close()
    }

    private TestFile jettyBuildScript(Map options, String script) {
        return super.buildScript("""
            apply plugin: 'java'
            apply plugin: 'war'
            apply plugin: 'jetty'
            stopPort = ${stopPort}
            httpPort = ${httpPort}

            jettyRun {
                daemon ${options.daemon}
                contextPath '${CONTEXT_PATH}'
            }
            """.stripIndent() + script
        )
    }
}
