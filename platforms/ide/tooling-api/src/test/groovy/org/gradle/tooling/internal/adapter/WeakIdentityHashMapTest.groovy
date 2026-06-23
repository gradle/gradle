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

package org.gradle.tooling.internal.adapter

import spock.lang.Specification
import spock.lang.Timeout

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger


class WeakIdentityHashMapTest extends Specification {

    def "can put a value to map"() {
        Thing thing = new Thing("thing")
        WeakIdentityHashMap<Thing, String> map = new WeakIdentityHashMap<>()
        map.put(thing, "Bar")

        expect:
        map.get(thing) == "Bar"
    }

    def "can compute a value in case of absence"() {
        Thing thing = new Thing("thing")
        WeakIdentityHashMap<Thing, String> map = new WeakIdentityHashMap<>()

        assert map.get(thing) == null

        String foo = map.computeIfAbsent(
            thing,
            new WeakIdentityHashMap.AbsentValueProvider<String>() {
                @Override
                String provide() {
                    return "Foo"
                }
            })

        expect:
        foo == "Foo"
        map.get(thing) == "Foo"
    }

    def "map preserves entries until keys in use"() {
        WeakIdentityHashMap<Thing, String> map = new WeakIdentityHashMap<>()

        Thing key1 = new Thing("Key1")
        Thing key2 = new Thing("Key2")


        when:
        map.put(key1, "Foo")
        map.put(key2, "Bar")

        then:
        map.keySet().every { it.get() != null }

        when:
        key1 = null
        key2 = null

        then:
        waitForConditionAfterGC { map.keySet().every { it.get() == null } }
    }

    def "weakKey is doesn't keep strong reference at referent"() {
        Thing referent = new Thing("thing")
        WeakIdentityHashMap.WeakKey weakKey = new WeakIdentityHashMap.WeakKey(referent)

        when:
        referent = null

        then:
        waitForConditionAfterGC { weakKey.get() == null }
    }

    def "weakKeys for equal objects are different"() {
        Thing thing1 = new Thing("thing")
        Thing thing2 = new Thing("thing")

        assert thing1 == thing2

        WeakIdentityHashMap.WeakKey<Thing> weakKey1 = new WeakIdentityHashMap.WeakKey<>(thing1)
        WeakIdentityHashMap.WeakKey<Thing> weakKey2 = new WeakIdentityHashMap.WeakKey<>(thing2)

        expect:
        weakKey1 != weakKey2
    }

    def "hashCodes of weakKeys for equal objects are different"() {
        Thing thing1 = new Thing("thing")
        Thing thing2 = new Thing("thing")

        assert thing1 == thing2

        WeakIdentityHashMap.WeakKey<Thing> weakKey1 = new WeakIdentityHashMap.WeakKey<>(thing1)
        WeakIdentityHashMap.WeakKey<Thing> weakKey2 = new WeakIdentityHashMap.WeakKey<>(thing2)

        expect:
        weakKey1.hashCode() != weakKey2.hashCode()
    }

    def "weakKeys for same object are equal"() {
        Thing thing1 = new Thing("thing")

        WeakIdentityHashMap.WeakKey<Thing> weakKey1 = new WeakIdentityHashMap.WeakKey<>(thing1)
        WeakIdentityHashMap.WeakKey<Thing> weakKey2 = new WeakIdentityHashMap.WeakKey<>(thing1)

        expect:
        weakKey1 == weakKey2
    }

    def "hashCode of weakKey is system identity hashCode"() {
        Thing thing = new Thing("thing")
        WeakIdentityHashMap.WeakKey<Thing> weakKey = new WeakIdentityHashMap.WeakKey<>(thing)

        expect:
        weakKey.hashCode() == System.identityHashCode(thing)
    }

    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    def "weakKeys are removed when reference is null"() {
        WeakIdentityHashMap<Object, String> map = new WeakIdentityHashMap<>()
        Thing thing1 = new Thing("thing")
        map.put(thing1, "Foo")

        when:
        thing1 = null

        then:
        waitForConditionAfterGC { map.keySet().size() == 0 }
    }

    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    def "equals() of two different dereferenced WeakKeys returns false"() {
        WeakIdentityHashMap<Thing, String> map = new WeakIdentityHashMap<>()
        Thing thing1 = new Thing("thing1")
        Thing thing2 = new Thing("thing2")

        map.put(thing1, "value1")
        map.put(thing2, "value2")

        WeakIdentityHashMap.WeakKey<Thing> weakKey1 = null
        WeakIdentityHashMap.WeakKey<Thing> weakKey2 = null
        for (WeakIdentityHashMap.WeakKey<Thing> key : map.keySet()) {
            if (key.get() == thing1) {
                weakKey1 = key
            }
            if (key.get() == thing2) {
                weakKey2 = key
            }
        }

        when:
        thing1 = null
        thing2 = null

        then:
        waitForConditionAfterGC { !weakKey1.equals(weakKey2) }
    }

    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    def "computeIfAbsent is thread-safe (provider executed once, all threads observe same value)"() {
        given:
        int threads = Math.max(8, Runtime.runtime.availableProcessors() * 2)
        int attempts = 200

        when:
        for (int a = 0; a < attempts; a++) {
            def map = new WeakIdentityHashMap<Object, Object>()
            def key = new Object()

            def computedCount = new AtomicInteger(0)
            def results = new ConcurrentLinkedQueue<Object>()

            def barrier = new CyclicBarrier(threads)
            def pool = Executors.newFixedThreadPool(threads)
            try {
                (0..<threads).each {
                    pool.submit {
                        barrier.await() // start all threads together

                        Object v = map.computeIfAbsent(key, new WeakIdentityHashMap.AbsentValueProvider<Object>() {
                            @Override
                            Object provide() {
                                computedCount.incrementAndGet()

                                // widen the race window a bit
                                Thread.yield()
                                try {
                                    Thread.sleep(2)
                                } catch (InterruptedException ignored) {
                                    Thread.currentThread().interrupt()
                                }

                                // unique object identity -> easy to detect multiple computations
                                return new Object()
                            }
                        })

                        results.add(v)
                        return null
                    }
                }

                pool.shutdown()
                assert pool.awaitTermination(10, TimeUnit.SECONDS)
            } finally {
                pool.shutdownNow()
            }

            // If computeIfAbsent is thread-safe, provider runs once and everyone gets same instance
            assert computedCount.get() == 1 : "Race detected on attempt=$a: provider executed ${computedCount.get()} times"

            Object first = results.peek()
            assert first != null
            boolean allSameInstance = results.every { it.is(first) }
            assert allSameInstance : "Race detected on attempt=$a: not all threads observed the same value instance"
        }

        then:
        noExceptionThrown()
    }

    class Thing {
        String name

        Thing(String name) {
            this.name = name
        }

        @Override
        boolean equals(Object obj) {
            return obj.name == name
        }

        @Override
        int hashCode() {
            return name.hashCode()
        }
    }

    private boolean waitForConditionAfterGC(Closure<Boolean> condition) {
        System.gc()
        waitForCondition(condition)
    }

    private boolean waitForCondition(Closure<Boolean> condition) {
        def conditionMet = condition.call()
        def startTime = System.currentTimeMillis()

        while (!conditionMet && (System.currentTimeMillis() - startTime) < 2000) {
            Thread.sleep(100)
            conditionMet = condition.call()
        }

        conditionMet
    }
}
