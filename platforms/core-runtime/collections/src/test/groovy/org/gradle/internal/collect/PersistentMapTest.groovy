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

import org.gradle.util.internal.ToBeImplemented
import spock.lang.Specification

class PersistentMapTest extends Specification {

    def 'empty === empty'() {
        expect:
        PersistentMap.of() === PersistentMap.of()
    }

    def 'copyOf(map) === map'() {
        expect:
        PersistentMap.copyOf(map) === map

        where:
        map << [PersistentMap.of(), PersistentMap.of(1, "1"), PersistentMap.of(1, "1").assoc(2, "2")]
    }

    def 'singleton maps with same element are equal'() {
        given:
        def map1 = PersistentMap.of(42, "42")
        def map2 = PersistentMap.of(42, "42")

        expect:
        map1 == map2
        map1.hashCode() == map2.hashCode()
        PersistentMap.of(37, "37") != map1
        PersistentMap.of(37, "37").hashCode() != map1.hashCode()
        map1.containsKey(42)
        !map1.containsKey(37)
    }

    def 'assoc existing entry returns same map'() {
        given:
        def collision = new HashCollision(42)
        def map1 = PersistentMap.of()
            .assoc(42, "42")
        def map2 = map1
            .assoc(33, "33")
            .assoc(collision, "*42*")

        expect:
        map1.containsKey(42)
        map1.get(42) == "42"
        map2.containsKey(42)
        map2.get(42) == "42"
        map2.containsKey(collision)
        map2.get(collision) == "*42*"
        map2.containsKey(33)
        map2.get(33) == "33"
        map1 === map1.assoc(42, "42")
        map2 === map2.assoc(42, "42")
        map2 === map2.assoc(collision, "*42*")
        map2 === map2.assoc(33, "33")
    }

    def 'assoc replaces entry'() {
        given:
        def collision = new HashCollision(42)
        def map = PersistentMap.of()
            .assoc(42, "42")
            .assoc(collision, "*42*")
            .assoc(37, "37")
            .assoc(42, "24")
            .assoc(37, "73")
            .assoc(collision, "*24*")
        def expected = PersistentMap.of()
            .assoc(42, "24")
            .assoc(37, "73")
            .assoc(collision, "*24*")

        expect:
        map.containsKey(42)
        map.get(42) == "24"
        map.containsKey(collision)
        map.get(collision) == "*24*"
        map.get(37) == "73"
        map.size() == 3
        map == expected
    }

    <T> PersistentMap<T, String> mapOf(Iterable<T> keys) {
        def map = PersistentMap.<T, String> of()
        for (T key : keys) {
            map = map.assoc(key, key.toString())
        }
        return map
    }

    def 'lookup'() {
        given:
        def random = new Random(42)
        def present = []
        def absent = []
        def i = 0
        random.ints(2 * 1024).forEach {
            if (i++ % 2 == 0) {
                present.add(it)
            } else {
                absent.add(it)
            }
        }
        def map1 = mapOf(present)
        def map2 = mapOf(present.shuffled(random))
        def map3 = mapOf(present.shuffled(random))

        expect:
        present.every {
            def val = it.toString()
            assert map1.containsKey(it)
            assert val == map1.get(it)
            assert map2.containsKey(it)
            assert val == map2.get(it)
            assert map3.containsKey(it)
            assert val == map3.get(it)
            true
        }

        and:
        absent.every {
            assert !map1.containsKey(it)
            assert !map2.containsKey(it)
            assert !map3.containsKey(it)
            true
        }

        and:
        map1.size() == present.size()
        map2.size() == present.size()
        map1.size() == present.size()
    }

    def 'map equals with different type returns false'() {
        expect:
        PersistentMap.of(1, "a") != [1: "a"]
    }

    def 'maps with same elements are equal'() {
        given:
        def random = new Random(42)
        def list = random.ints(1024).toArray().toList()
        def map1 = mapOf(list)
        def map2 = mapOf(list.shuffled(random))
        def map3 = mapOf(list.shuffled(random))

        expect:
        map1 == map2
        map1 == map3
        map2 == map1
        map2 == map3
        map3 == map1
        map3 == map2

        and:
        map1.hashCode() == map2.hashCode()
        map2.hashCode() == map3.hashCode()
    }

    def 'maps with same elements, including collisions, are equal'() {
        given:
        def random = new Random(seed)
        def list = []
        random.ints(3 /*16 * 1024*/).forEach {
            list << it
            list << new HashCollision(it)
            list << new HashCollision(it)
        }
        def map1 = mapOf(list)
        def map2 = mapOf(list.shuffled(random))
        def map3 = mapOf(list.shuffled(random))

        expect:
        map1 == map2
        map1 == map3
        map2 == map1
        map2 == map3
        map3 == map1
        map3 == map2

        and:
        map1.hashCode() == map2.hashCode()
        map2.hashCode() == map3.hashCode()

        and:
        list.every { map1.containsKey(it) }

        where:
        seed << (1..10)
    }

    def 'iterator'() {
        given:
        def random = new Random(42)
        def keys = random.ints(collectionSize).toArray().toSet()

        when:
        def map = mapOf(keys)

        then:
        map.collect { [it.key, it.value] }.sort() == keys.collect { [it, it.toString()] }.sort()

        where:
        collectionSize << [0, 1, 5, 32, 1024]
    }

    def 'iterator with collisions'() {
        given:
        def random = new Random(42)
        def keys = []
        random.ints(collectionSize).forEach {
            keys << it
            keys << new HashCollision(it)
        }

        when:
        def map = mapOf(keys)

        then:
        map.collect { [it.key, it.value] }.sort() == keys.collect { [it, it.toString()] }.sort()

        where:
        collectionSize << [0, 1, 5, 32, 1024]
    }

