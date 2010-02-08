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
package org.gradle.api.internal.tasks.testing

import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.TestResult
import org.gradle.util.JUnit4GroovyMockery
import org.jmock.integration.junit4.JMock
import org.junit.runner.RunWith
import org.slf4j.Logger

@RunWith(JMock.class)
public class TestSummaryListenerTest {
    private final JUnit4GroovyMockery context = new JUnit4GroovyMockery()
    private final Logger logger = context.mock(Logger.class)
    private final TestSummaryListener listener = new TestSummaryListener(logger)

    @org.junit.Test
    public void logsSuccessfulTests() {
        context.checking {
            one(logger).info('Test {} PASSED', '<test>')
        }
        listener.afterTest(test('<test>'), result(TestResult.ResultType.SUCCESS))
    }

    @org.junit.Test
    public void logsSkippedTests() {
        context.checking {
            one(logger).info('Test {} SKIPPED', '<test>')
        }
        listener.afterTest(test('<test>'), result(TestResult.ResultType.SKIPPED))
    }

    @org.junit.Test
    public void logsFailedTestExecution() {
        context.checking {
            one(logger).error('Test {} FAILED', '<test>')
        }
        listener.afterTest(test('<test>'), result(TestResult.ResultType.FAILURE))
    }

    private TestResult result(TestResult.ResultType type) {
        return {-> type} as TestResult
    }

    private Test test(String name) {
        return {-> name} as Test
    }
}

