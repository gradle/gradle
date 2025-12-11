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

class PersistentSetTest extends Specification {

    def 'empty === empty'() {
        expect:
        PersistentSet.of() === PersistentSet.of()
    }

    def 'copyOf(set) === set'() {
        expect:
        PersistentSet.copyOf(set) === set

        where:
        set << [PersistentSet.of(), PersistentSet.of(1), PersistentSet.of(1, 2, 3)]
    }

    def 'empty union other === other'() {
        expect:
        empty.union(other) === other
        other.union(empty) === other

        where:
        empty = PersistentSet.of()
        other << [PersistentSet.of(), PersistentSet.of(1), PersistentSet.of(1, 2, 3)]
    }

    def 'set union is commutative'() {
        given:
        def set1 = PersistentSet.of(1, 2, 3)
        def set2 = PersistentSet.of(3, 4, 5)

        expect:
        set1.union(set2) == set2.union(set1)
    }

    def 'superset union subset === superset'() {
        expect:
        superset.union(subset) === superset
        superset.union(subset) === superset

        where:
        superset = PersistentSet.of(1, 2, 3, 4)
        subset << [
            PersistentSet.of(1),
            PersistentSet.of(1, 2),
            PersistentSet.of(1, 2, 3),
            PersistentSet.of(1, 2, 3, 4)
        ]
    }

    def 'empty intersect other === empty'() {
        expect:
        empty.intersect(other) === empty
        other.intersect(empty) === empty

        where:
        empty = PersistentSet.of()
        other << [PersistentSet.of(), PersistentSet.of(1), PersistentSet.of(1, 2, 3)]
    }

    def 'set intersect with disjoint sets returns empty'() {
        given:
        def set1 = PersistentSet.of(1, 2, 3)
        def set2 = PersistentSet.of(4, 5, 6)

        expect:
        set1.intersect(set2) === PersistentSet.of()
    }

    def 'superset intersect subset === subset'() {
        expect:
        superset.intersect(subset) === subset
        superset.intersect(subset) === subset

        where:
        superset = PersistentSet.of(1, 2, 3, 4)
        subset << [
            PersistentSet.of(1),
            PersistentSet.of(1, 2),
            PersistentSet.of(1, 2, 3),
        ]
    }

    def 'set intersect is commutative'() {
        given:
        def set1 = PersistentSet.of(1, 2, 3, 4)
        def set2 = PersistentSet.of(3, 4, 5, 6)

        expect:
        set1.intersect(set2) == set2.intersect(set1)
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

    def 'singleton(key) == singleton(key)'() {
        given:
        def set1 = PersistentSet.of(42)
        def set2 = PersistentSet.of(42)

        expect:
        set1 == set2
        set1.hashCode() == set2.hashCode()
        PersistentSet.of(37) != set1
        PersistentSet.of(37).hashCode() != set1.hashCode()
        set1.contains(42)
        !set1.contains(37)
    }

    def 'set plus existing === set'() {
        given:
        def set1 = PersistentSet.of(42)
        def set2 = set1 + 33

        expect:
        set2.contains(42)
        set2.contains(33)
        set1 === (set1 + 42)
        set2 === (set2 + 42)
        set2 === (set2 + 33)
    }

    def 'contains'() {
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
        def set1 = PersistentSet.copyOf(present)
        def set2 = PersistentSet.copyOf(present.shuffled(random))
        def set3 = PersistentSet.copyOf(present.shuffled(random))

        expect:
        present.every {
            assert set1.contains(it)
            assert set2.contains(it)
            assert set3.contains(it)
            true
        }

        and:
        absent.every {
            assert !set1.contains(it)
            assert !set2.contains(it)
            assert !set3.contains(it)
            true
        }

        and:
        set1.size() == present.size()
        set2.size() == present.size()
        set3.size() == present.size()
    }

    def 'set.of(xs) == set.of(xs)'() {
        given:
        def random = new Random(42)
        def list = random.ints(1024).toArray().toList()
        def set1 = PersistentSet.copyOf(list)
        def set2 = PersistentSet.copyOf(list.shuffled(random))
        def set3 = PersistentSet.copyOf(list.shuffled(random))

        expect:
        set1 == set2
        set1 == set3
        set2 == set1
        set2 == set3
        set3 == set1
        set3 == set2

        and:
        set1.hashCode() == set2.hashCode()
        set2.hashCode() == set3.hashCode()
    }

    def 'set.of(xs + collisions) == set.of(xs + collisions)'() {
        given:
        def random = new Random(seed)
        def list = []
        random.ints(16 * 1024).forEach {
            list << it
            list << new HashCollision(it)
            list << new HashCollision(it)
        }
        def set1 = PersistentSet.copyOf(list)
        def set2 = PersistentSet.copyOf(list.shuffled(random))
        def set3 = PersistentSet.copyOf(list.shuffled(random))

        expect:
        set1 == set2
        set1 == set3
        set2 == set1
        set2 == set3
        set3 == set1
        set3 == set2

        and:
        set1.hashCode() == set2.hashCode()
        set2.hashCode() == set3.hashCode()

        and:
        list.every { set1.contains(it) }

        where:
        seed << (1..10)
    }

    def 'iterator'() {
        given:
        def random = new Random(42)
        def keys = random.ints(collectionSize).toArray().toSet()

        when:
        Iterable<Integer> set = PersistentSet.copyOf(keys)

        then:
        set.toSet() == keys

        where:
        collectionSize << [0, 1, 1024]
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
        Iterable<Object> set = PersistentSet.copyOf(keys)

        then:
        set.toSet() == keys.toSet()

        where:
        collectionSize << [0, 1, 1024]
    }

    def 'forEach'() {
        given:
        def random = new Random(42)
        def keys = random.ints(collectionSize).toArray().toSet()
        def set = PersistentSet.copyOf(keys)

        when:
        def result = []
        set.forEach {
            result << it
        }

        then:
        result.size() == keys.size()
        result.toSet() == keys.toSet()

        where:
        collectionSize << [0, 1, 1024]
    }

    def 'set minus nonExisting === set'() {
        expect:
        set - nonExisting === set

        where:
        nonExisting = 42
        set << [PersistentSet.of(), PersistentSet.of(1), PersistentSet.of(1, 2, 3)]
    }

    def 'minus is inverse to plus'() {
        given:
        def random = new Random(42)
        def keys = []
        def set = PersistentSet.of()
        def sets = []

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
            sets << set
            set = set + it
        }

        then:
        for (int i = keys.size() - 1; i >= 0; i--) {
            set = set - keys[i]
            assert set.size() == i
            assert set == sets[i]
        }

        and:
        set.isEmpty()

        where:
        collectionSize = 1024
        withCollision << [false, true]
    }

