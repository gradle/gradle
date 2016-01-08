/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.model.internal.manage.binding

import org.gradle.api.Named
import org.gradle.model.*
import org.gradle.model.internal.manage.schema.extract.DefaultModelSchemaExtractor
import org.gradle.model.internal.manage.schema.extract.DefaultModelSchemaStore
import org.gradle.model.internal.type.ModelType
import spock.lang.Specification
import spock.lang.Unroll

class DefaultStructBindingsStoreTest extends Specification {
    def schemaStore = new DefaultModelSchemaStore(DefaultModelSchemaExtractor.withDefaultStrategies())
    def bindingStore = new DefaultStructBindingsStore(schemaStore)

    def "extracts empty"() {
        def bindings = extract(Object)
        expect:
        bindings.declaredViewSchemas*.type*.rawClass as List == [Object]
        bindings.delegateSchema == null
        bindings.managedProperties.isEmpty()
        bindings.methodBindings.isEmpty()
    }

    static abstract class TypeWithAbstractProperty {
        abstract int getZ()
        abstract void setZ(int value)
    }

    def "extracts simple type with a managed property"() {
        def bindings = extract(TypeWithAbstractProperty)
        expect:
        bindings.declaredViewSchemas*.type*.rawClass as List == [TypeWithAbstractProperty]
        bindings.delegateSchema == null
        bindings.managedProperties.values()*.name as List == ["z"]
        bindings.methodBindings*.getClass() == [ManagedPropertyMethodBinding, ManagedPropertyMethodBinding]
        bindings.methodBindings*.source*.name == ["getZ", "setZ"]
    }

    static abstract class TypeWithImplementedProperty {
        int z
    }

    def "extracts simple type with an implemented property"() {
        def bindings = extract(TypeWithImplementedProperty)
        expect:
        bindings.declaredViewSchemas*.type*.rawClass as List == [TypeWithImplementedProperty]
        bindings.delegateSchema == null
        bindings.managedProperties.isEmpty()
        bindings.methodBindings*.source*.name == ["getZ", "setZ"]
        bindings.methodBindings*.getClass() == [DirectMethodBinding, DirectMethodBinding]
    }

    static class DelegateTypeWithImplementedProperty {
        int z
    }

    def "extracts simple type with a delegated property"() {
        def bindings = extract(TypeWithAbstractProperty, DelegateTypeWithImplementedProperty)
        expect:
        bindings.declaredViewSchemas*.type*.rawClass as List == [TypeWithAbstractProperty]
        bindings.delegateSchema.type.rawClass == DelegateTypeWithImplementedProperty
        bindings.managedProperties.isEmpty()
        bindings.methodBindings*.source*.name == ["getZ", "setZ"]
        bindings.methodBindings*.getClass() == [DelegateMethodBinding, DelegateMethodBinding]
    }

    def "fails when implemented property is present in delegate"() {
        when:
        extract(TypeWithImplementedProperty, DelegateTypeWithImplementedProperty)
        then:
        def ex = thrown IllegalArgumentException
        ex.message == "Method '${DefaultStructBindingsStoreTest.simpleName}.${TypeWithImplementedProperty.simpleName}.getZ()' is both implemented by the view and the delegate type '${DefaultStructBindingsStoreTest.simpleName}.${DelegateTypeWithImplementedProperty.simpleName}.getZ()'"
    }

    static abstract class TypeWithAbstractWriteOnlyProperty {
        abstract void setZ(int value)
    }

    def "fails when abstract property has only setter"() {
        when:
        extract(TypeWithAbstractWriteOnlyProperty)
        then:
        def ex = thrown InvalidManagedPropertyException
        ex.message == "Property 'z' of type '${getName(TypeWithAbstractWriteOnlyProperty)}' must both have an abstract getter as well as a setter."
    }

    static abstract class TypeWithInconsistentPropertyType {
        abstract String getZ()
        abstract void setZ(int value)
    }

