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
import org.gradle.model.Managed
import org.gradle.model.ModelMap
import org.gradle.model.ModelSet
import org.gradle.model.NamedThingInterface
import org.gradle.model.Unmanaged
import org.gradle.model.internal.manage.schema.extract.DefaultModelSchemaExtractor
import org.gradle.model.internal.manage.schema.extract.DefaultModelSchemaStore
import org.gradle.model.internal.type.ModelType
import org.gradle.util.internal.VersionNumber
import spock.lang.Specification

import static org.gradle.model.ModelTypeTesting.fullyQualifiedNameOf
import static org.junit.Assume.assumeTrue

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
        bindings.methodBindings*.viewMethod*.name == ["getZ", "setZ"]
    }

    static abstract class TypeWithImplementedProperty {
        int getZ() { 0 }
        void setZ(int value) {}
    }

    def "extracts simple type with an implemented property"() {
        def bindings = extract(TypeWithImplementedProperty)
        expect:
        bindings.declaredViewSchemas*.type*.rawClass as List == [TypeWithImplementedProperty]
        bindings.delegateSchema == null
        bindings.managedProperties.isEmpty()
        bindings.methodBindings*.viewMethod*.name == ["getZ", "setZ"]
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
        bindings.methodBindings*.viewMethod*.name == ["getZ", "setZ"]
        bindings.methodBindings*.getClass() == [DelegateMethodBinding, DelegateMethodBinding]
    }

    def "fails when delegate type is abstract"() {
        when: extract(Object, Serializable)
        then: def ex = thrown InvalidManagedTypeException
        ex.message == "Type 'Object' is not a valid managed type: delegate type must be null or a non-abstract type instead of 'Serializable'."
    }

    @Managed
    static class EmptyStaticClass {}

    def "public type must be abstract"() {
        when: extract EmptyStaticClass
        then: def ex = thrown InvalidManagedTypeException
        ex.message == """Type ${fullyQualifiedNameOf(EmptyStaticClass)} is not a valid managed type:
- Must be defined as an interface or an abstract class."""
    }

    @Managed
    static interface ParameterizedEmptyInterface<T> {}

    def "public type cannot be parameterized"() {
        when: extract ParameterizedEmptyInterface
        then: def ex = thrown InvalidManagedTypeException
        ex.message == """Type ${fullyQualifiedNameOf(ParameterizedEmptyInterface)} is not a valid managed type:
- Cannot be a parameterized type."""
    }


    @Managed
    static abstract class WithInstanceScopedField {
        private String name
        private int age
    }

    def "instance scoped fields are not allowed"() {
        when:  extract WithInstanceScopedField
        then: def ex = thrown InvalidManagedTypeException
        ex.message == """Type ${fullyQualifiedNameOf(WithInstanceScopedField)} is not a valid managed type:
- Field name is not valid: Fields must be static final.
- Field age is not valid: Fields must be static final."""
    }

    @Managed
    static abstract class WithInstanceScopedFieldInSuperclass extends WithInstanceScopedField {
    }

    def "instance scoped fields are not allowed in super-class"() {
        when: extract WithInstanceScopedFieldInSuperclass
        then: def ex = thrown InvalidManagedTypeException
        ex.message == """Type ${fullyQualifiedNameOf(WithInstanceScopedFieldInSuperclass)} is not a valid managed type:
- Field DefaultStructBindingsStoreTest.WithInstanceScopedField.name is not valid: Fields must be static final.
- Field DefaultStructBindingsStoreTest.WithInstanceScopedField.age is not valid: Fields must be static final."""
    }

    @Managed
    static abstract class ProtectedAbstractMethods {
        protected abstract String getName()

        protected abstract void setName(String name)
    }

    @Managed
    static abstract class ProtectedAbstractMethodsInSuper extends ProtectedAbstractMethods {
    }

    def "protected abstract methods are not allowed"() {
        when:
        extract(ProtectedAbstractMethods)

        then:
        def e = thrown InvalidManagedTypeException
        e.message == """Type ${fullyQualifiedNameOf(ProtectedAbstractMethods)} is not a valid managed type:
- Method getName() is not a valid method: Protected and private methods are not supported.
- Method setName(java.lang.String) is not a valid method: Protected and private methods are not supported."""

        when:
        extract(ProtectedAbstractMethodsInSuper)

        then:
        e = thrown InvalidManagedTypeException
        e.message == """Type ${fullyQualifiedNameOf(ProtectedAbstractMethodsInSuper)} is not a valid managed type:
- Method DefaultStructBindingsStoreTest.ProtectedAbstractMethods.getName() is not a valid method: Protected and private methods are not supported.
- Method DefaultStructBindingsStoreTest.ProtectedAbstractMethods.setName(java.lang.String) is not a valid method: Protected and private methods are not supported."""
    }

    @Managed
    static abstract class ProtectedAndPrivateNonAbstractMethods {
        protected String getName() {
            return null;
        }

        private void setName(String name) {}
    }

    def "protected and private non-abstract methods are not allowed"() {
        when:
        extract ProtectedAndPrivateNonAbstractMethods
        then:
        def ex = thrown InvalidManagedTypeException
        ex.message == """Type ${fullyQualifiedNameOf(ProtectedAndPrivateNonAbstractMethods)} is not a valid managed type:
- Method setName(java.lang.String) is not a valid method: Protected and private methods are not supported.
- Method getName() is not a valid method: Protected and private methods are not supported."""
    }

    @Managed
    static abstract class ProtectedAndPrivateNonAbstractMethodsInSuper extends ProtectedAndPrivateNonAbstractMethods {
    }

    def "protected and private non-abstract methods are not allowed in super-type"() {
        when: extract ProtectedAndPrivateNonAbstractMethodsInSuper
        then: def ex = thrown InvalidManagedTypeException
        ex.message == """Type ${fullyQualifiedNameOf(ProtectedAndPrivateNonAbstractMethodsInSuper)} is not a valid managed type:
- Method DefaultStructBindingsStoreTest.ProtectedAndPrivateNonAbstractMethods.setName(java.lang.String) is not a valid method: Protected and private methods are not supported.
- Method DefaultStructBindingsStoreTest.ProtectedAndPrivateNonAbstractMethods.getName() is not a valid method: Protected and private methods are not supported."""
    }

    def "fails when implemented property is present in delegate"() {
        when:
        extract TypeWithImplementedProperty, DelegateTypeWithImplementedProperty
        then:
        def ex = thrown InvalidManagedTypeException
        ex.message == """Type ${fullyQualifiedNameOf(TypeWithImplementedProperty)} is not a valid managed type:
- Method getZ() is not a valid method: it is both implemented by the view '${getName(TypeWithImplementedProperty)}' and the delegate type '${getName(DelegateTypeWithImplementedProperty)}'
- Method setZ(int) is not a valid method: it is both implemented by the view '${getName(TypeWithImplementedProperty)}' and the delegate type '${getName(DelegateTypeWithImplementedProperty)}'"""
    }

    static abstract class TypeWithAbstractWriteOnlyProperty {
        abstract void setZ(int value)
    }

    def "fails when abstract property has only setter"() {
        when:
        extract(TypeWithAbstractWriteOnlyProperty)
        then:
        def ex = thrown InvalidManagedTypeException
        ex.message == """Type ${fullyQualifiedNameOf(TypeWithAbstractWriteOnlyProperty)} is not a valid managed type:
- Property 'z' is not valid: it must both have an abstract getter and a setter"""
    }

    static abstract class TypeWithInconsistentPropertyType {
        abstract String getZ()
        abstract void setZ(int value)
    }

    def "fails when property has inconsistent type"() {
        when:
        extract(TypeWithInconsistentPropertyType)
        then:
        def ex = thrown InvalidManagedTypeException
        ex.message == """Type ${fullyQualifiedNameOf(TypeWithInconsistentPropertyType)} is not a valid managed type:
- Method setZ(int) is not a valid method: it should take parameter with type 'String'"""
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
        bindings.methodBindings*.viewMethod*.name == ["getValue", "getValue"]
        bindings.methodBindings*.viewMethod*.method*.returnType == [Number, Integer]
        bindings.methodBindings*.implementerMethod*.name == ["getValue", "getValue"]
        bindings.methodBindings*.implementerMethod*.method*.returnType == [Integer, Integer]
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
        then: def ex = thrown InvalidManagedTypeException
        ex.message == """Type ${fullyQualifiedNameOf(HasUnmanagedOnManaged)} is not a valid managed type:
- Property 'myEnum' is not valid: it is marked as @Unmanaged, but is of @Managed type '${getName(MyEnum)}'; please remove the @Managed annotation"""
    }

    @Managed
    static interface NoSetterForUnmanaged {
        @Unmanaged
        InputStream getThing();
    }

    def "must have setter for unmanaged"() {
        when: extract NoSetterForUnmanaged
        then: def ex = thrown InvalidManagedTypeException
        ex.message == """Type ${fullyQualifiedNameOf(NoSetterForUnmanaged)} is not a valid managed type:
- Property 'thing' is not valid: it must not be read only, because it is marked as @Unmanaged"""
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
        then: def ex = thrown InvalidManagedTypeException
        ex.message == """Type ${fullyQualifiedNameOf(WritableMapProperty)} is not a valid managed type:
- Property 'map' is not valid: it cannot have a setter (ModelMap properties must be read only)"""
    }

    def "set cannot be writable"() {
        when: extract WritableSetProperty
        then: def ex = thrown InvalidManagedTypeException
        ex.message == """Type ${fullyQualifiedNameOf(WritableSetProperty)} is not a valid managed type:
- Property 'set' is not valid: it cannot have a setter (ModelSet properties must be read only)"""
    }

    @Managed
    static interface GetterWithParams {
        String getName(String name)
        void setName(String name)
    }

    def "malformed getter"() {
        when: extract GetterWithParams
        then: def ex = thrown InvalidManagedTypeException
        ex.message == """Type ${fullyQualifiedNameOf(GetterWithParams)} is not a valid managed type:
- Method getName(java.lang.String) is not a valid property accessor method: getter method must not take parameters
- Property 'name' is not valid: it must both have an abstract getter and a setter"""
    }

    @Managed
    static interface NonVoidSetter {
        String getName()
        String setName(String name)
    }

    def "non void setter"() {
        def bindings = extract(NonVoidSetter)

        expect:
        bindings.getManagedProperty("name") != null
    }

    @Managed
    static interface SetterWithExtraParams {
        String getName()
        void setName(String name, String otherName)
    }

    def "setter with extra params"() {
        when: extract SetterWithExtraParams
        then: def ex = thrown InvalidManagedTypeException
        ex.message == """Type ${fullyQualifiedNameOf(SetterWithExtraParams)} is not a valid managed type:
- Method setName(java.lang.String, java.lang.String) is not a valid property accessor method: setter method must take exactly one parameter"""
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

    def "can only have abstract getters and setters"() {
        when: extract HasExtraNonPropertyMethods
        then: def ex = thrown InvalidManagedTypeException
        ex.message == """Type ${fullyQualifiedNameOf(HasExtraNonPropertyMethods)} is not a valid managed type:
- Method foo(java.lang.String) is not a valid managed type method: it must have an implementation"""
    }

    def "can only have abstract getters and setters in inherited type"() {
        when: extract ChildWithExtraNonPropertyMethods
        then: def ex = thrown InvalidManagedTypeException
        ex.message == """Type ${fullyQualifiedNameOf(ChildWithExtraNonPropertyMethods)} is not a valid managed type:
- Method DefaultStructBindingsStoreTest.HasExtraNonPropertyMethods.foo(java.lang.String) is not a valid managed type method: it must have an implementation"""
    }

    @Managed
    static interface MisalignedSetterType {
        String getThing()
        void setThing(Object name)
    }

    def "misaligned setter type"() {
        when: extract MisalignedSetterType
        then: def ex = thrown InvalidManagedTypeException
        ex.message == """Type ${fullyQualifiedNameOf(MisalignedSetterType)} is not a valid managed type:
- Method setThing(java.lang.Object) is not a valid method: it should take parameter with type 'String'"""
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
        then: def ex = thrown InvalidManagedTypeException
        ex.message == """Type ${fullyQualifiedNameOf(NonAbstractGetterWithSetter)} is not a valid managed type:
- Property 'name' is not valid: it must have either only abstract accessor methods or only implemented accessor methods"""
    }

    def "non-abstract setter without getter is not allowed"() {
        when: extract NonAbstractSetter
        then: def ex = thrown InvalidManagedTypeException
        ex.message == """Type ${fullyQualifiedNameOf(NonAbstractSetter)} is not a valid managed type:
- Property 'name' is not valid: it must have either only abstract accessor methods or only implemented accessor methods"""
    }

    @Managed
    static interface CollectionType {
        List<String> getItems()
        void setItems(List<Integer> integers)
    }

    def "displays a reasonable error message when getter and setter of a property of collection of scalar types do not use the same generic type"() {
        given: when: extract CollectionType
        then: def ex = thrown InvalidManagedTypeException
        ex.message == """Type ${fullyQualifiedNameOf(CollectionType)} is not a valid managed type:
- Method setItems(java.util.List<java.lang.Integer>) is not a valid method: it should take parameter with type 'List<String>'"""
    }

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
        then: def ex = thrown InvalidManagedTypeException
        ex.message == """Type PrimitiveProperty is not a valid managed type:
- Method setPrimitiveProperty($secondType.name) is not a valid method: it should take parameter with type '$firstType.simpleName'"""

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
        e.message == """Type ${fullyQualifiedNameOf(MutableName)} is not a valid managed type:
- Property 'name' is not valid: it must not have a setter, because the type implements '$Named.name'"""
    }

    @Managed
    static interface HasIsAndGetPropertyWithDifferentTypes {
        boolean isValue()
        String getValue()
    }

    def "handles is/get property with non-matching type"() {
        when: extract HasIsAndGetPropertyWithDifferentTypes
        then: def ex = thrown InvalidManagedTypeException
        ex.message == """Type ${fullyQualifiedNameOf(HasIsAndGetPropertyWithDifferentTypes)} is not a valid managed type:
- Property 'value' is not valid: it must have a consistent type, but it's defined as String, boolean"""
    }

    @Managed
    interface IsNotAllowedForOtherTypeThanBoolean {
        String isThing()
        void setThing(String thing)
    }

    @Managed
    interface BoxedBoolean {
        Boolean isThing()
        void setThing(Boolean thing)
    }

    def "should not allow 'is' as a prefix for getter on non boolean in #type"() {
        when: extract IsNotAllowedForOtherTypeThanBoolean
        then: def ex = thrown InvalidManagedTypeException
        ex.message == """Type ${fullyQualifiedNameOf(IsNotAllowedForOtherTypeThanBoolean)} is not a valid managed type:
- Property 'thing' is not valid: it must both have an abstract getter and a setter"""
    }

    def "allows 'is' as a prefix for getter on non primitive Boolean in #type"() {
        assumeTrue('This test requires bundled Groovy 3', VersionNumber.parse(GroovySystem.version).major == 3)
        def bindings = extract(BoxedBoolean)

        expect:
        bindings.getManagedProperty("thing")
    }

    /**
     * See <a href="https://issues.apache.org/jira/browse/GROOVY-10708">GROOVY-10708</a>
     */
    def "does not allow 'is' as a prefix for getter on non primitive Boolean in #type"() {
        assumeTrue('This test requires bundled Groovy 4 or later', VersionNumber.parse(GroovySystem.version).major >= 4)
        when: extract(BoxedBoolean)
        then: def ex = thrown InvalidManagedTypeException
        ex.message == """Type ${fullyQualifiedNameOf(BoxedBoolean)} is not a valid managed type:
- Property 'thing' is not valid: it must both have an abstract getter and a setter"""
    }

    @Managed
    static abstract class ConstructorWithArguments {
        ConstructorWithArguments(String arg) {}
    }

    @Managed
    static abstract class AdditionalConstructorWithArguments {
        AdditionalConstructorWithArguments() {}
        AdditionalConstructorWithArguments(String arg) {}
    }

    static class SuperConstructorWithArguments {
        SuperConstructorWithArguments(String arg) {}
    }

    @Managed
    static abstract class ConstructorCallingSuperConstructorWithArgs extends SuperConstructorWithArguments {
        ConstructorCallingSuperConstructorWithArgs() {
            super("foo")
        }
    }

    @Managed
    static abstract class CustomConstructorInSuperClass extends ConstructorCallingSuperConstructorWithArgs {
    }

    def "custom constructors are not allowed"() {
        when: extract ConstructorWithArguments
        then: def ex = thrown InvalidManagedTypeException
        ex.message == """Type ${fullyQualifiedNameOf(ConstructorWithArguments)} is not a valid managed type:
- Constructor DefaultStructBindingsStoreTest.ConstructorWithArguments(java.lang.String) is not valid: Custom constructors are not supported."""

        when: extract AdditionalConstructorWithArguments
        then: ex = thrown InvalidManagedTypeException
        ex.message == """Type ${fullyQualifiedNameOf(AdditionalConstructorWithArguments)} is not a valid managed type:
- Constructor DefaultStructBindingsStoreTest.AdditionalConstructorWithArguments(java.lang.String) is not valid: Custom constructors are not supported."""

        when: extract CustomConstructorInSuperClass
        then: ex = thrown InvalidManagedTypeException
        ex.message == """Type ${fullyQualifiedNameOf(CustomConstructorInSuperClass)} is not a valid managed type:
- Constructor DefaultStructBindingsStoreTest.SuperConstructorWithArguments(java.lang.String) is not valid: Custom constructors are not supported."""
    }

    static abstract class MultipleProblemsSuper {
        private String field1
        MultipleProblemsSuper(String s) {}
        private String getPrivate() { field1 }
    }

    @Managed
    static class MultipleProblems<T extends List<?>> extends MultipleProblemsSuper {
        private String field2
        MultipleProblems(String s) { super(s) }
    }

    def "collects all problems for a type"() {
        when: extract MultipleProblems
        then: def ex = thrown InvalidManagedTypeException
        ex.message == """Type ${fullyQualifiedNameOf(MultipleProblems)} is not a valid managed type:
- Must be defined as an interface or an abstract class.
- Cannot be a parameterized type.
- Constructor DefaultStructBindingsStoreTest.MultipleProblems(java.lang.String) is not valid: Custom constructors are not supported.
- Field field2 is not valid: Fields must be static final.
- Constructor DefaultStructBindingsStoreTest.MultipleProblemsSuper(java.lang.String) is not valid: Custom constructors are not supported.
- Field DefaultStructBindingsStoreTest.MultipleProblemsSuper.field1 is not valid: Fields must be static final.
- Method DefaultStructBindingsStoreTest.MultipleProblemsSuper.getPrivate() is not a valid method: Protected and private methods are not supported."""
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
