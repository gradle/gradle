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

import java.util.concurrent.CyclicBarrier
import java.util.concurrent.atomic.AtomicInteger

class CachedTest extends Specification {

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
