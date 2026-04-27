/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.internal.serialization

import org.gradle.test.fixtures.ConcurrentTestUtil
import org.gradle.test.fixtures.Flaky
import spock.lang.Specification

import java.util.concurrent.Callable
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.BooleanSupplier

class CachedTest extends Specification {

    def "shouldEvaluate=false at serialization time skips the computation and yields null"() {
        given:
        def invocations = new AtomicInteger(0)
        def cached = Cached.of(
            { invocations.incrementAndGet(); "value" } as Callable<String>,
            { false } as BooleanSupplier
        )

        when:
        def restored = roundTrip(cached)

        then:
        invocations.get() == 0
        restored.get() == null
    }

    def "shouldEvaluate=true at serialization time forces the computation"() {
        given:
        def invocations = new AtomicInteger(0)
        def cached = Cached.of(
            { invocations.incrementAndGet(); "value" } as Callable<String>,
            { true } as BooleanSupplier
        )

        when:
        def restored = roundTrip(cached)

        then:
        invocations.get() == 1
        restored.get() == "value"
    }

    def "shouldEvaluate is queried at writeReplace time, not at construction"() {
        given:
        def shouldEvaluate = new AtomicBoolean(false)
        def invocations = new AtomicInteger(0)
        def cached = Cached.of(
            { invocations.incrementAndGet(); "value" } as Callable<String>,
            { shouldEvaluate.get() } as BooleanSupplier
        )

        when: "shouldEvaluate is flipped to true after construction, before serialization"
        shouldEvaluate.set(true)
        def restored = roundTrip(cached)

        then:
        invocations.get() == 1
        restored.get() == "value"
    }

    def "Cached.of without predicate behaves as before"() {
        given:
        def invocations = new AtomicInteger(0)
        def cached = Cached.of({ invocations.incrementAndGet(); "value" } as Callable<String>)

        when:
        def restored = roundTrip(cached)

        then:
        invocations.get() == 1
        restored.get() == "value"
    }

    /**
     * Simulates configuration cache serialization by invoking the private {@code writeReplace}
     * method that {@code Cached.Deferred} declares for that purpose. Returns the substituted
     * {@code Fixed} that would be serialized in production.
     */
    private static <T> Cached<T> roundTrip(Cached<T> input) {
        def writeReplace = input.getClass().getDeclaredMethod("writeReplace")
        writeReplace.setAccessible(true)
        return writeReplace.invoke(input) as Cached<T>
    }

    /**
     * Once https://github.com/gradle/gradle/issues/31239 is addressed,
     * we can remove this test, as unresolved {@link Cached} instances
     * are not supposed to be used from multiple threads to begin with.
     *
     * Alternatively, we could forbid using unresolved `Cached` instances
     * from multiple threads.
     */
    @Flaky(because = "https://github.com/gradle/gradle/issues/31239")
    def "Cached may misbehave when used from multiple threads"() {
        repetitions.times {
            def iteration = batch * repetitions + it
            int parties = Math.max((Runtime.getRuntime().availableProcessors() / 2) as int, 2)
            def barrier = new CyclicBarrier(parties + 1)
            def counter = new AtomicInteger(iteration)
            def cached = Cached.of(counter::incrementAndGet)
            def concurrent = new ConcurrentTestUtil()
            parties.times {
                concurrent.start {
                    barrier.await()
                    // given enough opportunities, this will fail with a NPE
                    def value = cached.get()
                    // a weak guarantee is all we can provide, as at-most-once is not honored in multi-threaded scenarios
                    assert value > iteration
                }
            }
            barrier.await()
            concurrent.finished()
        }

        where:
        batch << (0..<5)
        repetitions = 1000
    }

}
