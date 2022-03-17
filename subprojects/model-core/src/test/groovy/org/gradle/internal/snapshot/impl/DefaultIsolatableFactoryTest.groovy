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

package org.gradle.internal.snapshot.impl

import org.gradle.api.Named
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.internal.model.NamedObjectInstantiator
import org.gradle.api.internal.provider.DefaultMapProperty
import org.gradle.api.internal.provider.ManagedFactories
import org.gradle.api.internal.provider.PropertyHost
import org.gradle.api.internal.provider.Providers
import org.gradle.cache.internal.TestCrossBuildInMemoryCacheFactory
import org.gradle.internal.classloader.ClasspathUtil
import org.gradle.internal.classloader.FilteringClassLoader
import org.gradle.internal.hash.ClassLoaderHierarchyHasher
import org.gradle.internal.hash.TestHashCodes
import org.gradle.internal.state.ManagedFactoryRegistry
import org.gradle.util.TestUtil
import spock.lang.Specification

import static org.gradle.util.TestUtil.instantiatorFactory

class DefaultIsolatableFactoryTest extends Specification {

    def classLoaderHasher = Stub(ClassLoaderHierarchyHasher) {
        getClassLoaderHash(_) >> TestHashCodes.hashCodeFrom(123)
    }
    def managedFactoryRegistry = Mock(ManagedFactoryRegistry)
    def snapshotter = new DefaultValueSnapshotter([], classLoaderHasher)
    def isolatableFactory = new DefaultIsolatableFactory(classLoaderHasher, managedFactoryRegistry)

    def "creates isolated string"() {
        expect:
        def original = "abc"
        def isolated = isolatableFactory.isolate(original)
        isolated instanceof StringValueSnapshot
        isolated.isolate().is(original)
    }

    def "can coerce string value"() {
        expect:
        def original = "abc"
        def isolated = isolatableFactory.isolate(original)
        isolated.coerce(String).is(original)
        isolated.coerce(CharSequence).is(original)
        isolated.coerce(Object).is(original)
        isolated.coerce(Number) == null
    }

    def "creates isolated integer"() {
        expect:
        def original = 123
        def isolated = isolatableFactory.isolate(original)
        isolated instanceof IntegerValueSnapshot
        isolated.isolate().is(original)
    }

    def "can coerce integer value"() {
        expect:
        def original = 123
        def isolated = isolatableFactory.isolate(original)
        isolated.coerce(Integer).is(original)
        isolated.coerce(Number).is(original)
        isolated.coerce(Long) == null
        isolated.coerce(Short) == null
        isolated.coerce(Byte) == null
        isolated.coerce(String) == null
    }

    def "creates isolated long"() {
        expect:
        def original = 123L
        def isolated = isolatableFactory.isolate(original)
        isolated instanceof LongValueSnapshot
        isolated.isolate().is(original)
    }

    def "can coerce long value"() {
        expect:
        def original = 123L
        def isolated = isolatableFactory.isolate(original)
        isolated.coerce(Long).is(original)
        isolated.coerce(Number).is(original)
        isolated.coerce(Integer) == null
        isolated.coerce(Short) == null
        isolated.coerce(Byte) == null
        isolated.coerce(String) == null
    }

    def "creates isolated short"() {
        expect:
        def original = 123 as short
        def isolated = isolatableFactory.isolate(original)
        isolated instanceof ShortValueSnapshot
        isolated.isolate().is(original)
    }

    def "can coerce short value"() {
        expect:
        def original = 123 as short
        def isolated = isolatableFactory.isolate(original)
        isolated.coerce(Short).is(original)
        isolated.coerce(Number).is(original)
        isolated.coerce(Integer) == null
        isolated.coerce(Byte) == null
        isolated.coerce(String) == null
    }

    def "creates isolated boolean"() {
        expect:
        isolatableFactory.isolate(true).isolate()
        !isolatableFactory.isolate(false).isolate()
    }

    def "can coerce boolean value"() {
        expect:
        isolatableFactory.isolate(true).coerce(Boolean)
        !isolatableFactory.isolate(false).coerce(Boolean)
        isolatableFactory.isolate(false).coerce(String) == null
    }

