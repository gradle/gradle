/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.internal.tasks.testing.junit


import org.gradle.api.internal.tasks.testing.TestDescriptorInternal
import org.gradle.api.internal.tasks.testing.TestResultProcessor
import org.gradle.api.tasks.testing.TestFailure
import org.gradle.internal.id.IdGenerator
import org.gradle.internal.time.Clock
import org.gradle.internal.time.MockClock
import spock.lang.Specification

class TestClassExecutionEventGeneratorTest extends Specification {
    final TestResultProcessor target = Mock()
    final IdGenerator<Object> idGenerator = Mock()
    final Clock timeProvider = MockClock.create()
    final TestClassExecutionEventGenerator processor = new TestClassExecutionEventGenerator(target, idGenerator, timeProvider)

    def "fires event on test class start"() {
        given:
        idGenerator.generateId() >> 1
        timeProvider.increment(1200)

        when:
        processor.testClassStarted("some-test")

        then:
        1 * target.started({it.id == 1 && it.className == 'some-test'}, {it.startTime == 1200})
        0 * target._
    }

    def "fires event on test class finish"() {
        given:
        idGenerator.generateId() >> 1
        timeProvider.increment(1200)

        and:
        processor.testClassStarted("some-test")

        when:
        timeProvider.increment(100)
        processor.testClassFinished(null)

        then:
        1 * target.completed(1, {it.endTime == 1300})
        0 * target._
    }

    def "synthesises a broken test when test class fails and no tests have been started"() {
        def failure = TestFailure.fromTestFrameworkFailure(new RuntimeException())

        given:
        idGenerator.generateId() >>> [1, 2]

        and:
        processor.testClassStarted("some-test")

        when:
        processor.testClassFinished(failure)

        then:
        1 * target.started({it.id == 2 && it.className == 'some-test' && it.name == 'initializationError'}, !null)
        1 * target.failure(2, failure)
        1 * target.completed(2, !null)
        1 * target.completed(1, !null)
        0 * target._
    }

    def "fires event on test class failure when some tests have been started"() {
        def failure = TestFailure.fromTestFrameworkFailure(new RuntimeException())
        TestDescriptorInternal test1 = Mock()
        TestDescriptorInternal test2 = Mock()

        given:
        idGenerator.generateId() >> 1
        test1.id >> 2
        test2.id >> 3

        and:
        processor.testClassStarted("some-test")
        processor.started(test1, null)
        processor.started(test2, null)

        when:
        processor.testClassFinished(failure)

        then:
        1 * target.failure(2, failure)
        1 * target.failure(3, failure)
        1 * target.completed(2, !null)
        1 * target.completed(3, !null)
        1 * target.completed(1, !null)
        0 * target._
    }

    def "synthesises a broken test when test class fails and some tests have been completed"() {
        def failure = TestFailure.fromTestFrameworkFailure(new RuntimeException())
        TestDescriptorInternal test = Mock()

        given:
        idGenerator.generateId() >>> [1, 3]
        test.id >> 2

        and:
        processor.testClassStarted("some-test")
        processor.started(test, null)
        processor.completed(2, null)

        when:
        processor.testClassFinished(failure)

        then:
        1 * target.started({it.id == 3 && it.className == 'some-test' && it.name == 'executionError'}, !null)
        1 * target.failure(3, failure)
        1 * target.completed(3, !null)
        1 * target.completed(1, !null)
        0 * target._
    }
}
