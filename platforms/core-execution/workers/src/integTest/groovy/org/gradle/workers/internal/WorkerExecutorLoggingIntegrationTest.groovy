/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.workers.internal

import org.gradle.workers.fixtures.WorkerExecutorFixture.WorkActionClass
import spock.lang.Timeout

import static org.gradle.workers.fixtures.WorkerExecutorFixture.ISOLATION_MODES

@Timeout(120)
class WorkerExecutorLoggingIntegrationTest extends AbstractWorkerExecutorIntegrationTest {
    WorkActionClass executionWithLogging

    def setup() {
        executionWithLogging = fixture.workActionThatCreatesFiles
        executionWithLogging.with {
            imports.add("org.gradle.api.logging.Logging")
            action = """
                Logging.getLogger(getClass()).warn("warn message");
                Logging.getLogger(getClass()).info("info message");
                Logging.getLogger(getClass()).debug("debug message");
                Logging.getLogger(getClass()).error("error message");
                
                org.slf4j.LoggerFactory.getLogger(getClass()).warn("slf4j warn");
                org.slf4j.LoggerFactory.getLogger(getClass()).info("slf4j info");
                org.slf4j.LoggerFactory.getLogger(getClass()).debug("slf4j debug message");
                org.slf4j.LoggerFactory.getLogger(getClass()).error("slf4j error");
                
                java.util.logging.Logger.getLogger("worker").warning("jul warn");
                java.util.logging.Logger.getLogger("worker").warning("jul info");
                java.util.logging.Logger.getLogger("worker").fine("jul debug message");
                java.util.logging.Logger.getLogger("worker").severe("jul error");
                
                System.out.println("stdout message");
                System.err.println("stderr message");
            """
        }
    }

    def "worker lifecycle is logged in #isolationMode"() {
        def workAction = fixture.workActionThatCreatesFiles.writeToBuildSrc()

        buildFile << """
            task runInWorker(type: WorkerTask) {
                isolationMode = $isolationMode
            }
        """.stripIndent()

        when:
        args("--debug")
        def gradle = executer.withTasks("runInWorker").start()

        then:
        gradle.waitForFinish()

        and:
        gradle.standardOutput.contains("Build operation '${workAction.packageName}.${workAction.name}' started")
        gradle.standardOutput.contains("Build operation '${workAction.packageName}.${workAction.name}' completed")

        where:
        isolationMode << ISOLATION_MODES
    }

    def "stdout, stderr and logging output of worker is redirected in #isolationMode"() {
        executionWithLogging.writeToBuildFile()

        buildFile << """
            task runInWorker(type: WorkerTask) {
                isolationMode = $isolationMode
            }
        """.stripIndent()

        when:
        succeeds("runInWorker")

        then:
        output.contains("stdout message")
        output.contains("warn message")
        output.contains("slf4j warn")
        output.contains("jul warn")

        and:
        result.assertHasErrorOutput("stderr message")
        result.assertHasErrorOutput("error message")
        result.assertHasErrorOutput("slf4j error")
        result.assertHasErrorOutput("jul error")

        and:
        result.assertNotOutput("debug message")

        where:
        isolationMode << ISOLATION_MODES
    }

    def "stdout, stderr and logging output of worker is redirected in #isolationMode when Gradle logging is --info"() {
        executionWithLogging.writeToBuildFile()

        buildFile << """
            task runInWorker(type: WorkerTask) {
                isolationMode = $isolationMode
            }
        """.stripIndent()

        when:
        succeeds("runInWorker", "--info")

        then:
        output.contains("stdout message")
        output.contains("warn message")
        output.contains("slf4j warn")
        output.contains("jul warn")

        output.contains("info message")
        output.contains("slf4j info")
        output.contains("jul info")

        and:
        result.assertHasErrorOutput("stderr message")
        result.assertHasErrorOutput("error message")
        result.assertHasErrorOutput("slf4j error")
        result.assertHasErrorOutput("jul error")

        and:
        result.assertNotOutput("debug message")

        where:
        isolationMode << ISOLATION_MODES
    }

    def "stdout, stderr and logging output of worker is redirected in #isolationMode when Gradle logging is --debug"() {
        executionWithLogging.writeToBuildFile()

        buildFile << """
            task runInWorker(type: WorkerTask) {
                isolationMode = $isolationMode
            }
        """.stripIndent()

        when:
        succeeds("runInWorker", "--debug")

        then:
        output.contains("stdout message")
        output.contains("warn message")
        output.contains("slf4j warn")
        output.contains("jul warn")

        output.contains("info message")
        output.contains("slf4j info")
        output.contains("jul info")

        output.contains("debug message")
        output.contains("slf4j debug")
        output.contains("jul debug")

        and:
        result.assertHasErrorOutput("stderr message")
        result.assertHasErrorOutput("error message")
        result.assertHasErrorOutput("slf4j error")
        result.assertHasErrorOutput("jul error")

        where:
        isolationMode << ISOLATION_MODES
    }
}
