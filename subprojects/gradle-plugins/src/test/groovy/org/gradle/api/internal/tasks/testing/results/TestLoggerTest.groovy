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



package org.gradle.api.internal.tasks.testing.results

import spock.lang.Specification
import org.gradle.logging.ProgressLoggerFactory
import org.gradle.logging.ProgressLogger
import org.gradle.api.tasks.testing.TestDescriptor
import org.gradle.api.tasks.testing.TestResult
import org.slf4j.Logger

class TestLoggerTest extends Specification {
    private final ProgressLoggerFactory factory = Mock()
    private final ProgressLogger progressLogger = Mock()
    private final TestDescriptor rootSuite = suite(true)
    private final Logger errorLogger = Mock()
    private final TestLogger logger = new TestLogger(factory, errorLogger)

    def startsProgressLoggerWhenRootSuiteIsStartedAndStopsWhenRootSuiteIsCompleted() {
        when:
        logger.beforeSuite(rootSuite)

        then:
        1 * factory.start(TestLogger.name) >> progressLogger

        when:
        logger.afterSuite(rootSuite, result())

        then:
        1 * progressLogger.completed()
    }

    def logsCountOfTestsExecuted() {
        TestDescriptor test1 = test()
        TestDescriptor test2 = test()

        1 * factory.start(TestLogger.name) >> progressLogger
        logger.beforeSuite(rootSuite)

        when:
        logger.afterTest(test1, result())

        then:
        1 * progressLogger.progress('1 test completed')

        when:
        logger.afterTest(test2, result())

        then:
        1 * progressLogger.progress('2 tests completed')

        when:
        logger.afterSuite(rootSuite, result())

        then:
        1 * progressLogger.completed()
    }

    def logsCountOfFailedTests() {
        TestDescriptor test1 = test()
        TestDescriptor test2 = test()

        1 * factory.start(TestLogger.name) >> progressLogger
        logger.beforeSuite(rootSuite)

        when:
        logger.afterTest(test1, result())

        then:
        1 * progressLogger.progress('1 test completed')

        when:
        logger.afterTest(test2, result(true))

        then:
        1 * progressLogger.progress('2 tests completed, 1 failure')

        when:
        logger.afterSuite(rootSuite, result())

        then:
        1 * errorLogger.error('2 tests completed, 1 failure')
        1 * progressLogger.completed()
    }

    def ignoresSuitesOtherThanTheRootSuite() {
        TestDescriptor suite = suite()

        1 * factory.start(TestLogger.name) >> progressLogger
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

    private def test() {
        [:] as TestDescriptor
    }
    
    private def suite(boolean root = false) {
        [getParent: {root ? null : [:] as TestDescriptor}] as TestDescriptor
    }

    private def result(boolean failed = false) {
        [getTestCount: { 1L }, getFailedTestCount: { failed ? 1L : 0L}] as TestResult
    }
}

