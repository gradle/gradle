/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.internal.snapshot.impl

import org.gradle.api.attributes.Attribute
import org.gradle.internal.hash.ClassLoaderHierarchyHasher
import org.gradle.internal.hash.HashCode
import org.gradle.internal.hash.TestHashCodes
import org.gradle.internal.snapshot.ValueSnapshot
import org.gradle.util.TestUtil
import spock.lang.Specification

class ValueSnapshotHasherTest extends Specification {
    def classLoaderHasher = Stub(ClassLoaderHierarchyHasher) {
        getClassLoaderHash(_) >> TestHashCodes.hashCodeFrom(123)
    }
    def snapshotter = new DefaultValueSnapshotter([], classLoaderHasher)

    def "equal values have equal hashes (#value)"() {
        expect:
        snapshotter.snapshot(value) == snapshotter.snapshot(copy)
        hashOf(value) == hashOf(copy)

        where:
        value                              | copy
        "abc"                              | new String("abc")
        123                                | 123
        123 as short                       | 123 as short
        null                               | null
        [1, "a", null]                     | [1, "a", null]
        [1, "a"] as Set                    | [1, "a"] as Set
        [a: 1, b: [2]]                     | [a: 1, b: [2]]
        [1, 2] as int[]                    | [1, 2] as int[]
        [[1] as Set, "x"] as Object[]      | [[1] as Set, "x"] as Object[]
        Thread.State.RUNNABLE              | Thread.State.RUNNABLE
        new File("some/path")              | new File("some/path")
        TestHashCodes.hashCodeFrom(42)     | TestHashCodes.hashCodeFrom(42)
        Attribute.of("name", String)       | Attribute.of("name", String)
        [Float.NaN] as float[]             | [Float.intBitsToFloat(0x7fc00001)] as float[]
        [Double.NaN] as double[]           | [Double.longBitsToDouble(0x7ff8000000000001L)] as double[]
    }

    def "values with the same content but different types have different hashes (#value vs #other)"() {
        expect:
        snapshotter.snapshot(value) != snapshotter.snapshot(other)
        hashOf(value) != hashOf(other)

        where:
        value                 | other
        123                   | 123 as short
        123                   | 123L
        [123]                 | [123 as short]
        [123] as Set          | [123 as short] as Set
        [a: 123]              | [a: 123 as short]
        [[123]]               | [[123 as short]]
        "x"                   | new File("x")
        ["x"]                 | [new File("x")]
        null                  | 0
        [null]                | [0]
        [1] as int[]          | [0, 0, 0, 1] as byte[]
        [1] as Object[]       | [1]
        [1] as Set            | [1]
        false                 | 0
        "?"                   | "\uD800"
        ["?"]                 | ["\uD800"]
        new File("?")         | new File("\uD800")
        Attribute.of("?", String) | Attribute.of("\uD800", String)
        Attribute.of("a", String) | Attribute.of("a", Integer)
        [1.0f] as float[]     | [2.0f] as float[]
    }

    def "hash of a set does not depend on its iteration order"() {
        def forward = ["a", "b", "c"] as LinkedHashSet
        def backward = ["c", "b", "a"] as LinkedHashSet

        expect:
        snapshotter.snapshot(forward) == snapshotter.snapshot(backward)
        hashOf(forward) == hashOf(backward)
        hashOf([forward, 1]) == hashOf([backward, 1])
        hashOf([key: forward]) == hashOf([key: backward])
        hashOf(forward) != hashOf(["a", "b"] as LinkedHashSet)
    }

    def "hash of a map depends on the order of its entries, like equality"() {
        def forward = [a: 1, b: 2]
        def backward = [b: 2, a: 1]

        expect:
        snapshotter.snapshot(forward) != snapshotter.snapshot(backward)
        hashOf(forward) != hashOf(backward)
    }

    interface FirstBean {
        String getProp1()

        void setProp1(String value)
    }

    interface SecondBean {
        String getProp1()

        void setProp1(String value)
    }

    def "managed values of different types with the same state have different hashes"() {
        def instantiator = TestUtil.instantiatorFactory().inject()
        def first = instantiator.newInstance(FirstBean)
        first.prop1 = "a"
        def sameAsFirst = instantiator.newInstance(FirstBean)
        sameAsFirst.prop1 = "a"
        def second = instantiator.newInstance(SecondBean)
        second.prop1 = "a"

        expect:
        snapshotter.snapshot(first) instanceof ManagedValueSnapshot
        snapshotter.snapshot(first) == snapshotter.snapshot(sameAsFirst)
        hashOf(first) == hashOf(sameAsFirst)
        snapshotter.snapshot(first) != snapshotter.snapshot(second)
        hashOf(first) != hashOf(second)
    }

    def "absent and present values of a snapshot have different hashes"() {
        expect:
        first != second
        hashOf(first) != hashOf(second)

        where:
        first                                                     | second
        new ImmutableManagedValueSnapshot("Type", null)           | new ImmutableManagedValueSnapshot("Type", "\u0000\u0000")
        new JavaSerializedValueSnapshot(null, [] as byte[])        | new JavaSerializedValueSnapshot(HashCode.fromBytes([0, 0, 0, 0] as byte[]), [] as byte[])
        new GradleSerializedValueSnapshot(null, [] as byte[])      | new GradleSerializedValueSnapshot(HashCode.fromBytes([0, 0, 0, 0] as byte[]), [] as byte[])
    }

    def "fails for unknown snapshot types"() {
        when:
        ValueSnapshotHasher.hash(Stub(ValueSnapshot))

        then:
        def e = thrown(IllegalArgumentException)
        e.message.startsWith("Don't know how to hash a value snapshot of type ")
    }

    private static HashCode hashOf(ValueSnapshot snapshot) {
        ValueSnapshotHasher.hash(snapshot)
    }

    private HashCode hashOf(Object value) {
        ValueSnapshotHasher.hash(snapshotter.snapshot(value))
    }
}
