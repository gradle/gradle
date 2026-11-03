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

package org.gradle.cache

import spock.lang.Specification
import spock.lang.Timeout

import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Supplier

class ManualEvictionInMemoryCacheTest extends Specification {

    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    def "supports #concurrency concurrent computations"() {
        def latch = new CountDownLatch(concurrency)
        def executor = Executors.newFixedThreadPool(concurrency)
        def cache = new ManualEvictionInMemoryCache<String, String>()

        when:
        concurrency.times { idx ->
            executor.submit {
                String key = "key${idx}"
                cache.get(key, {
                    println "Waiting for key ${key}"
                    latch.countDown()
                    println "Waiting for key ${key}"
                    latch.await()
                    println "Created key ${key}"
                    key
                } as Supplier<String>)
            }
        }
        executor.shutdown()
        executor.awaitTermination(2, TimeUnit.SECONDS)
        then:
        executor.isTerminated()
        concurrency.times {
            String key = "key${it}"
            assert cache.getIfPresent(key) == key
        }

        where:
        concurrency << [8, 16, 32]
    }

    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    def "computing a key does not block another key in the same hash bin"() {
        def started = new CountDownLatch(1)
        def release = new CountDownLatch(1)
        def executor = Executors.newFixedThreadPool(2)
        def cache = new ManualEvictionInMemoryCache<Integer, String>()

        when:
        def first = executor.submit({
            cache.get(1, {
                started.countDown()
                release.await()
                "one"
            } as Supplier<String>)
        } as Callable<String>)
        started.await()
        def second = executor.submit({
            cache.get(513, { "five-thirteen" } as Supplier<String>)
        } as Callable<String>)

        then:
        second.get(1, TimeUnit.SECONDS) == "five-thirteen"

        when:
        release.countDown()

        then:
        first.get() == "one"

        cleanup:
        release.countDown()
        executor.shutdownNow()
    }

    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    def "computes a key once when requested concurrently"() {
        def started = new CountDownLatch(1)
        def release = new CountDownLatch(1)
        def calls = new AtomicInteger()
        def executor = Executors.newFixedThreadPool(2)
        def cache = new ManualEvictionInMemoryCache<String, String>()

        when:
        def first = executor.submit({
            cache.get("key", {
                calls.incrementAndGet()
                started.countDown()
                release.await()
                "value"
            } as Supplier<String>)
        } as Callable<String>)
        started.await()
        def inFlight = cache.getIfPresent("key")
        def second = executor.submit({
            cache.get("key", {
                calls.incrementAndGet()
                "other"
            } as Supplier<String>)
        } as Callable<String>)
        release.countDown()

        then:
        inFlight == null
        first.get() == "value"
        second.get() == "value"
        calls.get() == 1

        cleanup:
        executor.shutdownNow()
    }

    def "recomputes a key after the factory fails"() {
        def cache = new ManualEvictionInMemoryCache<String, String>()

        when:
        cache.get("key", { throw new RuntimeException("boom") } as Supplier<String>)

        then:
        def e = thrown(RuntimeException)
        e.message == "boom"
        cache.getIfPresent("key") == null

        and:
        cache.get("key", { "value" } as Supplier<String>) == "value"
    }

    def "fails on recursive computation of the same key"() {
        def cache = new ManualEvictionInMemoryCache<String, String>()

        when:
        cache.get("key", { cache.get("key", { "inner" } as Supplier<String>) } as Supplier<String>)

        then:
        thrown(IllegalStateException)
        cache.getIfPresent("key") == null
    }
}