    def 'minus can happen in any order'() {
        given:
        def random = new Random(42)
        def keys = random.ints(collectionSize).toArray().toSet().toList()
        def keysToRemove = keys.take(collectionSize >> 2)
        def set = PersistentSet.copyOf(keys)
        def expected = PersistentSet.copyOf(keys - keysToRemove)

        when:
        def set1 = set.minusAll(keysToRemove)
        def set2 = set.minusAll(keysToRemove.shuffled(random))
        def set3 = set.minusAll(keysToRemove.shuffled(random))

        then:
        def remainingSize = keys.size() - keysToRemove.size()
        set1.size() == remainingSize
        set2.size() == remainingSize
        set3.size() == remainingSize
        keysToRemove.every {
            !set1.contains(it) && !set2.contains(it) && !set3.contains(it)
        }
        expected.every {
            set1.contains(it) && set1.contains(it) && set3.contains(it)
        }

        and:
        set1 == expected
        set1 == set2
        set1 == set3

        where:
        collectionSize = 1024
    }

    def 'singleton set and set after minus left with same single element should be equal'() {
        given: 'A singleton set created directly'
        def set1 = PersistentSet.of(42)

        and: 'A set that becomes singleton after operations'
        def setViaOps = PersistentSet.of(1, 2, 42) - 1 - 2

        expect: 'should be equal regardless of internal representation'
        set1 == setViaOps
        setViaOps == set1
        set1.hashCode() == setViaOps.hashCode()
    }

    def 'minusAll but one'() {
        given:
        def random = new Random(42)
        def keys = random.ints(collectionSize).toArray().toSet().toList()
        def keysToRemove = keys.take(collectionSize - 1)
        def set = PersistentSet.copyOf(keys)
        def remaining = keys.last()
        def expected = PersistentSet.of(remaining)

        when:
        def set1 = set.minusAll(keysToRemove)

        then:
        set1 == expected
        set1 - remaining === PersistentSet.of()

        when:
        def set2 = set.minusAll(keys)

        then:
        set2 === PersistentSet.of()

        where:
        collectionSize << [2, 32]
    }

    def 'set minusAll with empty iterable returns same set'() {
        given:
        def set = PersistentSet.of(1, 2, 3)

        expect:
        set.minusAll([]) === set
    }

    def 'set minusAll absent returns same set'() {
        given:
        def set = PersistentSet.of(1, 2, 3)

        expect:
        set.minusAll([4, 5, 6]) === set
    }

    def 'minusAll stops iterating when set becomes empty'() {
        given:
        def iterations = 0
        def iterable = new Iterable() {
            @Override
            Iterator iterator() {
                return new Iterator() {
                    @Override
                    boolean hasNext() {
                        return iterations < 5
                    }

                    @Override
                    Object next() {
                        ++iterations
                        return iterations
                    }
                }
            }
        }

        when:
        def result = set.minusAll iterable

        then:
        result === PersistentSet.of()
        iterations == set.size()

        where:
        set << [PersistentSet.of(), PersistentSet.of(1), PersistentSet.of(1, 2, 3)]
    }

    def 'toString == {k1,k2,...}'() {
        expect:
        "{}" == PersistentSet.of().toString()
        "{1}" == PersistentSet.of(1).toString()
        "{3,2,1}" == PersistentSet.of(1, 2, 3).toString()
        "{3,2,1}" == PersistentSet.of(3, 2, 1).toString()
    }

    def 'set key cannot be null'() {
        when:
        PersistentSet.of() + null

        then:
        thrown(IllegalArgumentException)

        when:
        PersistentSet.of(null)

        then:
        thrown(IllegalArgumentException)

        when:
        (PersistentSet.of() + "a") + null

        then:
        thrown(IllegalArgumentException)
    }

}
