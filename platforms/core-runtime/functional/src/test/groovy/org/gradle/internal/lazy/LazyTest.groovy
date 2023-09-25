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

package org.gradle.internal.lazy

import spock.lang.Specification

import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Supplier

class LazyTest extends Specification {

    def "#factoryName supplier code is executed once with use"() {
        def supplier = Mock(Supplier)

        when:
        def lazy = factory(supplier)

        then:
        0 * supplier._

        when:
        lazy.use {
            assert it == 123
        }

        then:
        1 * supplier.get() >> 123

        when:
        lazy.get()

        then:
        0 * supplier.get()

        when:
        lazy.use {
            throw new RuntimeException("boom")
        }
        then:
        def e = thrown(RuntimeException)
        e.message == "boom"

        where:
        factory            | factoryName
        Lazy.unsafe()::of  | "unsafe"
        Lazy.locking()::of | "locking"
        Lazy.atomic()::of  | "atomic"
    }

    def "#factoryName supplier code is executed once with apply"() {
        def supplier = Mock(Supplier)

        when:
        def lazy = factory(supplier)

        then:
        0 * supplier._

        when:
        def val = lazy.apply {
            3 * it
        }

        then:
        1 * supplier.get() >> 123
        val == 369

        when:
        lazy.get()

        then:
        0 * supplier.get()

        where:
        factory            | factoryName
        Lazy.unsafe()::of  | "unsafe"
        Lazy.locking()::of | "locking"
        Lazy.atomic()::of  | "atomic"
    }

    def "#factoryName supplier code is executed once"() {
        def supplier = Mock(Supplier)

        when:
        def lazy = factory(supplier)

        then:
        0 * supplier._

        when:
        lazy.get()

        then:
        1 * supplier.get() >> 123

        when:
        lazy.get()

        then:
        0 * supplier.get()

        where:
        factory            | factoryName
        Lazy.unsafe()::of  | "unsafe"
        Lazy.locking()::of | "locking"
        Lazy.atomic()::of  | "atomic"
    }

    def "#factoryName supplier code is executed once with map"() {
        def supplier = Mock(Supplier)

        when:
        def lazy = factory(supplier).map {
            2 * it
        }

        then:
        0 * supplier._

        when:
        def result = lazy.get()

        then:
        1 * supplier.get() >> 123
        result == 246

        when:
        lazy.get()

        then:
        0 * supplier.get()

        where:
        factory            | factoryName
        Lazy.unsafe()::of  | "unsafe"
        Lazy.locking()::of | "locking"
        Lazy.atomic()::of  | "atomic"
    }

    def "locking lazy can handle concurrent threads"() {
        def supplier = Mock(Supplier)
        Lazy<Integer> lazy = Lazy.locking().of(supplier)

        when:
        int concurrency = 20
        AtomicInteger total = addAndGetLazyConcurrently(concurrency, lazy)

        then:
        1 * supplier.get() >> 123
        total.get() == 123 * concurrency
    }

    def "locking lazy can handle concurrent threads with map"() {
        def supplier = Mock(Supplier)
        Lazy<Integer> lazy = Lazy.locking().of(supplier).map { 2 * it }

        when:
        int concurrency = 20
        AtomicInteger total = addAndGetLazyConcurrently(concurrency, lazy)

        then:
        1 * supplier.get() >> 123
        total.get() == 123 * concurrency * 2
    }

    def "atomic lazy can handle concurrent threads"() {
        def mutableValueSource = new AtomicInteger()
        Lazy<Integer> lazy = Lazy.atomic().of(mutableValueSource::incrementAndGet)

        when: 'multiple threads access a lazy built from a supplier that can return different values'
        int concurrency = 20
        AtomicInteger total = addAndGetLazyConcurrently(concurrency, lazy)

        then: 'all threads observe the same value'
        total.get() % concurrency == 0
    }

    private AtomicInteger addAndGetLazyConcurrently(int concurrency, Lazy<Integer> lazy) {
        def total = new AtomicInteger()
        def barrier = new CyclicBarrier(concurrency)
        def executors = Executors.newFixedThreadPool(concurrency)
        concurrency.times {
            executors.submit {
                // The barrier ensures that the threads all try to access lazy at nearly the same time
                barrier.await()
                total.addAndGet(lazy.get())
            }
        }
        executors.shutdown()
        executors.awaitTermination(1, TimeUnit.MINUTES)
        total
    }
}
