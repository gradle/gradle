/*
 * Copyright 2011 the original author or authors.
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

package org.gradle.integtests.tooling.m3

import org.gradle.integtests.tooling.fixture.TestOutputStream
import org.gradle.integtests.tooling.fixture.TestResultHandler
import org.gradle.integtests.tooling.fixture.ToolingApiLoggingSpecification
import org.gradle.test.fixtures.ConcurrentTestUtil
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.gradle.tooling.ProjectConnection
import org.junit.Rule

class ToolingApiLoggingCrossVersionSpec extends ToolingApiLoggingSpecification {
    @Rule BlockingHttpServer server = new BlockingHttpServer()

    def "logging is live"() {
        server.start()
        def waitingMessage = "logging task: connecting to ${server.uri}"
        def finishedMessage = "logging task: finished"

        file("build.gradle") << """
task log {
    doLast {
        println "${waitingMessage}"
        ${server.callFromBuild("waiting")}
        println "${finishedMessage}"
    }
}
"""

        when:
        def resultHandler = new TestResultHandler()
        def sync = server.expectAndBlock("waiting")
        def output = new TestOutputStream()
        withConnection { ProjectConnection connection ->
            def build = connection.newBuild()
            build.standardOutput = output
            build.forTasks("log")
            build.run(resultHandler)
            sync.waitForAllPendingCalls(resultHandler)
            ConcurrentTestUtil.poll {
                // Need to poll, as logging output is delivered asynchronously to client
                assert output.toString().contains(waitingMessage)
            }
            assert !output.toString().contains(finishedMessage)
            sync.releaseAll()
            resultHandler.finished()
            resultHandler.failWithFailure()
        }

        then:
        output.toString().contains(waitingMessage)
        output.toString().contains(finishedMessage)
    }
}
