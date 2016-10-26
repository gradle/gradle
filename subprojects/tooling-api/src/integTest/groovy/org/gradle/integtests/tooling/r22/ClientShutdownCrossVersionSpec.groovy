/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.integtests.tooling.r22

import org.gradle.integtests.fixtures.executer.GradleExecuter
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.test.fixtures.server.http.CyclicBarrierHttpServer
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.internal.consumer.DefaultGradleConnector
import org.gradle.tooling.model.gradle.GradleBuild
import org.junit.Rule
import spock.lang.Ignore

@Ignore // TODO:DAZ Ignoring this test on the suspicion that it is causing flakiness
// My theory is that the static methods `ConnectorServices.close()` and `ConnectorServices.reset()` may be interfering with other TAPI tests
@ToolingApiVersion(">=2.2")
class ClientShutdownCrossVersionSpec extends ToolingApiSpecification {
    @Rule
    CyclicBarrierHttpServer server = new CyclicBarrierHttpServer()

    def setup() {
        toolingApi.requireIsolatedDaemons()
    }

    def cleanup() {
        reset()
    }

    def "can shutdown tooling API session when no operations have been executed"() {
        given:
        DefaultGradleConnector.close()

        when:
        GradleConnector.newConnector()
        then:
        IllegalStateException e = thrown()
    }

    @TargetGradleVersion(">=2.2")
    def "cleans up idle daemons when tooling API session is shutdown"() {
        withConnection { connection ->
            connection.getModel(GradleBuild)
        }
        toolingApi.daemons.daemon.assertIdle()

        when:
        DefaultGradleConnector.close()

        then:
        toolingApi.daemons.daemon.stops()
    }

    @TargetGradleVersion(">=2.2")
    def "cleans up busy daemons once they become idle when tooling API session is shutdown"() {
        given:
        buildFile << """
task slow { doLast { new URL("${server.uri}").text } }
"""
        withConnection { connection ->
            connection.getModel(GradleBuild)
        }
        toolingApi.daemons.daemon.assertIdle()

        def build = daemonExecutor().withTasks("slow").start()
        server.waitFor()
        toolingApi.daemons.daemon.assertBusy()

        when:
        DefaultGradleConnector.close()

        then:
        toolingApi.daemons.daemon.assertBusy()

        when:
        server.release()
        build.waitForFinish()

        then:
        toolingApi.daemons.daemon.stops()
    }

    @TargetGradleVersion(">=2.2")
    def "shutdown ignores daemons that are no longer running"() {
        given:
        withConnection { connection ->
            connection.getModel(GradleBuild)
        }
        toolingApi.daemons.daemon.assertIdle()
        toolingApi.daemons.daemon.kill()

        when:
        DefaultGradleConnector.close()

        then:
        noExceptionThrown()
    }

    @TargetGradleVersion(">=2.2")
    def "shutdown ignores daemons that were not started by client"() {
        given:
        daemonExecutor().run()
        toolingApi.daemons.daemon.assertIdle()

        withConnection { connection ->
            connection.getModel(GradleBuild)
        }
        toolingApi.daemons.daemon.assertIdle()

        when:
        DefaultGradleConnector.close()

        then:
        toolingApi.daemons.daemon.assertIdle()
    }

    private GradleExecuter daemonExecutor() {
        // Need to use the same JVM args to start daemon as those used by tooling api fixture
        // TODO - use more sane JVM args here and for the daemons started using tooling api fixture
        targetDist.executer(temporaryFolder).withNoExplicitTmpDir().withDaemonBaseDir(toolingApi.daemonBaseDir).withBuildJvmOpts("-Xmx1024m", "-XX:MaxPermSize=256m", "-XX:+HeapDumpOnOutOfMemoryError").useDefaultBuildJvmArgs().requireDaemon()
    }
}
