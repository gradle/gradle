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

import org.gradle.api.internal.tasks.testing.DecoratingTestDescriptor
import org.gradle.api.internal.tasks.testing.DefaultTestDescriptor
import org.gradle.api.internal.tasks.testing.DefaultTestOutputEvent
import org.gradle.api.internal.tasks.testing.DefaultTestSuiteDescriptor
import org.gradle.api.internal.tasks.testing.TestCompleteEvent
import org.gradle.api.internal.tasks.testing.TestStartEvent
import org.gradle.api.tasks.testing.TestDescriptor
import org.gradle.api.tasks.testing.TestFailure
import org.gradle.api.tasks.testing.TestOutputEvent
import org.gradle.api.tasks.testing.TestResult
import org.gradle.api.tasks.testing.TestResult.ResultType
import spock.lang.Issue
import spock.lang.Specification

class StateTrackingTestResultProcessorTest extends Specification {

    def listener = Mock(TestListenerInternal.class)
    def adapter = new StateTrackingTestResultProcessor(listener)

    void notifiesBefore() {
        given:
        def test = new DefaultTestDescriptor("id", "Foo", "bar");
        def startEvent = new TestStartEvent(100L)

        when:
        adapter.started(test, startEvent)

        then:
        1 * listener.started({ it instanceof DecoratingTestDescriptor && it.descriptor == test }, startEvent)
        0 * _
    }

    void notifiesAfter() {
        given:
        def test = new DefaultTestDescriptor("id", "Foo", "bar");
        def startEvent = new TestStartEvent(100L)
        def completeEvent = new TestCompleteEvent(200L)

        when:
        adapter.started(test, startEvent)
        adapter.completed('id', completeEvent)

        then:
        1 * listener.started(_, _)
        1 * listener.completed(
                { it instanceof DecoratingTestDescriptor && it.descriptor == test },
                { it.successfulTestCount == 1 && it.testCount == 1 && it.failedTestCount == 0 },
                completeEvent
        )
        0 * _
    }

    void createsAResultForATestWithFailure() {
        given:
        def failure = TestFailure.fromTestFrameworkFailure(new RuntimeException())
        def test = new DefaultTestDescriptor("15", "Foo", "bar");
        def startEvent = new TestStartEvent(100L)
        def completeEvent = new TestCompleteEvent(200L)

        when:
        adapter.started(test, startEvent)
        adapter.failure('15', failure)
        adapter.completed('15', completeEvent)

        then:
        1 * listener.started(_, _)
        1 * listener.completed({ it.descriptor == test },
                { it.successfulTestCount == 0 && it.testCount == 1 && it.failedTestCount == 1 && it.exception.is(failure.rawFailure) },
                completeEvent
        )
        0 * _
    }

    void createsAResultForATestWithMultipleFailures() {
        given:
        def failure1 = TestFailure.fromTestFrameworkFailure(new RuntimeException())
        def failure2 = TestFailure.fromTestFrameworkFailure(new RuntimeException())
        def test = new DefaultTestDescriptor("15", "Foo", "bar");
        def startEvent = new TestStartEvent(100L)
        def completeEvent = new TestCompleteEvent(200L)

        when:
        adapter.started(test, startEvent)
        adapter.failure('15', failure1)
        adapter.failure('15', failure2)
        adapter.completed('15', completeEvent)

        then:
        1 * listener.completed(_,
                { it.exception.is(failure1.rawFailure) && it.exceptions == [failure1.rawFailure, failure2.rawFailure] },
                completeEvent
        )
    }

    void createsAnAggregateResultForEmptyTestSuite() {
        given:
        def suite = new DefaultTestSuiteDescriptor("15", "FastTests");
        def startEvent = new TestStartEvent(100L)
        def completeEvent = new TestCompleteEvent(200L)

        when:
        adapter.started(suite, startEvent)
        adapter.completed('15', completeEvent)

        then:
        1 * listener.started({it.descriptor == suite}, startEvent)
        1 * listener.completed({it.descriptor == suite}, {
            it.testCount == 0 && it.resultType == ResultType.SUCCESS
        },
            completeEvent
        )
        0 * _
    }

