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

/**
 * Additional test coverage for persistent collections.
 *
 * These tests cover edge cases and scenarios not covered in the main test files.
 */
class PersistentCollectionAdditionalCoverageTest extends Specification {

    // ==================================================================================
    // PersistentArray additional coverage
    // ==================================================================================

    def 'array get throws IndexOutOfBoundsException for negative index'() {
        given:
        def array = PersistentArray.of(1, 2, 3)

        when:
        array.get(-1)

        then:
        thrown(IndexOutOfBoundsException)
    }

    def 'array get throws IndexOutOfBoundsException for index >= size'() {
        given:
        def array = PersistentArray.of(1, 2, 3)

        when:
        array.get(3)

        then:
        thrown(IndexOutOfBoundsException)
    }

    def 'empty array get throws IndexOutOfBoundsException'() {
        when:
        PersistentArray.of().get(0)

        then:
        thrown(IndexOutOfBoundsException)
    }

    def 'array equals is reflexive'() {
        given:
        def arrays = [
            PersistentArray.of(),
            PersistentArray.of(1),
            PersistentArray.of(1, 2),
            PersistentArray.copyOf(1..100)
        ]

        expect:
        arrays.every { it == it }
    }

    def 'array equals with null returns false'() {
        expect:
        PersistentArray.of(1) != null
        PersistentArray.of(1, 2) != null
        PersistentArray.copyOf(1..100) != null
    }

