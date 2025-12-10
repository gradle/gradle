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
import spock.lang.Issue

/**
 * Tests that expose bugs and edge cases in the persistent collection implementations.
 *
 * These tests are expected to FAIL until the underlying bugs are fixed.
 */
class PersistentCollectionBugsTest extends Specification {

    // ==================================================================================
    // ❌ BUG: HashCollisionNode.equals() is broken for maps
    // ==================================================================================

    @Issue("HashCollisionNode.equals() uses hardcoded payload=0, breaking map equality")
    def 'maps with same keys but different values in collision bucket should NOT be equal'() {
        given: 'Two keys that have the same hash code (collision)'
        def key1 = 42
        def key2 = new HashCollision(42)  // Same hash as 42

        and: 'Two maps with the same keys but DIFFERENT values'
        def map1 = PersistentMap.of()
            .assoc(key1, "value1")
            .assoc(key2, "value2")

        def map2 = PersistentMap.of()
            .assoc(key1, "value1")
            .assoc(key2, "DIFFERENT_VALUE")  // Different value for key2

        expect: 'The maps should NOT be equal because values differ'
        // ❌ BUG: This currently passes incorrectly because HashCollisionNode.equals()
        // only checks if keys exist, ignoring values entirely
        map1 != map2

        and: 'Verify the maps actually have different values'
        map1.get(key2) == "value2"
        map2.get(key2) == "DIFFERENT_VALUE"
    }

    @Issue("HashCollisionNode.equals() iterates over values as if they were keys")
    def 'map collision equality should compare key-value pairs, not just keys'() {
        given: 'Keys that collide'
        def key1 = 100
        def collision1 = new HashCollision(100)
        def collision2 = new HashCollision(100)  // Another collision

        and: 'A map with multiple colliding entries'
        def map1 = PersistentMap.of()
            .assoc(key1, "A")
            .assoc(collision1, "B")
            .assoc(collision2, "C")

        def map2 = PersistentMap.of()
            .assoc(key1, "A")
            .assoc(collision1, "X")  // Different value
            .assoc(collision2, "Y")  // Different value

        expect: 'Maps with different values should not be equal'
        // ❌ BUG: Currently this may incorrectly pass
        map1 != map2
    }

    @Issue("HashCollisionNode.equals() checks values as keys")
    def 'map equality with collision should work correctly even when values look like keys'() {
        given: 'Keys and values that could be confused'
        def key1 = 42
        def key2 = new HashCollision(42)

        // Map where value "42" happens to equal key1
        def map1 = PersistentMap.of()
            .assoc(key1, "42")
            .assoc(key2, "collision")

        // Map with different values
        def map2 = PersistentMap.of()
            .assoc(key1, "different")
            .assoc(key2, "also_different")

        expect: 'Maps should not be equal'
        // ❌ BUG: HashCollisionNode.equals() might check if "42" (a value) is a key
        map1 != map2
    }

    // ==================================================================================
    // 🤔 Issue: PersistentMap.getOrDefault() cannot distinguish null values
    // ==================================================================================

    @Issue("PersistentMap.getOrDefault default implementation can't handle null values")
    def 'getOrDefault should return null when key is mapped to null, not defaultValue'() {
        given: 'A map with a key explicitly mapped to null'
        // Note: This tests if null values are even supported
        def map = PersistentMap.of("key", null)

        expect: 'get() should return null for the existing key'
        map.get("key") == null

        and: 'containsKey should return true'
        map.containsKey("key")

        and: 'getOrDefault should return null (the stored value), not the default'
        // 🤔 This may fail for PersistentMap1 which uses the buggy default implementation
        map.getOrDefault("key", "default") == null

        and: 'For a missing key, getOrDefault should return the default'
        map.getOrDefault("missing", "default") == "default"
    }

    @Issue("PersistentMap1 inherits buggy getOrDefault from interface")
    def 'PersistentMap1 getOrDefault with null value'() {
        given: 'A singleton map with null value (uses PersistentMap1)'
        def map = PersistentMap.of("key", null)

        expect: 'This is a PersistentMap1 instance'
        map.class.simpleName == "PersistentMap1"

        and: 'getOrDefault should return null, not default'
        // 🤔 BUG: PersistentMap1 uses default getOrDefault which returns defaultValue when get() returns null
        map.getOrDefault("key", "DEFAULT") == null
    }

    // ==================================================================================
    // Additional Coverage: Equals symmetry
    // ==================================================================================

    def 'PersistentSet1 and PersistentSetTrie with same single element should be equal'() {
        given: 'A singleton set created directly'
        def set1 = PersistentSet.of(42)

        and: 'A set that becomes singleton after operations (could be trie internally)'
        def setViaOps = PersistentSet.of(1, 2, 42).minus(1).minus(2)

        expect: 'Both should be equal regardless of internal representation'
        set1 == setViaOps
        setViaOps == set1
        set1.hashCode() == setViaOps.hashCode()
    }

