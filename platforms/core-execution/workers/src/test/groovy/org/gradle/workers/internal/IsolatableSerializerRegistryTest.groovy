/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.workers.internal

import org.gradle.api.attributes.Attribute
import org.gradle.internal.hash.ClassLoaderHierarchyHasher
import org.gradle.internal.hash.TestHashCodes
import org.gradle.internal.instantiation.InstantiatorFactory
import org.gradle.internal.isolation.Isolatable
import org.gradle.internal.isolation.IsolatableFactory
import org.gradle.internal.serialize.kryo.KryoBackedDecoder
import org.gradle.internal.serialize.kryo.KryoBackedEncoder
import org.gradle.internal.service.DefaultServiceRegistry
import org.gradle.internal.service.ServiceLookup
import org.gradle.internal.snapshot.impl.DefaultIsolatableFactory
import org.gradle.internal.snapshot.impl.IsolatedImmutableManagedValue
import org.gradle.internal.snapshot.impl.IsolatedJavaSerializedValueSnapshot
import org.gradle.internal.snapshot.impl.IsolatedManagedValue
import org.gradle.util.TestUtil
import org.gradle.workers.fixtures.TestManagedTypes
import spock.lang.Specification

class IsolatableSerializerRegistryTest extends Specification {
    def managedFactoryRegistry = TestUtil.managedFactoryRegistry()
    def classLoaderHasher = Stub(ClassLoaderHierarchyHasher) {
        getClassLoaderHash(_) >> TestHashCodes.hashCodeFrom(123)
    }
    IsolatableFactory isolatableFactory = new DefaultIsolatableFactory(classLoaderHasher, managedFactoryRegistry)
    InstantiatorFactory instantiatorFactory = TestUtil.instantiatorFactory()
    ServiceLookup services = new DefaultServiceRegistry().add(InstantiatorFactory, instantiatorFactory)

    def serializer = IsolatableSerializerRegistry.create(classLoaderHasher, managedFactoryRegistry)
    def outputStream = new ByteArrayOutputStream()
    def encoder = new KryoBackedEncoder(outputStream)

    def "can serialize/deserialize isolated String values"() {
        def string1 = "foo"
        def string2 = "bar"
        Isolatable<?>[] isolatables = [isolatableFactory.isolate(string1), isolatableFactory.isolate(string2)]

        when:
        serialize(isolatables)

        and:
        Isolatable<?>[] newIsolatables = deserialize()

        then:
        newIsolatables[0].isolate() == string1
        newIsolatables[1].isolate() == string2
    }

    def "can serialize/deserialize isolated Boolean values"() {
        def boolean1 = true
        def boolean2 = false
        Isolatable<?>[] isolatables = [isolatableFactory.isolate(boolean1), isolatableFactory.isolate(boolean2)]

        when:
        serialize(isolatables)

        and:
        Isolatable<?>[] newIsolatables = deserialize()

        then:
        newIsolatables[0].isolate() == boolean1
        newIsolatables[1].isolate() == boolean2
    }

    def "can serialize/deserialize isolated Short values"() {
        short short1 = 1
        short short2 = 2
        Isolatable<?>[] isolatables = [isolatableFactory.isolate(short1), isolatableFactory.isolate(short2)]

        when:
        serialize(isolatables)

        and:
        Isolatable<?>[] newIsolatables = deserialize()

        then:
        newIsolatables[0].isolate() == short1
        newIsolatables[1].isolate() == short2
    }

    def "can serialize/deserialize isolated Integer values"() {
        int integer1 = 32768
        int integer2 = 32769
        Isolatable<?>[] isolatables = [isolatableFactory.isolate(integer1), isolatableFactory.isolate(integer2)]

        when:
        serialize(isolatables)

        and:
        Isolatable<?>[] newIsolatables = deserialize()

        then:
        newIsolatables[0].isolate() == integer1
        newIsolatables[1].isolate() == integer2
    }

    def "can serialize/deserialize isolated Long values"() {
        long long1 = 2147483649
        long long2 = 2147483650
        Isolatable<?>[] isolatables = [isolatableFactory.isolate(long1), isolatableFactory.isolate(long2)]

        when:
        serialize(isolatables)

        and:
        Isolatable<?>[] newIsolatables = deserialize()

        then:
        newIsolatables[0].isolate() == long1
        newIsolatables[1].isolate() == long2
    }

    def "can serialize/deserialize isolated Attribute values"() {
        Attribute attr1 = Attribute.of("foo", String.class)
        Attribute attr2 = Attribute.of("bar", SomeType.class)
        Isolatable<?>[] isolatables = [isolatableFactory.isolate(attr1), isolatableFactory.isolate(attr2)]

        when:
        serialize(isolatables)

        and:
        Isolatable<?>[] newIsolatables = deserialize()

        then:
        newIsolatables[0].isolate() == attr1
        newIsolatables[1].isolate() == attr2
    }

    def "can serialize/deserialize generated Managed values"() {
        def instantiator = instantiatorFactory.decorate(services)
        def managedValue1 = instantiator.newInstance(TestManagedTypes.ManagedThing)
        def managedValue2 = instantiator.newInstance(TestManagedTypes.ManagedThing)

        managedValue1.foo = "bar"
        managedValue2.foo = "baz"

        Isolatable<?>[] isolatables = [isolatableFactory.isolate(managedValue1), isolatableFactory.isolate(managedValue2)]
        assert isolatables.every { it instanceof IsolatedManagedValue }

        when:
        serialize(isolatables)

        and:
        Isolatable<?>[] newIsolatables = deserialize()

        then:
        assert newIsolatables.every { it instanceof IsolatedManagedValue }

        and:
        newIsolatables[0].isolate().getFoo() == "bar"
        newIsolatables[1].isolate().getFoo() == "baz"
    }

