/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.execution.plan

import spock.lang.Issue
import spock.lang.Specification

import java.util.concurrent.CyclicBarrier
import java.util.concurrent.TimeUnit

class LazySortedReferenceHashSetTest extends Specification {

    def set = createLazySet()

    private NodeSets.LazySortedReferenceHashSet<String> createLazySet() {
        new NodeSets.LazySortedReferenceHashSet<String>({ s1, s2 -> s1.compareTo(s2) })
    }

    def "reference set behavior"() {
        expect:
        set.add("foo")
        set.size() == 1
        set.contains("foo")
        !set.contains("bar")
        set.add("bar")
        set.size() == 2
        set.contains("foo")
        set.contains("bar")
        set.containsAll(["foo", "bar"])
        !set.add("foo")
        !set.add("bar")
        set.toList() == ["bar", "foo"]
        set.remove("foo")
        set.size() == 1
        !set.contains("foo")
        set.toList() == ["bar"]
        // set is identity based
        set.add(new String("bar"))
        set.toList() == ["bar", "bar"]
    }

    def "iterators can remove elements"() {
        given:
        set.addAll(["1", "2", "3"])

        expect:
        def it = set.iterator()
        it.next() == "1"
        it.remove()
        !set.contains("1")
        it.next() == "2"
        it.next() == "3"
        it.remove()
        !set.contains("3")
        set.contains("2")
        !it.hasNext()
    }

    def "iterators throw ConcurrentModificationException on direct set modification"() {
        given:
        set.addAll(["1", "2", "3"])
        def it = set.iterator()

        when:
        set.add("4")
        it.next()

        then:
        thrown(ConcurrentModificationException)
    }

    def "iterators throw ConcurrentModificationException on iterator set modification"() {
        given:
        set.addAll(["1", "2", "3"])
        def it1 = set.iterator()
        def it2 = set.iterator()
        assert it1.next() == "1"
        assert it2.next() == "1"
        it1.remove()

        when:
        it2.next()

        then:
        thrown(ConcurrentModificationException)
    }

    @Issue('https://github.com/gradle/gradle/issues/35522')
    def "can remove first element via iterator when backing array is exactly full"() {
        given:
        // Fill up to initial capacity so that array.length == size
        def initialSet = (1..NodeSets.LazySortedReferenceHashSet.INITIAL_CAPACITY).collect { it.toString() }
        set.addAll(initialSet)

        when:
        def it = set.iterator()
        assert it.next() == "1"
        it.remove()

        then:
        noExceptionThrown()

        and:
        set.toList() == initialSet.drop(1)
    }

    def "iterator.remove before next throws IllegalStateException"() {
        given:
        set.addAll(["a", "b"]) // ensure we use the non-empty iterator from our implementation
        def it = set.iterator()

        when:
        it.remove()

        then:
        thrown(IllegalStateException)
    }

    def "iterator.remove called twice without next throws IllegalStateException"() {
        given:
        set.addAll(["1", "2", "3"])
        def it = set.iterator()
        assert it.next() == "1"
        it.remove()

        when:
        it.remove()

        then:
        thrown(IllegalStateException)
    }

    def "next past end throws NoSuchElementException"() {
        given:
        set.addAll(["x"])
        def it = set.iterator()
        assert it.next() == "x"

        when:
        it.next()

        then:
        thrown(NoSuchElementException)
    }

    @Issue("https://github.com/gradle/gradle/issues/36081")
    def "multiple readers should be safe"() {
        given:
        def randomizer = new Random(0)
        def items = (1..size).collect {
            randomizer.nextInt().collect { it.abs() }.toString()
        }.toSet()
        def control = items.sort()
        // we build the collection from a single thread
        def collection = implementation.create(items)
        // then read it from multiple threads
        CyclicBarrier barrier = new CyclicBarrier(threadCount + 1)
        List<Object> collected = (1..threadCount).toList()
        threadCount.times { index ->
            new Thread() {
                @Override
                void run() {
                    // wait for all threads to be ready
                    barrier.await()
                    try {
                        collected[index - 1] = collection.toList()
                    } catch (Exception e) {
                        collected[index - 1] = e
                    }
                    // wait for all threads to complete
                    barrier.await()
                }
            }.tap {
                it.start()
            }
        }

        // wait for all threads to be ready
        barrier.await(5, TimeUnit.SECONDS)

        // wait for all threads to complete
        barrier.await(30, TimeUnit.SECONDS)

        expect:
        def exception = collected.find {
            it instanceof Exception
        }
        if (exception) {
            throw exception
        }
        collected.each { List<String> sorted ->
            assert sorted.size() == items.size()
            sorted.eachWithIndex { value, i ->
                // all elements are unique and in order
                assert i == 0 || sorted[i - 1] < sorted[i]
                // and identical to control
                assert control[i] == sorted[i]
            }
        }

        where:
        [size, threadCount, implementation] << [
            [100000],
            [2, 10, 20, 40],
            [
                [type: "LazySortedReferenceHashSet", create: { source -> createLazySet().tap { set -> set.addAll(source) } }],
                // test the test
                [type: "TreeSet", create: { source -> new TreeSet<String>(source) }]
            ]
        ].combinations()
    }
}