    void createsAnAggregateResultForTestSuiteWithPassedTest() {
        given:
        def suite = new DefaultTestSuiteDescriptor("suiteId", "FastTests");
        def test = new DefaultTestDescriptor("testId", "DogTest", "shouldBarkAtStrangers");
        def startEvent = new TestStartEvent(100L)
        def testStartEvent = new TestStartEvent(100L, "suiteId")
        def testCompleteEvent = new TestCompleteEvent(200L)
        def completeEvent = new TestCompleteEvent(300L)

        when:
        adapter.started(suite, startEvent)
        adapter.started(test, testStartEvent)
        adapter.completed('testId', testCompleteEvent)
        adapter.completed('suiteId', completeEvent)

        then:
        1 * listener.started({it.descriptor == suite}, startEvent)
        1 * listener.started({it.descriptor == test}, testStartEvent)
        1 * listener.completed({it.descriptor == test}, _ as TestResult, testCompleteEvent)
        1 * listener.completed({it.descriptor == suite}, { it.testCount == 1 }, completeEvent)
        0 * _
    }

    void createsAnAggregateResultForTestSuiteWithFailedTest() {
        given:
        def suite = new DefaultTestSuiteDescriptor("suiteId", "FastTests");
        def ok = new DefaultTestDescriptor("okId", "DogTest", "shouldBarkAtStrangers");
        def broken = new DefaultTestDescriptor("brokenId", "DogTest", "shouldDrinkMilk");

        when:
        adapter.started(suite, new TestStartEvent(100L))
        adapter.started(ok, new TestStartEvent(100L, 'suiteId'))
        adapter.started(broken, new TestStartEvent(100L, 'suiteId'))
        adapter.completed('okId', new TestCompleteEvent(200L))
        adapter.failure('brokenId', TestFailure.fromTestFrameworkFailure(new RuntimeException()))
        adapter.completed('brokenId', new TestCompleteEvent(200L))
        adapter.completed('suiteId', new TestCompleteEvent(200L))

        then:
        1 * listener.started({it.descriptor == suite}, _)
        1 * listener.started({it.descriptor == ok && it.parent.descriptor == suite}, _)
        1 * listener.started({it.descriptor == broken && it.parent.descriptor == suite}, _)
        1 * listener.completed({it.descriptor == ok}, _ as TestResult, _)
        1 * listener.completed({it.descriptor == broken}, _ as TestResult, _)
        1 * listener.completed({it.descriptor == suite}, { it.testCount == 2 && it.failedTestCount == 1 && it.successfulTestCount == 1 }, _)
        0 * _
    }

    void createsAnAggregateResultForTestSuiteWithSkippedTest() {
        given:
        def suite = new DefaultTestSuiteDescriptor("suiteId", "FastTests");
        def test = new DefaultTestDescriptor("testId", "DogTest", "shouldBarkAtStrangers");

        when:
        adapter.started(suite, new TestStartEvent(100L))
        adapter.started(test, new TestStartEvent(100L, 'suiteId'))
        adapter.completed('testId', new TestCompleteEvent(200L, ResultType.SKIPPED))
        adapter.completed('suiteId', new TestCompleteEvent(200L))

        then:
        1 * listener.started({it.descriptor == suite}, _)
        1 * listener.started({it.descriptor == test && it.parent.descriptor == suite}, _)
        1 * listener.completed({it.descriptor == test}, _ as TestResult, _)
        1 * listener.completed({it.descriptor == suite},
                { it.resultType == ResultType.SUCCESS && it.testCount == 1 && it.failedTestCount == 0 && it.successfulTestCount == 0 },
                _
        )
        0 * _
    }

