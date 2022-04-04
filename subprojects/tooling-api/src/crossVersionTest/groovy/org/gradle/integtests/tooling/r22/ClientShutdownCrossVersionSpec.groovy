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
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.gradle.tooling.model.gradle.GradleBuild
import org.junit.Rule

class ClientShutdownCrossVersionSpec extends ToolingApiSpecification {
    private static final JVM_OPTS = ["-Xmx1024m", "-XX:+HeapDumpOnOutOfMemoryError"] + NORMALIZED_BUILD_JVM_OPTS

    @Rule
    BlockingHttpServer server = new BlockingHttpServer()

    def setup() {
        toolingApi.requireIsolatedToolingApi()
        toolingApi.requireIsolatedUserHome()
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

    def "cleans up idle daemons when tooling API session is shutdown"() {
        withConnection { connection ->
            connection.model(GradleBuild).setJvmArguments(buildJvmArguments).get()
        }
        toolingApi.daemons.daemon.assertIdle()

        when:
        toolingApi.close()

        then:
        toolingApi.daemons.daemon.stops()
    }

    def "cleans up busy daemons once they become idle when tooling API session is shutdown"() {
        given:
        server.start()
        buildFile << """
task slow { doLast { ${server.callFromBuild('sync')} } }
"""
        def sync = server.expectAndBlock('sync')
        withConnection { connection ->
            connection.model(GradleBuild).setJvmArguments(buildJvmArguments).get()
        }
        toolingApi.daemons.daemon.assertIdle()

        def build = daemonExecutor().withTasks("slow").start()
        sync.waitForAllPendingCalls()
        toolingApi.daemons.daemon.assertBusy()

        when:
        toolingApi.close()

        then:
        toolingApi.daemons.daemon.assertBusy()

        when:
        sync.releaseAll()
        build.waitForFinish()

        then:
        toolingApi.daemons.daemon.stops()
    }

    def "shutdown ignores daemons that are no longer running"() {
        given:
        withConnection { connection ->
            connection.model(GradleBuild).setJvmArguments(buildJvmArguments).get()
        }
        toolingApi.daemons.daemon.assertIdle()
        toolingApi.daemons.daemon.kill()

        when:
        toolingApi.close()

        then:
        noExceptionThrown()
    }

    def "shutdown ignores daemons that were not started by client"() {
        given:
        daemonExecutor().run()
        toolingApi.daemons.daemon.assertIdle()

        withConnection { connection ->
            connection.model(GradleBuild).setJvmArguments(buildJvmArguments).get()
        }
        toolingApi.daemons.daemon.assertIdle()

        when:
        toolingApi.close()

        then:
        toolingApi.daemons.daemon.assertIdle()
    }

    private GradleExecuter daemonExecutor() {
        targetDist.executer(temporaryFolder, getBuildContext())
            .withDaemonBaseDir(toolingApi.daemonBaseDir)
            .withBuildJvmOpts(buildJvmArguments)
            .useOnlyRequestedJvmOpts()
            .requireDaemon()
    }

    private List<String> getBuildJvmArguments() {
        return ["-Djava.io.tmpdir=${buildContext.getTmpDir().absolutePath}".toString()] + JVM_OPTS
    }
}
