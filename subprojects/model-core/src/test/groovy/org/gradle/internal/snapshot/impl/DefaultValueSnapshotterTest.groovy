/*
 * Copyright 2017 the original author or authors.
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

import org.gradle.api.Named
import org.gradle.api.internal.provider.Providers
import org.gradle.internal.hash.ClassLoaderHierarchyHasher
import org.gradle.internal.hash.TestHashCodes
import org.gradle.internal.serialize.Decoder
import org.gradle.internal.serialize.DefaultSerializerRegistry
import org.gradle.internal.serialize.Encoder
import org.gradle.internal.serialize.Serializer
import org.gradle.internal.snapshot.ValueSnapshot
import org.gradle.internal.state.ManagedFactoryRegistry
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.TestUtil
import spock.lang.Specification

class DefaultValueSnapshotterTest extends Specification {
    def classLoaderHasher = Stub(ClassLoaderHierarchyHasher) {
        getClassLoaderHash(_) >> TestHashCodes.hashCodeFrom(123)
    }
    def managedFactoryRegistry = Mock(ManagedFactoryRegistry)
    def valueSnapshotSerializerRegistry = new TestValueSnapshotSerializerRegistry().tap {
        register(GradleBean, new GradleBeanSerializer())
    }
    def snapshotter = new DefaultValueSnapshotter([valueSnapshotSerializerRegistry], classLoaderHasher)
    def isolatableFactory = new DefaultIsolatableFactory(classLoaderHasher, managedFactoryRegistry)

    def "creates snapshot for string"() {
        expect:
        def snapshot = snapshotter.snapshot("abc")
        snapshot instanceof StringValueSnapshot
        snapshot == snapshotter.snapshot("abc")
        snapshot != snapshotter.snapshot("other")
    }

    def "creates snapshot for isolated string"() {
        expect:
        def isolated = isolatableFactory.isolate("abc")
        snapshotter.snapshot("abc") == snapshotter.snapshot(isolated)
        snapshotter.snapshot("other") != snapshotter.snapshot(isolated)
    }

    def "creates snapshot for integer"() {
        expect:
        def snapshot = snapshotter.snapshot(123)
        snapshot instanceof IntegerValueSnapshot
        snapshot == snapshotter.snapshot(123)
        snapshot != snapshotter.snapshot(-1)
        snapshot != snapshotter.snapshot(123L)
        snapshot != snapshotter.snapshot(123 as short)
        snapshot != snapshotter.snapshot(123 as BigDecimal)
    }

    def "creates snapshot for isolated integer"() {
        expect:
        def isolated = isolatableFactory.isolate(123)
        snapshotter.snapshot(123) == snapshotter.snapshot(isolated)
        snapshotter.snapshot(456) != snapshotter.snapshot(isolated)
    }

    def "creates snapshot for long"() {
        expect:
        def snapshot = snapshotter.snapshot(123L)
        snapshot instanceof LongValueSnapshot
        snapshot == snapshotter.snapshot(123L)
        snapshot != snapshotter.snapshot(-1L)
        snapshot != snapshotter.snapshot(123)
        snapshot != snapshotter.snapshot(123 as short)
        snapshot != snapshotter.snapshot(123 as BigDecimal)
    }

    def "creates snapshot for isolated long"() {
        expect:
        def isolated = isolatableFactory.isolate(123L)
        snapshotter.snapshot(123L) == snapshotter.snapshot(isolated)
        snapshotter.snapshot(456L) != snapshotter.snapshot(isolated)
    }

    def "creates snapshot for short"() {
        expect:
        def snapshot = snapshotter.snapshot(123 as short)
        snapshot instanceof ShortValueSnapshot
        snapshot == snapshotter.snapshot(123 as short)
        snapshot != snapshotter.snapshot(-1L)
        snapshot != snapshotter.snapshot(123)
        snapshot != snapshotter.snapshot(123L)
        snapshot != snapshotter.snapshot(123 as BigDecimal)
    }

    def "creates snapshot for isolated short"() {
        expect:
        def isolated = isolatableFactory.isolate(123 as short)
        snapshotter.snapshot(123 as short) == snapshotter.snapshot(isolated)
        snapshotter.snapshot(456 as short) != snapshotter.snapshot(isolated)
    }

    def "creates snapshot for boolean"() {
        expect:
        snapshotter.snapshot(true).is BooleanValueSnapshot.TRUE
        snapshotter.snapshot(false).is BooleanValueSnapshot.FALSE
    }

    def "creates snapshot for isolated boolean"() {
        expect:
        def isolated = isolatableFactory.isolate(true).isolate()
        snapshotter.snapshot(true) == snapshotter.snapshot(isolated)
        snapshotter.snapshot(false) != snapshotter.snapshot(isolated)
    }

    def "creates snapshot for null value"() {
        expect:
        snapshotter.snapshot(null).is NullValueSnapshot.INSTANCE
    }

    def "creates snapshot for isolated null value"() {
        expect:
        def isolated = isolatableFactory.isolate(null)
        snapshotter.snapshot(null) == snapshotter.snapshot(isolated)
    }

    def "creates snapshot for array"() {
        expect:
        def snapshot1 = snapshotter.snapshot([] as String[])
        snapshot1 instanceof ArrayValueSnapshot
        snapshot1 == snapshotter.snapshot([] as String[])
        snapshot1 == snapshotter.snapshot([] as Object[])
        snapshot1 == snapshotter.snapshot([] as Integer[])
        snapshot1 != snapshotter.snapshot("abc" as String[])
        snapshot1 != snapshotter.snapshot([])

        def snapshot2 = snapshotter.snapshot(["123"] as String[])
        snapshot2 instanceof ArrayValueSnapshot
        snapshot2 == snapshotter.snapshot(["123"] as String[])
        snapshot2 == snapshotter.snapshot(["123"] as CharSequence[])
        snapshot2 == snapshotter.snapshot(["123"] as Object[])
        snapshot2 != snapshotter.snapshot(["123"])
        snapshot2 != snapshotter.snapshot("123")
        snapshot2 != snapshot1
    }

    def "creates snapshot for isolated array"() {
        expect:
        def isolated1 = isolatableFactory.isolate([] as String[])
        snapshotter.snapshot([] as String[]) == snapshotter.snapshot(isolated1)

        def isolated2 = isolatableFactory.isolate(["123"] as String[])
        snapshotter.snapshot(["123"] as String[]) == snapshotter.snapshot(isolated2)
        snapshotter.snapshot(["1", "2"] as String[]) != snapshotter.snapshot(isolated2)
    }

    def "creates snapshot for list"() {
        expect:
        def snapshot1 = snapshotter.snapshot([])
        snapshot1 instanceof ListValueSnapshot
        snapshot1 == snapshotter.snapshot([])
        snapshot1 != snapshotter.snapshot("abc")

        def snapshot2 = snapshotter.snapshot(["123"])
        snapshot2 instanceof ListValueSnapshot
        snapshot2 == snapshotter.snapshot(["123"])
        snapshot2 != snapshot1
    }


    def "creates snapshot for isolated list"() {
        expect:
        def isolated1 = isolatableFactory.isolate([])
        snapshotter.snapshot([]) == snapshotter.snapshot(isolated1)

        def isolated2 = isolatableFactory.isolate(["123"])
        snapshotter.snapshot(["123"]) == snapshotter.snapshot(isolated2)
        snapshotter.snapshot(["1", "2"]) != snapshotter.snapshot(isolated2)
    }

    def "creates snapshot for list from empty list"() {
        def snapshot1 = snapshotter.snapshot([])
        def snapshot2 = snapshotter.snapshot(["123"], snapshot1)

        expect:
        snapshot2 instanceof ListValueSnapshot
        snapshot2 == snapshotter.snapshot(["123"])
        snapshot2 != snapshot1
    }

    def "creates snapshot for set"() {
        expect:
        def snapshot1 = snapshotter.snapshot([] as Set)
        snapshot1 instanceof SetValueSnapshot
        snapshot1 == snapshotter.snapshot([] as Set)
        snapshot1 != snapshotter.snapshot("abc")
        snapshot1 != snapshotter.snapshot([])

        def snapshot2 = snapshotter.snapshot(["123"] as Set)
        snapshot2 instanceof SetValueSnapshot
        snapshot2 == snapshotter.snapshot(["123"] as Set)
        snapshot2 != snapshotter.snapshot(["123"])
        snapshot2 != snapshot1
    }

    def "creates snapshot for isolated set"() {
        expect:
        def isolated1 = isolatableFactory.isolate([] as Set)
        snapshotter.snapshot([] as Set) == snapshotter.snapshot(isolated1)

        def isolated2 = isolatableFactory.isolate(["123"] as Set)
        snapshotter.snapshot(["123"] as Set) == snapshotter.snapshot(isolated2)
        snapshotter.snapshot(["1", "2"] as Set) != snapshotter.snapshot(isolated2)
    }

    def "creates snapshot for map"() {
        expect:
        def snapshot1 = snapshotter.snapshot([:])
        snapshot1 instanceof MapValueSnapshot
        snapshot1 == snapshotter.snapshot([:])
        snapshot1 != snapshotter.snapshot("abc")
        snapshot1 != snapshotter.snapshot([a: "123"])

        def snapshot2 = snapshotter.snapshot([a: "123"])
        snapshot2 instanceof MapValueSnapshot
        snapshot2 == snapshotter.snapshot([a: "123"])
        snapshot2 != snapshotter.snapshot(["123"])
        snapshot2 != snapshotter.snapshot([:])
        snapshot2 != snapshotter.snapshot([a: "123", b: "abc"])
        snapshot2 != snapshot1
    }

    def "creates snapshot for isolated map"() {
        expect:
        def isolated1 = isolatableFactory.isolate([:])
        snapshotter.snapshot([:]) == snapshotter.snapshot(isolated1)

        def isolated2 = isolatableFactory.isolate([a: "123"])
        snapshotter.snapshot([a: "123"]) == snapshotter.snapshot(isolated2)
        snapshotter.snapshot([a: "1", b: "2"]) != snapshotter.snapshot(isolated2)
    }

    Properties properties(Map<String, String> entries) {
        def properties = new Properties()
        entries.each { key, value -> properties.setProperty(key, value) }
        return properties
    }

    def "creates snapshot for properties"() {
        expect:
        def snapshot1 = snapshotter.snapshot(properties([:]))
        snapshot1 instanceof MapValueSnapshot
        snapshot1 == snapshotter.snapshot(properties([:]))
        snapshot1 != snapshotter.snapshot("abc")
        snapshot1 != snapshotter.snapshot(properties(["a": "123"]))

        def snapshot2 = snapshotter.snapshot(properties(["a": "123"]))
        snapshot2 instanceof MapValueSnapshot
        snapshot2 == snapshotter.snapshot(properties([a: "123"]))
        snapshot2 != snapshotter.snapshot(["123"])
        snapshot2 != snapshotter.snapshot(properties([:]))
        snapshot2 != snapshotter.snapshot(properties([a: "123", b: "abc"]))
        snapshot2 != snapshot1
    }

    def "creates snapshot for isolated properties"() {
        expect:
        def isolated1 = isolatableFactory.isolate(properties([:]))
        snapshotter.snapshot(properties([:])) == snapshotter.snapshot(isolated1)

        def isolated2 = isolatableFactory.isolate(properties([a: "123"]))
        snapshotter.snapshot(properties([a: "123"])) == snapshotter.snapshot(isolated2)
        snapshotter.snapshot(properties([a: "1", b: "2"])) != snapshotter.snapshot(isolated2)
    }

    enum Type2 {
        TWO, THREE
    }

    def "creates snapshot for enum type"() {
        expect:
        def snapshot = snapshotter.snapshot(Type1.TWO)
        snapshot instanceof EnumValueSnapshot
        snapshot == snapshotter.snapshot(Type1.TWO)
        snapshot != snapshotter.snapshot(Type1.ONE)
        snapshot != snapshotter.snapshot(Type2.TWO)
        snapshot != snapshotter.snapshot(new Bean(prop: "value2"))
    }

    def "creates snapshot for isolated enum value"() {
        expect:
        def isolated = isolatableFactory.isolate(Type1.TWO)
        snapshotter.snapshot(Type1.TWO) == snapshotter.snapshot(isolated)
        snapshotter.snapshot(Type1.ONE) != snapshotter.snapshot(isolated)
    }

    def "creates snapshot for file"() {
        expect:
        def snapshot = snapshotter.snapshot(new File("abc"))
        snapshot instanceof FileValueSnapshot
        snapshot == snapshotter.snapshot(new File("abc"))
        snapshot != snapshotter.snapshot(new File("abc").getAbsoluteFile())
        snapshot != snapshotter.snapshot(new File("123"))
        snapshot != snapshotter.snapshot(new Bean(prop: "value2"))

        // Not subclasses of `File`
        snapshotter.snapshot(new TestFile("abc")) != snapshot
    }

    def "creates snapshot for isolated file"() {
        expect:
        def isolated = isolatableFactory.isolate(new File("abc"))
        snapshotter.snapshot(new File("abc")) == snapshotter.snapshot(isolated)
        snapshotter.snapshot(new File("abc").absoluteFile) != snapshotter.snapshot(isolated)
    }

    def "creates snapshot for provider type"() {
        def value = Providers.of("123")
        def value2 = Providers.of("123")
        def value3 = Providers.of("12")

        expect:
        def snapshot = snapshotter.snapshot(value)
        snapshot == snapshotter.snapshot(value)
        snapshot == snapshotter.snapshot(value2)
        snapshot != snapshotter.snapshot(value3)
        snapshot != snapshotter.snapshot("123")
    }

    def "creates snapshot for named managed type"() {
        def instantiator = TestUtil.objectInstantiator()
        def value = instantiator.named(Thing, "value1")
        def value1 = instantiator.named(Thing, "value1")
        def value2 = instantiator.named(Thing, "value2")
        def value3 = instantiator.named(Named, "value1")

        expect:
        def snapshot = snapshotter.snapshot(value)
        snapshot instanceof ImmutableManagedValueSnapshot
        snapshot == snapshotter.snapshot(value)
        snapshot == snapshotter.snapshot(value1)
        snapshot != snapshotter.snapshot(value2)
        snapshot != snapshotter.snapshot(value3)
    }

    def "creates snapshot for isolated named managed type"() {
        def instantiator = TestUtil.objectInstantiator()
        def value = instantiator.named(Thing, "value1")
        def other = instantiator.named(Thing, "other")

        expect:
        def isolated = isolatableFactory.isolate(value)
        snapshotter.snapshot(value) == snapshotter.snapshot(isolated)
        snapshotter.snapshot(other) != snapshotter.snapshot(isolated)
    }

    interface BeanInterface {
        String getProp1()

        void setProp1(String value)
    }

    def "creates snapshot for managed interface"() {
        def instantiator = TestUtil.instantiatorFactory().inject()
        def value = instantiator.newInstance(BeanInterface)
        value.prop1 = "a"
        def value1 = instantiator.newInstance(BeanInterface)
        value1.prop1 = "a"
        def value2 = instantiator.newInstance(BeanInterface)
        value2.prop1 = "b"
        def value3 = instantiator.newInstance(AbstractBean)
        value3.prop1 = "a"

        expect:
        def snapshot = snapshotter.snapshot(value)
        snapshot instanceof ManagedValueSnapshot
        snapshot == snapshotter.snapshot(value)
        snapshot == snapshotter.snapshot(value1)
        snapshot != snapshotter.snapshot(value2)
        snapshot != snapshotter.snapshot(value3)
    }

    def "creates snapshot for managed abstract class"() {
        def instantiator = TestUtil.instantiatorFactory().inject()
        def value = instantiator.newInstance(AbstractBean)
        value.prop1 = "a"
        def value1 = instantiator.newInstance(AbstractBean)
        value1.prop1 = "a"
        def value2 = instantiator.newInstance(AbstractBean)
        value2.prop1 = "b"
        def value3 = instantiator.newInstance(BeanInterface)
        value3.prop1 = "a"

        expect:
        def snapshot = snapshotter.snapshot(value)
        snapshot instanceof ManagedValueSnapshot
        snapshot == snapshotter.snapshot(value)
        snapshot == snapshotter.snapshot(value1)
        snapshot != snapshotter.snapshot(value2)
        snapshot != snapshotter.snapshot(value3)
    }

    def "creates snapshot for serializable type"() {
        def value = new Bean()

        expect:
        def snapshot = snapshotter.snapshot(value)
        snapshot instanceof JavaSerializedValueSnapshot
        snapshot == snapshotter.snapshot(value)
        snapshot == snapshotter.snapshot(new Bean())
        snapshot != snapshotter.snapshot(new Bean(prop: "value2"))
    }

    def "creates snapshot for isolated serializable type"() {
        def value = new Bean()

        expect:
        def isolated = isolatableFactory.isolate(value)
        snapshotter.snapshot(value) == snapshotter.snapshot(isolated)
        snapshotter.snapshot(new Bean(prop: "123")) != snapshotter.snapshot(isolated)
    }

    def "creates snapshot for string from candidate"() {
        expect:
        def snapshot = snapshotter.snapshot("abc")
        areTheSame(snapshot, "abc")

        areNotTheSame(snapshot, "other")
        areNotTheSame(snapshot, 123L)
        areNotTheSame(snapshot, null)
        areNotTheSame(snapshot, new Bean())
    }

    def "creates snapshot for integer from candidate"() {
        expect:
        def snapshot = snapshotter.snapshot(123)
        areTheSame(snapshot, 123)

        areNotTheSame(snapshot, -12)
        areNotTheSame(snapshot, 123L)
        areNotTheSame(snapshot, 123 as short)
        areNotTheSame(snapshot, null)
        areNotTheSame(snapshot, new Bean())
    }

    def "creates snapshot for long from candidate"() {
        expect:
        def snapshot = snapshotter.snapshot(123L)
        areTheSame(snapshot, 123L)

        areNotTheSame(snapshot, -12L)
        areNotTheSame(snapshot, 123)
        areNotTheSame(snapshot, 123 as short)
        areNotTheSame(snapshot, null)
        areNotTheSame(snapshot, new Bean())
    }

    def "creates snapshot for short from candidate"() {
        expect:
        def snapshot = snapshotter.snapshot(123 as short)
        areTheSame(snapshot, 123 as short)

        areNotTheSame(snapshot, -12 as short)
        areNotTheSame(snapshot, 123)
        areNotTheSame(snapshot, 123L)
        areNotTheSame(snapshot, null)
        areNotTheSame(snapshot, new Bean())
    }

    def "creates snapshot for file from candidate"() {
        expect:
        def snapshot = snapshotter.snapshot(new File("abc"))
        areTheSame(snapshot, new File("abc"))

        areNotTheSame(snapshot, new File("other"))
        areNotTheSame(snapshot, new TestFile("abc"))
        areNotTheSame(snapshot, "abc")
        areNotTheSame(snapshot, 123)
        areNotTheSame(snapshot, null)
        areNotTheSame(snapshot, new Bean())
    }

    def "creates snapshot for enum from candidate"() {
        expect:
        def snapshot = snapshotter.snapshot(Type1.TWO)
        snapshotter.snapshot(Type1.TWO, snapshot).is(snapshot)

        snapshotter.snapshot(Type1.ONE, snapshot) != snapshot
        snapshotter.snapshot(Type1.ONE, snapshot) == snapshotter.snapshot(Type1.ONE)

        snapshotter.snapshot(Type2.TWO, snapshot) != snapshot
        snapshotter.snapshot(Type2.TWO, snapshot) == snapshotter.snapshot(Type2.TWO)

        snapshotter.snapshot(new Bean(), snapshot) != snapshot
        snapshotter.snapshot(new Bean(), snapshot) == snapshotter.snapshot(new Bean())
    }

    def "creates snapshot for null from candidate"() {
        expect:
        def snapshot = snapshotter.snapshot(null)
        snapshotter.snapshot(null, snapshot).is(snapshot)

        snapshotter.snapshot("other", snapshot) != snapshot
        snapshotter.snapshot("other", snapshot) == snapshotter.snapshot("other")

        snapshotter.snapshot(new Bean(), snapshot) != snapshot
        snapshotter.snapshot(new Bean(), snapshot) == snapshotter.snapshot(new Bean())
    }

    def "creates snapshot for boolean from candidate"() {
        expect:
        def snapshot = snapshotter.snapshot(true)
        snapshotter.snapshot(true, snapshot).is(snapshot)

        snapshotter.snapshot("other", snapshot) != snapshot
        snapshotter.snapshot("other", snapshot) == snapshotter.snapshot("other")

        snapshotter.snapshot(new Bean(), snapshot) != snapshot
        snapshotter.snapshot(new Bean(), snapshot) == snapshotter.snapshot(new Bean())
    }

    def "creates snapshot for array from candidate"() {
        expect:
        def snapshot1 = snapshotter.snapshot([] as Object[])
        snapshotter.snapshot([] as Object[], snapshot1).is(snapshot1)

        snapshotter.snapshot(["123"] as Object[], snapshot1) != snapshot1
        snapshotter.snapshot("other", snapshot1) != snapshot1
        snapshotter.snapshot(new Bean(), snapshot1) != snapshot1

        def snapshot2 = snapshotter.snapshot(["123"] as Object[])
        snapshotter.snapshot(["123"] as Object[], snapshot2).is(snapshot2)

        snapshotter.snapshot(["456"] as Object[], snapshot2) != snapshot2
        snapshotter.snapshot([] as Object[], snapshot2) != snapshot2
        snapshotter.snapshot(["123", "456"] as Object[], snapshot2) != snapshot2
    }

    def "creates snapshot for list from candidate"() {
        expect:
        def snapshot1 = snapshotter.snapshot([])
        snapshotter.snapshot([], snapshot1).is(snapshot1)

        snapshotter.snapshot(["123"], snapshot1) != snapshot1
        snapshotter.snapshot("other", snapshot1) != snapshot1
        snapshotter.snapshot(new Bean(), snapshot1) != snapshot1

        def snapshot2 = snapshotter.snapshot(["123"])
        snapshotter.snapshot(["123"], snapshot2).is(snapshot2)

        snapshotter.snapshot(["456"], snapshot2) != snapshot2
        snapshotter.snapshot([], snapshot2) != snapshot2
        snapshotter.snapshot(["123", "456"], snapshot2) != snapshot2

        def snapshot3 = snapshotter.snapshot([new Bean(prop: "value")])
        snapshotter.snapshot([new Bean(prop: "value")], snapshot3).is(snapshot3)

        snapshotter.snapshot([new Bean(prop: "value 2")], snapshot3) != snapshot3
        snapshotter.snapshot([], snapshot3) != snapshot3
        snapshotter.snapshot([new Bean(prop: "value"), new Bean(prop: "value")], snapshot3) != snapshot3

        def snapshot4 = snapshotter.snapshot([new Bean(prop: "value1"), new Bean(prop: "value2")])
        snapshotter.snapshot([new Bean(prop: "value1"), new Bean(prop: "value2")], snapshot4).is(snapshot4)
        snapshotter.snapshot([new Bean(prop: "value1"), new Bean(prop: "value3")], snapshot4) != snapshot4

        def snapshot5 = snapshotter.snapshot(["abc", "123"])
        def snapshot6 = snapshotter.snapshot(["abc", "123", "xyz"], snapshot5)
        snapshotter.snapshot(["abc", "123", "xyz"], snapshot6).is(snapshot6)
    }

    def "creates snapshot for set from candidates"() {
        expect:
        def snapshot1 = snapshotter.snapshot([] as Set)
        snapshotter.snapshot([] as Set, snapshot1).is(snapshot1)

        snapshotter.snapshot(["123"] as Set, snapshot1) != snapshot1
        snapshotter.snapshot("other", snapshot1) != snapshot1
        snapshotter.snapshot(new Bean(), snapshot1) != snapshot1

        def snapshot2 = snapshotter.snapshot(["123"] as Set)
        snapshotter.snapshot(["123"] as Set, snapshot2).is(snapshot2)

        snapshotter.snapshot(["123"], snapshot2) != snapshot2
        snapshotter.snapshot(["456"] as Set, snapshot2) != snapshot2
        snapshotter.snapshot([] as Set, snapshot2) != snapshot2
        snapshotter.snapshot(["123", "456"] as Set, snapshot2) != snapshot2

        def snapshot3 = snapshotter.snapshot([new Bean(prop: "value")] as Set)
        snapshotter.snapshot([new Bean(prop: "value")] as Set, snapshot3).is(snapshot3)

        snapshotter.snapshot([new Bean(prop: "value 2")] as Set, snapshot3) != snapshot3
        snapshotter.snapshot([] as Set, snapshot3) != snapshot3
        snapshotter.snapshot([new Bean(prop: "value 2"), new Bean(prop: "value")] as Set, snapshot3) != snapshot3
    }

    def "creates snapshot for map from candidate"() {
        def map1 = [:]
        map1.put(new Bean(prop: "value"), new Bean(prop: "value"))
        def map2 = [:]
        map2.put(new Bean(prop: "value"), new Bean(prop: "value2"))
        def map3 = [:]
        map3.putAll(map1)
        map3.put(new Bean(prop: "value2"), new Bean(prop: "value2"))

        expect:
        def snapshot1 = snapshotter.snapshot([:])
        snapshotter.snapshot([:], snapshot1).is(snapshot1)

        snapshotter.snapshot([12: "123"], snapshot1) != snapshot1

        snapshotter.snapshot("other", snapshot1) != snapshot1
        snapshotter.snapshot("other", snapshot1) == snapshotter.snapshot("other")
        snapshotter.snapshot(new Bean(), snapshot1) != snapshot1
        snapshotter.snapshot(new Bean(), snapshot1) == snapshotter.snapshot(new Bean())

        def snapshot2 = snapshotter.snapshot([12: "123"])
        snapshotter.snapshot([12: "123"], snapshot2).is(snapshot2)

        snapshotter.snapshot([12: "456"], snapshot2) != snapshot2
        snapshotter.snapshot([:], snapshot2) != snapshot2
        snapshotter.snapshot([123: "123"], snapshot2) != snapshot2
        snapshotter.snapshot([12: "123", 10: "123"], snapshot2) != snapshot2

        def snapshot3 = snapshotter.snapshot([a: new Bean(prop: "value")])
        snapshotter.snapshot([a: new Bean(prop: "value")], snapshot3).is(snapshot3)

        snapshotter.snapshot([a: new Bean(prop: "value 2")], snapshot3) != snapshot3
        snapshotter.snapshot([:], snapshot3) != snapshot3
        snapshotter.snapshot(map1, snapshot3) != snapshot3

        def snapshot4 = snapshotter.snapshot(map1)
        snapshotter.snapshot(map1, snapshot4).is(snapshot4)

        snapshotter.snapshot(map2, snapshot4) != snapshot4
        snapshotter.snapshot(map2, snapshot4) == snapshotter.snapshot(map2)
        snapshotter.snapshot(map3, snapshot4) != snapshot4
        snapshotter.snapshot(map3, snapshot4) == snapshotter.snapshot(map3)
    }

    def "creates snapshot for properties from candidate"() {
        expect:
        def snapshot1 = snapshotter.snapshot(properties([:]))
        snapshotter.snapshot(properties([:]), snapshot1).is(snapshot1)

        snapshotter.snapshot(properties(["12": "123"]), snapshot1) != snapshot1

        snapshotter.snapshot("other", snapshot1) != snapshot1
        snapshotter.snapshot("other", snapshot1) == snapshotter.snapshot("other")
        snapshotter.snapshot(new Bean(), snapshot1) != snapshot1
        snapshotter.snapshot(new Bean(), snapshot1) == snapshotter.snapshot(new Bean())

        def snapshot2 = snapshotter.snapshot(properties(["12": "123"]))
        snapshotter.snapshot(properties(["12": "123"]), snapshot2).is(snapshot2)

        snapshotter.snapshot(properties(["12": "456"]), snapshot2) != snapshot2
        snapshotter.snapshot(properties([:]), snapshot2) != snapshot2
        snapshotter.snapshot(properties(["123": "123"]), snapshot2) != snapshot2
        snapshotter.snapshot(properties(["12": "123", "10": "123"]), snapshot2) != snapshot2
    }

    def "creates snapshot for provider type from candidate"() {
        def value = Providers.of("123")
        def value2 = Providers.of("123")
        def value3 = Providers.of("12")

        expect:
        def snapshot = snapshotter.snapshot(value)
        areTheSame(snapshot, value2)
        areNotTheSame(snapshot, value3)
        areNotTheSame(snapshot, "123")
    }

    def "creates snapshot for named managed type from candidate"() {
        def instantiator = TestUtil.objectInstantiator()
        def value = instantiator.named(Thing, "value")
        def value1 = instantiator.named(Thing, "value")
        def value2 = instantiator.named(Thing, "value2")
        def value3 = instantiator.named(Named, "value2")

        expect:
        def snapshot = snapshotter.snapshot(value)
        areTheSame(snapshot, value1)
        areNotTheSame(snapshot, value2)
        areNotTheSame(snapshot, value3)
        areNotTheSame(snapshot, "value")
    }

    def "creates snapshot for serializable type from candidate"() {
        expect:
        def snapshot = snapshotter.snapshot(new Bean(prop: "value"))
        snapshotter.snapshot(new Bean(prop: "value"), snapshot).is(snapshot)

        snapshotter.snapshot(new Bean(), snapshot) != snapshot
        snapshotter.snapshot(new Bean(), snapshot) == snapshotter.snapshot(new Bean())

        snapshotter.snapshot("other", snapshot) != snapshot
        snapshotter.snapshot("other", snapshot) == snapshotter.snapshot("other")
    }

    class GradleBean {
        String prop
    }

    class GradleBeanSerializer implements Serializer<GradleBean> {

        @Override
        GradleBean read(Decoder decoder) throws EOFException, Exception {
            return new GradleBean(prop: decoder.readNullableString())
        }

        @Override
        void write(Encoder encoder, GradleBean value) throws Exception {
            encoder.writeNullableString(value.prop)
        }
    }

    class TestValueSnapshotSerializerRegistry extends DefaultSerializerRegistry implements ValueSnapshotterSerializerRegistry {
    }

    def "creates snapshot for gradle serialized type"() {
        expect:
        def snapshot = snapshotter.snapshot(new GradleBean(prop: "value"))
        snapshotter.snapshot(new GradleBean(prop: "value"), snapshot).is(snapshot)

        snapshotter.snapshot(new GradleBean(), snapshot) != snapshot
        snapshotter.snapshot(new GradleBean(), snapshot) == snapshotter.snapshot(new GradleBean())

        snapshotter.snapshot("other", snapshot) != snapshot
        snapshotter.snapshot("other", snapshot) == snapshotter.snapshot("other")
    }

    private void areTheSame(ValueSnapshot snapshot, Object value) {
        assert snapshotter.snapshot(value, snapshot).is(snapshot)
        assert snapshotter.snapshot(value, snapshot) == snapshotter.snapshot(value)
    }

    private void areNotTheSame(ValueSnapshot snapshot, Object value) {
        assert snapshotter.snapshot(value, snapshot) != snapshot
        assert snapshotter.snapshot(value) != snapshot
        def sn1 = snapshotter.snapshot(value, snapshot)
        def sn2 = snapshotter.snapshot(value)
        assert sn1 == sn2
    }
}
