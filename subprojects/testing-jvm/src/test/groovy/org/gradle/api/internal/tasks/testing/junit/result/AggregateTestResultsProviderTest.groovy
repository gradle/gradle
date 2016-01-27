/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.internal.tasks.testing.junit.result

import org.gradle.api.Action
import org.gradle.api.tasks.testing.TestOutputEvent
import org.gradle.api.tasks.testing.TestResult
import spock.lang.Specification

class AggregateTestResultsProviderTest extends Specification {
    def provider1 = Mock(TestResultsProvider)
    def provider2 = Mock(TestResultsProvider)
    def provider = new AggregateTestResultsProvider([provider1, provider2])

    def "visits classes from each provider and reassigns class ids"() {
        def action = Mock(Action)
        def class1 = Stub(TestClassResult) {
            getClassName() >> 'class-1'
        }
        def class2 = Stub(TestClassResult) {
            getClassName() >> 'class-2'
        }

        when:
        provider.visitClasses(action)

        then:
        1 * provider1.visitClasses(_) >> { Action a -> a.execute(class1) }
        1 * provider2.visitClasses(_) >> { Action a -> a.execute(class2) }
        // TODO(radimk): should not assume order
        1 * action.execute(_) >> { TestClassResult r ->
            assert r.id == 1
            assert r.className == 'class-1'
        }
        1 * action.execute(_) >> { TestClassResult r ->
            assert r.id == 2
            assert r.className == 'class-2'
        }
        0 * action._
    }

    def "maps class id to original id when fetching test output"() {
        def writer = Stub(Writer)
        def class1 = Stub(TestClassResult) {
            getId() >> 12
            getClassName() >> 'class-1'
        }
        def class2 = Stub(TestClassResult) {
            getId() >> 12
            getClassName() >> 'class-2'
        }

        when:
        provider.visitClasses(Stub(Action))

        then:
        1 * provider1.visitClasses(_) >> { Action a -> a.execute(class1) }
        1 * provider2.visitClasses(_) >> { Action a -> a.execute(class2) }

        when:
        provider.hasOutput(1, TestOutputEvent.Destination.StdOut)
        provider.writeAllOutput(1, TestOutputEvent.Destination.StdOut, writer)
        provider.writeTestOutput(1, 11, TestOutputEvent.Destination.StdOut, writer)
        provider.writeNonTestOutput(1, TestOutputEvent.Destination.StdOut, writer)

        then:
        1 * provider1.hasOutput(12, TestOutputEvent.Destination.StdOut)
        1 * provider1.writeAllOutput(12, TestOutputEvent.Destination.StdOut, writer)
        1 * provider1.writeTestOutput(12, 11, TestOutputEvent.Destination.StdOut, writer)
        1 * provider1.writeNonTestOutput(12, TestOutputEvent.Destination.StdOut, writer)

        when:
        provider.hasOutput(2, TestOutputEvent.Destination.StdOut)
        provider.writeAllOutput(2, TestOutputEvent.Destination.StdOut, writer)

        then:
        1 * provider2.hasOutput(12, TestOutputEvent.Destination.StdOut)
        1 * provider2.writeAllOutput(12, TestOutputEvent.Destination.StdOut, writer)
    }

    def "processes duplicate classes"() {
        def action = Mock(Action)
        def class1 = Stub(TestClassResult) {
            getClassName() >> 'class-1'
        }
        def class2 = Stub(TestClassResult) {
            getClassName() >> 'class-1'
        }

        when:
        provider.visitClasses(action)

        then:
        1 * provider1.visitClasses(_) >> { Action a -> a.execute(class1) }
        1 * provider2.visitClasses(_) >> { Action a -> a.execute(class2) }
        1 * action.execute(_) >> { TestClassResult r ->
            assert r.id == 1
            assert r.className == 'class-1'
        }
        0 * action._
    }

    def "merge methods in duplicate classes"() {
        final long startTimeSooner = 122000
        final long startTimeLater = 123000
        def action = Mock(Action)
        def class1 = Stub(TestClassResult) {
            getClassName() >> 'class-1'
            getResults() >> Collections.singletonList(new TestMethodResult(101, 'methodFoo', TestResult.ResultType.SUCCESS, 10, 123456))
            getStartTime() >> startTimeLater
        }
        def class2 = Stub(TestClassResult) {
            getClassName() >> 'class-1'
            getResults() >> Collections.singletonList(new TestMethodResult(101, 'methodFoo', TestResult.ResultType.FAILURE, 100, 123678))
            getStartTime() >> startTimeSooner
        }

        when:
        provider.visitClasses(action)

        then:
        1 * provider1.visitClasses(_) >> { Action a -> a.execute(class1) }
        1 * provider2.visitClasses(_) >> { Action a -> a.execute(class2) }
        1 * action.execute(_) >> { TestClassResult r ->
            assert r.id == 1
            assert r.className == 'class-1'
            assert r.startTime == startTimeSooner
            assert r.results.any { TestMethodResult m ->
                m.name == 'methodFoo' && m.resultType == TestResult.ResultType.SUCCESS
            }
            assert r.results.any { TestMethodResult m ->
                m.name == 'methodFoo' && m.resultType == TestResult.ResultType.FAILURE
            }
        }
        0 * action._
    }

    def "maps class ids to original id when fetching test output for merged classes"() {
        def writer = Stub(Writer)
        def class1 = Stub(TestClassResult) {
            getId() >> 12
            getClassName() >> 'class-1'
        }
        def class2 = Stub(TestClassResult) {
            getId() >> 12
            getClassName() >> 'class-1'
        }

        when:
        provider.visitClasses(Stub(Action))

        then:
        1 * provider1.visitClasses(_) >> { Action a -> a.execute(class1) }
        1 * provider2.visitClasses(_) >> { Action a -> a.execute(class2) }

        when:
        provider.hasOutput(1, TestOutputEvent.Destination.StdOut)
        provider.writeAllOutput(1, TestOutputEvent.Destination.StdOut, writer)
        provider.writeTestOutput(1, 11, TestOutputEvent.Destination.StdOut, writer)
        provider.writeNonTestOutput(1, TestOutputEvent.Destination.StdOut, writer)

        then:
        1 * provider1.hasOutput(12, TestOutputEvent.Destination.StdOut)
        1 * provider1.writeAllOutput(12, TestOutputEvent.Destination.StdOut, writer)
        1 * provider1.writeTestOutput(12, 11, TestOutputEvent.Destination.StdOut, writer)
        1 * provider1.writeNonTestOutput(12, TestOutputEvent.Destination.StdOut, writer)
        1 * provider2.hasOutput(12, TestOutputEvent.Destination.StdOut)
        1 * provider2.writeAllOutput(12, TestOutputEvent.Destination.StdOut, writer)
        1 * provider2.writeTestOutput(12, 11, TestOutputEvent.Destination.StdOut, writer)
        1 * provider2.writeNonTestOutput(12, TestOutputEvent.Destination.StdOut, writer)
    }

}