    void createsAnAggregateResultForTestSuiteWithNestedSuites() {
        given:
        def root = new DefaultTestSuiteDescriptor("root", "AllTests");
        def suite1 = new DefaultTestSuiteDescriptor("suite1", "FastTests");
        def suite2 = new DefaultTestSuiteDescriptor("suite2", "SlowTests");
        def ok = new DefaultTestDescriptor("ok", "DogTest", "shouldBarkAtStrangers");
        def broken = new DefaultTestDescriptor("broken", "DogTest", "shouldDrinkMilk");

        when:
        adapter.started(root, new TestStartEvent(100L))
        adapter.started(suite1, new TestStartEvent(100L, 'root'))
        adapter.started(ok, new TestStartEvent(100L, 'suite1'))
        adapter.started(suite2, new TestStartEvent(100L, 'root'))
        adapter.completed('ok', new TestCompleteEvent(200L))
        adapter.completed('suite1', new TestCompleteEvent(200L))
        adapter.started(broken, new TestStartEvent(100L, 'suite2'))
        adapter.failure('broken', TestFailure.fromTestFrameworkFailure(new RuntimeException()))
        adapter.completed('broken', new TestCompleteEvent(200L))
        adapter.completed('suite2', new TestCompleteEvent(200L))
        adapter.completed('root', new TestCompleteEvent(200L))

        then:
        1 * listener.started({it.descriptor == root}, _)
        1 * listener.started({it.descriptor == suite1}, _)
        1 * listener.started({it.descriptor == suite2}, _)


        1 * listener.started({it.descriptor == ok && it.parent.descriptor == suite1}, _)
        1 * listener.started({it.descriptor == broken && it.parent.descriptor == suite2}, _)

        1 * listener.completed({it.descriptor == ok}, _ as TestResult, _)
        1 * listener.completed({it.descriptor == broken}, _ as TestResult, _)

        1 * listener.completed({it.descriptor == root}, { it.successfulTestCount == 1 && it.testCount == 2 && it.resultType == ResultType.FAILURE}, _)
        1 * listener.completed({it.descriptor == suite1}, { it.successfulTestCount == 1 && it.testCount == 1 && it.resultType == ResultType.SUCCESS}, _)
        1 * listener.completed({it.descriptor == suite2}, { it.successfulTestCount == 0 && it.testCount == 1 && it.resultType == ResultType.FAILURE}, _)

        0 * _
    }

    void createsAnAggregateResultForTestSuiteWithFailure() {
        given:
        def suite = new DefaultTestSuiteDescriptor("id", "FastTests");
        def test = new DefaultTestDescriptor("testid", "DogTest", "shouldBarkAtStrangers");
        def failure = TestFailure.fromTestFrameworkFailure(new RuntimeException())

        when:
        adapter.started(suite, new TestStartEvent(100L))
        adapter.started(test, new TestStartEvent(100L, 'id'))
        adapter.completed('testid', new TestCompleteEvent(200L, ResultType.SKIPPED))
        adapter.failure('id', failure)
        adapter.completed('id', new TestCompleteEvent(200L))

        then:
        1 * listener.started({it.descriptor == suite}, _)
        1 * listener.started({it.descriptor == test && it.parent.descriptor == suite}, _)
        1 * listener.completed({it.descriptor == test}, _ as TestResult, _)
        1 * listener.completed({it.descriptor == suite},
                { it.resultType == ResultType.FAILURE && it.exception.is(failure.rawFailure) && it.exceptions == [failure.rawFailure] },
                _
        )
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
        1 * listener.output({it.descriptor == test}, event)
    }

    @Issue("GRADLE-2035")
    def "behaves gracefully even if cannot match output to the test"() {
        given:
        def event = new DefaultTestOutputEvent(TestOutputEvent.Destination.StdOut, "hey!")

        when:
        adapter.output("testid", event)

        then:
        1 * listener.output({it instanceof UnknownTestDescriptor}, event)
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
        1 * listener.output({ it.id == '1' }, woof)
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
        1 * listener.output({ it instanceof DecoratingTestDescriptor && it.id == '1.1' }, woof)
        1 * listener.output({ it instanceof DecoratingTestDescriptor && it.id == '1' }, woof)
        1 * listener.output({ it instanceof UnknownTestDescriptor }, grrr)
        0 * listener.output(_, _)
    }
}
