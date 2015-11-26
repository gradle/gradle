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

import com.google.common.base.Optional
import groovy.transform.NotYetImplemented
import org.gradle.api.internal.file.FileResolver
import org.gradle.internal.typeconversion.DefaultTypeConverter
import org.gradle.model.Managed
import org.gradle.model.internal.core.MutableModelNode
import org.gradle.model.internal.core.UnmanagedStruct
import org.gradle.model.internal.manage.instance.ManagedInstance
import org.gradle.model.internal.manage.instance.ModelElementState
import org.gradle.model.internal.manage.schema.StructSchema
import org.gradle.model.internal.type.ModelType
import org.gradle.util.Matchers
import spock.lang.Specification
import spock.lang.Unroll

class ManagedProxyClassGeneratorTest extends Specification {
    static def generator = new ManagedProxyClassGenerator()
    static Map<Class<?>, Map<Class<?>, Class<?>>> generated = [:].withDefault { [:] }
    def schemaStore = DefaultModelSchemaStore.instance
    def typeConverter = new DefaultTypeConverter(Stub(FileResolver))

    def "generates a proxy class for an interface"() {
        expect:
        def impl = newInstance(SomeType)
        impl instanceof SomeType
    }

    def "generates a proxy class for an interface with type parameters"() {
        when:
        def generatedType = generate(SomeTypeWithParameters)

        then:
        generatedType.getMethod("getValues").returnType == List
        generatedType.getMethod("getValues").genericReturnType.actualTypeArguments == [String]

        generatedType.getMethod("getOptional").returnType == Optional
        generatedType.getMethod("getOptional").genericReturnType.actualTypeArguments == [Boolean]

        generatedType.getMethod("setOptional", Optional).genericParameterTypes*.actualTypeArguments == [[Boolean]]
    }

