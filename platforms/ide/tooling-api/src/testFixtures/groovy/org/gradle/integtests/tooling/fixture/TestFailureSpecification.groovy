/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.integtests.tooling.fixture

import org.gradle.tooling.TestAssertionFailure
import org.gradle.tooling.events.ProgressEvent
import org.gradle.tooling.events.ProgressListener
import org.gradle.tooling.events.test.TestFailureResult
import org.gradle.tooling.events.test.TestFinishEvent
import org.gradle.tooling.events.test.TestOperationResult

class TestFailureSpecification extends ToolingApiSpecification {

    /**
     * Controls if the test executor JVM is started in debug mode. Defaults to false.
     * The debugger should be a server listening on port 5006.
     *
     * @see #setup()
     */
    Boolean enableTestJvmDebugging = false

    /**
     * Controls if the stdout/stderr of the test gradle execution will be redirected to the main stdout/stderr.
     * Handy for debugging failures happening in the inner Gradle execution.
     *
     * @see #
     */
    Boolean enableStdoutProxying = false

    def setup() {
        buildFile << """
            plugins {
                id 'java-library'
            }

            ${mavenCentralRepository()}

            test {
                useJUnitPlatform()
            }
        """
    }

    void setupJUnit4() {
        buildFile << """
             dependencies {
                testImplementation 'junit:junit:4.13.2'
                testImplementation 'org.opentest4j:opentest4j:1.3.0'
            }

            test {
                useJUnit()
                ${debuggingSettings()}
            }
            """
    }

    void setupJUnit5() {
        buildFile << """
             dependencies {
                testImplementation(platform("org.junit:junit-bom:5.10.0"))
                testImplementation("org.junit.jupiter:junit-jupiter-api")
                testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
                testImplementation("org.opentest4j:opentest4j:1.3.0")
            }

            test {
                useJUnitPlatform()
                ${debuggingSettings()}
            }
            """
    }

    private String debuggingSettings() {
        if (enableTestJvmDebugging) {
            return '''
                debugOptions {
                    enabled = true
                    host = 'localhost'
                    port = 5006
                    server = false
                    suspend = true
                }
            '''
        } else {
            return ''
        }
    }

    /**
     * Runs the test task and collects all test failures.
     */
    protected List<TestAssertionFailure> runTestTaskWithFailureCollection(TestFailureEventCollector progressEventCollector) {
        withConnection { connection ->
            def build = connection.newBuild()
                .addProgressListener(progressEventCollector)
                .forTasks('test')
            if (enableStdoutProxying) {
                build.setStandardOutput(System.out).setStandardError(System.err)
            }
            build.run()
        }
    }

    protected static class  TestFailureEventCollector implements ProgressListener {
        public List<TestAssertionFailure> failures = new ArrayList<>()

        @Override
        void statusChanged(ProgressEvent event) {
            if (event instanceof TestFinishEvent) {
                TestOperationResult result = ((TestFinishEvent) event).getResult()
                if (result instanceof TestFailureResult) {
                    failures += ((TestFailureResult) result).failures
                }
            }
        }
    }

}
