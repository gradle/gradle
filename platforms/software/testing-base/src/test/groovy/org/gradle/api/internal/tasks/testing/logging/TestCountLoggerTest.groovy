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

import org.gradle.api.internal.tasks.testing.TestWorkerFailureException
import org.gradle.api.internal.tasks.testing.results.DefaultTestResult
import org.gradle.api.tasks.testing.TestDescriptor
import org.gradle.api.tasks.testing.TestFailure
import org.gradle.api.tasks.testing.TestResult
import org.gradle.internal.logging.progress.ProgressLogger
import org.gradle.internal.logging.progress.ProgressLoggerFactory
import org.gradle.util.internal.TextUtil
import org.slf4j.Logger
import spock.lang.Specification

class TestCountLoggerTest extends Specification {
    private final ProgressLoggerFactory factory = Mock()
    private final ProgressLogger progressLogger = Mock()
    private final TestDescriptor rootSuite = Stub() {
        getParent() >> null
        getClassName() >> null
    }
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
        TestDescriptor suite = Stub() {
            getParent() >> rootSuite
            getClassName() >> 'not-root'
        }

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

    def "discover worker failures"() {
        when:
        logger.beforeSuite(rootSuite)
        logger.afterSuite(rootSuite, result(true, 5))

        then:
        logger.hadFailures()
        logger.hasWorkerFailures()

        when:
        logger.handleWorkerFailures()
        then:
        def e = thrown(TestWorkerFailureException)
        e.getCauses().size() == 5
    }

    private test() {
        [:] as TestDescriptor
    }

    private result(boolean failed = false, int failureCount = 1) {
        if (failed) {
            List<TestFailure> failures = (1..failureCount).collect {Mock(TestFailure) }
            return new DefaultTestResult(TestResult.ResultType.FAILURE, 0, 0, 1, 0, 1, failures, null)
        }
        return new DefaultTestResult(TestResult.ResultType.SUCCESS, 0, 0, 1, 1, 0, [], null)
    }
}
