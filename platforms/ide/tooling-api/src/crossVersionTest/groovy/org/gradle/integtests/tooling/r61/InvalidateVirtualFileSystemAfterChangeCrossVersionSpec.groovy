/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.integtests.tooling.r61

import org.gradle.integtests.fixtures.daemon.DaemonFixture
import org.gradle.integtests.fixtures.executer.GradleExecuter
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.gradle.tooling.model.gradle.GradleBuild
import org.junit.Rule

import java.nio.file.Path
import java.nio.file.Paths

class InvalidateVirtualFileSystemAfterChangeCrossVersionSpec extends ToolingApiSpecification {
    @Rule BlockingHttpServer server = new BlockingHttpServer()
    GradleExecuter executer

    List<String> changedPaths = [file("src/main/java").absolutePath]

    def setup() {
        toolingApi.requireIsolatedToolingApi()

        buildFile << """
            apply plugin: 'java'
        """

        executer = toolingApi.createExecuter()
    }

    @TargetGradleVersion(">=3.0")
    def "no daemon is started for request"() {
        when:
        withConnection { connection ->
            connection.notifyDaemonsAboutChangedPaths(toPaths(changedPaths))
        }

        then:
        toolingApi.daemons.daemons.empty
    }

    @TargetGradleVersion(">=6.1")
    def "changed paths need to be absolute"() {
        changedPaths = ["some/relative/path"]

        when:
        withConnection { connection ->
            connection.notifyDaemonsAboutChangedPaths(toPaths(changedPaths))
        }

        then:
        def exception = thrown(IllegalArgumentException)
        exception.message == "Changed path '${Paths.get(changedPaths[0])}' is not absolute"
    }

    @TargetGradleVersion(">=6.1")
    def "invalidates paths for single idle daemon"() {
        when:
        createIdleDaemon()

        withConnection { connection ->
            connection.notifyDaemonsAboutChangedPaths(toPaths(changedPaths))
        }

        then:
        pathsInvalidated()
    }

    @TargetGradleVersion(">=6.8")
    def "invalidates paths for single busy daemon"() {
        server.start()
        buildFile << """
            task block {
                doLast {
                    ${server.callFromBuild("block")}
                }
            }
        """
        def block = server.expectAndBlock("block")
        def build = executer.withTasks("block", "--info").start()
        block.waitForAllPendingCalls()
        toolingApi.daemons.daemon.assertBusy()

        when:
        withConnection { connection ->
            connection.notifyDaemonsAboutChangedPaths(toPaths(changedPaths))
        }

        then:
        block.releaseAll()
        build.waitForFinish()
        pathsInvalidated()

        cleanup:
        block?.releaseAll()
        build?.waitForFinish()
    }

    @TargetGradleVersion(">=3.0 <6.1")
    def "invalidating paths has no effect on older daemons"() {
        when:
        createIdleDaemon()
        def executedCommandCount = countExecutedCommands()

        then:
        executedCommandCount == 3

        when:
        withConnection { connection ->
            connection.notifyDaemonsAboutChangedPaths(toPaths(changedPaths))
        }

        then:
        !pathsInvalidated()
        countExecutedCommands() == executedCommandCount
    }

    private int countExecutedCommands() {
        toolingApi.daemons.daemon.log.count("command = ")
    }

    private void createIdleDaemon() {
        withConnection { connection ->
            connection.model(GradleBuild).get()
        }
        toolingApi.daemons.daemon.assertIdle()
    }

    def cleanup() {
        toolingApi.close()
    }

    boolean pathsInvalidated() {
        pathsInvalidatedForDaemon(toolingApi.daemons.daemon)
    }

    boolean pathsInvalidatedForDaemon(DaemonFixture daemon) {
        daemon.log.contains("Invalidating ${changedPaths}")
    }

    static List<Path> toPaths(List<String> paths) {
        paths.collect { Paths.get(it) }
    }
}
