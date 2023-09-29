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
}