    def 'PersistentMap1 and PersistentMapTrie with same single entry should be equal'() {
        given: 'A singleton map created directly'
        def map1 = PersistentMap.of("key", "value")

        and: 'A map that becomes singleton after operations'
        def mapViaOps = PersistentMap.of("key", "value")
            .assoc("other", "other_value")
            .dissoc("other")

        expect: 'Both should be equal regardless of internal representation'
        // This tests the collapse invariant - dissoc should return PersistentMap1
        map1 == mapViaOps
        mapViaOps == map1
        map1.hashCode() == mapViaOps.hashCode()
    }

    // ==================================================================================
    // Additional Coverage: Edge cases in collision handling
    // ==================================================================================

    def 'multiple levels of hash collision should work correctly'() {
        given: 'Many keys with the same hash'
        def baseHash = 12345
        def keys = (0..10).collect { new HashCollision(baseHash) }

        when: 'Build a map with all colliding keys'
        def map = PersistentMap.of()
        keys.eachWithIndex { key, idx ->
            map = map.assoc(key, "value_$idx")
        }

        then: 'All keys should be retrievable'
        keys.eachWithIndex { key, idx ->
            assert map.get(key) == "value_$idx"
            assert map.containsKey(key)
        }

        and: 'Size should be correct'
        map.size() == keys.size()
    }

    def 'removing from collision bucket should maintain correct values'() {
        given: 'Keys that collide'
        def key1 = 42
        def key2 = new HashCollision(42)
        def key3 = new HashCollision(42)

        and: 'A map with collision entries'
        def map = PersistentMap.of()
            .assoc(key1, "v1")
            .assoc(key2, "v2")
            .assoc(key3, "v3")

        when: 'Remove the middle entry'
        def afterRemove = map.dissoc(key2)

        then: 'Remaining entries should have correct values'
        afterRemove.get(key1) == "v1"
        afterRemove.get(key3) == "v3"
        !afterRemove.containsKey(key2)
        afterRemove.size() == 2
    }

    def 'set collision equality is symmetric'() {
        given: 'Sets with colliding elements built in different orders'
        def key1 = 42
        def key2 = new HashCollision(42)
        def key3 = new HashCollision(42)

        def set1 = PersistentSet.of().plus(key1).plus(key2).plus(key3)
        def set2 = PersistentSet.of().plus(key3).plus(key1).plus(key2)
        def set3 = PersistentSet.of().plus(key2).plus(key3).plus(key1)

        expect: 'All sets should be equal to each other'
        set1 == set2
        set2 == set1
        set1 == set3
        set3 == set1
        set2 == set3
        set3 == set2
    }

    // ==================================================================================
    // Additional Coverage: Iterator consistency
    // ==================================================================================

    def 'iterator count matches size for maps with collisions'() {
        given: 'A map with many collisions'
        def keys = []
        (0..100).each {
            keys << it
            keys << new HashCollision(it)
        }

        when:
        def map = PersistentMap.of()
        keys.each { key ->
            map = map.assoc(key, key.toString())
        }

        then:
        def count = 0
        map.each { count++ }
        count == map.size()
        count == keys.size()
    }

    def 'iterator count matches size for sets with collisions'() {
        given: 'A set with many collisions'
        def keys = []
        (0..100).each {
            keys << it
            keys << new HashCollision(it)
        }

        when:
        def set = PersistentSet.copyOf(keys)

        then:
        def count = 0
        set.each { count++ }
        count == set.size()
        count == keys.size()
    }

    // ==================================================================================
    // Additional Coverage: Boundary conditions
    // ==================================================================================

    def 'array transitions between implementation types correctly'() {
        given:
        def array = PersistentArray.of()

        when: 'Add exactly 32 elements (PersistentArraySmall max)'
        32.times { array = array.plus(it) }

        then:
        array.size() == 32
        array.class.simpleName == "PersistentArraySmall"

        when: 'Add one more to trigger trie'
        array = array.plus(32)

        then:
        array.size() == 33
        array.class.simpleName == "PersistentArrayTrie"

        and: 'All elements accessible'
        (0..32).every { array.get(it) == it }
    }

    def 'set operations at size boundaries'() {
        given: 'A set with 2 elements (minimum for PersistentSetTrie)'
        def set = PersistentSet.of(1, 2)

        expect:
        set.size() == 2
        set.class.simpleName == "PersistentSetTrie"

        when: 'Remove one element'
        def singleton = set.minus(1)

        then: 'Should collapse to PersistentSet1'
        singleton.size() == 1
        singleton.class.simpleName == "PersistentSet1"
        singleton.contains(2)
        !singleton.contains(1)
    }

    def 'map operations at size boundaries'() {
        given: 'A map with 2 entries (minimum for PersistentMapTrie)'
        def map = PersistentMap.of("a", 1).assoc("b", 2)

        expect:
        map.size() == 2
        map.class.simpleName == "PersistentMapTrie"

        when: 'Remove one entry'
        def singleton = map.dissoc("a")

        then: 'Should collapse to PersistentMap1'
        singleton.size() == 1
        singleton.class.simpleName == "PersistentMap1"
        singleton.containsKey("b")
        !singleton.containsKey("a")
    }
}
