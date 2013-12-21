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

import org.gradle.api.tasks.testing.TestResult.ResultType
import org.gradle.api.internal.tasks.testing.*
import org.gradle.api.tasks.testing.*

import org.junit.Test

import spock.lang.Issue
import spock.lang.Specification

class TestListenerAdapterTest extends Specification {

    def listener = Mock(TestListener.class)
    def outputListener = Mock(TestOutputListener.class)
    TestListenerAdapter adapter = new TestListenerAdapter(listener, outputListener)

    public void notifiesBefore() {
        given:
        TestDescriptor test = new DefaultTestDescriptor("id", "Foo", "bar");

        when:
        adapter.started(test, new TestStartEvent(100L))

        then:
        1 * listener.beforeTest({ it instanceof DecoratingTestDescriptor && it.descriptor == test })
        0 * _
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
        0 * _
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
        1 * listener.afterTest({ it.descriptor == test },
                { it.successfulTestCount == 0 && it.testCount == 1 && it.failedTestCount == 1 && it.exception.is(failure) })
        0 * _
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
        0 * _
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
        1 * listener.afterSuite({it.descriptor == suite}, { it.testCount == 1 })
        0 * _
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
        1 * listener.afterSuite({it.descriptor == suite}, { it.testCount == 2 && it.failedTestCount == 1 && it.successfulTestCount == 1 })
        0 * _
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
                { it.resultType == ResultType.SUCCESS && it.testCount == 1 && it.failedTestCount == 0 && it.successfulTestCount == 0 })
        0 * _
    }

    @Test
    public void createsAnAggregateResultForTestSuiteWithNestedSuites() {
        given:
        TestDescriptor root = new DefaultTestSuiteDescriptor("root", "AllTests");
        TestDescriptor suite1 = new DefaultTestSuiteDescriptor("suite1", "FastTests");
        TestDescriptor suite2 = new DefaultTestSuiteDescriptor("suite2", "SlowTests");
        TestDescriptor ok = new DefaultTestDescriptor("ok", "DogTest", "shouldBarkAtStrangers");
        TestDescriptor broken = new DefaultTestDescriptor("broken", "DogTest", "shouldDrinkMilk");

        when:
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

        then:
        1 * listener.beforeSuite({it.descriptor == root})
        1 * listener.beforeSuite({it.descriptor == suite1})
        1 * listener.beforeSuite({it.descriptor == suite2})


        1 * listener.beforeTest({it.descriptor == ok && it.parent.descriptor == suite1})
        1 * listener.beforeTest({it.descriptor == broken && it.parent.descriptor == suite2})

        1 * listener.afterTest({it.descriptor == ok}, _ as TestResult)
        1 * listener.afterTest({it.descriptor == broken}, _ as TestResult)

        1 * listener.afterSuite({it.descriptor == root}, { it.successfulTestCount == 1 && it.testCount == 2 && it.resultType == ResultType.FAILURE})
        1 * listener.afterSuite({it.descriptor == suite1}, { it.successfulTestCount == 1 && it.testCount == 1 && it.resultType == ResultType.SUCCESS})
        1 * listener.afterSuite({it.descriptor == suite2}, { it.successfulTestCount == 0 && it.testCount == 1 && it.resultType == ResultType.FAILURE})

        0 * _
    }

    public void createsAnAggregateResultForTestSuiteWithFailure() {
        given:
        TestDescriptor suite = new DefaultTestSuiteDescriptor("id", "FastTests");
        TestDescriptor test = new DefaultTestDescriptor("testid", "DogTest", "shouldBarkAtStrangers");
        RuntimeException failure = new RuntimeException()

        when:
        adapter.started(suite, new TestStartEvent(100L))
        adapter.started(test, new TestStartEvent(100L, 'id'))
        adapter.completed('testid', new TestCompleteEvent(200L, ResultType.SKIPPED))
        adapter.failure('id', failure)
        adapter.completed('id', new TestCompleteEvent(200L))

        then:
        1 * listener.beforeSuite({it.descriptor == suite})
        1 * listener.beforeTest({it.descriptor == test && it.parent.descriptor == suite})
        1 * listener.afterTest({it.descriptor == test}, _ as TestResult)
        1 * listener.afterSuite({it.descriptor == suite},
                { it.resultType == ResultType.FAILURE && it.exception.is(failure) && it.exceptions == [failure] })
        0 * _
    }

    def "notifies output listener"() {
        given:
        def event = new DefaultTestOutputEvent(TestOutputEvent.Destination.StdOut, "hey!")
        def test = new DefaultTestDescriptor("testid", "DogTest", "shouldBarkAtStrangers");

        when:
        adapter.started(test, new TestStartEvent(100L))
        adapter.output("testid", event)

        then:
        1 * outputListener.onOutput({it.descriptor == test}, event)
    }

    @Issue("GRADLE-2035")
    def "behaves gracefully even if cannot match output to the test"() {
        given:
        def event = new DefaultTestOutputEvent(TestOutputEvent.Destination.StdOut, "hey!")

        when:
        adapter.output("testid", event)

        then:
        1 * outputListener.onOutput({it instanceof UnknownTestDescriptor}, event)
    }

    @Issue("GRADLE-2035")
    def "output can be received after test completion"() {
        given:
        TestDescriptor suite = new DefaultTestSuiteDescriptor("1", "DogTest");
        TestDescriptor test1 = new DefaultTestDescriptor("1.1", "DogTest", "shouldBarkAtStrangers");

        def woof = new DefaultTestOutputEvent(TestOutputEvent.Destination.StdOut, "woof woof!")

        when:
        adapter.started(suite, new TestStartEvent(100L))
        adapter.started(test1, new TestStartEvent(100L, '1'))

        adapter.completed('1.1', new TestCompleteEvent(200L))
        adapter.output('1.1', woof)

        adapter.completed('1', new TestCompleteEvent(200L))

        then:
        1 * outputListener.onOutput({ it.id == '1' }, woof)
    }

    @Issue("GRADLE-2035")
    def "outputs for completed tests use parent descriptors"() {
        given:
        TestDescriptor root = new DefaultTestSuiteDescriptor("1", "CanineSuite");
        TestDescriptor suite = new DefaultTestSuiteDescriptor("1.1", "DogTest");
        TestDescriptor test1 = new DefaultTestDescriptor("1.1.1", "DogTest", "shouldBarkAtStrangers");
        TestDescriptor test2 = new DefaultTestDescriptor("1.1.2", "DogTest", "shouldLoiter");

        def woof = new DefaultTestOutputEvent(TestOutputEvent.Destination.StdOut, "woof woof!")
        def grrr = new DefaultTestOutputEvent(TestOutputEvent.Destination.StdErr, "grrr!")

        when:
        adapter.started(root, new TestStartEvent(1))
        adapter.started(suite, new TestStartEvent(1, '1'))
        adapter.started(test1, new TestStartEvent(1, '1.1'))
        adapter.started(test2, new TestStartEvent(1, '1.1'))

        adapter.completed('1.1.1', new TestCompleteEvent(1))

        //test completed but we receive an output
        adapter.output('1.1.1', woof)

        adapter.completed('1.1.2', new TestCompleteEvent(1))
        adapter.completed('1.1', new TestCompleteEvent(1))

        //the suite is completed but for we receive an output
        adapter.output('1.1.1', woof)

        adapter.completed('1', new TestCompleteEvent(1))

        //all tests are completed but we receive an output for one of the other tests
        adapter.output('1.1.2', grrr)

        then:
        1 * outputListener.onOutput({ it instanceof DecoratingTestDescriptor && it.id == '1.1' }, woof)
        1 * outputListener.onOutput({ it instanceof DecoratingTestDescriptor && it.id == '1' }, woof)
        1 * outputListener.onOutput({ it instanceof UnknownTestDescriptor }, grrr)
        0 * outputListener._
    }
}