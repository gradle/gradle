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

package org.gradle.internal.collect

import spock.lang.Specification

class PersistentArrayTest extends Specification {

    def 'empty arrays are the same'() {
        expect:
        PersistentArray.of() === PersistentArray.of()
        PersistentArray.of().size() == 0
    }

    def 'copyOf(array) === array'() {
        expect:
        PersistentArray.copyOf(array) === array

        where:
        array << [PersistentArray.of(), PersistentArray.of(1), PersistentArray.of(1, 2, 3)]
    }

    def 'plus'() {
        given:
        def array = PersistentArray.of()

        when:
        size.times {
            array = array + it
            assert array.get(it) == it
            assert array.size() == it + 1
        }

        then:
        size.times {
            assert array.get(it) == it, "${array.size()}:${it}"
        }

        where:
        size << [1, 32, 33, 32 * 32, 32 * 32 + 1, 32 * 32 * 32 + 1, 32 * 32 * 32 * 32 + 1]
    }

    def 'iterator'() {
        given:
        def list = (0..<size).collect()
        def array = PersistentArray.copyOf(list)

        expect:
        array.toList() == list

        where:
        size << [0, 1, 32, 33, 32 * 32 + 1, 32 * 32 * 32 * 32 + 1]
    }

    def 'contains'() {
        given:
        def list = size > 0 ? (1..size).collect() : []
        def array = PersistentArray.copyOf(list)

        expect:
        !array.contains(0)
        !array.contains(size + 1)
        list.every {
            array.contains(it)
        }

        where:
        size << [0, 1, 32, 33, 32 * 32 + 1]
    }

    def 'forEach'() {
        given:
        def list = size > 0 ? (1..size).collect() : []
        def array = PersistentArray.copyOf(list)

        when:
        def result = []
        array.forEach {
            result << it
        }

        then:
        result == list

        where:
        size << [0, 1, 32, 33, 32 * 32 + 1]
    }

    def 'iterator throws NoSuchElement'() {
        given:
        def array = PersistentArray.copyOf((0..<size).collect())
        def iterator = array.iterator()

        when:
        size.times {
            iterator.next()
        }

        and:
        iterator.next()

        then:
        thrown(NoSuchElementException)

        where:
        size << [0, 1, 32, 33, 32 * 32 + 1, 32 * 32 * 32 * 32 + 1]
    }

    def 'getLast'() {
        expect:
        null === PersistentArray.of().last
        1 == PersistentArray.of(1).last
        2 == PersistentArray.of(1, 2).last
        33 == PersistentArray.copyOf((1..33)).last
    }

    def 'toString == [v1,v2,...]'() {
        expect:
        def integers = 0..<arraySize
        def expected = integers.toList().toString().replaceAll(" ", "")
        expected == PersistentArray.copyOf(integers).toString()

        where:
        arraySize << [0, 1, 2, 33]
    }

    def 'array get throws IndexOutOfBoundsException for negative index'() {
        when:
        array.get(-1)

        then:
        thrown(IndexOutOfBoundsException)

        where:
        array << [
            PersistentArray.of(),
            PersistentArray.of(1),
            PersistentArray.of(1, 2, 3),
            PersistentArray.copyOf((1..33))
        ]
    }

    def 'array get throws IndexOutOfBoundsException for index >= size'() {
        when:
        array.get(array.size())

        then:
        thrown(IndexOutOfBoundsException)

        where:
        array << [
            PersistentArray.of(),
            PersistentArray.of(1),
            PersistentArray.of(1, 2, 3),
            PersistentArray.copyOf((1..33))
        ]
    }

    def 'array hashCode is consistent with equals'() {
        given:
        def array1 = PersistentArray.copyOf(1..size)
        def array2 = PersistentArray.copyOf(1..size)

        expect:
        array1 == array2
        array1.hashCode() == array2.hashCode()

        where:
        size << [1, 2, 32, 33, 100, 1025]
    }
}