    def "can serialize/deserialize generated immutable Managed values"() {
        def instantiator = TestUtil.objectInstantiator()
        def managedValue1 = instantiator.named(TestManagedTypes.ImmutableManagedThing, "bar")
        def managedValue2 = instantiator.named(TestManagedTypes.ImmutableManagedThing, "baz")

        Isolatable<?>[] isolatables = [isolatableFactory.isolate(managedValue1), isolatableFactory.isolate(managedValue2)]
        assert isolatables.every { it instanceof IsolatedImmutableManagedValue }

        when:
        serialize(isolatables)

        and:
        Isolatable<?>[] newIsolatables = deserialize()

        then:
        assert newIsolatables.every { it instanceof IsolatedImmutableManagedValue }

        and:
        newIsolatables[0].isolate().name == "bar"
        newIsolatables[1].isolate().name == "baz"
    }

    def "can serialize/deserialize isolated File values"() {
        File file1 = new File("foo")
        File file2 = new File("bar")
        Isolatable<?>[] isolatables = [isolatableFactory.isolate(file1), isolatableFactory.isolate(file2)]

        when:
        serialize(isolatables)

        and:
        Isolatable<?>[] newIsolatables = deserialize()

        then:
        newIsolatables[0].isolate() == file1
        newIsolatables[1].isolate() == file2
    }

    def "can serialize/deserialize isolated Serialized values"() {
        SerializableType type1 = new SerializableType("bar")
        SerializableType type2 = new SerializableType("baz")
        Isolatable<?>[] isolatables = [isolatableFactory.isolate(type1), isolatableFactory.isolate(type2)]
        assert isolatables.every { it instanceof IsolatedJavaSerializedValueSnapshot }

        when:
        serialize(isolatables)

        and:
        Isolatable<?>[] newIsolatables = deserialize()

        then:
        newIsolatables[0].isolate().foo == "bar"
        newIsolatables[1].isolate().foo == "baz"
    }

    def "can serialize/deserialize isolated Null values"() {
        Isolatable<?>[] isolatables = [isolatableFactory.isolate(null), isolatableFactory.isolate(null)]

        when:
        serialize(isolatables)

        and:
        Isolatable<?>[] newIsolatables = deserialize()

        then:
        newIsolatables[0].isolate() == null
        newIsolatables[1].isolate() == null
    }

    def "can serialize/deserialize isolated Enum values"() {
        Isolatable<?>[] isolatables = [isolatableFactory.isolate(EnumType.FOO), isolatableFactory.isolate(EnumType.BAR)]

        when:
        serialize(isolatables)

        and:
        Isolatable<?>[] newIsolatables = deserialize()

        then:
        newIsolatables[0].isolate() == EnumType.FOO
        newIsolatables[1].isolate() == EnumType.BAR
    }

    def "can serialize/deserialize isolated Map"() {
        Map<String, String> map = [
                "foo": "bar",
                "baz": "buzz"
        ]

        when:
        serialize(isolatableFactory.isolate(map))

        and:
        Isolatable<?>[] newIsolatables = deserialize()

        then:
        newIsolatables[0].isolate() == map
    }

    def "can serialize/deserialize isolated Properties"() {
        Properties properties = new Properties()
        properties.setProperty("foo", "bar")
        properties.setProperty("baz", "buzz")

        when:
        serialize(isolatableFactory.isolate(properties))

        and:
        Isolatable<?>[] newIsolatables = deserialize()

        then:
        newIsolatables[0].isolate() == properties
    }

    def "can serialize/deserialize isolated Array"() {
        String[] array = ["foo", "bar"]

        when:
        serialize(isolatableFactory.isolate(array))

        and:
        Isolatable<?>[] newIsolatables = deserialize()

        then:
        newIsolatables[0].isolate() == array

        and:
        newIsolatables[0].isolate().class == String[].class
    }

    def "can serialize/deserialize isolated zero-length Array"() {
        String[] array = []

        when:
        serialize(isolatableFactory.isolate(array))

        and:
        Isolatable<?>[] newIsolatables = deserialize()

        then:
        newIsolatables[0].isolate() == array

        and:
        newIsolatables[0].isolate().class == String[].class
    }

    def "can serialize/deserialize isolated List"() {
        List<String> list = ["foo", "bar"]

        when:
        serialize(isolatableFactory.isolate(list))

        and:
        Isolatable<?>[] newIsolatables = deserialize()

        then:
        newIsolatables[0].isolate() == list
    }

    def "can serialize/deserialize isolated Set"() {
        Set<String> list = ["foo", "bar"]

        when:
        serialize(isolatableFactory.isolate(list))

        and:
        Isolatable<?>[] newIsolatables = deserialize()

        then:
        newIsolatables[0].isolate() == list
    }

    def serialize(Isolatable<?>... isolatables) {
        encoder.writeInt(isolatables.size())
        isolatables.each { serializer.writeIsolatable(encoder, it) }
        encoder.flush()
    }

    Isolatable<?>[] deserialize() {
        def isolatables = []
        def decoder = new KryoBackedDecoder(new ByteArrayInputStream(outputStream.toByteArray()));
        int size = decoder.readInt()
        size.times {
            isolatables.add(serializer.readIsolatable(decoder))
        }
        return isolatables as Isolatable<?>[]
    }

    static class SomeType { }

    static class SerializableType implements Serializable {
        final String foo

        SerializableType(String foo) {
            this.foo = foo
        }
    }

    enum EnumType {
        FOO {}, BAR
    }
}
