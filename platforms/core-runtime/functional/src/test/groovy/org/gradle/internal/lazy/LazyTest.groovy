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

import groovy.transform.CompileStatic
import org.gradle.test.fixtures.ConcurrentTestUtil
import spock.lang.Specification
import spock.lang.Timeout

import java.lang.ref.WeakReference
import java.util.concurrent.Callable
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Function
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

    def "#factoryName lazy is initialized once the supplier succeeds"() {
        def supplierCount = new AtomicInteger()
        def lazy = factory {
            if (supplierCount.incrementAndGet() == 1) {
                throw new RuntimeException("broken")
            }
            return 5
        }

        expect: "a new lazy is not initialized"
        !lazy.isInitialized()

        when: "the supplier fails"
        lazy.get()
        then: "the failure propagates and the lazy stays uninitialized"
        def e = thrown(RuntimeException)
        e.message == "broken"
        !lazy.isInitialized()

        when: "the supplier is retried and succeeds"
        def result = lazy.get()
        then: "the lazy is initialized and the supplier is not called again"
        result == 5
        lazy.isInitialized()
        lazy.get() == 5
        supplierCount.get() == 2

        where:
        factory            | factoryName
        Lazy.unsafe()::of  | "unsafe"
        Lazy.locking()::of | "locking"
        Lazy.atomic()::of  | "atomic"
    }

    def "fixed lazy is immediately initialized"() {
        expect:
        Lazy.fixed(5).isInitialized()
    }

    def "mapped #factoryName lazy is initialized once its source is, without calling the mapper"() {
        def mappingCount = new AtomicInteger()
        def source = factory { 5 }
        def mapped = source.map { Integer input ->
            mappingCount.incrementAndGet()
            return input + 25
        }

        expect: "the mapped lazy is not initialized while its source is not"
        !mapped.isInitialized()

        when: "the source is initialized"
        source.get()
        then: "the mapped lazy is initialized too, but has not called the mapper yet"
        mapped.isInitialized()
        mappingCount.get() == 0

        when: "the mapped lazy is read"
        def result = mapped.get()
        then: "the mapper is called once"
        result == 30
        mappingCount.get() == 1

        where:
        factory            | factoryName
        Lazy.unsafe()::of  | "unsafe"
        Lazy.locking()::of | "locking"
        Lazy.atomic()::of  | "atomic"
    }

    def "mapped lazy memoizes the mapped value"() {
        def mappingCount = new AtomicInteger()
        def mapped = Lazy.locking().of { 5 }.map { Integer input ->
            mappingCount.incrementAndGet()
            return new Object()
        }

        when: "the mapped lazy is read twice"
        def first = mapped.get()
        def second = mapped.get()

        then: "both reads return the same instance from a single mapper call"
        first.is(second)
        mapped.isInitialized()
        mappingCount.get() == 1
    }

    def "mapped lazy memoizes a null mapped value"() {
        def mappingCount = new AtomicInteger()
        def mapped = Lazy.fixed(5).map { Integer input ->
            mappingCount.incrementAndGet()
            return null
        }

        expect: "null is memoized like any other mapped value"
        mapped.get() == null
        mapped.get() == null
        mapped.isInitialized()
        mappingCount.get() == 1
    }

    def "mapped lazy does not memoize a failure of the mapper"() {
        def mappingCount = new AtomicInteger()
        def mapped = Lazy.fixed(5).map { Integer input ->
            if (mappingCount.incrementAndGet() == 1) {
                throw new RuntimeException("broken")
            }
            return input + 25
        }

        when: "the mapper fails"
        mapped.get()
        then: "the failure propagates"
        def e = thrown(RuntimeException)
        e.message == "broken"

        when: "the mapped lazy is read again, and the mapper succeeds"
        def result = mapped.get()
        def again = mapped.get()
        then: "the mapper is retried once and its result is memoized"
        result == 30
        again == 30
        mappingCount.get() == 2
    }

    def "mapped lazy calls the mapper once for concurrent threads"() {
        int concurrency = 20
        def sourceBarrier = new CyclicBarrier(concurrency)
        def mappingCount = new AtomicInteger()
        Lazy<Object> mapped = Lazy.atomic().of {
            sourceBarrier.await()
            return 5
        }.map { Integer input ->
            mappingCount.incrementAndGet()
            return new Object()
        }
        def executors = Executors.newFixedThreadPool(concurrency)

        when: "many threads have read the source and race to map it"
        def futures = (1..concurrency).collect {
            executors.submit({ mapped.get() } as Callable<Object>)
        }
        def results = futures.collect { it.get(1, TimeUnit.MINUTES) }
        executors.shutdown()

        then: "every thread sees the same instance"
        results.size() == concurrency
        results.every { it.is(results[0]) }

        and: "the mapper was called only once"
        mappingCount.get() == 1
    }

    def "flat mapping #description lazies only creates the second lazy once"() {
        def creationCount = new AtomicInteger()

        def composed = first().flatMap { Integer input ->
            creationCount.incrementAndGet()
            return second(input)
        }

        expect: "the second lazy is only created up front when the first is already initialized"
        composed.isInitialized() == expectInitialized
        creationCount.get() == (createSecondEagerly ? 1 : 0)

        when: "the composed lazy is read"
        def result = composed.get()
        then: "the second lazy has been created exactly once"
        result == 30
        creationCount.get() == 1

        where:
        description          | first                          | second                                        | expectInitialized | createSecondEagerly
        "locking -> locking" | { -> Lazy.locking().of { 5 } } | { input -> Lazy.locking().of { input + 25 } } | false             | false
        "locking -> fixed"   | { -> Lazy.locking().of { 5 } } | { input -> Lazy.fixed(input + 25) }           | false             | false
        "fixed -> locking"   | { -> Lazy.fixed(5) }           | { input -> Lazy.locking().of { input + 25 } } | false             | true
        "fixed -> fixed"     | { -> Lazy.fixed(5) }           | { input -> Lazy.fixed(input + 25) }           | true              | true
    }

    def "flat mapped lazy is not initialized until the second lazy is read"() {
        def first = Lazy.locking().of { 5 }
        def second = Lazy.locking().of { 30 }
        def composed = first.flatMap { second }

        when: "only the first lazy is read"
        first.get()
        then: "the composed lazy is not yet initialized"
        !composed.isInitialized()
        !second.isInitialized()

        when: "the composed lazy is read"
        def result = composed.get()
        then: "it is initialized"
        result == 30
        composed.isInitialized()
        second.isInitialized()
    }

    /*
     * Garbage collection probe tests:
     *
     * The tests below check that a lazy stops referencing the state it needed to compute its value (its supplier, or
     * its source and mapper) once that value is computed. They do this by having that state be the only strong
     * reference to an object, and holding only a WeakReference ("probe") to it. Once the object is garbage collected,
     * the probe's get() returns null. If the lazy still references the state, the object stays reachable and get()
     * keeps returning it, however often we GC.
     *
     * Each test follows the same pattern:
     *  1. Before initialization, get() on every probe still returns its object, even after a GC. This shows the objects
     *     really are kept alive by the lazy, so get() returning null later on means the lazy released them.
     *  2. After initialization, poll with System.gc() until get() on every probe returns null.
     *  3. Use the lazy again. Otherwise the lazy itself could have been collected, which would also make get() return
     *     null without proving anything.
     *
     * The helper after each test builds the lazy and its probes in a @CompileStatic static method, and returns only the
     * lazy and the weak probes. This keeps the probed objects out of Spock's and Groovy's own bookkeeping which would
     * keep them strongly reachable for the duration of the test. For the same reason, a probed lazy is never a value in
     * a where: block. To work around this, it can pass a closure that creates it instead.
     */

    @Timeout(30)
    def "#factoryName lazy releases its supplier once initialized"() {
        def probed = supplierReferencingProbe(factory)

        expect: "the probed state is reachable through the lazy before it is initialized"
        System.gc()
        probed.probes.every { it.get() != null }

        when: "the lazy is initialized"
        def result = probed.lazy.get()
        then: "the probed state is released, while the lazy itself is still in use"
        result == "value"
        ConcurrentTestUtil.poll(10) {
            System.gc()
            assert probed.probes.every { it.get() == null }
        }
        probed.lazy.get() == "value"

        where:
        factory        | factoryName
        Lazy.unsafe()  | "unsafe"
        Lazy.locking() | "locking"
        Lazy.atomic()  | "atomic"
    }

    /**
     * Creates a lazy from the given factory whose supplier references the probed object.
     */
    @CompileStatic
    private static ProbedLazy supplierReferencingProbe(Lazy.Factory factory) {
        Object referenced = new Object()
        Supplier<String> supplier = { -> referenced.hashCode(); "value" } as Supplier<String>
        return new ProbedLazy(factory.of(supplier), [new WeakReference<Object>(referenced)])
    }

    @Timeout(30)
    def "mapping a #description source releases the source and the mapper once initialized"() {
        def probed = mappedReferencingProbes(createSource)

        expect: "the probed state is reachable through the lazy before it is initialized"
        System.gc()
        probed.probes.every { it.get() != null }

        when: "the lazy is initialized"
        def result = probed.lazy.get()
        then: "the probed state is released, while the lazy itself is still in use"
        result == "value mapped"
        ConcurrentTestUtil.poll(10) {
            System.gc()
            assert probed.probes.every { it.get() == null }
        }
        probed.lazy.isInitialized()
        probed.lazy.get() == "value mapped"

        where:
        description | createSource
        "fixed"     | { -> Lazy.fixed("value") }
        "locking"   | { -> Lazy.locking().of { "value" } }
    }

    /**
     * Creates a mapped lazy over the source created by {@code createSource}. Probes the object referenced by the
     * mapping function, and the source lazy itself.
     */
    @CompileStatic
    private static ProbedLazy mappedReferencingProbes(Closure<Lazy<String>> createSource) {
        Object referenced = new Object()
        Lazy<String> source = createSource()
        Lazy<String> mapped = source.map({ String input ->
            referenced.hashCode()
            input + " mapped"
        } as Function<String, String>)
        return new ProbedLazy(mapped, [new WeakReference<Object>(referenced), new WeakReference<Object>(source)])
    }

    @Timeout(30)
    def "flat mapping locking lazies releases the intermediate state once initialized"() {
        def probed = flatMappedReferencingProbes()

        expect: "the probed state is reachable through the lazy before it is initialized"
        System.gc()
        probed.probes.every { it.get() != null }

        when: "the lazy is initialized"
        def result = probed.lazy.get()
        then: "the probed state is released, while the lazy itself is still in use"
        result == "value composed"
        ConcurrentTestUtil.poll(10) {
            System.gc()
            assert probed.probes.every { it.get() == null }
        }
        probed.lazy.get() == "value composed"
    }

    /**
     * Creates a flat-mapped lazy over a locking source. Probes the object referenced by the source's
     * supplier, the object referenced by the flat-mapping function, and the source lazy itself.
     */
    @CompileStatic
    private static ProbedLazy flatMappedReferencingProbes() {
        Object referencedBySupplier = new Object()
        Object referencedByMapper = new Object()
        Lazy<String> first = Lazy.locking().of({ -> referencedBySupplier.hashCode(); "value" } as Supplier<String>)
        Lazy<String> composed = first.flatMap({ String input ->
            referencedByMapper.hashCode()
            Lazy.locking().of({ -> input + " composed" } as Supplier<String>)
        } as Function<String, Lazy<String>>)
        return new ProbedLazy(composed, [
            new WeakReference<Object>(referencedBySupplier),
            new WeakReference<Object>(referencedByMapper),
            new WeakReference<Object>(first)
        ])
    }

    /**
     * A lazy under test, plus weak references to the state it should release once initialized.
     */
    @CompileStatic
    private static class ProbedLazy {
        final Lazy<String> lazy
        final List<WeakReference<Object>> probes

        ProbedLazy(Lazy<String> lazy, List<WeakReference<Object>> probes) {
            this.lazy = lazy
            this.probes = probes
        }
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