    def "fails when property has inconsistent type"() {
        when:
        extract(TypeWithInconsistentPropertyType)
        then:
        def ex = thrown InvalidManagedPropertyException
        ex.message == "Property 'z' of type '${getName(TypeWithInconsistentPropertyType)}' must have setter accepting parameter with type 'String' instead of 'int'."
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

    def "detects overloads"() {
        def bindings = extract(OverloadingNumber, OverloadingIntegerImpl)
        expect:
        bindings.declaredViewSchemas*.type*.rawClass as List == [OverloadingNumber]
        bindings.delegateSchema.type.rawClass == OverloadingIntegerImpl
        bindings.managedProperties.isEmpty()
        bindings.methodBindings*.getClass() == [DelegateMethodBinding, DelegateMethodBinding]
        bindings.methodBindings*.source*.name == ["getValue", "getValue"]
        bindings.methodBindings*.source*.method*.returnType == [Number, Integer]
        bindings.methodBindings*.implementor*.name == ["getValue", "getValue"]
        bindings.methodBindings*.implementor*.method*.returnType == [Integer, Integer]
    }

    static enum MyEnum {
        A, B, C
    }

    @Managed
    static interface HasUnmanagedOnManaged {
        @Unmanaged
        MyEnum getMyEnum();
        void setMyEnum(MyEnum myEnum)
    }

    def "cannot annotate managed type property with unmanaged"() {
        when: extract HasUnmanagedOnManaged
        then: def ex = thrown InvalidManagedPropertyException
        ex.message == "Property 'myEnum' of type '${getName(HasUnmanagedOnManaged)}' is marked as @Unmanaged, but is of @Managed type '${getName(MyEnum)}'. Please remove the @Managed annotation."
    }

    @Managed
    static interface NoSetterForUnmanaged {
        @Unmanaged
        InputStream getThing();
    }

    def "must have setter for unmanaged"() {
        when: extract NoSetterForUnmanaged
        then: def ex = thrown InvalidManagedPropertyException
        ex.message == "Property 'thing' of type '${getName(NoSetterForUnmanaged)}' must not be read only, because it is marked as @Unmanaged."
    }

    @Managed
    static interface AddsSetterToNoSetterForUnmanaged extends NoSetterForUnmanaged {
        void setThing(InputStream inputStream);
    }

    def "subtype can add unmanaged setter"() {
        def bindings = extract(AddsSetterToNoSetterForUnmanaged)
        expect:
        bindings.getManagedProperty("thing").type == ModelType.of(InputStream)
    }

    @Managed
    static abstract class WritableMapProperty {
        abstract void setMap(ModelMap<NamedThingInterface> map)
        abstract ModelMap<NamedThingInterface> getMap()
    }

    @Managed
    static abstract class WritableSetProperty {
        abstract void setSet(ModelSet<NamedThingInterface> set)
        abstract ModelSet<NamedThingInterface> getSet()
    }

    def "map cannot be writable"() {
        when: extract WritableMapProperty
        then: def ex = thrown InvalidManagedPropertyException
        ex.message == "Property 'map' of type '${getName(WritableMapProperty)}' cannot have a setter (ModelMap properties must be read only)."
    }

    def "set cannot be writable"() {
        when: extract WritableSetProperty
        then: def ex = thrown InvalidManagedPropertyException
        ex.message == "Property 'set' of type '${getName(WritableSetProperty)}' cannot have a setter (ModelSet properties must be read only)."
    }

    @Managed
    static interface GetterWithParams {
        String getName(String name)
        void setName(String name)
    }

    def "malformed getter"() {
        when: extract GetterWithParams
        then: def ex = thrown InvalidManagedPropertyException
        ex.message == "Property 'name' of type '${getName(GetterWithParams)}' is invalid: getter method '${getName(GetterWithParams)}.getName(String)' must not take parameters."
    }

    @Managed
    static interface NonVoidSetter {
        String getName()
        String setName(String name)
    }

    def "non void setter"() {
        when: extract NonVoidSetter
        then: def ex = thrown InvalidManagedPropertyException
        ex.message == "Property 'name' of type '${getName(NonVoidSetter)}' is invalid: setter method '${getName(NonVoidSetter)}.setName(String)' must have void return type."
    }

    @Managed
    static interface SetterWithExtraParams {
        String getName()
        void setName(String name, String otherName)
    }

    def "setter with extra params"() {
        when: extract SetterWithExtraParams
        then: def ex = thrown InvalidManagedPropertyException
        ex.message == "Property 'name' of type '${getName(SetterWithExtraParams)}' is invalid: setter method '${getName(SetterWithExtraParams)}.setName(String, String)' must have exactly one parameter."
    }

    @Managed
    static interface HasExtraNonPropertyMethods {
        String getName()

        void setName(String name)

        void foo(String bar)
    }

    @Managed
    static interface ChildWithExtraNonPropertyMethods extends HasExtraNonPropertyMethods {
    }

    @Unroll
    def "can only have abstract getters and setters in #type"() {
        when: extract type
        then: def ex = thrown IllegalArgumentException
        ex.message == "Abstract method '${getName(HasExtraNonPropertyMethods)}.foo(String)' is not a property accessor, and it has no implementation."

        where:
        type                             | _
        HasExtraNonPropertyMethods       | _
        ChildWithExtraNonPropertyMethods | _
    }

    @Managed
    interface HasTwoFirstsCharLowercaseGetter {
        String getccCompiler()
    }

    def "reject two firsts char lowercase getters"() {
        when: extract HasTwoFirstsCharLowercaseGetter
        then: def ex = thrown IllegalArgumentException
        ex.message == "Abstract method '${getName(HasTwoFirstsCharLowercaseGetter)}.getccCompiler()' is not a property accessor, and it has no implementation."
    }

    @Managed
    interface HasGetGetterLikeMethod {
        String gettingStarted()
    }

    def "get-getters-like methods not considered as getters"() {
        when: extract HasGetGetterLikeMethod
        then: def ex = thrown IllegalArgumentException
        ex.message == "Abstract method '${getName(HasGetGetterLikeMethod)}.gettingStarted()' is not a property accessor, and it has no implementation."
    }

    @Managed
    interface HasIsGetterLikeMethod {
        boolean isidore()
    }

    def "is-getters-like methods not considered as getters"() {
        when: extract HasIsGetterLikeMethod
        then: def ex = thrown IllegalArgumentException
        ex.message == "Abstract method '${getName(HasIsGetterLikeMethod)}.isidore()' is not a property accessor, and it has no implementation."
    }

    @Managed
    interface HasSetterLikeMethod {
        void settings(String settings)
    }

    def "setters-like methods not considered as setters"() {
        when: extract HasSetterLikeMethod
        then: def ex = thrown IllegalArgumentException
        ex.message == "Abstract method '${getName(HasSetterLikeMethod)}.settings(String)' is not a property accessor, and it has no implementation."
    }

    @Managed
    static interface MisalignedSetterType {
        String getThing()
        void setThing(Object name)
    }

    def "misaligned setter type"() {
        when: def bindings = extract MisalignedSetterType
        then: def ex = thrown InvalidManagedPropertyException
        ex.message == "Property 'thing' of type '${getName(MisalignedSetterType)}' must have setter accepting parameter with type 'String' instead of 'Object'."
    }

    @Managed
    static abstract class NonAbstractGetterWithSetter {
        String getName() {}
        abstract void setName(String name)
    }

    @Managed
    static abstract class NonAbstractSetter {
        abstract String getName()
        void setName(String name) {}
    }

    def "non-abstract getter with abstract setter is not allowed"() {
        when: extract NonAbstractGetterWithSetter
        then: def ex = thrown InvalidManagedPropertyException
        ex.message == "Property 'name' of type '${getName(NonAbstractGetterWithSetter)}' must have either only abstract accessor methods or only implemented accessor methods."
    }

    def "non-abstract setter without getter is not allowed"() {
        when: extract NonAbstractSetter
        then: def ex = thrown InvalidManagedPropertyException
        ex.message == "Property 'name' of type '${getName(NonAbstractSetter)}' must have either only abstract accessor methods or only implemented accessor methods."
    }

    @Managed
    static interface CollectionType {
        List<String> getItems()
        void setItems(List<Integer> integers)
    }

    def "displays a reasonable error message when getter and setter of a property of collection of scalar types do not use the same generic type"() {
        given: when: extract CollectionType
        then: def ex = thrown InvalidManagedPropertyException
        ex.message == "Property 'items' of type '${getName(CollectionType)}' must have setter accepting parameter with type 'List<String>' instead of 'List<Integer>'."
    }

    @Unroll
    def "misaligned types #firstType.simpleName and #secondType.simpleName"() {
        def interfaceWithPrimitiveProperty = new GroovyClassLoader(getClass().classLoader).parseClass """
            import org.gradle.model.Managed

            @Managed
            interface PrimitiveProperty {
                $firstType.name getPrimitiveProperty()

                void setPrimitiveProperty($secondType.name value)
            }
        """
        when: extract interfaceWithPrimitiveProperty
        then: def ex = thrown InvalidManagedPropertyException
        ex.message == "Property 'primitiveProperty' of type 'PrimitiveProperty' must have setter accepting parameter with type '$firstType.simpleName' instead of '$secondType.simpleName'."

        where:
        firstType | secondType
        byte      | Byte
        boolean   | Boolean
        char      | Character
        float     | Float
        long      | Long
        short     | Short
        int       | Integer
        double    | Double
        Byte      | byte
        Boolean   | boolean
        Character | char
        Float     | float
        Long      | long
        Short     | short
        Integer   | int
        Double    | double
    }

    @Managed
    abstract static class MutableName implements Named {
        abstract void setName(String name)
    }

    def "Named cannot have setName"() {
        when:
        extract MutableName

        then:
        def e = thrown Exception
        e.message == "Property 'name' of type '${getName(MutableName)}' must not have a setter, because the type implements '$Named.name'."
    }

    @Managed
    static interface HasIsAndGetPropertyWithDifferentTypes {
        boolean isValue()
        String getValue()
    }

    def "handles is/get property with non-matching type"() {
        when: extract HasIsAndGetPropertyWithDifferentTypes
        then: def ex = thrown InvalidManagedPropertyException
        ex.message == "Property 'value' of type '${getName(HasIsAndGetPropertyWithDifferentTypes)}' must have a consistent type, but it's defined as String, boolean."
    }

    @Managed
    interface IsNotAllowedForOtherTypeThanBoolean {
        String isThing()
        void setThing(String thing)
    }

    @Managed
    interface IsNotAllowedForOtherTypeThanBooleanWithBoxedBoolean {
        Boolean isThing()
        void setThing(Boolean thing)
    }

    @Unroll
    def "should not allow 'is' as a prefix for getter on non primitive boolean in #managedType"() {
        when: extract managedType
        then: def ex = thrown InvalidManagedPropertyException
        ex.message == "Property 'thing' of type '${getName(managedType)}' has invalid getter '${getName(managedType)}.isThing()': it should either return 'boolean', or its name should be 'getThing()'."

        where:
        managedType << [IsNotAllowedForOtherTypeThanBoolean, IsNotAllowedForOtherTypeThanBooleanWithBoxedBoolean]
    }


    def extract(Class<?> type, Class<?> delegateType = null) {
        return extract(type, [], delegateType)
    }
    def extract(Class<?> type, List<Class<?>> viewTypes, Class<?> delegateType = null) {
        return bindingStore.getBindings(
            ModelType.of(type),
            viewTypes.collect { ModelType.of(it) },
            delegateType == null ? null : ModelType.of(delegateType)
        )
    }

    @Unroll
    def "finds #results.simpleName as the converging types among #types.simpleName"() {
        expect:
        DefaultStructBindingsStore.findConvergingTypes(types.collect { ModelType.of(it) }) as List == results.collect { ModelType.of(it) }

        where:
        types                                 | results
        [Object]                              | [Object]
        [Object, Serializable]                | [Serializable]
        [Object, Number, Comparable, Integer] | [Integer]
        [Integer, Object, Number, Comparable] | [Integer]
        [Integer, Double]                     | [Integer, Double]
        [Integer, Object, Double]             | [Integer, Double]
        [Integer, Object, Comparable, Double] | [Integer, Double]
    }

    String getName(ModelType<?> type) {
        type.displayName
    }

    String getName(Class<?> type) {
        getName(ModelType.of(type))
    }
}
