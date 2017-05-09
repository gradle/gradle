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
import org.gradle.tooling.model.gradle.GradleBuild
import org.junit.Rule

@ToolingApiVersion(">=2.2")
class ClientShutdownCrossVersionSpec extends ToolingApiSpecification {
    @Rule
    CyclicBarrierHttpServer server = new CyclicBarrierHttpServer()

    def setup() {
        toolingApi.requireIsolatedToolingApi()
    }

    def cleanup() {
        toolingApi.close()
    }

    def "can shutdown tooling API session when no operations have been executed"() {
        given:
        toolingApi.close()

        when:
        toolingApi.withConnection {}

        then:
        thrown(IllegalStateException)
    }

    @TargetGradleVersion(">=2.2")
    def "cleans up idle daemons when tooling API session is shutdown"() {
        withConnection { connection ->
            connection.getModel(GradleBuild)
        }
        toolingApi.daemons.daemon.assertIdle()

        when:
        toolingApi.close()

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
        toolingApi.close()

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
        toolingApi.close()

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
        toolingApi.close()

        then:
        toolingApi.daemons.daemon.assertIdle()
    }

    private GradleExecuter daemonExecutor() {
        // Need to use the same JVM args to start daemon as those used by tooling api fixture
        // TODO - use more sane JVM args here and for the daemons started using tooling api fixture
        targetDist.executer(temporaryFolder, getBuildContext()).withNoExplicitTmpDir().withDaemonBaseDir(toolingApi.daemonBaseDir).withBuildJvmOpts("-Xmx1024m", "-XX:MaxPermSize=256m", "-XX:+HeapDumpOnOutOfMemoryError").useOnlyRequestedJvmOpts().requireDaemon()
    }
}
