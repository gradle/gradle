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

    def "has no effect when all tests pass (gradle version #gradleVersion)"() {
        when:
        buildFile << """
            test.retry.maxRetries = 1
        """

        successfulTest()

        then:
        def result = gradleRunner(gradleVersion).build()

        and:
        result.output.count('PASSED') == 1

        where:
        gradleVersion << GRADLE_VERSIONS_UNDER_TEST
    }

    def "is benign when unconfigured (gradle version #gradleVersion)"() {
        when:
        successfulTest()
        gradleRunner(gradleVersion).build()

        then:
        assertTestReportContains("SuccessfulTests", reportedTestName("successTest"), 1, 0)

        where:
        gradleVersion << GRADLE_VERSIONS_UNDER_TEST
    }

    def "does not retry by default (gradle version #gradleVersion)"() {
        when:
        failedTest()
        def result = gradleRunner(gradleVersion).buildAndFail()

        then:
        result.output.contains("There were failing tests.")
        assertTestReportContains("FailedTests", reportedTestName("failedTest"), 0, 1)

        where:
        gradleVersion << GRADLE_VERSIONS_UNDER_TEST
    }

    def "retries failed tests (gradle version #gradleVersion)"() {
        given:
        buildFile << """
            test.retry.maxRetries = 1
        """

        successfulTest()
        failedTest()

        when:
        def result = gradleRunner(gradleVersion).buildAndFail()

        then: 'Only the failed test is retried a second time'
        result.output.count('PASSED') == 1

        // 2 individual tests FAILED + 1 overall task FAILED + 1 overall build FAILED
        result.output.count('FAILED') == 2 + 1 + 1

        assertTestReportContains("SuccessfulTests", reportedTestName("successTest"), 1, 0)
        assertTestReportContains("FailedTests", reportedTestName("failedTest"), 0, 2)

        where:
        gradleVersion << GRADLE_VERSIONS_UNDER_TEST
    }

    def "still publishes test report when test is un-retryable (gradle version #gradleVersion)"() {
        given:
        buildFile << """
            test.retry.maxRetries = 1
        """

        failedTest()

        when:
        try {
            System.setProperty(TestRetryTaskExtensionAdapter.SIMULATE_NOT_RETRYABLE_PROPERTY, "true")
            gradleRunner(gradleVersion).buildAndFail()
        } finally {
            System.clearProperty(TestRetryTaskExtensionAdapter.SIMULATE_NOT_RETRYABLE_PROPERTY)
        }

        then:
        assertTestReportContains("FailedTests", reportedTestName("failedTest"), 0, 1)

        where:
        gradleVersion << GRADLE_VERSIONS_UNDER_TEST
    }

    def "stops when all tests pass (gradle version #gradleVersion)"() {
        given:
        buildFile << """
            test.retry.maxRetries = 1
        """

        flakyTest()

        when:
        def result = gradleRunner(gradleVersion).build()

        then:
        with(result.output) {
            it.count('PASSED') == 1
            it.count('FAILED') == 1
        }

        assertTestReportContains("FlakyTests", reportedTestName("flaky"), 1, 1)

        where:
        gradleVersion << GRADLE_VERSIONS_UNDER_TEST
    }

    def "optionally fail when flaky tests are detected (gradle version #gradleVersion)"() {
        given:
        buildFile << """
            test.retry.maxRetries = 1
        """

        buildFile << """
            test.retry.failOnPassedAfterRetry = true
        """

        when:
        flakyTest()

        then:
        def result = gradleRunner(gradleVersion).buildAndFail()
        // 1 initial + 1 retries + 1 overall task FAILED + 1 build FAILED
        result.output.count('FAILED') == 1 + 0 + 1 + 1

        where:
        gradleVersion << GRADLE_VERSIONS_UNDER_TEST
    }

    def "default behaviour is to not retry (gradle version #gradleVersion)"() {
        when:
        flakyTest()

        then:
        def result = gradleRunner(gradleVersion).buildAndFail()

        expect:
        // 1 initial + 0 retries + 1 overall task FAILED + 1 build FAILED
        result.output.count('FAILED') == 1 + 0 + 1 + 1

        where:
        gradleVersion << GRADLE_VERSIONS_UNDER_TEST
    }

    def "retries stop after max failures is reached (gradle version #gradleVersion)"() {
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

        then:
        def result = gradleRunner(gradleVersion).buildAndFail()
        // 1 initial + 0 retries + 1 overall task FAILED + 1 build FAILED
        result.output.count('FAILED') == 1 + 0 + 1 + 1

        where:
        gradleVersion << GRADLE_VERSIONS_UNDER_TEST
    }
}
