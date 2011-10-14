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

import org.gradle.api.tasks.testing.OutputListener
import org.gradle.api.tasks.testing.TestDescriptor
import org.gradle.api.tasks.testing.TestListener
import org.gradle.api.tasks.testing.TestResult
import org.gradle.api.tasks.testing.TestResult.ResultType
import org.junit.Test
import spock.lang.Ignore
import spock.lang.Specification
import org.gradle.api.internal.tasks.testing.*
import static org.gradle.util.Matchers.isEmpty
import static org.hamcrest.Matchers.*
import static org.junit.Assert.assertThat

class TestListenerAdapterTest extends Specification {

    def listener = Mock(TestListener.class)
    def outputListener = Mock(OutputListener.class)
    TestListenerAdapter adapter = new TestListenerAdapter(listener, outputListener)

    public void notifiesBefore() {
        given:
        TestDescriptor test = new DefaultTestDescriptor("id", "Foo", "bar");

        when:
        adapter.started(test, new TestStartEvent(100L))

        then:
        1 * listener.beforeTest({ it instanceof DecoratingTestDescriptor && it.descriptor == test })
        0 * _._
    }

    public void notifiesAfter() {
        given:
        TestDescriptor test = new DefaultTestDescriptor("id", "Foo", "bar");

        when:
        adapter.started(test, new TestStartEvent(100L))
        adapter.completed('id', new TestCompleteEvent(200L))

        then:
        1 * listener.beforeTest(_)
        1 * listener.afterTest(
                { it instanceof DecoratingTestDescriptor && it.descriptor == test },
                { it.successfulTestCount == 1 && it.testCount == 1 && it.failedTestCount == 0 }
        )
        0 * _._
    }

    public void createsAResultForATestWithFailure() {
        given:
        RuntimeException failure = new RuntimeException()
        TestDescriptor test = new DefaultTestDescriptor("15", "Foo", "bar");

        when:
        adapter.started(test, new TestStartEvent(100L))
        adapter.failure('15', failure)
        adapter.completed('15', new TestCompleteEvent(200L))

        then:
        1 * listener.beforeTest(_)
        1 * listener.afterTest( { it.descriptor == test },
            { it.successfulTestCount == 0 && it.testCount == 1 && it.failedTestCount == 1 && it.exception.is(failure) })
        0 * _._
    }

    public void createsAResultForATestWithMultipleFailures() {
        given:
        RuntimeException failure1 = new RuntimeException()
        RuntimeException failure2 = new RuntimeException()
        TestDescriptor test = new DefaultTestDescriptor("15", "Foo", "bar");

        when:
        adapter.started(test, new TestStartEvent(100L))
        adapter.failure('15', failure1)
        adapter.failure('15', failure2)
        adapter.completed('15', new TestCompleteEvent(200L))

        then:
        1 * listener.afterTest(_,
            { it.exception.is(failure1) && it.exceptions == [failure1, failure2] })
    }

    public void createsAnAggregateResultForEmptyTestSuite() {
        given:
        TestDescriptor suite = new DefaultTestSuiteDescriptor("15", "FastTests");

        when:
        adapter.started(suite, new TestStartEvent(100L))
        adapter.completed('15', new TestCompleteEvent(200L))

        then:
        1 * listener.beforeSuite({it.descriptor == suite})
        1 * listener.afterSuite({it.descriptor == suite}, {
            it.testCount == 0 && it.resultType == ResultType.SUCCESS
        })
        0 * _._
    }

    public void createsAnAggregateResultForTestSuiteWithPassedTest() {
        given:
        TestDescriptor suite = new DefaultTestSuiteDescriptor("suiteId", "FastTests");
        TestDescriptor test = new DefaultTestDescriptor("testId", "DogTest", "shouldBarkAtStrangers");

        when:
        adapter.started(suite, new TestStartEvent(100L))
        adapter.started(test, new TestStartEvent(100L, 'suiteId'))
        adapter.completed('testId', new TestCompleteEvent(200L))
        adapter.completed('suiteId', new TestCompleteEvent(200L))

        then:
        1 * listener.beforeSuite({it.descriptor == suite})
        1 * listener.beforeTest({it.descriptor == test})
        1 * listener.afterTest({it.descriptor == test}, _ as TestResult)
        1 * listener.afterSuite({it.descriptor == suite}, { it.testCount == 1 } )
        0 * _._
    }

    public void createsAnAggregateResultForTestSuiteWithFailedTest() {
        given:
        TestDescriptor suite = new DefaultTestSuiteDescriptor("suiteId", "FastTests");
        TestDescriptor ok = new DefaultTestDescriptor("okId", "DogTest", "shouldBarkAtStrangers");
        TestDescriptor broken = new DefaultTestDescriptor("brokenId", "DogTest", "shouldDrinkMilk");

        when:
        adapter.started(suite, new TestStartEvent(100L))
        adapter.started(ok, new TestStartEvent(100L, 'suiteId'))
        adapter.started(broken, new TestStartEvent(100L, 'suiteId'))
        adapter.completed('okId', new TestCompleteEvent(200L))
        adapter.failure('brokenId', new RuntimeException())
        adapter.completed('brokenId', new TestCompleteEvent(200L))
        adapter.completed('suiteId', new TestCompleteEvent(200L))

        then:
        1 * listener.beforeSuite({it.descriptor == suite})
        1 * listener.beforeTest({it.descriptor == ok && it.parent.descriptor == suite})
        1 * listener.beforeTest({it.descriptor == broken && it.parent.descriptor == suite})
        1 * listener.afterTest({it.descriptor == ok}, _ as TestResult)
        1 * listener.afterTest({it.descriptor == broken}, _ as TestResult)
        1 * listener.afterSuite({it.descriptor == suite}, { it.testCount == 2 && it.failedTestCount == 1 && it.successfulTestCount == 1 } )
        0 * _._
    }

    public void createsAnAggregateResultForTestSuiteWithSkippedTest() {
        given:
        TestDescriptor suite = new DefaultTestSuiteDescriptor("suiteId", "FastTests");
        TestDescriptor test = new DefaultTestDescriptor("testId", "DogTest", "shouldBarkAtStrangers");

        when:
        adapter.started(suite, new TestStartEvent(100L))
        adapter.started(test, new TestStartEvent(100L, 'suiteId'))
        adapter.completed('testId', new TestCompleteEvent(200L, ResultType.SKIPPED))
        adapter.completed('suiteId', new TestCompleteEvent(200L))

        then:
        1 * listener.beforeSuite({it.descriptor == suite})
        1 * listener.beforeTest({it.descriptor == test && it.parent.descriptor == suite})
        1 * listener.afterTest({it.descriptor == test}, _ as TestResult)
        1 * listener.afterSuite({it.descriptor == suite},
                { it.resultType == ResultType.SUCCESS && it.testCount == 1 && it.failedTestCount == 0 && it.successfulTestCount == 0 } )
        0 * _._
    }

    @Test
    @Ignore
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
    @Ignore
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
}
