/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.internal

import spock.lang.Specification

import java.util.concurrent.atomic.AtomicInteger

class DeferrableTest extends Specification {

    def "mapping #description invocations only creates the second invocation once"() {
        def mappingCount = new AtomicInteger()

        def mapped = deferred.map { Integer input ->
            mappingCount.incrementAndGet()
            return input + 25
        }

        expect:
        mapped.getCompleted().present == expectCompleted
        mappingCount.get() == (callMapperEagerly ? 1 : 0)

        when:
        def result = mapped.completeAndGet()
        then:
        result == 30
        mappingCount.get() == (callMapperEagerly ? 2 : 1)

        where:
        description | deferred                  | expectCompleted | callMapperEagerly
        "completed" | Deferrable.completed(5)   | true            | true
        "deferred"  | Deferrable.deferred { 5 } | false           | false
    }

    def "composing #description invocations only creates the second invocation once"() {
        def creationCount = new AtomicInteger()

        def composed = first.flatMap { Integer input ->
            creationCount.incrementAndGet()
            return second(input)
        }

        expect:
        composed.getCompleted().present == expectCompleted
        creationCount.get() == (resolveSecondEagerly ? 1 : 0)

        when:
        def result = composed.completeAndGet()
        then:
        result == 30
        creationCount.get() == 1

        where:
        description              | first                     | second                                          | expectCompleted | resolveSecondEagerly
        "deferred -> deferred"   | Deferrable.deferred { 5 } | { input -> Deferrable.deferred { input + 25 } } | false           | false
        "deferred -> completed"  | Deferrable.deferred { 5 } | { input -> Deferrable.completed(input + 25) }   | false           | false
        "completed -> deferred"  | Deferrable.completed(5)   | { input -> Deferrable.deferred { input + 25 } } | false           | true
        "completed -> completed" | Deferrable.completed(5)   | { input -> Deferrable.completed(input + 25) }   | true            | true
    }

    def "mapping already completed to null fails early"() {
        when:
        def deferrable = Deferrable.completed("value").map(result -> null)
        then:
        noExceptionThrown()

        when:
        deferrable.getCompleted()
        then:
        thrown NullPointerException

        when:
        deferrable.completeAndGet()
        then:
        thrown NullPointerException
    }

    def "mapping deferred to null fails late"() {
        when:
        def deferrable = Deferrable.deferred(() -> "value").map(result -> null)
        then:
        noExceptionThrown()

        when:
        def result = deferrable.getCompleted()
        then:
        !result.present

        when:
        deferrable.completeAndGet()
        then:
        thrown NullPointerException
    }
}
