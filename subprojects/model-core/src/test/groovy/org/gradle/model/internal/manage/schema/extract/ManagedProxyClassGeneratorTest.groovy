/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.model.internal.manage.schema.extract

import com.google.common.collect.ImmutableMap
import org.gradle.model.internal.core.MutableModelNode
import org.gradle.model.internal.manage.instance.ManagedInstance
import org.gradle.model.internal.manage.instance.ModelElementState
import org.gradle.model.internal.manage.schema.ModelProperty
import org.gradle.model.internal.type.ModelType
import spock.lang.Ignore
import spock.lang.Specification

import java.lang.reflect.Type

class ManagedProxyClassGeneratorTest extends Specification {
    static def generator = new ManagedProxyClassGenerator()
    static Map<Class<?>, Map<Class<?>, Class<?>>> generated = [:]

    def "generates a proxy class for an interface"() {
        expect:
        def impl = newInstance(SomeType)
        impl instanceof SomeType
    }

    def "mixes in ManagedInstance"() {
        def node = Stub(MutableModelNode)
        def state = Mock(ModelElementState) {
            getBackingNode() >> node
        }

        when:
        Class<? extends SomeType> proxyClass = generate(SomeType)
        SomeType impl = proxyClass.newInstance(state)

        then:
        impl instanceof ManagedInstance
        ((ManagedInstance) impl).backingNode == node

        when:
        impl.value = 1
        then:
        1 * state.set("value", 1)

        when:
        def value = impl.value
        then:
        value == 1
        1 * state.get("value") >> { 1 }
    }

    def "mixes in UnmanagedInstance"() {
        def node = Stub(MutableModelNode)
        def state = Mock(ModelElementState) {
            getBackingNode() >> node
        }

        when:
        Class<? extends ManagedSubType> proxyClass = generate(ManagedSubType, InternalUnmanagedType)
        def unmanagedInstance = new UnmanagedImplType()
        ManagedSubType impl = proxyClass.newInstance(state, unmanagedInstance)

        then:
        impl instanceof ManagedInstance
        ((ManagedInstance) impl).backingNode == node

        when: impl.unmanagedValue = "Lajos"
        then: unmanagedInstance.unmanagedValue == "Lajos"

        when:
        def greeting = impl.sayHello()
        then:
        greeting == "Hello Lajos"

        expect:
        ((InternalUnmanagedType) impl).add(2, 3) == 5

        when:
        ((InternalUnmanagedType) impl).throwError()
        then:
        def ex = thrown RuntimeException
        ex.message == "error"

        when:
        impl.managedValue = "Tibor"
        then:
        1 * state.set("managedValue", "Tibor")

        when:
        def managedValue = impl.managedValue
        then:
        managedValue == "Tibor"
        1 * state.get("managedValue") >> { "Tibor" }
    }

    def "mixes in toString() implementation that delegates to element state"() {
        def state = Stub(ModelElementState) {
            getDisplayName() >> "<display-name>"
        }

        expect:
        def proxyClass = generate(SomeType)
        def impl = proxyClass.newInstance(state)
        impl.toString() == "<display-name>"
    }

    def "reports contract type rather than implementation class in groovy missing property error message"() {
        given:
        def impl = newInstance(SomeType)

        when:
        impl.unknown

        then:
        MissingPropertyException e = thrown()
        e.message == "No such property: unknown for class: ${SomeType.name}"

        when:
        impl.unknown = '12'

        then:
        e = thrown()
        e.message == "No such property: unknown for class: ${SomeType.name}"
    }

    @Ignore
    def "reports contract type rather than implementation class when attempting to set read-only property"() {
        given:
        def impl = newInstance(SomeType)

        when:
        impl.readOnly = '12'

        then:
        ReadOnlyPropertyException e = thrown()
        e.message == "Cannot set readonly property: readOnly for class: ${SomeType.name}"
    }

    def "reports contract type rather than implementation class when attempting to invoke unknown method"() {
        given:
        def impl = newInstance(SomeType)

        when:
        impl.unknown('12')

        then:
        MissingMethodException e = thrown()
        e.message.startsWith("No signature of method: ${SomeType.name}.unknown() is applicable")
    }

    def "reports contract type rather than implementation class when attempting to invoke method with unsupported parameters"() {
        given:
        def impl = newInstance(SomeType)

        when:
        impl.setValue('12')

        then:
        MissingMethodException e = thrown()
        e.message.startsWith("No signature of method: ${SomeType.name}.setValue() is applicable")
    }

    def <T> T newInstance(Class<T> type) {
        def generated = generate(type)
        return generated.newInstance(Stub(ModelElementState))
    }

    def <T, M extends T, D extends T> Class<? extends T> generate(Class<T> managedType, Class<D> delegateType = null) {
        Map<Class<?>, Class<?>> generatedForDelegateType = generated[managedType]
        if (generatedForDelegateType == null) {
            generatedForDelegateType = [:]
            generated[managedType] = generatedForDelegateType
        }
        Class<? extends T> generated = generatedForDelegateType[delegateType] as Class<? extends T>
        if (generated == null) {
            def properties = managedProperties[managedType]
            generated = generator.generate(managedType, delegateType, properties)
            generatedForDelegateType[delegateType] = generated
        }
        return generated
    }

    static def property(String name, Type type, boolean managed, boolean writable = true) {
        return ModelProperty.of(ModelType.of(type), name, managed, writable, Collections.emptySet(), Collections.emptyMap())
    }

    static interface SomeType {
        Integer getValue()

        void setValue(Integer value)

        String getReadOnly()
    }

    static interface PublicUnmanagedType {
        String getUnmanagedValue()
        void setUnmanagedValue(String unmanagedValue)
        String sayHello()
    }

    static interface InternalUnmanagedType extends PublicUnmanagedType {
        Integer add(Integer a, Integer b)
        void throwError()
    }

    static class UnmanagedImplType implements InternalUnmanagedType {
        String unmanagedValue

        @Override
        Integer add(Integer a, Integer b) {
            return a + b
        }

        @Override
        String sayHello() {
            return "Hello ${unmanagedValue}"
        }

        @Override
        void throwError() {
            throw new RuntimeException("error")
        }
    }

    static interface ManagedSubType extends PublicUnmanagedType {
        String getManagedValue()
        void setManagedValue(String managedValue)
    }

    static Map<Class<?>, Collection<ModelProperty<?>>> managedProperties = ImmutableMap.builder()
        .put(SomeType, [
            property("value", Integer, true),
            property("readOnly", String, true, false)
        ])
        .put(PublicUnmanagedType, [
            property("unmanagedValue", String, false)
        ])
        .put(ManagedSubType, [
            property("unmanagedValue", String, false),
            property("managedValue", String, true)
        ])
        .build()
}
