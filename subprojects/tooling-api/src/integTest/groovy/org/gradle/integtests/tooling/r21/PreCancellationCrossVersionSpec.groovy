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

package org.gradle.integtests.tooling.r21

import org.gradle.integtests.tooling.fixture.*
import org.gradle.test.fixtures.server.http.CyclicBarrierHttpServer
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ProjectConnection
import org.junit.Rule

@ToolingApiVersion(">=2.1")
@TargetGradleVersion("<2.1 >=1.2")
class PreCancellationCrossVersionSpec extends ToolingApiSpecification {
    @Rule CyclicBarrierHttpServer server = new CyclicBarrierHttpServer()

    def setup() {
        settingsFile << '''
rootProject.name = 'cancelling'
'''
    }

    def "cancel with older provider issues warning only"() {
        buildFile << """
task t {
    doLast {
        new URL("${server.uri}").text
        println "finished"
    }
}
"""

        def cancel = GradleConnector.newCancellationTokenSource()
        def resultHandler = new TestResultHandler()
        def output = new TestOutputStream()

        when:
        withConnection { ProjectConnection connection ->
            def build = connection.newBuild()
            build.forTasks('t')
                .withCancellationToken(cancel.token())
                .setStandardOutput(output)
            build.run(resultHandler)
            server.waitFor()
            cancel.cancel()
            server.release()
            resultHandler.finished()
        }

        then:
        resultHandler.failure == null
        output.toString().contains("finished")
    }
}