    def 'forEach'() {
        given:
        def random = new Random(42)
        def keys = random.ints(collectionSize).toArray().toSet()
        def map = mapOf(keys)

        when:
        def result = []
        map.forEach {
            result << [it.key, it.value]
        }

        then:
        result.size() == keys.size()
        result.sort() == keys.collect { [it, it.toString()] }.sort()

        where:
        collectionSize << [0, 1, 5, 32, 1024, 16 * 1024]
    }

    def 'modify puts element'() {
        given:
        def map = PersistentMap.<Integer, String> of()

        when:
        map = map.modify(42, { k, v ->
            assert k == 42
            assert v === null
            "42"
        })

        then:
        map.size() == 1
        map.get(42) == "42"

        when:
        map = map.modify(42, { k, v ->
            assert k == 42
            assert v == "42"
            "*42"
        })

        then:
        map.size() == 1
        map.get(42) == "*42"

        when:
        map = map.modify(37, { k, v ->
            assert k == 37
            assert v == null
            "37"
        })

        then:
        map.size() == 2
        map.get(42) == "*42"
        map.get(37) == "37"

        when:
        map = map.modify(37, { k, v ->
            assert k == 37
            assert v == "37"
            "*37"
        }).modify(42, { k, v ->
            assert k == 42
            assert v == "*42"
            "**42"
        })

        then:
        map.size() == 2
        map.get(42) == "**42"
        map.get(37) == "*37"
    }

    def 'modify removes element when function returns null'() {
        expect:
        PersistentMap.of() === PersistentMap.of().modify(42, { k, v -> null })
        PersistentMap.of() === PersistentMap.of(42, "42").modify(42, { k, v -> null })
        PersistentMap.of(37, "37") == PersistentMap.of(42, "42").assoc(37, "37").modify(42, { k, v -> null })
    }

    def 'map modify with no change returns same map'() {
        expect:
        map.modify("key", { k, v -> v }) === map

        where:
        map << [PersistentMap.of("key", "value"), PersistentMap.of("key", "value").assoc("a", "1")]
    }

    def 'map modify non-existing key with null returns same map'() {
        expect:
        map.modify("b", { k, v -> null }) === map

        where:
        map << [PersistentMap.of(), PersistentMap.of("key", "value"), PersistentMap.of("key", "value").assoc("a", "1")]
    }

    def 'dissoc is inverse to assoc'() {
        given:
        def random = new Random(42)
        def keys = []
        def map = PersistentMap.of()
        def maps = []

        when:
        random.ints(collectionSize).toArray().toSet().forEach {
            keys << it
        }
        if (withCollision) {
            keys.take(collectionSize >> 2).toList().forEach {
                keys << new HashCollision(it)
            }
        }

        and:
        keys.forEach {
            maps << map
            map = map.assoc(it, it.toString())
        }

        then:
        for (int i = keys.size() - 1; i >= 0; i--) {
            map = map.dissoc(keys[i])
            assert map.size() == i
            assert map == maps[i]
        }

        and:
        map.isEmpty()

        where:
        collectionSize = 1024
        withCollision << [false, true]
    }

    def 'map dissoc non-existing key returns same map'() {
        given:
        def map = PersistentMap.of("a", 1).assoc("b", 2)

        expect:
        map.dissoc("c") === map
    }

    def 'map get returns null for missing key'() {
        expect:
        map.get("missing") == null

        where:
        map << [
            PersistentMap.of(),
            PersistentMap.of("a", 1),
            PersistentMap.of("a", 1).assoc("b", 2)
        ]
    }

    def 'map getOrDefault returns default for missing key'() {
        expect:
        map.getOrDefault("missing", 42) == 42

        where:
        map << [
            PersistentMap.of(),
            PersistentMap.of("a", 1),
            PersistentMap.of("a", 1).assoc("b", 2)
        ]
    }

    def 'map value cannot be null'() {
        when:
        PersistentMap.of().assoc("key", null)

        then:
        thrown(IllegalArgumentException)

        when:
        PersistentMap.of("key", null)

        then:
        thrown(IllegalArgumentException)

        when:
        PersistentMap.of().assoc("a", "b").assoc("key", null)

        then:
        thrown(IllegalArgumentException)
    }

    def 'toString == {k1:v1,k2:v2,...}'() {
        expect:
        "{}" == PersistentMap.of().toString()
        "{1:2}" == PersistentMap.of(1, 2).toString()
        "{3:4,1:2}" == PersistentMap.of(1, 2).assoc(3, 4).toString()
    }

    def 'maps with same keys but different values in collision bucket are not equal'() {
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
        map1 != map2

        and: 'Verify the maps actually have different values'
        map1.get(key2) == "value2"
        map2.get(key2) == "DIFFERENT_VALUE"
    }

    @ToBeImplemented
    def "maps with different values have different hash codes"() {
        when:
        def m1 = PersistentMap.of("a", 1)
        def m2 = PersistentMap.of("a", 2)

        then:
        m1.hashCode() != m2.hashCode()
        m1.assoc("a", 2).hashCode() == m2.hashCode()
        m1.hashCode() == m2.assoc("a", 1).hashCode()

        when:
        def m3 = m1.assoc("b", 2).assoc("c", 3)
        def m4 = m1.assoc("b", 3).assoc("c", 3)

        then:
        // Undesirable behavior (hash code collisions)
        m3.hashCode() == m4.hashCode()
        m3.assoc("b", 3).hashCode() == m4.hashCode()
        // Desirable behavior
//        m3.hashCode() != m4.hashCode()
//        m3.assoc("b", 3).hashCode() != m4.hashCode()
//        m3.hashCode() != m4.assoc("b", 2).hashCode()
//        m3.dissoc("b").hashCode() == m4.dissoc("b").hashCode()
    }
}
