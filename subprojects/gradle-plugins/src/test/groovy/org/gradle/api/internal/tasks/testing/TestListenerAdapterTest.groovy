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

import org.gradle.api.tasks.testing.TestListener
import org.gradle.api.tasks.testing.TestResult
import org.gradle.util.JUnit4GroovyMockery
import org.jmock.integration.junit4.JMock
import org.junit.Test
import org.junit.runner.RunWith
import static org.junit.Assert.*
import static org.hamcrest.Matchers.*
import org.gradle.api.tasks.testing.TestResult.ResultType

@RunWith(JMock.class)
class TestListenerAdapterTest {
    private final JUnit4GroovyMockery context = new JUnit4GroovyMockery()
    private final TestListener listener = context.mock(TestListener.class)
    private final TestListenerAdapter adapter = new TestListenerAdapter(listener)

    @Test
    public void createsAResultForATest() {
        TestInternalDescriptor test = test('id')

        context.checking {
            one(listener).beforeTest(test)
            one(listener).afterTest(withParam(sameInstance(test)), withParam(notNullValue()))
            will { arg, TestResult result ->
                assertThat(result.resultType, equalTo(ResultType.SUCCESS))
                assertThat(result.startTime, equalTo(100L))
                assertThat(result.endTime, equalTo(200L))
                assertThat(result.testCount, equalTo(1L))
                assertThat(result.successfulTestCount, equalTo(1L))
                assertThat(result.failedTestCount, equalTo(0L))
            }
        }

        adapter.started(test, new TestStartEvent(100L))
        adapter.completed('id', new TestCompleteEvent(200L))
    }

    @Test
    public void createsAResultForATestWithFailure() {
        RuntimeException failure = new RuntimeException()

        TestInternalDescriptor test = test('id')

        context.checking {
            one(listener).beforeTest(test)
            one(listener).afterTest(withParam(sameInstance(test)), withParam(notNullValue()))
            will { arg, TestResult result ->
                assertThat(result.resultType, equalTo(ResultType.FAILURE))
                assertThat(result.exception, sameInstance(failure))
                assertThat(result.startTime, equalTo(100L))
                assertThat(result.endTime, equalTo(200L))
                assertThat(result.testCount, equalTo(1L))
                assertThat(result.successfulTestCount, equalTo(0L))
                assertThat(result.failedTestCount, equalTo(1L))
            }
        }

        adapter.started(test, new TestStartEvent(100L))
        adapter.addFailure('id', failure)
        adapter.completed('id', new TestCompleteEvent(200L))
    }

    @Test
    public void createsAResultForATestWithFailureInEndEvent() {
        RuntimeException failure = new RuntimeException()

        TestInternalDescriptor test = test('id')

        context.checking {
            one(listener).beforeTest(test)
            one(listener).afterTest(withParam(sameInstance(test)), withParam(notNullValue()))
            will { arg, TestResult result ->
                assertThat(result.resultType, equalTo(ResultType.FAILURE))
                assertThat(result.exception, sameInstance(failure))
                assertThat(result.startTime, equalTo(100L))
                assertThat(result.endTime, equalTo(200L))
                assertThat(result.testCount, equalTo(1L))
                assertThat(result.successfulTestCount, equalTo(0L))
                assertThat(result.failedTestCount, equalTo(1L))
            }
        }

        adapter.started(test, new TestStartEvent(100L))
        adapter.completed('id', new TestCompleteEvent(200L, ResultType.FAILURE, failure))
    }

    @Test
    public void createsAResultForASkippedTest() {
        TestInternalDescriptor test = test('id')

        context.checking {
            one(listener).beforeTest(test)
            one(listener).afterTest(withParam(sameInstance(test)), withParam(notNullValue()))
            will { arg, TestResult result ->
                assertThat(result.resultType, equalTo(ResultType.SKIPPED))
                assertThat(result.startTime, equalTo(100L))
                assertThat(result.endTime, equalTo(200L))
                assertThat(result.testCount, equalTo(1L))
                assertThat(result.successfulTestCount, equalTo(0L))
                assertThat(result.failedTestCount, equalTo(0L))
            }
        }

        adapter.started(test, new TestStartEvent(100L))
        adapter.completed('id', new TestCompleteEvent(200L, ResultType.SKIPPED, null))
    }

    @Test
    public void createsAnAggregateResultForEmptyTestSuite() {
        TestInternalDescriptor suite = suite('id')

        context.checking {
            one(listener).beforeSuite(suite)
            one(listener).afterSuite(withParam(sameInstance(suite)), withParam(notNullValue()))
            will { arg, TestResult result ->
                assertThat(result.resultType, equalTo(ResultType.SUCCESS))
                assertThat(result.startTime, equalTo(100L))
                assertThat(result.endTime, equalTo(200L))
                assertThat(result.testCount, equalTo(0L))
                assertThat(result.successfulTestCount, equalTo(0L))
                assertThat(result.failedTestCount, equalTo(0L))
            }
        }

        adapter.started(suite, new TestStartEvent(100L))
        adapter.completed('id', new TestCompleteEvent(200L))
    }

