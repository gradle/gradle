/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.testing.fixture

import groovy.transform.SelfType

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import static org.gradle.util.Matchers.matchesRegexp

/**
 * A fixture for tests that testt the behavior of the test task when there are problems starting test processing.
 * <p>
 * The class provides methods to insert a test listener that logs the test execution lifecycle,
 * and assertions to verify the behavior of the test task when it fails due to testing framework or test worker
 * JVM startup issues.
 */
@SelfType(AbstractIntegrationSpec)
trait TestFrameworkStartupTestFixture {
    void assertTestWorkerFailedToStart(String taskName = ":test") {
        failure.assertHasDescription("Execution failed for task '$taskName'.")

        def taskOutput = result.groupedOutput.task(taskName).output
        assert !(taskOutput =~ /beforeSuite Gradle Test Executor \d+/)
        assert taskOutput =~ /afterSuite Gradle Test Run $taskName \[.*\] FAILURE/
    }

    void assertTestWorkerStartedAndTestFrameworkFailedToStart(String taskName = ":test") {
        failure.assertHasDescription("Execution failed for task '$taskName'.")
        failure.assertThatCause(matchesRegexp(/Could not start Gradle Test Executor \d+:.*/))

        def taskOutput = result.groupedOutput.task(taskName).output
        assert taskOutput =~ /beforeSuite Gradle Test Executor \d+/
        assert taskOutput =~ (/afterSuite Gradle Test Run $taskName \[.*\] FAILURE/)
    }

    void assertSuggestsInspectTaskConfiguration() {
        failure.assertHasResolution("Inspect your task configuration for errors.")
    }

    String addLoggingTestListener() {
        // language=Groovy
        return """
            testing {
                suites {
                    test {
                        targets {
                            all {
                                testTask.configure {
                                    addTestListener(new TestListener() {
                                        void beforeSuite(TestDescriptor testDescriptor) {
                                            println("beforeSuite " + testDescriptor + " ")
                                        }
                                        void afterSuite(TestDescriptor testDescriptor, TestResult result) {
                                            println("afterSuite " + testDescriptor + " " + result.failures + " " + result)
                                        }
                                        void beforeTest(TestDescriptor testDescriptor) {
                                            println("beforeTest " + testDescriptor + " ")
                                        }
                                        void afterTest(TestDescriptor testDescriptor, TestResult result) {
                                            println("afterTest " + testDescriptor + " " + result.failures)
                                        }
                                    })
                                }
                            }
                        }
                    }
                }
            }
        """
    }
}
