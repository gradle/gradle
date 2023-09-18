/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.api.internal.tasks.testing.logging

import org.gradle.internal.logging.progress.ProgressLoggerFactory
import org.gradle.internal.logging.progress.ProgressLogger
import org.gradle.api.tasks.testing.TestDescriptor
import org.gradle.api.tasks.testing.TestResult
import org.slf4j.Logger

import spock.lang.Specification
import org.gradle.util.internal.TextUtil

class TestCountLoggerTest extends Specification {
    private final ProgressLoggerFactory factory = Mock()
    private final ProgressLogger progressLogger = Mock()
    private final TestDescriptor rootSuite = suite(true)
    private final Logger errorLogger = Mock()
    private final TestCountLogger logger = new TestCountLogger(factory, errorLogger)
    private final String sep = TextUtil.platformLineSeparator

    def setup() {
        factory.newOperation(TestCountLogger) >> progressLogger
    }

    def startsProgressLoggerWhenRootSuiteIsStartedAndStopsWhenRootSuiteIsCompleted() {
        when:
        logger.beforeSuite(rootSuite)

        then:
        1 * progressLogger.started()

        when:
        logger.afterSuite(rootSuite, result())

        then:
        1 * progressLogger.completed()
    }

    def logsCountOfTestsExecuted() {
        TestDescriptor test1 = test()
        TestDescriptor test2 = test()

        logger.beforeSuite(rootSuite)

        when:
        logger.afterTest(test1, result())
        logger.beforeTest(test2)

        then:
        1 * progressLogger.progress('1 test completed')

        when:
        logger.afterTest(test2, result())
        logger.afterSuite(rootSuite, result())

        then:
        1 * progressLogger.progress('2 tests completed')
        1 * progressLogger.completed()
    }

    def logsCountOfFailedTests() {
        TestDescriptor test1 = test()
        TestDescriptor test2 = test()

        logger.beforeSuite(rootSuite)

        when:
        logger.afterTest(test1, result())
        logger.beforeTest(test2)

        then:
        1 * progressLogger.progress('1 test completed')

        when:
        logger.afterTest(test2, result(true))
        logger.afterSuite(rootSuite, result())

        then:
        1 * progressLogger.progress('2 tests completed, 1 failed')
        1 * errorLogger.error("${sep}2 tests completed, 1 failed")
        1 * progressLogger.completed()
    }

    def ignoresSuitesOtherThanTheRootSuite() {
        TestDescriptor suite = suite()

        logger.beforeSuite(rootSuite)

        when:
        logger.beforeSuite(suite)
        logger.afterSuite(suite, result())

        then:
        0 * progressLogger._

        when:
        logger.afterSuite(rootSuite, result())

        then:
        1 * progressLogger.completed()
    }

    def "remembers whether any root suite reported failure and sums up total tests"() {
        when:
        logger.beforeSuite(rootSuite)
        logger.beforeTest(test())
        logger.afterTest(test(), result())
        logger.afterSuite(rootSuite, result())

        then:
        !logger.hadFailures()
        logger.totalTests == 1

        when:
        logger.beforeSuite(rootSuite)
        logger.beforeTest(test())
        logger.afterTest(test(), result())
        logger.afterSuite(rootSuite, result(true))

        then:
        logger.hadFailures()
        logger.totalTests == 2
    }

    private test() {
        [:] as TestDescriptor
    }

    private suite(boolean root = false) {
        [getParent: {root ? null : [:] as TestDescriptor}] as TestDescriptor
    }

    private result(boolean failed = false) {
        [getTestCount: { 1L }, getFailedTestCount: { failed ? 1L : 0L }, getSkippedTestCount: { 0L },
                getResultType: { failed ? TestResult.ResultType.FAILURE : TestResult.ResultType.SUCCESS }] as TestResult
    }
}
