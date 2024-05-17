/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.api.internal.provider

import org.gradle.api.Task

abstract class AbstractPropertySpec<T> extends PropertySpec<T> {
    @Override
    AbstractProperty<T, ? extends ValueSupplier> providerWithValue(T value) {
        return propertyWithNoValue().value(value)
    }

    @Override
    AbstractProperty<T, ? extends ValueSupplier> providerWithNoValue() {
        return propertyWithNoValue()
    }

    @Override
    abstract AbstractProperty<T, ? extends ValueSupplier> propertyWithNoValue()

    AbstractProperty<T, ? extends ValueSupplier> propertyWithValue(T value) {
        return propertyWithNoValue().value(value)
    }

    def "finalization checking works empty providers"() {
        given:
        def property = propertyWithNoValue()

        expect:
        !property.isPresent()
        !property.isFinalized()
    }

    def "finalization checking works with simple values"() {
        given:
        def property = propertyWithValue(someValue())

        expect:
        property.isPresent()
        !property.isFinalized()

        when:
        property.set(someOtherValue())

        then:
        !property.isFinalized()

        when:
        property.finalizeValue()

        then:
        property.isFinalized()
    }

    def "finalization checking works with providers"() {
        given:
        def property = propertyWithValue(someValue())

        expect:
        property.isPresent()
        !property.isFinalized()

        when:
        property.set(providerWithValue(someOtherValue()))

        then:
        !property.isFinalized()

        when:
        property.finalizeValue()

        then:
        property.isFinalized()
    }

    def "finalization checking works with conventions"() {
        given:
        def property = propertyWithNoValue()
        property.convention(someValue())

        expect:
        property.isPresent()
        !property.isFinalized()

        when:
        property.finalizeValue()

        then:
        property.isFinalized()
    }

    def "finalization checking works with finalizeOnRead and isPresent"() {
        given:
        def property = propertyWithValue(someValue())
        property.finalizeValueOnRead()

        when:
        property.isPresent()

        then:
        property.isFinalized()
    }

    def "finalization checking works with finalizeOnRead and get"() {
        given:
        def property = propertyWithValue(someValue())
        property.finalizeValueOnRead()

        when:
        property.get()

        then:
        property.isFinalized()
    }

    def "can obtain shallow copy of property"() {
        given:
        def property = propertyWithValue(someValue())

        when:
        def copy = property.shallowCopy()

        then:
        copy.orNull == someValue()
    }

    def "shallow copy of property does not follow changes to original"() {
        given:
        def property = propertyWithValue(someValue())

        when:
        def copy = property.shallowCopy()
        property.set(someOtherValue())

        then:
        copy.orNull != property.orNull
        copy.orNull == someValue()
    }

    def "shallow copy shares the type with the property"() {
        given:
        def property = propertyWithValue(someValue())

        expect:
        property.type == property.shallowCopy().type
    }

    def "shallow copy inherits dependencies of the original"() {
        given:
        def task = Mock(Task)
        def provider = supplierWithProducer(task)
        def property = propertyWithNoValue()
        property.set(provider)

        expect:
        assertHasProducer(property.shallowCopy(), task)
    }

    def "shallow copy does not follow changes to dependencies of the original"() {
        given:
        def task = Mock(Task)
        def provider = supplierWithProducer(task)
        def property = propertyWithNoValue()
        property.set(provider)

        when:
        def copy = property.shallowCopy()
        property.set(someOtherValue())

        then:
        assertHasProducer(copy, task)
        assertHasNoProducer(property)
    }

    def "shallow copy does not inherit producer from the original"() {
        given:
        def task = Mock(Task)
        def property = propertyWithNoValue()
        property.attachProducer(owner(task))

        expect:
        assertHasNoProducer(property.shallowCopy())
    }

    def "shallow copy reflects changes to the value"() {
        given:
        def innerProperty = propertyWithValue(someValue())
        def property = propertyWithNoValue()
        property.set(innerProperty)

        when:
        def copy = property.shallowCopy()
        innerProperty.set(someOtherValue())

        then:
        copy.orNull == innerProperty.orNull
    }

    def "shallow copy is fixed if the underlying provider is fixed"() {
        given:
        def property = propertyWithValue(someValue())

        when:
        def copy = property.shallowCopy()

        then:
        !copy.calculateExecutionTimeValue().isChangingValue()
    }

    def "shallow copy is changing if the underlying provider is changing"() {
        given:
        def upstream = supplierWithChangingExecutionTimeValues(someValue())
        def property = propertyWithNoValue()
        property.set(upstream)

        when:
        def copy = property.shallowCopy()

        then:
        copy.calculateExecutionTimeValue().isChangingValue()
    }

    def "shallow copy copies the convention if the property has no explicit value"() {
        given:
        def property = propertyWithNoValue()
        property.convention(someValue())

        expect:
        property.shallowCopy().orNull == someValue()
    }

    def "shallow copy is not affected by changes to original convention"() {
        given:
        def property = propertyWithNoValue()
        property.convention(someValue())

        when:
        def copy = property.shallowCopy()
        property.convention(someOtherValue())

        then:
        copy.orNull == someValue()
    }

    def "obtaining value of the shallow copy does not finalize property"() {
        given:
        def property = propertyWithValue(someValue())
        property.finalizeValueOnRead()

        when:
        property.shallowCopy().get()

        then:
        !property.finalized
    }
}
