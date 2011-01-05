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

import org.gradle.api.internal.tasks.testing.TestCompleteEvent
import org.gradle.api.internal.tasks.testing.TestDescriptorInternal
import org.gradle.api.internal.tasks.testing.TestStartEvent
import org.gradle.api.tasks.testing.TestDescriptor
import org.gradle.api.tasks.testing.TestListener
import org.gradle.api.tasks.testing.TestResult
import org.gradle.api.tasks.testing.TestResult.ResultType
import org.gradle.util.JUnit4GroovyMockery
import org.jmock.integration.junit4.JMock
import org.junit.Test
import org.junit.runner.RunWith
import static org.gradle.util.Matchers.*
import static org.hamcrest.Matchers.*
import static org.junit.Assert.*

@RunWith(JMock.class)
class TestListenerAdapterTest {
    private final JUnit4GroovyMockery context = new JUnit4GroovyMockery()
    private final TestListener listener = context.mock(TestListener.class)
    private final TestListenerAdapter adapter = new TestListenerAdapter(listener)

    @Test
    public void createsAResultForATest() {
        TestDescriptorInternal test = test('id')

        context.checking {
            one(listener).beforeTest(withParam(notNullValue()))
            will { TestDescriptor t ->
                assertThat(t.descriptor, equalTo(test))
                assertThat(t.parent, nullValue())
            }
            one(listener).afterTest(withParam(notNullValue()), withParam(notNullValue()))
            will { TestDescriptor t, TestResult result ->
                assertThat(t.descriptor, equalTo(test))
                assertThat(result.resultType, equalTo(ResultType.SUCCESS))
                assertThat(result.exception, nullValue())
                assertThat(result.exceptions, isEmpty())
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

        TestDescriptorInternal test = test('id')

        context.checking {
            one(listener).beforeTest(withParam(notNullValue()))
            will { TestDescriptor t ->
                assertThat(t.descriptor, equalTo(test))
                assertThat(t.parent, nullValue())
            }
            one(listener).afterTest(withParam(notNullValue()), withParam(notNullValue()))
            will { TestDescriptor t, TestResult result ->
                assertThat(t.descriptor, equalTo(test))
                assertThat(result.resultType, equalTo(ResultType.FAILURE))
                assertThat(result.exception, sameInstance(failure))
                assertThat(result.exceptions, equalTo([failure]))
                assertThat(result.startTime, equalTo(100L))
                assertThat(result.endTime, equalTo(200L))
                assertThat(result.testCount, equalTo(1L))
                assertThat(result.successfulTestCount, equalTo(0L))
                assertThat(result.failedTestCount, equalTo(1L))
            }
        }

        adapter.started(test, new TestStartEvent(100L))
        adapter.failure('id', failure)
        adapter.completed('id', new TestCompleteEvent(200L))
    }

    @Test
    public void createsAResultForATestWithMultipleFailures() {
        RuntimeException failure1 = new RuntimeException()
        RuntimeException failure2 = new RuntimeException()

        TestDescriptorInternal test = test('id')

        context.checking {
            one(listener).beforeTest(withParam(notNullValue()))
            will { TestDescriptor t ->
                assertThat(t.descriptor, equalTo(test))
                assertThat(t.parent, nullValue())
            }
            one(listener).afterTest(withParam(notNullValue()), withParam(notNullValue()))
            will { TestDescriptor t, TestResult result ->
                assertThat(t.descriptor, equalTo(test))
                assertThat(result.resultType, equalTo(ResultType.FAILURE))
                assertThat(result.exception, sameInstance(failure1))
                assertThat(result.exceptions, equalTo([failure1, failure2]))
                assertThat(result.startTime, equalTo(100L))
                assertThat(result.endTime, equalTo(200L))
                assertThat(result.testCount, equalTo(1L))
                assertThat(result.successfulTestCount, equalTo(0L))
                assertThat(result.failedTestCount, equalTo(1L))
            }
        }

        adapter.started(test, new TestStartEvent(100L))
        adapter.failure('id', failure1)
        adapter.failure('id', failure2)
        adapter.completed('id', new TestCompleteEvent(200L))
    }

    @Test
    public void createsAResultForASkippedTest() {
        TestDescriptorInternal test = test('id')

        context.checking {
            one(listener).beforeTest(withParam(notNullValue()))
            will { TestDescriptor t ->
                assertThat(t.descriptor, equalTo(test))
                assertThat(t.parent, nullValue())
            }
            one(listener).afterTest(withParam(notNullValue()), withParam(notNullValue()))
            will { TestDescriptor t, TestResult result ->
                assertThat(t.descriptor, equalTo(test))
                assertThat(result.resultType, equalTo(ResultType.SKIPPED))
                assertThat(result.exception, nullValue())
                assertThat(result.exceptions, isEmpty())
                assertThat(result.startTime, equalTo(100L))
                assertThat(result.endTime, equalTo(200L))
                assertThat(result.testCount, equalTo(1L))
                assertThat(result.successfulTestCount, equalTo(0L))
                assertThat(result.failedTestCount, equalTo(0L))
            }
        }

        adapter.started(test, new TestStartEvent(100L))
        adapter.completed('id', new TestCompleteEvent(200L, ResultType.SKIPPED))
    }

    @Test
    public void createsAnAggregateResultForEmptyTestSuite() {
        TestDescriptorInternal suite = suite('id')

        context.checking {
            one(listener).beforeSuite(withParam(notNullValue()))
            will { TestDescriptor t ->
                assertThat(t.descriptor, equalTo(suite))
                assertThat(t.parent, nullValue())
            }
            one(listener).afterSuite(withParam(notNullValue()), withParam(notNullValue()))
            will { TestDescriptor t, TestResult result ->
                assertThat(t.descriptor, equalTo(suite))
                assertThat(result.resultType, equalTo(ResultType.SUCCESS))
                assertThat(result.exception, nullValue())
                assertThat(result.exceptions, isEmpty())
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
        TestDescriptorInternal suite = suite('id')
        TestDescriptorInternal test = test('testid')

        context.checking {
            one(listener).beforeSuite(withParam(notNullValue()))
            will { TestDescriptor t ->
                assertThat(t.descriptor, equalTo(suite))
                assertThat(t.parent, nullValue())
            }
            one(listener).beforeTest(withParam(notNullValue()))
            will { TestDescriptor t ->
                assertThat(t.descriptor, equalTo(test))
                assertThat(t.parent.descriptor, equalTo(suite))
            }
            one(listener).afterTest(withParam(notNullValue()), withParam(notNullValue()))
            will { TestDescriptor t ->
                assertThat(t.descriptor, equalTo(test))
            }
            one(listener).afterSuite(withParam(notNullValue()), withParam(notNullValue()))
            will { TestDescriptor t, TestResult result ->
                assertThat(t.descriptor, equalTo(suite))
                assertThat(result.resultType, equalTo(ResultType.SUCCESS))
                assertThat(result.exception, nullValue())
                assertThat(result.exceptions, isEmpty())
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
        TestDescriptorInternal suite = suite('id')
        TestDescriptorInternal ok = test('ok')
        TestDescriptorInternal broken = test('broken')

        context.checking {
            one(listener).beforeSuite(withParam(notNullValue()))
            will { TestDescriptor t ->
                assertThat(t.descriptor, equalTo(suite))
                assertThat(t.parent, nullValue())
            }
            one(listener).beforeTest(withParam(notNullValue()))
            will { TestDescriptor t ->
                assertThat(t.descriptor, equalTo(ok))
                assertThat(t.parent.descriptor, equalTo(suite))
            }
            one(listener).beforeTest(withParam(notNullValue()))
            will { TestDescriptor t ->
                assertThat(t.descriptor, equalTo(broken))
                assertThat(t.parent.descriptor, equalTo(suite))
            }
            one(listener).afterTest(withParam(notNullValue()), withParam(notNullValue()))
            one(listener).afterTest(withParam(notNullValue()), withParam(notNullValue()))
            one(listener).afterSuite(withParam(notNullValue()), withParam(notNullValue()))
            will { TestDescriptor t, TestResult result ->
                assertThat(t.descriptor, equalTo(suite))
                assertThat(result.resultType, equalTo(ResultType.FAILURE))
                assertThat(result.exception, nullValue())
                assertThat(result.exceptions, isEmpty())
                assertThat(result.testCount, equalTo(2L))
                assertThat(result.successfulTestCount, equalTo(1L))
                assertThat(result.failedTestCount, equalTo(1L))
            }
        }

        adapter.started(suite, new TestStartEvent(100L))
        adapter.started(ok, new TestStartEvent(100L, 'id'))
        adapter.started(broken, new TestStartEvent(100L, 'id'))
        adapter.completed('ok', new TestCompleteEvent(200L))
        adapter.failure('broken', new RuntimeException())
        adapter.completed('broken', new TestCompleteEvent(200L))
        adapter.completed('id', new TestCompleteEvent(200L))
    }

    @Test
    public void createsAnAggregateResultForTestSuiteWithSkippedTest() {
        TestDescriptorInternal suite = suite('id')
        TestDescriptorInternal test = test('testid')

        context.checking {
            one(listener).beforeSuite(withParam(notNullValue()))
            will { TestDescriptor t ->
                assertThat(t.descriptor, equalTo(suite))
                assertThat(t.parent, nullValue())
            }
            one(listener).beforeTest(withParam(notNullValue()))
            will { TestDescriptor t ->
                assertThat(t.descriptor, equalTo(test))
                assertThat(t.parent.descriptor, equalTo(suite))
            }
            one(listener).afterTest(withParam(notNullValue()), withParam(notNullValue()))
            one(listener).afterSuite(withParam(notNullValue()), withParam(notNullValue()))
            will { TestDescriptor t, TestResult result ->
                assertThat(t.descriptor, equalTo(suite))
                assertThat(result.resultType, equalTo(ResultType.SUCCESS))
                assertThat(result.exception, nullValue())
                assertThat(result.exceptions, isEmpty())
                assertThat(result.testCount, equalTo(1L))
                assertThat(result.successfulTestCount, equalTo(0L))
                assertThat(result.failedTestCount, equalTo(0L))
            }
        }

        adapter.started(suite, new TestStartEvent(100L))
        adapter.started(test, new TestStartEvent(100L, 'id'))
        adapter.completed('testid', new TestCompleteEvent(200L, ResultType.SKIPPED))
        adapter.completed('id', new TestCompleteEvent(200L))
    }

    @Test
    public void createsAnAggregateResultForTestSuiteWithNestedSuites() {
        TestDescriptorInternal root = suite('root')
        TestDescriptorInternal suite1 = suite('suite1')
        TestDescriptorInternal suite2 = suite('suite2')
        TestDescriptorInternal ok = test('ok')
        TestDescriptorInternal broken = test('broken')

        context.checking {
            one(listener).beforeSuite(withParam(notNullValue()))
            one(listener).beforeSuite(withParam(notNullValue()))
            one(listener).beforeTest(withParam(notNullValue()))
            one(listener).afterTest(withParam(notNullValue()), withParam(notNullValue()))
            one(listener).beforeSuite(withParam(notNullValue()))
            one(listener).beforeTest(withParam(notNullValue()))
            one(listener).afterTest(withParam(notNullValue()), withParam(notNullValue()))
            one(listener).afterSuite(withParam(notNullValue()), withParam(notNullValue()))
            will { TestDescriptor t, TestResult result ->
                assertThat(t.descriptor, equalTo(suite1))
                assertThat(result.resultType, equalTo(ResultType.SUCCESS))
                assertThat(result.exception, nullValue())
                assertThat(result.exceptions, isEmpty())
                assertThat(result.testCount, equalTo(1L))
                assertThat(result.successfulTestCount, equalTo(1L))
                assertThat(result.failedTestCount, equalTo(0L))
            }
            one(listener).afterSuite(withParam(notNullValue()), withParam(notNullValue()))
            will { TestDescriptor t, TestResult result ->
                assertThat(t.descriptor, equalTo(suite2))
                assertThat(result.resultType, equalTo(ResultType.FAILURE))
                assertThat(result.exception, nullValue())
                assertThat(result.exceptions, isEmpty())
                assertThat(result.testCount, equalTo(1L))
                assertThat(result.successfulTestCount, equalTo(0L))
                assertThat(result.failedTestCount, equalTo(1L))
            }
            one(listener).afterSuite(withParam(notNullValue()), withParam(notNullValue()))
            will { TestDescriptor t, TestResult result ->
                assertThat(t.descriptor, equalTo(root))
                assertThat(result.resultType, equalTo(ResultType.FAILURE))
                assertThat(result.exception, nullValue())
                assertThat(result.exceptions, isEmpty())
                assertThat(result.testCount, equalTo(2L))
                assertThat(result.successfulTestCount, equalTo(1L))
                assertThat(result.failedTestCount, equalTo(1L))
            }
        }

        adapter.started(root, new TestStartEvent(100L))
        adapter.started(suite1, new TestStartEvent(100L, 'root'))
        adapter.started(ok, new TestStartEvent(100L, 'suite1'))
        adapter.started(suite2, new TestStartEvent(100L, 'root'))
        adapter.completed('ok', new TestCompleteEvent(200L))
        adapter.completed('suite1', new TestCompleteEvent(200L))
        adapter.started(broken, new TestStartEvent(100L, 'suite2'))
        adapter.failure('broken', new RuntimeException())
        adapter.completed('broken', new TestCompleteEvent(200L))
        adapter.completed('suite2', new TestCompleteEvent(200L))
        adapter.completed('root', new TestCompleteEvent(200L))
    }

    @Test
    public void createsAnAggregateResultForTestSuiteWithFailure() {
        TestDescriptorInternal suite = suite('id')
        TestDescriptorInternal test = test('testid')
        RuntimeException failure = new RuntimeException()

        context.checking {
            one(listener).beforeSuite(withParam(notNullValue()))
            one(listener).beforeTest(withParam(notNullValue()))
            one(listener).afterTest(withParam(notNullValue()), withParam(notNullValue()))
            one(listener).afterSuite(withParam(notNullValue()), withParam(notNullValue()))
            will { TestDescriptor t, TestResult result ->
                assertThat(t.descriptor, equalTo(suite))
                assertThat(result.resultType, equalTo(ResultType.FAILURE))
                assertThat(result.exception, sameInstance(failure))
                assertThat(result.exceptions, equalTo([failure]))
            }
        }

        adapter.started(suite, new TestStartEvent(100L))
        adapter.started(test, new TestStartEvent(100L, 'id'))
        adapter.completed('testid', new TestCompleteEvent(200L, ResultType.SKIPPED))
        adapter.failure('id', failure)
        adapter.completed('id', new TestCompleteEvent(200L))
    }

    private TestDescriptorInternal test(String id) {
        TestDescriptorInternal test = context.mock(TestDescriptorInternal.class, id)
        context.checking {
            allowing(test).getId()
            will(returnValue(id))
            allowing(test).isComposite()
            will(returnValue(false))
        }
        return test
    }

    private TestDescriptorInternal suite(String id) {
        TestDescriptorInternal test = context.mock(TestDescriptorInternal.class, id)
        context.checking {
            allowing(test).getId()
            will(returnValue(id))
            allowing(test).isComposite()
            will(returnValue(true))
        }
        return test
    }
}
