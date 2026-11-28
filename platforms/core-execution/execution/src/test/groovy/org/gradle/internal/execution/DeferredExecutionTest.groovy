/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.internal.execution

import org.gradle.internal.Try
import spock.lang.Specification

import java.util.function.Function
import java.util.function.Supplier

class DeferredExecutionTest extends Specification {

    def "cache hit is available without executing"() {
        def execution = DeferredExecution.cacheHit(Try.successful("cached"))

        expect:
        execution.cacheHit.get().get() == "cached"
        execution.execute().get() == "cached"
    }

    def "cache miss runs its work only when executed"() {
        def work = Mock(Supplier)

        when:
        def execution = DeferredExecution.cacheMiss(work)

        then:
        !execution.cacheHit.present
        0 * work._

        when:
        def first = execution.execute()
        def second = execution.execute()

        then:
        2 * work.get() >> Try.successful("executed")
        first.get() == "executed"
        second.get() == "executed"
        !execution.cacheHit.present
    }

    def "maps a cache hit right away"() {
        def mapper = Mock(Function)

        when:
        def mapped = DeferredExecution.cacheHit(Try.successful(1)).map(mapper)

        then:
        1 * mapper.apply(1) >> 2
        mapped.cacheHit.get().get() == 2

        when:
        def result = mapped.execute()

        then:
        0 * mapper._
        result.get() == 2
    }

    def "maps a cache miss when executed"() {
        def mapper = Mock(Function)

        when:
        def mapped = DeferredExecution.cacheMiss({ Try.successful(1) } as Supplier).map(mapper)

        then:
        0 * mapper._
        !mapped.cacheHit.present

        when:
        def result = mapped.execute()

        then:
        1 * mapper.apply(1) >> 2
        result.get() == 2
    }

    def "maps the failure of a #description"() {
        def failure = new RuntimeException("original")
        def wrapped = new RuntimeException("wrapped")
        def execution = cached
            ? DeferredExecution.cacheHit(Try.failure(failure))
            : DeferredExecution.cacheMiss({ Try.failure(failure) } as Supplier)

        when:
        def mapped = execution.mapFailure { it.is(failure) ? wrapped : it }

        then:
        mapped.execute().failure.get().is(wrapped)
        mapped.cacheHit.present == cached

        where:
        description  | cached
        "cache hit"  | true
        "cache miss" | false
    }
}