    def "creates isolated null value"() {
        expect:
        isolatableFactory.isolate(null).isolate() == null
        isolatableFactory.isolate(null).is(isolatableFactory.isolate(null))
    }

    def "can coerce null value"() {
        expect:
        isolatableFactory.isolate(null).coerce(String) == null
        isolatableFactory.isolate(null).coerce(Number) == null
    }

    def "creates isolated array"() {
        expect:
        def original1 = [] as String[]
        def isolated1 = isolatableFactory.isolate(original1)
        isolated1 instanceof IsolatedArray
        def copy1 = isolated1.isolate()
        copy1 == [] as String[]
        copy1.class == String[].class
        !copy1.is(original1)

        def original2 = ["123"] as String[]
        def isolated2 = isolatableFactory.isolate(original2)
        isolated2 instanceof IsolatedArray
        def copy2 = isolated2.isolate()
        copy2 == ["123"] as String[]
        copy2.class == String[].class
        !copy2.is(original2)
    }

    def "creates isolated list"() {
        expect:
        def original1 = []
        def isolated1 = isolatableFactory.isolate(original1)
        isolated1 instanceof IsolatedList
        def copy1 = isolated1.isolate()
        copy1 == []
        !copy1.is(original1)

        def original2 = ["123"]
        def isolated2 = isolatableFactory.isolate(original2)
        isolated2 instanceof IsolatedList
        def copy2 = isolated2.isolate()
        copy2 == ["123"]
        !copy2.is(original2)
    }

    def "creates isolated set"() {
        expect:
        def original1 = [] as Set
        def isolated1 = isolatableFactory.isolate(original1)
        isolated1 instanceof IsolatedSet
        def copy1 = isolated1.isolate()
        copy1 == [] as Set
        !copy1.is(original1)

        def original2 = ["123"] as Set
        def isolated2 = isolatableFactory.isolate(original2)
        isolated2 instanceof IsolatedSet
        def copy2 = isolated2.isolate()
        copy2 == ["123"] as Set
        !copy2.is(original2)
    }

    def "creates isolated map"() {
        expect:
        def original1 = [:]
        def isolated1 = isolatableFactory.isolate(original1)
        isolated1 instanceof IsolatedMap
        def copy1 = isolated1.isolate()
        copy1 == [:]
        !copy1.is(original1)

        def original2 = [a: "123"]
        def isolated2 = isolatableFactory.isolate(original2)
        isolated2 instanceof IsolatedMap
        def copy2 = isolated2.isolate()
        copy2 == [a: "123"]
        !copy2.is(isolated2)
    }

    Properties properties(Map<String, String> entries) {
        def properties = new Properties()
        entries.each { key, value -> properties.setProperty(key, value) }
        return properties
    }

    def "creates isolated properties"() {
        expect:
        def original1 = properties([:])
        def isolated1 = isolatableFactory.isolate(original1)
        isolated1 instanceof IsolatedProperties
        def copy1 = isolated1.isolate()
        copy1 == properties([:])
        !copy1.is(original1)

        def original2 = properties([a: "123"])
        def isolated2 = isolatableFactory.isolate(original2)
        isolated2 instanceof IsolatedProperties
        def copy2 = isolated2.isolate()
        copy2 == properties([a: "123"])
        !copy2.is(isolated2)
    }

    enum Type2 {
        TWO, THREE
    }

    def "creates isolated enum value"() {
        expect:
        def isolated = isolatableFactory.isolate(Type1.TWO)
        isolated instanceof EnumValueSnapshot
        isolated.isolate() == Type1.TWO
    }

    def "can coerce enum value"() {
        def loader = new GroovyClassLoader(getClass().getClassLoader().parent)
        loader.addURL(ClasspathUtil.getClasspathForClass(GroovyObject).toURI().toURL())
        def cl = loader.parseClass("package ${Type1.package.name}; enum Type1 { TWO, THREE } ")
        assert cl != Type1
        assert cl.name == Type1.name

        expect:
        def isolated = isolatableFactory.isolate(Type1.TWO)
        isolated.coerce(Type1).is(Type1.TWO)
        isolated.coerce(Type2) == null
        isolated.coerce(String) == null

        def v = isolated.coerce(cl)
        cl.isInstance(v)
        v.name() == "TWO"
    }

