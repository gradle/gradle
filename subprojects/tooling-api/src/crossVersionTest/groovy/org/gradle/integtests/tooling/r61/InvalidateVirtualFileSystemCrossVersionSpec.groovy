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

import org.gradle.integtests.fixtures.executer.GradleExecuter
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.gradle.tooling.model.gradle.GradleBuild
import org.junit.Rule

@TargetGradleVersion(">=6.1")
class InvalidateVirtualFileSystemCrossVersionSpec extends AbstractInvalidateVirtualFileSystemCrossVersionSpec {
    @Rule BlockingHttpServer server = new BlockingHttpServer()
    GradleExecuter executer

    def setup() {
        executer = toolingApi.createExecuter()
    }

    def "invalidates paths for single idle daemon"() {
        when:
        createIdleDaemon()

        withConnection { connection ->
            connection.notifyDaemonsAboutChangedPaths(changedPaths)
        }

        then:
        pathsInvalidated()
    }

    def "does not invalidate paths for single busy daemon"() {
        server.start()
        buildFile << """
            task block {
                doLast {
                    ${server.callFromBuild("block")}
                }
            }
        """
        def block = server.expectAndBlock("block")
        def build = executer.withTasks("block").start()
        block.waitForAllPendingCalls()
        toolingApi.daemons.daemon.assertBusy()

        when:
        withConnection { connection ->
            connection.notifyDaemonsAboutChangedPaths(changedPaths)
        }

        then:
        block.releaseAll()
        build.waitForFinish()
        !pathsInvalidated()

        cleanup:
        block?.releaseAll()
        build?.waitForFinish()
    }

    private void createIdleDaemon() {
        withConnection { connection ->
            connection.model(GradleBuild).get()
        }
        toolingApi.daemons.daemon.assertIdle()
    }
}
