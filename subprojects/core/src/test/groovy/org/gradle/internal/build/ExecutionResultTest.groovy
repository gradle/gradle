/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.internal.build

import org.gradle.execution.MultipleBuildFailures
import spock.lang.Specification

import java.util.function.Consumer

class ExecutionResultTest extends Specification {
    def "can query successful result"() {
        def result = ExecutionResult.succeeded(12)

        expect:
        result.value == 12
        result.valueOrRethrow == 12
        result.failureOrNull == null
        result.failures.empty
        result.rethrow()

        when:
        result.failure

        then:
        thrown(IllegalArgumentException)

        when:
        result.asFailure()

        then:
        thrown(IllegalArgumentException)
    }

    def "can query successful void result"() {
        def result = ExecutionResult.succeeded()

        expect:
        result.value == null
        result.valueOrRethrow == null
        result.failureOrNull == null
        result.failures.empty
        result.rethrow()
    }

    def "can query failed result"() {
        def failure = new RuntimeException()
        def result = ExecutionResult.failed(failure)

        expect:
        result.failures == [failure]
        result.failure == failure
        result.failureOrNull == failure

        when:
        result.valueOrRethrow

        then:
        def e = thrown(RuntimeException)
        e == failure

        when:
        result.rethrow()

        then:
        def e2 = thrown(RuntimeException)
        e2 == failure

        when:
        result.value

        then:
        thrown(IllegalArgumentException)
    }

    def "can query failed result with multiple exceptions"() {
        def failure1 = new RuntimeException()
        def failure2 = new RuntimeException()
        def result = ExecutionResult.maybeFailed([failure1, failure2])

        expect:
        result.failures == [failure1, failure2]
        result.failure instanceof MultipleBuildFailures
        result.failure.causes == [failure1, failure2]
        result.failureOrNull instanceof MultipleBuildFailures
        result.failureOrNull.causes == [failure1, failure2]

        when:
        result.valueOrRethrow

        then:
        def e = thrown(MultipleBuildFailures)
        e.causes == [failure1, failure2]

        when:
        result.rethrow()

        then:
        def e2 = thrown(MultipleBuildFailures)
        e2.causes == [failure1, failure2]

        when:
        result.value

        then:
        thrown(IllegalArgumentException)
    }

    def "can query successful void result created using empty list of failures"() {
        def result = ExecutionResult.maybeFailed([])

        expect:
        result.value == null
        result.valueOrRethrow == null
        result.failureOrNull == null
        result.failures.empty
        result.rethrow()
    }

    def "can combine results"() {
        def failure1 = new RuntimeException()
        def failure2 = new RuntimeException()
        def successful = ExecutionResult.succeeded(12)
        def otherSuccessful = ExecutionResult.succeeded()
        def failed = ExecutionResult.failed(failure1)
        def otherFailed = ExecutionResult.failed(failure2)
        def allFailed = ExecutionResult.maybeFailed([failure1, failure2])

        expect:
        def result = successful.withFailures(otherSuccessful)
        result.value == 12
        result.failures.empty

        def result2 = successful.withFailures(failed)
        result2.failures == [failure1]
        result2.failure == failure1

        def result3 = failed.withFailures(otherFailed)
        result3.failures == [failure1, failure2]
        result3.failure instanceof MultipleBuildFailures

        def result4 = failed.withFailures(otherSuccessful)
        result4.failures == [failure1]
        result4.failure == failure1

        def result5 = failed.withFailures(allFailed)
        result5.failures == [failure1, failure2]
        result5.failure instanceof MultipleBuildFailures
    }

    def "can run action for collection of items"() {
        def action = Mock(Consumer)

        when:
        def result1 = ExecutionResult.forEach([], action)

        then:
        result1.failures.empty
        0 * action._

        when:
        def result2 = ExecutionResult.forEach([1, 2], action)

        then:
        result2.failures.empty
        1 * action.accept(1)
        1 * action.accept(2)
        0 * action._
    }

    def "collects failures for actions"() {
        def action = Mock(Consumer)
        def failure1 = new RuntimeException()
        def failure2 = new RuntimeException()

        when:
        def result1 = ExecutionResult.forEach([1], action)

        then:
        result1.failure == failure1
        result1.failures == [failure1]
        1 * action.accept(1) >> { throw failure1 }
        0 * action._

        when:
        def result2 = ExecutionResult.forEach([1, 2, 3], action)

        then:
        result2.failure instanceof MultipleBuildFailures
        result2.failures == [failure1, failure2]
        1 * action.accept(1) >> { throw failure1 }
        1 * action.accept(2) >> { throw failure2 }
        1 * action.accept(3)
        0 * action._
    }
}
