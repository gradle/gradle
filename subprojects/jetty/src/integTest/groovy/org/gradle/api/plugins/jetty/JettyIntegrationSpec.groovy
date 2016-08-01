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
    private static final String STOP_KEY = 'now'

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
        def handle = executer.withTasks('jettyRun', 'block').expectDeprecationWarning().start()
        server.waitFor()

        then:
        assertJettyIsUp()

        when:
        server.release()
        stopJettyViaMonitor()

        then:
        handle.waitForFinish()
        pollingConditions.eventually {
            assertJettyIsDown()
        }
    }

    def 'Jetty Run starts blocking jetty'() {
        given:
        jettyBuildScript(daemon: false, """
            jettyRun.doFirst {
              new URL("$server.uri").text
            }
        """.stripIndent())

        when:
        def handle = executer.withTasks('jettyRun').expectDeprecationWarning().start()
        server.sync()

        then:
        pollingConditions.eventually {
            assertJettyIsUp()
        }

        when:
        stopJettyViaMonitor()
        handle.waitForFinish()

        then:
        pollingConditions.eventually {
            assertJettyIsDown()
        }
    }

    def "emits deprecation warning"() {
        given:
        buildFile << "apply plugin: 'jetty'"

        when:
        def result = executer.withTasks('help').expectDeprecationWarning().run()

        then:
        result.assertOutputContains("The Jetty plugin has been deprecated and is scheduled to be removed in Gradle 4.0. Consider using the Gretty (https://github.com/akhikhl/gretty) plugin instead.")
    }

    private void stopJettyViaMonitor() {
        new Socket("localhost", stopPort).getOutputStream().withPrintWriter {
            it.println(STOP_KEY)
            it.println('stop')
        }
    }

    private void assertJettyIsUp() {
        try {
            new URL("http://localhost:${httpPort}/${CONTEXT_PATH}/test.jsp").text == JSP_CONTENT
        } catch (ConnectException e) {
            assert 'Jetty is not up, yet' && e == null
        }
    }

    private void assertJettyIsDown() {
        try {
            new ServerSocket(httpPort).close()
        } catch (BindException e) {
            assert 'Jetty is still up but should be stopped' && e == null
        }
    }

    private TestFile jettyBuildScript(Map options, String script) {
        return super.buildScript("""
            apply plugin: 'java'
            apply plugin: 'war'
            apply plugin: 'jetty'
            stopPort = ${stopPort}
            httpPort = ${httpPort}
            stopKey = '${STOP_KEY}'

            jettyRun {
                daemon ${options.daemon}
                contextPath '${CONTEXT_PATH}'
            }
            """.stripIndent() + script
        )
    }
}
