/*
 * Copyright 2021 the original author or authors.
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
package org.gradle.util.internal

import org.gradle.internal.InternalTransformer
import spock.lang.Specification

import static org.gradle.util.internal.CollectionUtils.addAll
import static org.gradle.util.internal.CollectionUtils.collect
import static org.gradle.util.internal.CollectionUtils.collectMap
import static org.gradle.util.internal.CollectionUtils.collectMapValues
import static org.gradle.util.internal.CollectionUtils.compact
import static org.gradle.util.internal.CollectionUtils.diffSetsBy
import static org.gradle.util.internal.CollectionUtils.every
import static org.gradle.util.internal.CollectionUtils.filter
import static org.gradle.util.internal.CollectionUtils.flattenCollections
import static org.gradle.util.internal.CollectionUtils.intersection
import static org.gradle.util.internal.CollectionUtils.join
import static org.gradle.util.internal.CollectionUtils.partition
import static org.gradle.util.internal.CollectionUtils.replace
import static org.gradle.util.internal.CollectionUtils.sort
import static org.gradle.util.internal.CollectionUtils.stringize
import static org.gradle.util.internal.CollectionUtils.toList
import static org.gradle.util.internal.CollectionUtils.toSet
import static org.gradle.util.internal.CollectionUtils.toStringList
import static org.gradle.util.internal.CollectionUtils.unpack

class CollectionUtilsTest extends Specification {

    def "list filtering"() {
        given:
        def spec = { it < 5 }
        def filter = { Integer[] nums -> filter(nums as List, spec) }

        expect:
        filter(1, 2, 3) == [1, 2, 3]
        filter(7, 8, 9) == []
        filter() == []
        filter(4, 5, 6) == [4]
    }

    def "array filtering"() {
        given:
        def spec = { it < 5 }
        def filter = { Integer[] nums -> filter(nums, spec) }

        expect:
        filter(1, 2, 3) == [1, 2, 3]
        filter(7, 8, 9) == []
        filter() == []
        filter(4, 5, 6) == [4]
    }

    def "list collecting"() {
        def transformer = new InternalTransformer() {
            def transform(i) { i * 2 }
        }
        def collect = { Integer[] nums -> collect(nums as List, transformer) }

        expect:
        collect(1, 2, 3) == [2, 4, 6]
        collect() == []
    }

    def "set filtering"() {
        given:
        def spec = { it < 5 }
        def filter = { Integer[] nums -> filter(nums as Set, spec) }

        expect:
        filter(1, 2, 3) == [1, 2, 3] as Set
        filter(7, 8, 9).empty
        filter().empty
        filter(4, 5, 6) == [4] as Set
    }

    def "map filtering"() {
        expect:
        def filtered = filter(a: 1, b: 2, c: 3, { it.value < 2 })
        filtered.size() == 1
        filtered.a == 1
    }

    def toStringList() {
        def list = [42, "string"]

        expect:
        toStringList([]) == []
        toStringList(list) == ["42", "string"]
    }

    def "list compacting"() {
        expect:
        compact([1, null, 2]) == [1, 2]
        compact([null, 1, 2]) == [1, 2]
        compact([1, 2, null]) == [1, 2]

        def l = [1, 2, 3]
        compact(l).is l

    }

    def "collect array"() {
        expect:
        collect([1, 2, 3] as Object[], transformer { it * 2 }) == [2, 4, 6]
    }

    def "collect non-collection Iterable"() {
        expect:
        def nonCollectionIterable = [iterator: { [1, 2, 3].iterator() }] as Iterable
        collect(nonCollectionIterable, transformer { it * 2 }) == [2, 4, 6]
    }

    def "collect of collection iterable"() {
        collect([1, 2, 3] as Iterable, transformer { it * 2 }) == [2, 4, 6]
    }

    def "list stringize"() {
        expect:
        stringize([1, 2, 3]) == ["1", "2", "3"]
        stringize([]) == []
    }

    def "stringize"() {
        expect:
        stringize(["c", "b", "a"], new TreeSet<String>()) == ["a", "b", "c"] as Set
    }

    def "replacing"() {
        given:
        def l = [1, 2, 3]

        expect:
        replace l, { it == 2 }, transformer { 2 * 2 }
        l == [1, 4, 3]

        replace l, { it > 1 }, transformer { 0 }
        l == [1, 0, 0]

        !replace(l, { false }, transformer { it })
    }

    def "diffing sets"() {
        given:
        def leftSet = left as Set
        def rightSet = right as Set
        def leftOnlySet = leftOnly as Set
        def rightOnlySet = rightOnly as Set

        when:
        def diff = diffSetsBy(leftSet, rightSet, transformer { it + 10 })

        then:
        diff.leftOnly == leftOnlySet
        diff.common.size() == common.size()
        if (common) {
            common.each { inCommon ->
                diff.common.find { it.left == inCommon && it.right == inCommon }
            }
        }
        diff.rightOnly == rightOnlySet

        where:
        left      | right     | leftOnly  | rightOnly | common
        [1, 2, 3] | [2, 3, 4] | [1]       | [4]       | [2, 3]
        []        | []        | []        | []        | []
        [1, 2, 3] | []        | [1, 2, 3] | []        | []
        []        | [1, 2, 3] | []        | [1, 2, 3] | []
        [1, 2, 3] | [1, 2, 3] | []        | []        | [1, 2, 3]
    }

    def "collect as map"() {
        expect:
        collectMap([1, 2, 3], transformer { it * 10 }) == [10: 1, 20: 2, 30: 3]
        collectMap([], transformer { it * 10 }) == [:]
    }

    def "collect values as map"() {
        expect:
        collectMapValues([1, 2, 3], transformer { it * 10 }) == [1: 10, 2: 20, 3: 30]
        collectMapValues([], transformer { it * 10 }) == [:]
    }

    def "every"() {
        expect:
        every([1, 2, 3], { it < 4 })
        !every([1, 2, 4], { it < 4 })
        !every([1], { it instanceof String })
        every([], { false })
    }

    def "intersection"() {
        expect:
        intersection([collA, collB]) == collC
        where:
        collA           | collB           | collC
        []              | ["a", "b", "c"] | []
        ['a', 'b', 'c'] | ["a", "b", "c"] | ['a', 'b', 'c']
        ['a', 'b', 'c'] | ["b", "c"]      | ['b', 'c']
    }

    def "flattenToList"() {
        given:
        def integers = [1, 2, 3]

        expect:
        flattenCollections([1, 2, 3] as Set) == [1, 2, 3]
        flattenCollections("asdfa") == ["asdfa"]
        flattenCollections(null) == [null]
        flattenCollections([null, [null, null]]) == [null, null, null]
        flattenCollections(integers) == integers
        flattenCollections([1, 2, 3] as Set) == [1, 2, 3]
        flattenCollections([] as Set) == []

        when:
        flattenCollections(Map, "foo")

        then:
        thrown(ClassCastException)

        when:
        flattenCollections(Map, [[a: 1], "foo"])

        then:
        thrown(ClassCastException)

        and:
        flattenCollections(Number, 1, [2, 3]) == [1, 2, 3]
    }

    def "joining"() {
        expect:
        join(",", [1, 2, 3]) == "1,2,3"
        join(",", [1]) == "1"
        join(",", []) == ""

        and:
        join(",", [1, 2, 3].toArray()) == "1,2,3"
        join(",", [1].toArray()) == "1"
        join(",", [].toArray()) == ""
    }

    def "joining with nulls"() {
        when:
        join(separator, objects)

        then:
        def e = thrown(NullPointerException)
        e.message == "The '$param' cannot be null"

        where:
        separator | objects            | param
        null      | [] as Object[]     | "separator"
        ""        | null as Object[]   | "objects"
        null      | []                 | "separator"
        ""        | null as Collection | "objects"
        null      | null as Collection | "separator"
        null      | null as Object[]   | "separator"
    }

    def "partitioning"() {
        when:
        def pair = partition([1, 2, 3], { it % 2 == 0 })

        then:
        pair.left == [2]
        pair.right == [1, 3]
    }

    def "partitioning empty collection"() {
        when:
        def pair = partition([], { it })

        then:
        pair.left == []
        pair.right == []
    }

    def "partitioning throws exception given nulls"() {
        when:
        partition(null, { it })

        then:
        thrown(NullPointerException)

        when:
        partition([], null)

        then:
        thrown(NullPointerException)
    }

    def "addAll from iterable"() {
        expect:
        addAll([], [1, 2, 3] as Iterable) == [1, 2, 3]
        addAll([] as Set, [1, 2, 3, 1] as Iterable) == [1, 2, 3] as Set
    }

    def "addAll from array"() {
        expect:
        addAll([], 1, 2, 3) == [1, 2, 3]
        addAll([] as Set, 1, 2, 3, 1) == [1, 2, 3] as Set
    }

    def "to set"() {
        expect:
        toSet([1, 2, 3]) == [1, 2, 3] as Set
        toSet([1, 2, 2, 3]) == [1, 2, 3] as Set
        def set = [1] as Set
        toSet(set).is(set)
        toSet([]).empty
    }

    def "to list"() {
        expect:
        toList([1, 2, 3] as Set) == [1, 2, 3]
        toList([]).empty
        toList(([1, 2, 3] as Vector).elements()) == [1, 2, 3]
    }

    def "sorting with comparator"() {
        given:
        def naturalComparator = { a, b -> a <=> b } as Comparator

        expect:
        def l = [1, 2, 3]
        !sort(l, naturalComparator).is(l)

        and:
        sort([2, 1, 3], naturalComparator) == [1, 2, 3]
        sort([2, 1, 3] as Set, naturalComparator) == [1, 2, 3]
        sort([], naturalComparator) == []
        sort([] as Set, naturalComparator) == []
    }

    def "sorting"() {
        expect:
        def l = [1, 2, 3]
        !sort(l).is(l)

        and:
        sort([2, 1, 3]) == [1, 2, 3]
        sort([2, 1, 3] as Set) == [1, 2, 3]
        sort([]) == []
        sort([] as Set) == []
    }

    def unpack() {
        expect:
        unpack([{ 1 } as org.gradle.internal.Factory, { 2 } as org.gradle.internal.Factory, { 3 } as org.gradle.internal.Factory]).toList() == [1, 2, 3]
        unpack([]).toList().isEmpty()
    }

    InternalTransformer<?, ?> transformer(Closure c) {
        c as InternalTransformer
    }
}