    def "mixes in ManagedInstance"() {
        def node = Stub(MutableModelNode)
        def state = Mock(ModelElementState) {
            getBackingNode() >> node
        }

        when:
        Class<? extends SomeType> proxyClass = generate(SomeType)

        then:
        ManagedInstance.methods.each { method ->
            assert proxyClass.getMethod(method.name, method.parameterTypes).synthetic
        }

        when:
        SomeType impl = proxyClass.newInstance(state, typeConverter)

        then:
        assert impl instanceof ManagedInstance
        impl.backingNode == node
        impl.managedType == ModelType.of(SomeType)

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

    @Unroll
    def "only generates the requested boolean getter methods"() {
        given:
        def impl = newInstance(type)

        when:
        def methods = impl.class.declaredMethods
        def getGetter = methods.find { it.name == 'getFlag' }
        def isGetter = methods.find { it.name == 'isFlag' }

        then:
        (getGetter != null) == expectGetGetter
        (isGetter != null) == expectIsGetter

        where:
        type           | expectGetGetter | expectIsGetter
        BooleanGetter1 | true            | false
        BooleanGetter2 | false           | true
        BooleanGetter3 | true            | true
        BooleanGetter4 | true            | true
        BooleanGetter5 | false           | true
    }

    @Managed
    static interface BooleanGetter1 {
        boolean getFlag()
    }

    @Managed
    static interface BooleanGetter2 {
        boolean isFlag()
    }

    @Managed
    static interface BooleanGetter3 {
        boolean getFlag()

        boolean isFlag()
    }

    @Managed
    static interface BooleanGetter4 extends BooleanGetter1 {
        // make sure that getters from parents are used
        boolean isFlag()
    }

    @Managed
    static interface BooleanGetter5 extends BooleanGetter2 {
        // make sure that overrides do not generate duplicates
        boolean isFlag()
    }

    def "equals() returns false for non-compatible types"() {
        def impl = newInstance(SomeType)
        expect:
        !impl.equals(null)
        !impl.equals(1)
    }

    def "Two views are equal when they are backed by the same node"() {
        def node1 = Mock(MutableModelNode)
        def node2 = Mock(MutableModelNode)
        def state1 = Mock(ModelElementState) {
            getBackingNode() >> node1
        }
        def state1alternative = Mock(ModelElementState) {
            getBackingNode() >> node1
        }
        def state2 = Mock(ModelElementState) {
            getBackingNode() >> node2
        }

        when:
        Class<? extends SomeType> proxyClass = generate(SomeType)
        SomeType impl1 = proxyClass.newInstance(state1, typeConverter)
        SomeType impl1alternative = proxyClass.newInstance(state1alternative, typeConverter)
        SomeType impl2 = proxyClass.newInstance(state2, typeConverter)

        then:
        Matchers.strictlyEquals(impl1, impl1alternative)
        !impl1.equals(impl2)
    }

    def "hashCode() works as expected"() {
        def node = Mock(MutableModelNode)
        def state = Mock(ModelElementState) {
            getBackingNode() >> node
        }

        when:
        Class<? extends SomeType> proxyClass = generate(SomeType)
        SomeType impl = proxyClass.newInstance(state, typeConverter)
        def hashCode = impl.hashCode()

        then:
        hashCode == 123
        1 * node.hashCode() >> 123
    }

    @Unroll
    def "mixes in unmanaged delegate from #managedType.simpleName"() {
        def node = Stub(MutableModelNode)
        def state = Mock(ModelElementState) {
            getBackingNode() >> node
        }

        when:
        Class<? extends PublicUnmanagedType> proxyClass = generate(managedType, UnmanagedImplType)
        def unmanagedInstance = new UnmanagedImplType()
        PublicUnmanagedType impl = proxyClass.newInstance(state, typeConverter, unmanagedInstance)

        then:
        impl instanceof ManagedInstance
        ((ManagedInstance) impl).backingNode == node
        managedType.isAssignableFrom(proxyClass)
        managedType.isAssignableFrom(impl.class)

        when:
        impl.unmanagedValue = "Lajos"
        then:
        unmanagedInstance.unmanagedValue == "Lajos"

        when:
        def greeting = impl.sayHello()
        then:
        greeting == "Hello Lajos"

        when:
        impl.managedValue = "Tibor"
        then:
        1 * state.set("managedValue", "Tibor")

        when:
        def managedValue = impl.managedValue
        then:
        managedValue == "Tibor"
        1 * state.get("managedValue") >> { "Tibor" }

        when:
        Class<? extends InternalUnmanagedType> proxyClassInternal = generate(InternalUnmanagedType, UnmanagedImplType)
        InternalUnmanagedType internalImpl = proxyClassInternal.newInstance(state, typeConverter, unmanagedInstance)

        then:
        unmanagedInstance.unmanagedValue == "Lajos"

        when:
        internalImpl.unmanagedValue = "Geza"
        then:
        unmanagedInstance.unmanagedValue == "Geza"

        expect:
        internalImpl.add(2, 3) == 5

        when:
        internalImpl.throwError()
        then:
        def ex = thrown RuntimeException
        ex.message == "error"

        where:
        managedType                    | _
        ManagedSubTypeViaInterface     | _
        ManagedSubTypeViaAbstractClass | _
    }

    static interface OverloadingNumber {
        Number getValue()
    }

    static interface OverloadingInteger extends OverloadingNumber {
        @Override
        Integer getValue()
    }

    static class OverloadingNumberImpl implements OverloadingNumber {
        @Override
        Number getValue() { 1.0d }
    }

    static class OverloadingIntegerImpl extends OverloadingNumberImpl implements OverloadingInteger {
        @Override
        Integer getValue() { 2 }
    }

    @Managed
    static interface CustomManagedOverloading extends OverloadingNumber {
    }

    def "can call overridden delegate method"() {
        def node = Stub(MutableModelNode)
        def state = Mock(ModelElementState) {
            getBackingNode() >> node
        }

        Class<? extends CustomManagedOverloading> proxyClass = generate(CustomManagedOverloading, OverloadingIntegerImpl)

        when:
        OverloadingNumber impl = proxyClass.newInstance(state, typeConverter, new OverloadingIntegerImpl())

        then:
        impl.value == 2
    }

    def "can call delegate methods that accept and return primitive values"() {
        def node = Stub(MutableModelNode)
        def state = Mock(ModelElementState) {
            getBackingNode() >> node
        }

        def proxyClass = generate(TypeWithPrimitiveMethods, ClassWithPrimitiveMethods)

        when:
        def impl = proxyClass.newInstance(state, typeConverter, new ClassWithPrimitiveMethods())

        then:
        impl.someLong(12L) == 13L
        impl.someBoolean(true) == false
        impl.someChar('a' as char) == 'b' as char
        impl.someThing(1, 4) == 5
        impl.dontReturn(1 as short, 4 as byte)
    }

    static interface TypeWithPrimitiveMethods {
        long someLong(long l)
        boolean someBoolean(boolean b)
        char someChar(char ch)
        int someThing(int a, int b)
        void dontReturn(short s, byte b)
    }

    static class ClassWithPrimitiveMethods {
        long someLong(long l) {
            l + 1
        }
        boolean someBoolean(boolean b) {
            !b
        }
        char someChar(char ch) {
            ch + 1
        }
        int someThing(int a, int b) {
            a + b
        }
        void dontReturn(short s, byte b) {
        }
    }

    def "mixes in configure method that forwards to element state for property with managed type"() {
        def state = Mock(ModelElementState)

        given:
        def proxyClass = generate(SomeTypeWithReadOnlyProperty)
        def impl = proxyClass.newInstance(state, typeConverter)
        def cl = { }

        when:
        impl.value(cl)

        then:
        1 * state.apply("value", cl)
        0 * state._
    }

    def "mixes in eager configure method for managed property with unmanaged struct type"() {
        def state = Mock(ModelElementState)
        def prop = Mock(SomeUnmanagedStruct)

        given:
        def proxyClass = generate(SomeTypeWithReadOnlyProperty)
        def impl = proxyClass.newInstance(state, typeConverter)

        when:
        impl.otherValue {
            value = "12"
        }

        then:
        1 * state.get("otherValue") >> prop
        1 * prop.setValue("12")
        0 * state._
    }

    def "mixes in eager configure method for delegating property with unmanaged struct type"() {
        def state = Mock(ModelElementState)
        def prop = Mock(SomeUnmanagedStruct)
        def delegate = new Object() {
            SomeUnmanagedStruct getOtherValue() {
                return prop
            }
        }

        given:
        def proxyClass = generate(SomeTypeWithReadOnlyProperty, delegate.class)
        def impl = proxyClass.newInstance(state, typeConverter, delegate)

        when:
        impl.otherValue {
            value = "12"
        }

        then:
        1 * prop.setValue("12")
        0 * state._
    }

    def "mixes in set method for managed property with scalar type"() {
        def state = Mock(ModelElementState)

        given:
        def proxyClass = generate(SomeType)
        def impl = proxyClass.newInstance(state, typeConverter)

        when:
        impl.value 12
        impl.primitive 14L

        then:
        1 * state.set("value", 12)
        1 * state.set("primitive", 14L)
        0 * state._

        when:
        impl.value "123"

        then:
        1 * state.set("value", 123)
        0 * state._
    }

    def "mixes in set method for delegated property with scalar type"() {
        def state = Mock(ModelElementState)
        def delegate = Mock(UnmanagedImplType)

        given:
        def proxyClass = generate(PublicUnmanagedType, UnmanagedImplType)
        def impl = proxyClass.newInstance(state, typeConverter, delegate)

        when:
        impl.unmanagedValue "value"
        impl.intValue 12

        then:
        1 * delegate.setUnmanagedValue("value")
        1 * delegate.setIntValue(12)
        0 * delegate._

        when:
        impl.intValue "123"

        then:
        1 * delegate.setIntValue(123)
        0 * delegate._
    }

    def "mixes in toString() implementation that delegates to delegate object when it has a displayName property"() {
        def state = Stub(ModelElementState)
        def delegate = new Object() {
            String getDisplayName() {
                return "<delegate>"
            }
        }

        expect:
        def proxyClass = generate(SomeType, delegate.class)
        def impl = proxyClass.newInstance(state, typeConverter, delegate)
        impl.toString() == "<delegate>"
    }

    def "mixes in toString() implementation that delegates to element state"() {
        def state = Stub(ModelElementState) {
            getDisplayName() >> "<display-name>"
        }

        expect:
        def proxyClass = generate(SomeType)
        def impl = proxyClass.newInstance(state, typeConverter)
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

    @NotYetImplemented
    def "reports contract type rather than implementation class when attempting to set read-only property"() {
        given:
        def impl = newInstance(SomeTypeWithReadOnly)

        when:
        impl.readOnly = '12'

        then:
        ReadOnlyPropertyException e = thrown()
        e.message == "Cannot set readonly property: readOnly for class: ${SomeTypeWithReadOnly.name}"
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

    def "reports contract type rather than implementation class when attempting to invoke configure method for property that does not have one"() {
        given:
        def impl = newInstance(SomeType)

        when:
        impl.value { broken }

        then:
        MissingMethodException e = thrown()
        e.message.startsWith("No signature of method: ${SomeType.name}.value() is applicable")
    }

    def "reports contract type rather than implementation class when attempting to invoke set method for property that does not have one"() {
        given:
        def state = Mock(ModelElementState) {
            get("readOnly") >> true
        }
        def impl = generate(SomeTypeWithReadOnlyProperty).newInstance(state, typeConverter)

        when:
        impl.readOnly Boolean.FALSE

        then:
        MissingMethodException e = thrown()
        e.message.startsWith("No signature of method: ${SomeTypeWithReadOnlyProperty.name}.readOnly() is applicable")
    }

    @Unroll
    def "can read and write #value to managed property of type #primitiveType"() {
        def loader = new GroovyClassLoader(getClass().classLoader)
        when:
        def interfaceWithPrimitiveProperty = loader.parseClass """
            @org.gradle.model.Managed
            interface PrimitiveProperty {
                $primitiveType.name getPrimitiveProperty()

                void setPrimitiveProperty($primitiveType.name value)
            }
        """

        def data = [:]
        def state = Mock(ModelElementState)
        state.get(_) >> { args ->
            data[args[0]]
        }
        state.set(_, _) >> { args ->
            data[args[0]] = args[1]
        }

        def proxy = generate(interfaceWithPrimitiveProperty)
        def instance = proxy.newInstance(state, typeConverter)

        then:
        new GroovyShell(loader, new Binding(instance: instance)).evaluate """
            instance.primitiveProperty = $value
            assert instance.primitiveProperty == $value
            instance
        """

        where:
        primitiveType | value
        byte          | "(byte)123"
        boolean       | "false"
        boolean       | "true"
        char          | "'c'"
        float         | "123.45f"
        long          | "123L"
        short         | "(short)123"
        int           | "123"
        double        | "123.456d"
    }

    @Unroll
    def "can read and write #value to managed property of type List<#scalarType>"() {
        def loader = new GroovyClassLoader(getClass().classLoader)
        when:
        def clazz = loader.parseClass """
            @org.gradle.model.Managed
            interface PrimitiveProperty {
                List<$scalarType.name> getItems()

                void setItems(List<$scalarType.name> value)
            }
        """


        def data = [:]
        def state = Mock(ModelElementState)
        state.get(_) >> { args ->
            data[args[0]]
        }
        state.set(_, _) >> { args ->
            data[args[0]] = args[1]
        }

        def proxy = generate(clazz)
        def instance = proxy.newInstance(state, typeConverter)

        then:
        new GroovyShell(loader, new Binding(instance: instance)).evaluate """
            instance.items = $value
            assert instance.items == $value
            instance
        """

        where:
        scalarType | value
        String     | "null"
        String     | "['123']"
        Boolean    | "null"
        Boolean    | "[true, false]"
        Character  | "null"
        Character  | "[(char)'1',(char)'2',(char)'3']"
        Byte       | "null"
        Byte       | "[1,2]"
        Short      | "null"
        Short      | "[1,2,3]"
        Integer    | "null"
        Integer    | "[1,2,3]"
        Long       | "null"
        Long       | "[1L,2L,3L]"
        Float      | "null"
        Float      | "[1f,2f]"
        Double     | "null"
        Double     | "[1d,2d,3d]"
        BigInteger | "null"
        BigInteger | "[1G,2G,3G]"
        BigDecimal | "null"
        BigDecimal | "[1G,2G,3G]"
        File       | "null"
        File       | "[new File('foo')]"
    }

    def <T> T newInstance(Class<T> type) {
        def generated = generate(type)
        return generated.newInstance(Stub(ModelElementState), typeConverter)
    }

    def <T, M extends T, D extends T> Class<? extends T> generate(Class<T> managedType, Class<D> delegateType = null) {
        Map<Class<?>, Class<?>> generatedForDelegateType = generated[managedType]
        Class<? extends T> generated = generatedForDelegateType[delegateType] as Class<? extends T>
        if (generated == null) {
            def managedSchema = schemaStore.getSchema(managedType)
            def delegateSchema = delegateType == null ? null : schemaStore.getSchema(delegateType)
            generated = generator.generate((StructSchema) managedSchema, (StructSchema) delegateSchema)
            generatedForDelegateType[delegateType] = generated
        }
        return generated
    }

    @Managed
    static interface SomeType {
        Integer getValue()
        void setValue(Integer value)

        long getPrimitive()
        void setPrimitive(long l)
    }

    @Managed
    static interface SomeTypeWithReadOnlyProperty {
        SomeType getValue()
        SomeUnmanagedStruct getOtherValue()
        boolean isReadOnly()
    }

    @UnmanagedStruct
    static interface SomeUnmanagedStruct {
        void setValue(String value)
    }

    @Managed
    static abstract class SomeTypeWithReadOnly {
        abstract Integer getValue()

        abstract void setValue(Integer value)

        String getReadOnly() {
            return "read-only"
        }
    }

    @Managed
    static interface SomeTypeWithParameters {
        List<String> getValues();

        Optional<Boolean> getOptional();
        void setOptional(Optional<Boolean> optional);
    }

    static interface PublicUnmanagedType {
        String getUnmanagedValue()

        void setUnmanagedValue(String unmanagedValue)

        String sayHello()

        int getIntValue()
        void setIntValue(int value)
    }

    static interface InternalUnmanagedType extends PublicUnmanagedType {
        Integer add(Integer a, Integer b)

        void throwError()
    }

    static class UnmanagedImplType implements InternalUnmanagedType {
        String unmanagedValue
        int intValue

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

    @Managed
    static interface ManagedSubTypeViaInterface extends PublicUnmanagedType {
        String getManagedValue()
        void setManagedValue(String managedValue)
    }

    @Managed
    static abstract class ManagedSubTypeViaAbstractClass implements PublicUnmanagedType {
        abstract String getManagedValue()
        abstract void setManagedValue(String managedValue)
    }

    static interface UnmanagedSuperType {}

    static class DefaultPublicTypeAsAbstractClassWithMethod implements UnmanagedSuperType {
        String getSomeValue() {
            "from default implementation"
        }
    }

    @Managed static abstract class PublicTypeAsAbstractClassWithMethod extends UnmanagedSuperType {
        String getSomeValue() {
            "from abstract class"
        }
    }

    @NotYetImplemented
    def "favour managed public type abstract class methods over default implementation methods"() {
        def node = Stub(MutableModelNode)
        def state = Mock(ModelElementState) {
            getBackingNode() >> node
        }

        when:
        Class<? extends UnmanagedSuperType> proxyClass = generate(PublicTypeAsAbstractClassWithMethod, DefaultPublicTypeAsAbstractClassWithMethod)
        def unmanagedInstance = new DefaultPublicTypeAsAbstractClassWithMethod()
        UnmanagedSuperType impl = proxyClass.newInstance(state, unmanagedInstance)

        then:
        impl instanceof ManagedInstance
        ((ManagedInstance) impl).backingNode == node
        PublicTypeAsAbstractClassWithMethod.isAssignableFrom(proxyClass)
        PublicTypeAsAbstractClassWithMethod.isAssignableFrom(impl.class)

        when:
        def value = impl.getSomeValue()
        then:
        value == "from abstract class"
    }

}
