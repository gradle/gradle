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
        bindings.allViewSchemas*.type*.rawClass as List == [Object]
        bindings.delegateSchema == null
        bindings.managedProperties.isEmpty()
        bindings.viewBindings.isEmpty()
        bindings.delegateBindings.isEmpty()
    }

    static abstract class TypeWithAbstractProperty {
        abstract int getZ()
        abstract void setZ(int value)
    }

    def "extracts simple type with a managed property"() {
        def bindings = extract(TypeWithAbstractProperty)
        expect:
        bindings.allViewSchemas*.type*.rawClass as List == [TypeWithAbstractProperty]
        bindings.delegateSchema == null
        bindings.managedProperties.values()*.name as List == ["z"]
        bindings.viewBindings.isEmpty()
        bindings.delegateBindings.isEmpty()
    }

    static abstract class TypeWithImplementedProperty {
        int z
    }

    def "extracts simple type with an implemented property"() {
        def bindings = extract(TypeWithImplementedProperty)
        expect:
        bindings.allViewSchemas*.type*.rawClass as List == [TypeWithImplementedProperty]
        bindings.delegateSchema == null
        bindings.managedProperties.isEmpty()
        bindings.viewBindings*.source*.name == ["getZ", "setZ"]
        bindings.delegateBindings.isEmpty()
    }

    static class DelegateTypeWithImplementedProperty {
        int z
    }

    def "extracts simple type with a delegated property"() {
        def bindings = extract(TypeWithAbstractProperty, DelegateTypeWithImplementedProperty)
        expect:
        bindings.allViewSchemas*.type*.rawClass as List == [TypeWithAbstractProperty]
        bindings.delegateSchema.type.rawClass == DelegateTypeWithImplementedProperty
        bindings.managedProperties.isEmpty()
        bindings.viewBindings.isEmpty()
        bindings.delegateBindings*.source*.name == ["getZ", "setZ"]
        bindings.delegateBindings*.target*.name == ["getZ", "setZ"]
    }

    def "fails when implemented property is present in delegate"() {
        when:
        extract(TypeWithImplementedProperty, DelegateTypeWithImplementedProperty)
        then:
        def ex = thrown Exception
        ex.message.contains "Method 'public int ${TypeWithImplementedProperty.name}.getZ()' is both implemented by the view and the delegate type 'public int ${DelegateTypeWithImplementedProperty.name}.getZ()'"
    }

    static abstract class TypeWithAbstractWriteOnlyProperty {
        abstract void setZ(int value)
    }

    def "fails when abstract property has only setter"() {
        when:
        extract(TypeWithAbstractWriteOnlyProperty)
        then:
        def ex = thrown Exception
        ex.message.contains "Managed property 'z' must both have an abstract getter as well as a setter."
    }

    static abstract class TypeWithInconsistentPropertyType {
        abstract String getZ()
        abstract void setZ(int value)
    }

    def "fails when property has inconsistent type"() {
        when:
        extract(TypeWithInconsistentPropertyType)
        then:
        def ex = thrown Exception
        ex.message.contains "Managed property 'z' must have a consistent type, but it's defined as String, int."
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
        bindings.allViewSchemas*.type*.rawClass as List == [OverloadingNumber]
        bindings.delegateSchema.type.rawClass == OverloadingIntegerImpl
        bindings.managedProperties.isEmpty()
        bindings.viewBindings.isEmpty()
        bindings.delegateBindings*.source*.name == ["getValue"]
        bindings.delegateBindings*.source*.method*.returnType == [Number]
        bindings.delegateBindings*.target*.name == ["getValue"]
        bindings.delegateBindings*.target*.method*.returnType == [Integer]
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
}
