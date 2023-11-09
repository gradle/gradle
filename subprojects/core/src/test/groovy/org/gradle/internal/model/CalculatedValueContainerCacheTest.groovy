/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.internal.model

import org.gradle.internal.Describables
import org.gradle.test.fixtures.concurrent.ConcurrentSpec

import java.util.concurrent.CountDownLatch
import java.util.function.Supplier

class CalculatedValueContainerCacheTest extends ConcurrentSpec {
    def factory = Mock(CalculatedValueContainerFactory)
    def cache = new CalculatedValueContainerCache(factory)
    def container = Mock(CalculatedValueContainer)

    def "uses the same container for each concurrent producer"() {
        def started = new CountDownLatch(3)
        def produced = new String[3]
        def supplier = { "foo" } as Supplier<String>

        when:
        async {
            3.times {i ->
                start {
                    produced[i] = cache.getReference(Describables.of("foo"), supplier).apply {
                        started.countDown()
                        started.await()
                        assert it == container
                        return container.get()
                    }
                }
            }
        }

        then:
        1 * factory.create(_, _) >> container
        3 * container.get() >> supplier.get()
        produced.every { it == "foo" }
    }

    def "finalizes the container on finalizeAndGet()"() {
        when:
        def reference = cache.getReference(Describables.of("foo"), { "foo" })
        reference.finalizeAndGet()

        then:
        1 * factory.create(_, _) >> container
        1 * container.finalizeIfNotAlready()
        1 * container.get()
    }

    def "subsequent calls reuse the same container"() {
        when:
        def reference = cache.getReference(Describables.of("foo"), { "foo" })
        reference.apply { assert it == container }

        then:
        1 * factory.create(_, _) >> container

        when:
        reference.apply { assert it == container }

        then:
        0 * factory.create(_, _)
    }

    def "containers are not leaked when no longer in use"() {
        when:
        def reference = cache.getReference(Describables.of("foo"), { "foo" })
        reference.apply {
            assert it == container
            assert cache.cache.size() == 1
        }

        then:
        1 * factory.create(_, _) >> container

        and:
        cache.cache.size() == 0

        when:
        reference.apply {
            assert cache.cache.size() == 1
            throw new RuntimeException()
        }

        then:
        thrown(RuntimeException)

        and:
        cache.cache.size() == 0
    }

    def "uses different containers for concurrent consumers with different keys"() {
        def started = new CountDownLatch(3)
        def containers = [container, Mock(CalculatedValueContainer), Mock(CalculatedValueContainer)] as Set

        when:
        async {
            3.times {i ->
                start {
                    cache.getReference(Describables.of("foo${i}"), { "foo${i}" }).apply {
                        started.countDown()
                        started.await()
                        assert containers.remove(it)
                    }
                }
            }
        }

        then:
        3 * factory.create(_, _) >>> containers

        and:
        containers.size() == 0
    }
}
