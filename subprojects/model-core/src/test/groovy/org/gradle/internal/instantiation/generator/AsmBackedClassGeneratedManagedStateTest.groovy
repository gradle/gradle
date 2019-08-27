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

package org.gradle.internal.instantiation.generator

import org.gradle.cache.internal.TestCrossBuildInMemoryCacheFactory
import org.gradle.internal.Describables
import org.gradle.internal.state.DefaultManagedFactoryRegistry
import org.gradle.internal.state.Managed
import org.gradle.internal.state.ManagedFactory
import org.gradle.internal.state.ManagedFactoryRegistry
import org.gradle.util.TestUtil
import spock.lang.Unroll

import static org.gradle.internal.instantiation.generator.AsmBackedClassGeneratorTest.AbstractBean
import static org.gradle.internal.instantiation.generator.AsmBackedClassGeneratorTest.AbstractBeanWithInheritedFields
import static org.gradle.internal.instantiation.generator.AsmBackedClassGeneratorTest.AbstractClassWithTypeParamProperty
import static org.gradle.internal.instantiation.generator.AsmBackedClassGeneratorTest.Bean
import static org.gradle.internal.instantiation.generator.AsmBackedClassGeneratorTest.BeanWithAbstractProperty
import static org.gradle.internal.instantiation.generator.AsmBackedClassGeneratorTest.InterfaceBean
import static org.gradle.internal.instantiation.generator.AsmBackedClassGeneratorTest.InterfaceDirectoryPropertyBean
import static org.gradle.internal.instantiation.generator.AsmBackedClassGeneratorTest.InterfaceFileCollectionBean
import static org.gradle.internal.instantiation.generator.AsmBackedClassGeneratorTest.InterfaceFilePropertyBean
import static org.gradle.internal.instantiation.generator.AsmBackedClassGeneratorTest.InterfaceListPropertyBean
import static org.gradle.internal.instantiation.generator.AsmBackedClassGeneratorTest.InterfaceMapPropertyBean
import static org.gradle.internal.instantiation.generator.AsmBackedClassGeneratorTest.InterfaceNestedBean
import static org.gradle.internal.instantiation.generator.AsmBackedClassGeneratorTest.InterfacePrimitiveBean
import static org.gradle.internal.instantiation.generator.AsmBackedClassGeneratorTest.InterfacePropertyBean
import static org.gradle.internal.instantiation.generator.AsmBackedClassGeneratorTest.InterfacePropertyWithParamTypeBean
import static org.gradle.internal.instantiation.generator.AsmBackedClassGeneratorTest.InterfaceSetPropertyBean
import static org.gradle.internal.instantiation.generator.AsmBackedClassGeneratorTest.InterfaceWithDefaultMethods
import static org.gradle.internal.instantiation.generator.AsmBackedClassGeneratorTest.Param

class AsmBackedClassGeneratedManagedStateTest extends AbstractClassGeneratorSpec {
    ManagedFactory generatorFactory = TestUtil.instantiatorFactory().managedFactory
    final ManagedFactoryRegistry managedFactoryRegistry = new DefaultManagedFactoryRegistry().withFactories(generatorFactory)
    final ClassGenerator generator = AsmBackedClassGenerator.injectOnly([], [], new TestCrossBuildInMemoryCacheFactory(), generatorFactory.id)

    def canConstructInstanceOfAbstractClassWithAbstractPropertyGetterAndSetter() {
        def bean = create(BeanWithAbstractProperty)

        expect:
        bean.name == null
        bean.setName("name")
        bean.name == "name"
    }

    def canUnpackAndRecreateAbstractClassWithAbstractPropertyGetterAndSetter() {
        def bean = create(BeanWithAbstractProperty)

        expect:
        bean instanceof Managed
        bean.publicType() == BeanWithAbstractProperty
        !bean.immutable()
        def state = bean.unpackState()
        state.length == 1
        state[0] == null

        def copy = managedFactoryRegistry.lookup(bean.getFactoryId()).fromState(BeanWithAbstractProperty, state)
        !copy.is(bean)
        copy.name == null

        bean.setName("name")
        copy.name == null

        def state2 = bean.unpackState()
        state2.length == 1
        state2[0] == "name"

        def copy2 = managedFactoryRegistry.lookup(bean.getFactoryId()).fromState(BeanWithAbstractProperty, state2)
        !copy2.is(bean)
        copy2.name == "name"
    }

    def canConstructInstanceOfInterfaceWithPropertyGetterAndSetter() {
        def bean = create(InterfaceBean)

        expect:
        bean.name == null
        bean.setName("name")
        bean.name == "name"

        bean.numbers == null
        bean.setNumbers([12] as Set)
        bean.numbers == [12] as Set
    }

    def canUnpackAndRecreateInstanceOfInterface() throws Exception {
        def bean = create(InterfaceBean.class)

        expect:
        bean instanceof Managed
        bean.publicType() == InterfaceBean
        !bean.immutable()
        def state = bean.unpackState()
        state.length == 2
        state[0] == null
        state[1] == null

        def copy = managedFactoryRegistry.lookup(bean.getFactoryId()).fromState(InterfaceBean, state)
        !copy.is(bean)
        copy.name == null
        copy.numbers == null

        bean.setName("name")
        bean.setNumbers([12] as Set)
        copy.name == null
        copy.numbers == null

        def state2 = bean.unpackState()
        state2.length == 2
        state2[0] == "name"
        state2[1] == [12] as Set

        def copy2 = managedFactoryRegistry.lookup(bean.getFactoryId()).fromState(InterfaceBean, state2)
        !copy2.is(bean)
        copy2.name == "name"
        copy2.numbers == [12] as Set
    }