    def "creates isolated file"() {
        expect:
        def original = new File("abc")
        def isolated = isolatableFactory.isolate(original)
        isolated instanceof FileValueSnapshot
        isolated.isolate() == original
    }

    def "can coerce file value"() {
        expect:
        def original = new File("abc")
        def isolated = isolatableFactory.isolate(original)
        isolated.coerce(File) == original
        isolated.coerce(String) == null
    }

    def "creates isolated provider"() {
        def originalValue = "123"
        def original = Providers.of(originalValue)

        given:
        1 * managedFactoryRegistry.lookup(_) >> new ManagedFactories.ProviderManagedFactory()

        expect:
        def isolated = isolatableFactory.isolate(original)
        isolated instanceof IsolatedManagedValue
        def copy = isolated.isolate()
        !copy.is(original)
        copy.get().is(originalValue)
    }

    def "creates isolated property"() {
        def originalValue = "123"
        def original = TestUtil.propertyFactory().property(String)
        original.set(originalValue)

        given:
        1 * managedFactoryRegistry.lookup(ManagedFactories.PropertyManagedFactory.FACTORY_ID) >> new ManagedFactories.PropertyManagedFactory(TestUtil.propertyFactory())
        1 * managedFactoryRegistry.lookup(ManagedFactories.ProviderManagedFactory.FACTORY_ID) >> new ManagedFactories.ProviderManagedFactory()

        expect:
        def isolated = isolatableFactory.isolate(original)
        isolated instanceof IsolatedManagedValue
        def copy = isolated.isolate()
        !copy.is(original)
        copy.get().is(originalValue)
    }

    def "creates isolated list property"() {
        def originalValue = ["123"]
        def original = TestUtil.propertyFactory().listProperty(String)
        original.set(originalValue)

        given:
        1 * managedFactoryRegistry.lookup(_) >> new ManagedFactories.ListPropertyManagedFactory(TestUtil.propertyFactory())

        expect:
        def isolated = isolatableFactory.isolate(original)
        isolated instanceof IsolatedManagedValue
        def copy = isolated.isolate()
        !copy.is(original)
        copy.get() == ["123"]
        !copy.get().is(originalValue)
    }

    def "creates isolated set property"() {
        def originalValue = ["123"]
        def original = TestUtil.propertyFactory().setProperty(String)
        original.set(originalValue)

        given:
        1 * managedFactoryRegistry.lookup(_) >> new ManagedFactories.SetPropertyManagedFactory(TestUtil.propertyFactory())

        expect:
        def isolated = isolatableFactory.isolate(original)
        isolated instanceof IsolatedManagedValue
        def copy = isolated.isolate()
        !copy.is(original)
        copy.get() == ["123"] as Set
        !copy.get().is(originalValue)
    }

    def "creates isolated map property"() {
        def originalMap = [a: 1, b: 2]
        def original = new DefaultMapProperty(PropertyHost.NO_OP, String, Number)
        original.set(originalMap)

        given:
        1 * managedFactoryRegistry.lookup(_) >> new ManagedFactories.MapPropertyManagedFactory(TestUtil.propertyFactory())

        expect:
        def isolated = isolatableFactory.isolate(original)
        isolated instanceof IsolatedManagedValue
        def copy = isolated.isolate()
        !copy.is(original)
        copy.get() == [a: 1, b: 2]
        !copy.get().is(originalMap)
    }

    def "creates isolated named managed type"() {
        def instantiator = TestUtil.objectInstantiator()
        def value = instantiator.named(Thing, "value1")

        expect:
        def isolated = isolatableFactory.isolate(value)
        isolated instanceof IsolatedImmutableManagedValue
        isolated.isolate().is(value)
    }