    @Test
    public void createsAnAggregateResultForTestSuiteWithPassedTest() {
        TestInternalDescriptor suite = suite('id')
        TestInternalDescriptor test = test('testid')

        context.checking {
            one(listener).beforeSuite(suite)
            one(test).setParent(suite)
            one(listener).beforeTest(test)
            one(listener).afterTest(withParam(sameInstance(test)), withParam(notNullValue()))
            one(listener).afterSuite(withParam(sameInstance(suite)), withParam(notNullValue()))
            will { arg, TestResult result ->
                assertThat(result.resultType, equalTo(ResultType.SUCCESS))
                assertThat(result.testCount, equalTo(1L))
                assertThat(result.successfulTestCount, equalTo(1L))
                assertThat(result.failedTestCount, equalTo(0L))
            }
        }

        adapter.started(suite, new TestStartEvent(100L))
        adapter.started(test, new TestStartEvent(100L, 'id'))
        adapter.completed('testid', new TestCompleteEvent(200L))
        adapter.completed('id', new TestCompleteEvent(200L))
    }

    @Test
    public void createsAnAggregateResultForTestSuiteWithFailedTest() {
        TestInternalDescriptor suite = suite('id')
        TestInternalDescriptor ok = test('ok')
        TestInternalDescriptor broken = test('broken')

        context.checking {
            one(listener).beforeSuite(suite)
            one(ok).setParent(suite)
            one(listener).beforeTest(ok)
            one(broken).setParent(suite)
            one(listener).beforeTest(broken)
            one(listener).afterTest(withParam(sameInstance(ok)), withParam(notNullValue()))
            one(listener).afterTest(withParam(sameInstance(broken)), withParam(notNullValue()))
            one(listener).afterSuite(withParam(sameInstance(suite)), withParam(notNullValue()))
            will { arg, TestResult result ->
                assertThat(result.resultType, equalTo(ResultType.FAILURE))
                assertThat(result.testCount, equalTo(2L))
                assertThat(result.successfulTestCount, equalTo(1L))
                assertThat(result.failedTestCount, equalTo(1L))
            }
        }

        adapter.started(suite, new TestStartEvent(100L))
        adapter.started(ok, new TestStartEvent(100L, 'id'))
        adapter.started(broken, new TestStartEvent(100L, 'id'))
        adapter.completed('ok', new TestCompleteEvent(200L))
        adapter.completed('broken', new TestCompleteEvent(200L, ResultType.FAILURE, new RuntimeException()))
        adapter.completed('id', new TestCompleteEvent(200L))
    }

    @Test
    public void createsAnAggregateResultForTestSuiteWithSkippedTest() {
        TestInternalDescriptor suite = suite('id')
        TestInternalDescriptor test = test('testid')

        context.checking {
            one(listener).beforeSuite(suite)
            one(test).setParent(suite)
            one(listener).beforeTest(test)
            one(listener).afterTest(withParam(sameInstance(test)), withParam(notNullValue()))
            one(listener).afterSuite(withParam(sameInstance(suite)), withParam(notNullValue()))
            will { arg, TestResult result ->
                assertThat(result.resultType, equalTo(ResultType.SUCCESS))
                assertThat(result.testCount, equalTo(1L))
                assertThat(result.successfulTestCount, equalTo(0L))
                assertThat(result.failedTestCount, equalTo(0L))
            }
        }

        adapter.started(suite, new TestStartEvent(100L))
        adapter.started(test, new TestStartEvent(100L, 'id'))
        adapter.completed('testid', new TestCompleteEvent(200L, ResultType.SKIPPED, null))
        adapter.completed('id', new TestCompleteEvent(200L))
    }