    def canConstructInstanceOfInterfaceWithPrimitivePropertyGetterAndSetter() {
        def bean = create(InterfacePrimitiveBean)

        expect:
        !bean.prop1
        bean.setProp1(true)
        bean.prop1

        bean.prop2 == 0
        bean.setProp2(12)
        bean.prop2 == 12
    }

    def canConstructInstanceOfInterfaceWithFileCollectionGetter() {
        def projectDir = tmpDir.testDirectory
        def bean = create(InterfaceFileCollectionBean)

        expect:
        bean.prop.toString() == "file collection"
        bean.prop.empty
        bean.prop.from("a", "b")
        bean.prop.files == [projectDir.file("a"), projectDir.file("b")] as Set
    }

    def canConstructInstanceOfInterfaceWithNestedGetter() {
        def projectDir = tmpDir.testDirectory
        def bean = create(InterfaceNestedBean)

        expect:
        bean.prop.prop.toString() == "file collection"
        bean.prop.prop.empty
        bean.prop.prop.from("a", "b")
        bean.prop.prop.files == [projectDir.file("a"), projectDir.file("b")] as Set
    }

    @Unroll
    def "canConstructInstanceOfInterfaceWithGetterOfFilePropertyType #type.simpleName"() {
        def projectDir = tmpDir.testDirectory
        def bean = create(type)

        expect:
        bean.prop.toString() == "property 'prop'"
        bean.prop.getOrNull() == null
        bean.prop.set(projectDir.file("a"))
        bean.prop.get().asFile == projectDir.file("a")

        where:
        type << [InterfaceFilePropertyBean, InterfaceDirectoryPropertyBean]
    }

    @Unroll
    def "canConstructInstanceOfInterfaceWithGetterOfMutableType #type.simpleName"() {
        def bean = create(type)
        def beanWithDisplayName = create(type, Describables.of("<display-name>"))

        expect:
        bean.prop.toString() == "property 'prop'"
        bean.prop.getOrNull() == defaultValue
        bean.prop.set(newValue)
        bean.prop.get() == newValue

        beanWithDisplayName.prop.toString() == "<display-name> property 'prop'"

        where:
        type                               | defaultValue | newValue
        InterfacePropertyBean              | null         | "value"
        InterfacePropertyWithParamTypeBean | null         | Param.of(Param.of(12))
        AbstractClassWithTypeParamProperty | null         | Param.of("value")
        InterfaceListPropertyBean          | []           | ["a", "b"]
        InterfaceSetPropertyBean           | [] as Set    | ["a", "b"] as Set
        InterfaceMapPropertyBean           | [:]          | [a: 1, b: 12]
    }

    @Unroll
    def "canUnpackAndRecreateInterfaceWithGetterOfMutableType #type.simpleName"() {
        def projectDir = tmpDir.testDirectory
        def bean = create(type)

        expect:
        bean instanceof Managed
        bean.publicType() == type
        !bean.immutable()
        def state = bean.unpackState()
        state.length == 1
        state[0].is(bean.prop)

        def copy = managedFactoryRegistry.lookup(bean.getFactoryId()).fromState(type, state)
        copy.prop.is(bean.prop)

        where:
        type                           | _
        InterfaceFileCollectionBean    | _
        InterfacePropertyBean          | _
        InterfaceFilePropertyBean      | _
        InterfaceDirectoryPropertyBean | _
        InterfaceListPropertyBean      | _
        InterfaceSetPropertyBean       | _
        InterfaceMapPropertyBean       | _
    }

    def canConstructInstanceOfInterfaceWithDefaultMethodsOnly() {
        def bean = create(InterfaceWithDefaultMethods)

        expect:
        bean.name == "name"
    }

    def canUnpackAndRecreateInstanceOfInterfaceWithDefaultMethodsOnly() {
        def bean = create(InterfaceWithDefaultMethods)

        expect:
        bean instanceof Managed
        bean.publicType() == InterfaceWithDefaultMethods
        bean.immutable()
        def state = bean.unpackState()
        state.length == 0

        def copy = managedFactoryRegistry.lookup(bean.getFactoryId()).fromState(InterfaceWithDefaultMethods, state)
        !copy.is(bean)
        copy.name == "name"
    }

    def doesNotMixManagedIntoClassWithFields() {
        def bean = create(Bean)

        expect:
        !(bean instanceof Managed)
    }

    def doesNotMixManagedIntoAbstractClassWithFields() {
        def bean = create(AbstractBean, "value")

        expect:
        !(bean instanceof Managed)
    }

    def doesNotMixManagedIntoClassWithInheritedFields() {
        def bean = create(AbstractBeanWithInheritedFields, "value")

        expect:
        !(bean instanceof Managed)
    }
}
