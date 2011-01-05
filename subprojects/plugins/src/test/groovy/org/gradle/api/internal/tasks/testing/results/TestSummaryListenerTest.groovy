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

import org.gradle.api.tasks.testing.TestDescriptor
import org.gradle.api.tasks.testing.TestResult
import org.gradle.util.JUnit4GroovyMockery
import org.jmock.integration.junit4.JMock
import org.junit.runner.RunWith
import org.slf4j.Logger
import org.junit.Test
import org.junit.Before
import static org.junit.Assert.*
import org.gradle.api.internal.tasks.testing.TestSuiteExecutionException

@RunWith(JMock.class)
public class TestSummaryListenerTest {
    private final JUnit4GroovyMockery context = new JUnit4GroovyMockery()
    private final Logger logger = context.mock(Logger.class)
    private final RuntimeException failure = new RuntimeException()
    private final TestSummaryListener listener = new TestSummaryListener(logger)

    @Before
    public void setup() {
        context.checking {
            ignoring(logger).debug(withParam(anything()), (Object) withParam(anything()))
        }
    }

    @Test
    public void logsSuccessfulTests() {
        context.checking {
            one(logger).info('{} PASSED', '<test>')
        }
        listener.afterTest(test('<test>'), result(TestResult.ResultType.SUCCESS))
    }

    @Test
    public void logsSkippedTests() {
        context.checking {
            one(logger).info('{} SKIPPED', '<test>')
        }
        listener.afterTest(test('<test>'), result(TestResult.ResultType.SKIPPED))
    }

    @Test
    public void logsFailedTestExecution() {
        context.checking {
            one(logger).info('{} FAILED: {}', '<test>', failure)
            one(logger).error('Test {} FAILED', '<class>')
        }
        listener.afterTest(test('<test>', '<class>'), result(TestResult.ResultType.FAILURE))
    }

    @Test
    public void logsFailedTestExecutionWhenTestHasNoClass() {
        context.checking {
            one(logger).error('{} FAILED: {}', '<test>', failure)
        }
        listener.afterTest(test('<test>'), result(TestResult.ResultType.FAILURE))
    }

    @Test
    public void logsFailedSuiteExecution() {
        context.checking {
            one(logger).info('{} FAILED: {}', '<test>', failure)
            one(logger).error('Test {} FAILED', '<class>')
        }
        listener.afterSuite(test('<test>', '<class>'), result(TestResult.ResultType.FAILURE))
    }

    @Test
    public void logsFailedSuiteExecutionWhenSuiteHasNoClass() {
        context.checking {
            one(logger).error('{} FAILED: {}', '<test>', failure)
        }
        listener.afterSuite(test('<test>'), result(TestResult.ResultType.FAILURE))
    }

    @Test
    public void logsFailedSuiteExecutionWhenSuiteHasNoException() {
        listener.afterSuite(test('<test>'), result(TestResult.ResultType.FAILURE, null))
    }

    @Test
    public void logsSuiteInternalException() {
        TestSuiteExecutionException failure = new TestSuiteExecutionException('broken', new RuntimeException())
        context.checking {
            one(logger).error('Execution for <test> FAILED', failure)
        }
        listener.afterSuite(test('<test>'), result(TestResult.ResultType.FAILURE, failure))
    }

    @Test
    public void doesNotLogFailedClassMoreThanOnce() {
        context.checking {
            ignoring(logger).info(withParam(anything()), (Object) withParam(anything()), (Object) withParam(anything()))
            one(logger).error('Test {} FAILED', '<class>')
        }
        listener.afterTest(test('<test1>', '<class>'), result(TestResult.ResultType.FAILURE))
        listener.afterTest(test('<test2>', '<class>'), result(TestResult.ResultType.FAILURE))
        listener.afterSuite(test('<test3>', '<class>'), result(TestResult.ResultType.FAILURE))
    }

    @Test
    public void usesRootSuiteResultsToDetermineIfTestsHasFailed() {
        listener.afterSuite(test('<test>', null, null), result(TestResult.ResultType.FAILURE, null, 3, 5))
        assertTrue(listener.hadFailures())
    }

    private TestResult result(TestResult.ResultType type, Throwable failure = this.failure, long failures = 0, long total = 0) {
        return [getResultType: {-> type}, getException: {-> failure}, getTestCount: {-> total}, getFailedTestCount: {-> failures}] as TestResult
    }

    private TestDescriptor test(String name, String className = null, TestDescriptor parent = [:] as TestDescriptor) {
        return [toString: {-> name}, getClassName: {-> className}, getParent: {-> parent}] as TestDescriptor
    }
}