    @Test
    public void createsAnAggregateResultForTestSuiteWithNestedSuites() {
        TestInternalDescriptor root = suite('root')
        TestInternalDescriptor suite1 = suite('suite1')
        TestInternalDescriptor suite2 = suite('suite2')
        TestInternalDescriptor ok = test('ok')
        TestInternalDescriptor broken = test('broken')

        context.checking {
            one(listener).beforeSuite(root)
            one(suite1).setParent(root)
            one(listener).beforeSuite(suite1)
            one(ok).setParent(suite1)
            one(listener).beforeTest(ok)
            one(listener).afterTest(withParam(sameInstance(ok)), withParam(notNullValue()))
            one(suite2).setParent(root)
            one(listener).beforeSuite(suite2)
            one(broken).setParent(suite2)
            one(listener).beforeTest(broken)
            one(listener).afterTest(withParam(sameInstance(broken)), withParam(notNullValue()))
            one(listener).afterSuite(withParam(sameInstance(suite1)), withParam(notNullValue()))
            will { arg, TestResult result ->
                assertThat(result.resultType, equalTo(ResultType.SUCCESS))
                assertThat(result.testCount, equalTo(1L))
                assertThat(result.successfulTestCount, equalTo(1L))
                assertThat(result.failedTestCount, equalTo(0L))
            }
            one(listener).afterSuite(withParam(sameInstance(suite2)), withParam(notNullValue()))
            will { arg, TestResult result ->
                assertThat(result.resultType, equalTo(ResultType.FAILURE))
                assertThat(result.testCount, equalTo(1L))
                assertThat(result.successfulTestCount, equalTo(0L))
                assertThat(result.failedTestCount, equalTo(1L))
            }
            one(listener).afterSuite(withParam(sameInstance(root)), withParam(notNullValue()))
            will { arg, TestResult result ->
                assertThat(result.resultType, equalTo(ResultType.FAILURE))
                assertThat(result.testCount, equalTo(2L))
                assertThat(result.successfulTestCount, equalTo(1L))
                assertThat(result.failedTestCount, equalTo(1L))
            }
        }

        adapter.started(root, new TestStartEvent(100L))
        adapter.started(suite1, new TestStartEvent(100L, 'root'))
        adapter.started(ok, new TestStartEvent(100L, 'suite1'))
        adapter.started(suite2, new TestStartEvent(100L, 'root'))
        adapter.started(broken, new TestStartEvent(100L, 'suite2'))
        adapter.completed('ok', new TestCompleteEvent(200L))
        adapter.completed('broken', new TestCompleteEvent(200L, ResultType.FAILURE, new RuntimeException()))
        adapter.completed('suite1', new TestCompleteEvent(200L))
        adapter.completed('suite2', new TestCompleteEvent(200L))
        adapter.completed('root', new TestCompleteEvent(200L))
    }

    @Test
    public void createsAnAggregateResultForTestSuiteWithFailure() {
        TestInternalDescriptor suite = suite('id')
        TestInternalDescriptor test = test('testid')
        RuntimeException failure = new RuntimeException()

        context.checking {
            one(listener).beforeSuite(suite)
            one(test).setParent(suite)
            one(listener).beforeTest(test)
            one(listener).afterTest(withParam(sameInstance(test)), withParam(notNullValue()))
            one(listener).afterSuite(withParam(sameInstance(suite)), withParam(notNullValue()))
            will { arg, TestResult result ->
                assertThat(result.resultType, equalTo(ResultType.FAILURE))
                assertThat(result.exception, sameInstance(failure))
            }
        }

        adapter.started(suite, new TestStartEvent(100L))
        adapter.started(test, new TestStartEvent(100L, 'id'))
        adapter.completed('testid', new TestCompleteEvent(200L, ResultType.SKIPPED, null))
        adapter.addFailure('id', failure)
        adapter.completed('id', new TestCompleteEvent(200L))
    }

    @Test
    public void createsAnAggregateResultForTestSuiteWithFailureInEndEvent() {
        TestInternalDescriptor suite = suite('id')
        TestInternalDescriptor test = test('testid')
        RuntimeException failure = new RuntimeException()

        context.checking {
            one(listener).beforeSuite(suite)
            one(test).setParent(suite)
            one(listener).beforeTest(test)
            one(listener).afterTest(withParam(sameInstance(test)), withParam(notNullValue()))
            one(listener).afterSuite(withParam(sameInstance(suite)), withParam(notNullValue()))
            will { arg, TestResult result ->
                assertThat(result.resultType, equalTo(ResultType.FAILURE))
                assertThat(result.exception, sameInstance(failure))
            }
        }

        adapter.started(suite, new TestStartEvent(100L))
        adapter.started(test, new TestStartEvent(100L, 'id'))
        adapter.completed('testid', new TestCompleteEvent(200L, ResultType.SKIPPED, null))
        adapter.completed('id', new TestCompleteEvent(200L, ResultType.FAILURE, failure))
    }

    private TestInternalDescriptor test(String id) {
        TestInternalDescriptor test = context.mock(TestInternalDescriptor.class, id)
        context.checking {
            allowing(test).getId()
            will(returnValue(id))
            allowing(test).isComposite()
            will(returnValue(false))
        }
        return test
    }

    private TestInternalDescriptor suite(String id) {
        TestInternalDescriptor test = context.mock(TestInternalDescriptor.class, id)
        context.checking {
            allowing(test).getId()
            will(returnValue(id))
            allowing(test).isComposite()
            will(returnValue(true))
        }
        return test
    }
}
