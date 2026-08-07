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
package org.gradle.testretry

import org.gradle.testretry.internal.config.TestRetryTaskExtensionAdapter

class CorePluginFuncTest extends AbstractGeneralPluginFuncTest {

    def "has no effect when all tests pass"() {
        when:
        buildFile << """
            test.retry.maxRetries = 1
        """

        successfulTest()

        then:
        succeeds('test')

        and:
        output.count('PASSED') == 1
    }

    def "is benign when unconfigured"() {
        when:
        successfulTest()
        succeeds('test')

        then:
        assertTestReportContains("SuccessfulTests", reportedTestName("successTest"), 1, 0)
    }

    def "does not retry by default"() {
        when:
        failedTest()
        fails('test')

        then:
        assertTestReportContains("FailedTests", reportedTestName("failedTest"), 0, 1)
    }

    def "retries failed tests"() {
        given:
        buildFile << """
            test.retry.maxRetries = 1
        """

        successfulTest()
        failedTest()

        when:
        fails('test')

        then: 'Only the failed test is retried a second time'
        output.count('PASSED') == 1

        // 2 individual tests FAILED + 1 overall task FAILED (no BUILD FAILED in captured output)
        output.count('FAILED') == 2 + 1

        assertTestReportContains("SuccessfulTests", reportedTestName("successTest"), 1, 0)
        assertTestReportContains("FailedTests", reportedTestName("failedTest"), 0, 2)
    }

    def "still publishes test report when test is un-retryable"() {
        given:
        buildFile << """
            test.retry.maxRetries = 1
        """

        failedTest()

        when:
        // The plugin reads this via Boolean.getBoolean(...) at apply time. That "apply time"
        // runs in the forked build JVM under forkingIntegTest, but in the test JVM under
        // embeddedIntegTest. Set both to cover both executers; clear the System property
        // afterwards so it doesn't bleed into subsequent tests running in the same JVM.
        executer.withBuildJvmOpts("-D${TestRetryTaskExtensionAdapter.SIMULATE_NOT_RETRYABLE_PROPERTY}=true")
        System.setProperty(TestRetryTaskExtensionAdapter.SIMULATE_NOT_RETRYABLE_PROPERTY, "true")
        try {
            fails('test')
        } finally {
            System.clearProperty(TestRetryTaskExtensionAdapter.SIMULATE_NOT_RETRYABLE_PROPERTY)
        }

        then:
        assertTestReportContains("FailedTests", reportedTestName("failedTest"), 0, 1)
    }

    def "stops when all tests pass"() {
        given:
        buildFile << """
            test.retry.maxRetries = 1
        """

        flakyTest()

        when:
        succeeds('test')

        then:
        with(output) {
            it.count('PASSED') == 1
            it.count('FAILED') == 1
        }

        assertTestReportContains("FlakyTests", reportedTestName("flaky"), 1, 1)
    }

    def "optionally fail when flaky tests are detected"() {
        given:
        buildFile << """
            test.retry.maxRetries = 1
        """

        buildFile << """
            test.retry.failOnPassedAfterRetry = true
        """

        when:
        flakyTest()
        fails('test')

        then:
        // 1 initial test FAILED + 1 overall task FAILED (no BUILD FAILED in captured output)
        output.count('FAILED') == 1 + 0 + 1
    }

    def "default behaviour is to not retry"() {
        when:
        flakyTest()
        fails('test')

        then:
        // 1 initial test FAILED + 1 overall task FAILED (no BUILD FAILED in captured output)
        output.count('FAILED') == 1 + 0 + 1
    }

    def "retries stop after max failures is reached"() {
        given:
        buildFile << """
            test {
                retry {
                    maxRetries = 3
                    maxFailures = 1
                }
            }
        """

        when:
        failedTest()
        fails('test')

        then:
        // 1 initial test FAILED + 1 overall task FAILED (no BUILD FAILED in captured output)
        output.count('FAILED') == 1 + 0 + 1
    }
}