    def "can coerce named managed type"() {
        def instantiator = TestUtil.objectInstantiator()
        def value = instantiator.named(Thing, "value1")

        def spec = new FilteringClassLoader.Spec()
        spec.allowClass(Named)
        spec.allowPackage("org.gradle.api.internal.model") // mixed into the implementation
        spec.allowPackage("org.gradle.internal.state") // mixed into the implementation
        def filter = new FilteringClassLoader(getClass().classLoader, spec)
        def loader = new GroovyClassLoader(filter)
        loader.addURL(ClasspathUtil.getClasspathForClass(GroovyObject).toURI().toURL())
        def cl = loader.parseClass("package ${Thing.package.name}; interface Thing extends ${Named.name} { }")
        assert cl != Thing
        assert Named.isAssignableFrom(cl)
        assert cl.name == Thing.name

        given:
        _ * managedFactoryRegistry.lookup(_) >> new NamedObjectInstantiator(new TestCrossBuildInMemoryCacheFactory())

        expect:
        def isolated = isolatableFactory.isolate(value)
        isolated.coerce(Thing).is(value)
        isolated.coerce(Named).is(value)
        isolated.coerce(String) == null

        def v = isolated.coerce(cl)
        cl.isInstance(v)
        v.name == "value1"
    }

    interface BeanInterface {
        String getProp1()

        void setProp1(String value)
    }

    def "creates isolated managed interface"() {
        def instantiator = instantiatorFactory().inject()
        def original = instantiator.newInstance(BeanInterface)
        original.prop1 = "a"

        given:
        _ * managedFactoryRegistry.lookup(_) >> instantiatorFactory().managedFactory

        expect:
        def isolated = isolatableFactory.isolate(original)
        isolated instanceof IsolatedManagedValue
        def copy = isolated.isolate()
        !copy.is(original)
        copy.prop1 == "a"
    }

    def "creates isolated managed abstract class"() {
        def instantiator = instantiatorFactory().inject()
        def original = instantiator.newInstance(AbstractBean)
        original.prop1 = "a"

        given:
        _ * managedFactoryRegistry.lookup(_) >> instantiatorFactory().managedFactory

        expect:
        def isolated = isolatableFactory.isolate(original)
        isolated instanceof IsolatedManagedValue
        def copy = isolated.isolate()
        !copy.is(original)
        copy.prop1 == "a"
    }

    def "creates isolated ConfigurableFileCollection"() {
        def empty = configurableFiles()
        def files1 = configurableFiles()
        files1.from(new File("a").absoluteFile)

        given:
        _ * managedFactoryRegistry.lookup(org.gradle.api.internal.file.collections.ManagedFactories.ConfigurableFileCollectionManagedFactory.FACTORY_ID) >> new org.gradle.api.internal.file.collections.ManagedFactories.ConfigurableFileCollectionManagedFactory(TestFiles.fileCollectionFactory())

        expect:
        def isolatedEmpty = isolatableFactory.isolate(empty)
        isolatedEmpty instanceof IsolatedManagedValue
        def copyEmpty = isolatedEmpty.isolate()
        !copyEmpty.is(empty)
        copyEmpty.files as List == []

        def isolated = isolatableFactory.isolate(files1)
        isolated instanceof IsolatedManagedValue
        def copy = isolated.isolate()
        !copy.is(files1)
        copy.files == files1.files
    }

    private ConfigurableFileCollection configurableFiles() {
        TestFiles.fileCollectionFactory().configurableFiles()
    }

    def "creates isolated java serializable type"() {
        def original = new Bean(prop: "123")

        def loader = new GroovyClassLoader(getClass().classLoader)
        loader.addURL(ClasspathUtil.getClasspathForClass(GroovyObject).toURI().toURL())
        def cl = loader.parseClass("package ${Bean.package.name}; class Bean implements Serializable { String prop }")
        assert cl != Bean
        assert cl.name == Bean.name

        expect:
        def isolated = isolatableFactory.isolate(original)
        isolated instanceof JavaSerializedValueSnapshot
        def other = isolated.isolate()
        other.prop == "123"
        !other.is(original)

        def v = isolated.coerce(cl)
        v.prop == "123"
    }

    def "can coerce java serializable value"() {
        def original = new Bean(prop: "123")

        expect:
        def isolated = isolatableFactory.isolate(original)
        def other = isolated.coerce(Bean)
        other.prop == "123"
        !other.is(original)
        isolated.coerce(String) == null
    }
}
