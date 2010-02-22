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
        TestInternal test = test('id')

        context.checking {
            one(listener).beforeTest(test)
            one(listener).afterTest(withParam(sameInstance(test)), withParam(notNullValue()))
            will { arg, TestResult result ->
                assertThat(result.resultType, equalTo(ResultType.SUCCESS))
                assertThat(result.startTime, equalTo(100L))
                assertThat(result.endTime, equalTo(200L))
            }
        }

        adapter.started(test, new TestStartEvent(100L))
        adapter.completed('id', new TestCompleteEvent(200L))
    }

    @Test
    public void createsAResultForATestWithFailure() {
        RuntimeException failure = new RuntimeException()

        TestInternal test = test('id')

        context.checking {
            one(listener).beforeTest(test)
            one(listener).afterTest(withParam(sameInstance(test)), withParam(notNullValue()))
            will { arg, TestResult result ->
                assertThat(result.resultType, equalTo(ResultType.FAILURE))
                assertThat(result.exception, sameInstance(failure))
                assertThat(result.startTime, equalTo(100L))
                assertThat(result.endTime, equalTo(200L))
            }
        }

        adapter.started(test, new TestStartEvent(100L))
        adapter.addFailure('id', failure)
        adapter.completed('id', new TestCompleteEvent(200L))
    }

    @Test
    public void createsAResultForATestWithFailureInEndEvent() {
        RuntimeException failure = new RuntimeException()

        TestInternal test = test('id')

        context.checking {
            one(listener).beforeTest(test)
            one(listener).afterTest(withParam(sameInstance(test)), withParam(notNullValue()))
            will { arg, TestResult result ->
                assertThat(result.resultType, equalTo(ResultType.FAILURE))
                assertThat(result.exception, sameInstance(failure))
                assertThat(result.startTime, equalTo(100L))
                assertThat(result.endTime, equalTo(200L))
            }
        }

        adapter.started(test, new TestStartEvent(100L))
        adapter.completed('id', new TestCompleteEvent(200L, ResultType.FAILURE, failure))
    }

    @Test
    public void createsAResultForASkippedTest() {
        TestInternal test = test('id')

        context.checking {
            one(listener).beforeTest(test)
            one(listener).afterTest(withParam(sameInstance(test)), withParam(notNullValue()))
            will { arg, TestResult result ->
                assertThat(result.resultType, equalTo(ResultType.SKIPPED))
                assertThat(result.startTime, equalTo(100L))
                assertThat(result.endTime, equalTo(200L))
            }
        }

        adapter.started(test, new TestStartEvent(100L))
        adapter.completed('id', new TestCompleteEvent(200L, ResultType.SKIPPED, null))
    }

    @Test
    public void createsAnAggregateResultForEmptyTestSuite() {
        TestInternal suite = suite('id')

        context.checking {
            one(listener).beforeSuite(suite)
            one(listener).afterSuite(withParam(sameInstance(suite)), withParam(notNullValue()))
            will { arg, TestResult result ->
                assertThat(result.resultType, equalTo(ResultType.SUCCESS))
                assertThat(result.startTime, equalTo(100L))
                assertThat(result.endTime, equalTo(200L))
            }
        }

        adapter.started(suite, new TestStartEvent(100L))
        adapter.completed('id', new TestCompleteEvent(200L))
    }

    @Test
    public void createsAnAggregateResultForTestSuiteWithPassedTest() {
        TestInternal suite = suite('id')
        TestInternal test = test('testid')

        context.checking {
            one(listener).beforeSuite(suite)
            one(listener).beforeTest(test)
            one(listener).afterTest(withParam(sameInstance(test)), withParam(notNullValue()))
            one(listener).afterSuite(withParam(sameInstance(suite)), withParam(notNullValue()))
            will { arg, TestResult result ->
                assertThat(result.resultType, equalTo(ResultType.SUCCESS))
            }
        }

        adapter.started(suite, new TestStartEvent(100L))
        adapter.started(test, new TestStartEvent(100L, 'id'))
        adapter.completed('testid', new TestCompleteEvent(200L))
        adapter.completed('id', new TestCompleteEvent(200L))
    }

    @Test
    public void createsAnAggregateResultForTestSuiteWithFailedTest() {
        TestInternal suite = suite('id')
        TestInternal ok = test('ok')
        TestInternal broken = test('broken')

        context.checking {
            one(listener).beforeSuite(suite)
            one(listener).beforeTest(ok)
            one(listener).beforeTest(broken)
            one(listener).afterTest(withParam(sameInstance(ok)), withParam(notNullValue()))
            one(listener).afterTest(withParam(sameInstance(broken)), withParam(notNullValue()))
            one(listener).afterSuite(withParam(sameInstance(suite)), withParam(notNullValue()))
            will { arg, TestResult result ->
                assertThat(result.resultType, equalTo(ResultType.FAILURE))
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
        TestInternal suite = suite('id')
        TestInternal test = test('testid')

        context.checking {
            one(listener).beforeSuite(suite)
            one(listener).beforeTest(test)
            one(listener).afterTest(withParam(sameInstance(test)), withParam(notNullValue()))
            one(listener).afterSuite(withParam(sameInstance(suite)), withParam(notNullValue()))
            will { arg, TestResult result ->
                assertThat(result.resultType, equalTo(ResultType.SUCCESS))
            }
        }

        adapter.started(suite, new TestStartEvent(100L))
        adapter.started(test, new TestStartEvent(100L, 'id'))
        adapter.completed('testid', new TestCompleteEvent(200L, ResultType.SKIPPED, null))
        adapter.completed('id', new TestCompleteEvent(200L))
    }

    @Test
    public void createsAnAggregateResultForTestSuiteWithFailure() {
        TestInternal suite = suite('id')
        TestInternal test = test('testid')
        RuntimeException failure = new RuntimeException()

        context.checking {
            one(listener).beforeSuite(suite)
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
        TestInternal suite = suite('id')
        TestInternal test = test('testid')
        RuntimeException failure = new RuntimeException()

        context.checking {
            one(listener).beforeSuite(suite)
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

    private TestInternal test(String id) {
        TestInternal test = context.mock(TestInternal.class, id)
        context.checking {
            allowing(test).getId()
            will(returnValue(id))
            allowing(test).isComposite()
            will(returnValue(false))
        }
        return test
    }

    private TestInternal suite(String id) {
        TestInternal test = context.mock(TestInternal.class, id)
        context.checking {
            allowing(test).getId()
            will(returnValue(id))
            allowing(test).isComposite()
            will(returnValue(true))
        }
        return test
    }
}
