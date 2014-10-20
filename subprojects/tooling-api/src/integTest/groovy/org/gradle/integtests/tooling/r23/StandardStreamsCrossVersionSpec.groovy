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

package org.gradle.integtests.tooling.r23

import org.gradle.integtests.tooling.fixture.*
import org.gradle.test.fixtures.ConcurrentTestUtil
import org.gradle.test.fixtures.server.http.CyclicBarrierHttpServer
import org.gradle.tooling.ProjectConnection
import org.junit.Rule

@TargetGradleVersion(">=2.3")
class StandardStreamsCrossVersionSpec extends ToolingApiSpecification {
    @Rule CyclicBarrierHttpServer server = new CyclicBarrierHttpServer()
    @Rule RedirectStdOutAndErr stdOutAndErr = new RedirectStdOutAndErr()

    def setup() {
        toolingApi.requireDaemons()
    }

    def "logging is not sent to stderr/stdout if using custom output streams"() {
        String uuid = UUID.randomUUID().toString()
        file("build.gradle") << """
task log << {
    println "waiting-${uuid}"
    println "connecting to ${server.uri}"
    new URL("${server.uri}").text
    println "finished-${uuid}"
}
"""

        when:
        def resultHandler = new TestResultHandler()
        def output = new TestOutputStream()
        withConnection { ProjectConnection connection ->
            def build = connection.newBuild()
            build.standardOutput = output
            build.forTasks("log")
            build.run(resultHandler)
            server.waitFor()
            ConcurrentTestUtil.poll {
                // Need to poll, as logging output is delivered asynchronously to client
                assert output.toString().contains("waiting-" + uuid)
            }
            assert !output.toString().contains("finished-" + uuid)
            server.release()
            resultHandler.finished()
        }

        then:
        output.toString().contains("waiting-" + uuid)
        output.toString().contains("finished-" + uuid)
        !stdOutAndErr.stdOut.contains("waiting-" + uuid)
        !stdOutAndErr.stdOut.contains("finished-" + uuid)
    }

    @ToolingApiVersion(">=2.3")
    def "connector can redirect logging from System.out and err"() {
        String uuid = UUID.randomUUID().toString()
        file("build.gradle") << """
project.logger.error("error logging-${uuid}");
project.logger.warn("warn logging-${uuid}");
project.logger.lifecycle("lifecycle logging-${uuid}");
project.logger.quiet("quiet logging-${uuid}");
project.logger.info ("info logging-${uuid}");
project.logger.debug("debug logging-${uuid}");

task log << {
    println "waiting-${uuid}"
    println "connecting to ${server.uri}"
    new URL("${server.uri}").text
    println "finished-${uuid}"
}
"""

        when:
        def resultHandler = new TestResultHandler()
        def output = new TestOutputStream()
        def error = new TestOutputStream()
        withConnection { ProjectConnection connection ->
            def build = connection.newBuild()
            build.standardOutput = output
            build.standardError = error
            build.forTasks("log")
            build.run(resultHandler)
            server.waitFor()
            ConcurrentTestUtil.poll {
                // Need to poll, as logging output is delivered asynchronously to client
                assert output.toString().contains("waiting-" + uuid)
            }
            assert !output.toString().contains("finished-" + uuid)
            server.release()
            resultHandler.finished()
        }

        then:
        output.toString().contains("waiting-" + uuid)
        output.toString().contains("finished-" + uuid)
        output.toString().contains("warn logging-${uuid}")
        output.toString().contains("lifecycle logging-${uuid}")
        output.toString().contains("quiet logging-${uuid}")
        error.toString().contains("error logging-${uuid}")
        !stdOutAndErr.stdOut.contains("-" + uuid)
        !stdOutAndErr.stdErr.contains("-" + uuid)
    }

    @ToolingApiVersion(">=2.3")
    def "can specify color output"() {
        String uuid = UUID.randomUUID().toString()
        file("build.gradle") << """
task uptodate {
    outputs.upToDateWhen { true }
}
task log(dependsOn: uptodate) << {
    println "waiting-${uuid}"
    println "connecting to ${server.uri}"
    new URL("${server.uri}").text
    println "finished-${uuid}"
}
"""
        // def escapeHeader = "" + Ansi.FIRST_ESC_CHAR + Ansi.SECOND_ESC_CHAR
        def escapeHeader = "" + (char) 27 + '['

        when:
        def resultHandler = new TestResultHandler()
        def output = new TestOutputStream()
        withConnection { ProjectConnection connection ->
            def build = connection.newBuild()
            build.standardOutput = output
            build.colorOutput = true
            build.forTasks("log")
            build.run(resultHandler)
            server.waitFor()
            ConcurrentTestUtil.poll {
                // Need to poll, as logging output is delivered asynchronously to client
                assert output.toString().contains("waiting-" + uuid)
            }
            assert !output.toString().contains("finished-" + uuid)
            server.release()
            resultHandler.finished()
        }

        then:
        output.toString().contains("waiting-" + uuid)
        output.toString().contains("finished-" + uuid)
        output.toString().contains("UP-TO-DATE" + escapeHeader)
        !stdOutAndErr.stdOut.contains(escapeHeader)
        !stdOutAndErr.stdErr.contains(escapeHeader)
    }
}
