/*
 * Copyright 2017 the original author or authors.
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

import org.gradle.api.provider.PropertyState
import org.gradle.api.provider.Provider
import spock.lang.Specification
import spock.lang.Unroll

import static org.gradle.api.internal.provider.DefaultProvider.NON_NULL_VALUE_EXCEPTION_MESSAGE

class DefaultPropertyStateTest extends Specification {

    @Unroll
    def "can compare string representation with other instance returning value #value"() {
        given:
        boolean immutablePropertyStateValue1 = true
        def propertyState1 = createBooleanPropertyState(immutablePropertyStateValue1)
        def propertyState2 = createBooleanPropertyState(value)

        expect:
        (propertyState1.toString() == propertyState2.toString()) == stringRepresentation
        propertyState1.toString() == "value: $immutablePropertyStateValue1"
        propertyState2.toString() == "value: $value"

        where:
        value | stringRepresentation
        true  | true
        false | false
        null  | false
    }

    def "fails when get method is called when the property has no value"() {
        def propertyState = new DefaultPropertyState<Boolean>(Boolean)

        when:
        propertyState.get()

        then:
        def t = thrown(IllegalStateException)
        t.message == NON_NULL_VALUE_EXCEPTION_MESSAGE

        when:
        propertyState.set(null)
        propertyState.get()

        then:
        t = thrown(IllegalStateException)
        t.message == NON_NULL_VALUE_EXCEPTION_MESSAGE

        when:
        propertyState.set(true)

        then:
        propertyState.get()
    }

    def "returns value or null for get or null method"() {
        when:
        def propertyState = new DefaultPropertyState<Boolean>(Boolean)

        then:
        !propertyState.isPresent()
        propertyState.getOrNull() == null

        when:
        propertyState.set(true)

        then:
        propertyState.isPresent()
        propertyState.getOrNull()
    }

    def "fails when value is set using incompatible type"() {
        def propertyState = new DefaultPropertyState<Boolean>(Boolean)

        when:
        propertyState.set(12)

        then:
        IllegalArgumentException e = thrown()
        e.message == "Cannot set the value of a property of type java.lang.Boolean using an instance of type java.lang.Integer."

        and:
        !propertyState.present
    }

    def "fails when value set using provider whose type is known to be incompatible"() {
        def propertyState = new DefaultPropertyState<Boolean>(Boolean)
        def other = new DefaultPropertyState<Number>(Number)

        when:
        propertyState.set(other)

        then:
        IllegalArgumentException e = thrown()
        e.message == "Cannot set the value of a property of type java.lang.Boolean using a provider of type java.lang.Number."

        and:
        !propertyState.present
    }

    def "can set value to a provider whose type is not known"() {
        def provider = Mock(ProviderInternal)

        given:
        provider.getOrNull() >>> [true, false, true]

        def propertyState = new DefaultPropertyState<Boolean>(Boolean)

        when:
        propertyState.set(provider)

        then:
        propertyState.get()
        !propertyState.get()
        propertyState.get()
    }

    def "can set value to a provider whose type is compatible"() {
        def provider = Mock(ProviderInternal)

        given:
        provider.getType() >> Integer
        provider.get() >>> [1, 2, 3]

        def propertyState = new DefaultPropertyState<Number>(Number)

        when:
        propertyState.set(provider)

        then:
        propertyState.get() == 1
        propertyState.get() == 2
        propertyState.get() == 3
    }

    def "fails when provider produces an incompatible value"() {
        def provider = Mock(ProviderInternal)

        given:
        provider.get() >> 12
        provider.getOrNull() >> 12

        def propertyState = new DefaultPropertyState<Boolean>(Boolean)
        propertyState.set(provider)

        when:
        propertyState.get()

        then:
        def e = thrown(IllegalArgumentException)
        e.message == 'Cannot get the value of a property of type java.lang.Boolean as the provider associated with this property returned a value of type java.lang.Integer.'

        when:
        propertyState.getOrNull()

        then:
        def e2 = thrown(IllegalArgumentException)
        e2.message == 'Cannot get the value of a property of type java.lang.Boolean as the provider associated with this property returned a value of type java.lang.Integer.'
    }

    def "does not allow a null provider"() {
        given:
        def propertyState = new DefaultPropertyState<Boolean>(Boolean)

        when:
        propertyState.set((Provider)null)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == 'Cannot set the value of a property using a null provider.'
    }

    def "get only calls provider once"() {
        given:
        def provider = Mock(ProviderInternal)
        def propertyState = new DefaultPropertyState<Boolean>(Boolean)
        propertyState.set(provider)

        when:
        propertyState.get()

        then:
        1 * provider.getOrNull() >> true
        0 * _
    }

    def "getOrNull only calls provider once"() {
        given:
        def provider = Mock(ProviderInternal)
        def propertyState = new DefaultPropertyState<Boolean>(Boolean)
        propertyState.set(provider)

        when:
        propertyState.getOrNull()

        then:
        1 * provider.getOrNull() >> true
        0 * _
    }

    private PropertyState<Boolean> createBooleanPropertyState(Boolean value) {
        PropertyState<Boolean> propertyState = new DefaultPropertyState<Boolean>(Boolean)
        propertyState.set(value)
        propertyState
    }
}