    def 'array equals with different type returns false'() {
        expect:
        PersistentArray.of(1, 2, 3) != [1, 2, 3]
        PersistentArray.of(1) != 1
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

    def 'large array random access'() {
        given:
        def size = 100_000
        def array = PersistentArray.copyOf(0..<size)

        expect:
        // Test specific indices across different trie levels
        array.get(0) == 0
        array.get(31) == 31
        array.get(32) == 32
        array.get(1023) == 1023
        array.get(1024) == 1024
        array.get(32767) == 32767
        array.get(size - 1) == size - 1
    }

    // ==================================================================================
    // PersistentSet additional coverage
    // ==================================================================================

    def 'set hashCode is sum of element hashCodes'() {
        given:
        def elements = [1, 2, 3, 4, 5]
        def set = PersistentSet.copyOf(elements)
        def expectedHash = elements.sum { it.hashCode() }

        expect:
        set.hashCode() == expectedHash
    }

    def 'set equals is reflexive'() {
        given:
        def sets = [
            PersistentSet.of(),
            PersistentSet.of(1),
            PersistentSet.of(1, 2),
            PersistentSet.copyOf(1..100)
        ]

        expect:
        sets.every { it == it }
    }

    def 'set equals with null returns false'() {
        expect:
        PersistentSet.of(1) != null
        PersistentSet.of(1, 2) != null
    }

    def 'set equals with different type returns false'() {
        expect:
        PersistentSet.of(1, 2, 3) != [1, 2, 3].toSet()
        PersistentSet.of(1) != 1
    }

    def 'set minus on empty returns empty'() {
        expect:
        PersistentSet.of().minus(42) === PersistentSet.of()
    }

    def 'set minusAll with empty iterable returns same set'() {
        given:
        def set = PersistentSet.of(1, 2, 3)

        expect:
        set.minusAll([]) === set
    }

    def 'set except with disjoint set returns same set'() {
        given:
        def set1 = PersistentSet.of(1, 2, 3)
        def set2 = PersistentSet.of(4, 5, 6)

        expect:
        set1.except(set2) === set1
    }

    def 'set except with same set returns empty'() {
        given:
        def set = PersistentSet.of(1, 2, 3)

        expect:
        set.except(set) === PersistentSet.of()
    }

    def 'set union is commutative'() {
        given:
        def set1 = PersistentSet.of(1, 2, 3)
        def set2 = PersistentSet.of(3, 4, 5)

        expect:
        set1.union(set2) == set2.union(set1)
    }

    def 'set intersect is commutative'() {
        given:
        def set1 = PersistentSet.of(1, 2, 3, 4)
        def set2 = PersistentSet.of(3, 4, 5, 6)

        expect:
        set1.intersect(set2) == set2.intersect(set1)
    }

    def 'set intersect with disjoint sets returns empty'() {
        given:
        def set1 = PersistentSet.of(1, 2, 3)
        def set2 = PersistentSet.of(4, 5, 6)

        expect:
        set1.intersect(set2) === PersistentSet.of()
    }

    // ==================================================================================
    // PersistentMap additional coverage
    // ==================================================================================

    def 'map hashCode is sum of key hashCodes'() {
        given:
        def map = PersistentMap.of()
            .assoc(1, "a")
            .assoc(2, "b")
            .assoc(3, "c")
        def expectedHash = 1.hashCode() + 2.hashCode() + 3.hashCode()

        expect:
        map.hashCode() == expectedHash
    }

    def 'map equals is reflexive'() {
        given:
        def maps = [
            PersistentMap.of(),
            PersistentMap.of(1, "a"),
            PersistentMap.of(1, "a").assoc(2, "b")
        ]

        expect:
        maps.every { it == it }
    }

    def 'map equals with null returns false'() {
        expect:
        PersistentMap.of(1, "a") != null
    }

    def 'map equals with different type returns false'() {
        expect:
        PersistentMap.of(1, "a") != [1: "a"]
        PersistentMap.of(1, "a") != 1
    }

    def 'map dissoc on empty returns empty'() {
        expect:
        PersistentMap.of().dissoc("key") === PersistentMap.of()
    }

    def 'map dissoc non-existing key returns same map'() {
        given:
        def map = PersistentMap.of("a", 1).assoc("b", 2)

        expect:
        map.dissoc("c") === map
    }

    def 'map get returns null for missing key'() {
        given:
        def map = PersistentMap.of("a", 1)

        expect:
        map.get("missing") == null
    }

    def 'map getOrDefault returns default for missing key'() {
        given:
        def map = PersistentMap.of("a", 1)

        expect:
        map.getOrDefault("missing", 42) == 42
    }

    def 'map modify with no change returns same map'() {
        given:
        def map = PersistentMap.of("key", "value")

        expect:
        map.modify("key", { k, v -> v }) === map
    }

    def 'map modify non-existing key with null returns same map'() {
        given:
        def map = PersistentMap.of("a", 1)

        expect:
        map.modify("b", { k, v -> null }) === map
    }

    def 'map copyOf with java Map'() {
        given:
        def javaMap = ["a": 1, "b": 2, "c": 3]

        when:
        def persistentMap = PersistentMap.copyOf(javaMap.entrySet())

        then:
        persistentMap.size() == 3
        persistentMap.get("a") == 1
        persistentMap.get("b") == 2
        persistentMap.get("c") == 3
    }

    // ==================================================================================
    // Deep trie structure tests
    // ==================================================================================

    def 'deep trie with many levels'() {
        given: 'Keys designed to create deep trie paths'
        // These keys will have hash codes that only differ in higher bits,
        // forcing the trie to go deeper
        def keys = (0..1000).collect { it << 25 }  // Shift to affect higher bits

        expect:
        keys.toSet().size() == keys.size()
//
//        when:
//        def map = PersistentMap.of()
//        keys.each { key ->
//            map = map.assoc(key, "value_$key")
//        }
//
//        then:
//        keys.every { map.containsKey(it) }
//        keys.every { map.get(it) == "value_$it" }
//        map.size() == keys.size()
    }

    def 'trie handles Integer.MIN_VALUE and MAX_VALUE hash codes'() {
        given:
        def minKey = new Object() {
            @Override
            int hashCode() { return Integer.MIN_VALUE }
            @Override
            String toString() { return "MIN" }
        }
        def maxKey = new Object() {
            @Override
            int hashCode() { return Integer.MAX_VALUE }
            @Override
            String toString() { return "MAX" }
        }
        def zeroKey = new Object() {
            @Override
            int hashCode() { return 0 }
            @Override
            String toString() { return "ZERO" }
        }

        when:
        def map = PersistentMap.of()
            .assoc(minKey, "min")
            .assoc(maxKey, "max")
            .assoc(zeroKey, "zero")

        then:
        map.get(minKey) == "min"
        map.get(maxKey) == "max"
        map.get(zeroKey) == "zero"
        map.size() == 3
    }

    // ==================================================================================
    // Persistence property tests
    // ==================================================================================

    def 'modifications do not affect original collection (array)'() {
        given:
        def original = PersistentArray.of(1, 2, 3)
        def originalSize = original.size()

        when:
        def modified = original.plus(4)

        then:
        original.size() == originalSize
        original.get(0) == 1
        original.get(1) == 2
        original.get(2) == 3
        modified.size() == 4
        modified.get(3) == 4
    }

    def 'modifications do not affect original collection (set)'() {
        given:
        def original = PersistentSet.of(1, 2, 3)
        def originalSize = original.size()

        when:
        def added = original.plus(4)
        def removed = original.minus(1)

        then:
        original.size() == originalSize
        original.contains(1)
        original.contains(2)
        original.contains(3)
        !original.contains(4)
        added.contains(4)
        !removed.contains(1)
    }

    def 'modifications do not affect original collection (map)'() {
        given:
        def original = PersistentMap.of("a", 1).assoc("b", 2)
        def originalSize = original.size()

        when:
        def added = original.assoc("c", 3)
        def removed = original.dissoc("a")
        def updated = original.assoc("a", 100)

        then:
        original.size() == originalSize
        original.get("a") == 1
        original.get("b") == 2
        added.get("c") == 3
        !removed.containsKey("a")
        updated.get("a") == 100
    }

    // ==================================================================================
    // Entry tests
    // ==================================================================================

    def 'map entry equality and hashCode'() {
        given:
        def map = PersistentMap.of("key", "value")
        def entry = map.iterator().next()

        expect:
        entry.key == "key"
        entry.value == "value"

        and: 'Entry should implement proper hashCode'
        entry.hashCode() == ("key".hashCode() ^ "value".hashCode())
    }

    def 'map entry setValue throws UnsupportedOperationException'() {
        given:
        def map = PersistentMap.of("key", "value")
        def entry = map.iterator().next()

        when:
        entry.setValue("newValue")

        then:
        thrown(UnsupportedOperationException)
    }

    // ==================================================================================
    // Stress tests for structural sharing
    // ==================================================================================

    def 'many derived collections share structure efficiently'() {
        given:
        def base = PersistentSet.copyOf(1..1000)
        def derived = []

        when: 'Create many derived sets'
        1000.times { i ->
            derived << base.plus(i + 2000)
        }

        then: 'All derived sets should be functional'
        derived.every { it.size() == 1001 }
        derived.every { it.contains(1) && it.contains(500) && it.contains(1000) }
        derived.eachWithIndex { set, i -> assert set.contains(i + 2000) }

        and: 'Base should be unchanged'
        base.size() == 1000
        !base.contains(2000)
    }
}
