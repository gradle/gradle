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
        PropertyState<Boolean> propertyState1 = createBooleanPropertyState(immutablePropertyStateValue1)
        PropertyState<Boolean> propertyState2 = createBooleanPropertyState(value)

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
        when:
        PropertyState<Boolean> propertyState = createBooleanPropertyState()
        propertyState.get()

        then:
        def t = thrown(IllegalStateException)
        t.message == NON_NULL_VALUE_EXCEPTION_MESSAGE

        when:
        propertyState = createBooleanPropertyState(null)
        propertyState.get()

        then:
        t = thrown(IllegalStateException)
        t.message == NON_NULL_VALUE_EXCEPTION_MESSAGE

        when:
        propertyState = createBooleanPropertyState(true)
        def value = propertyState.get()

        then:
        value
    }

    def "returns value or null for get or null method"() {
        when:
        PropertyState<Boolean> propertyState = createBooleanPropertyState()

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
        when:
        PropertyState<Boolean> propertyState = createBooleanPropertyState()
        propertyState.set(12)

        then:
        IllegalArgumentException e = thrown()
        e.message == "Cannot set the value of a property of type java.lang.Boolean using an instance of type java.lang.Integer."

        and:
        !propertyState.present
    }

    def "can set value of external provider"() {
        def provider = Mock(Provider)

        given:
        PropertyState<Boolean> propertyState = createBooleanPropertyState()

        when:
        Boolean value = propertyState.getOrNull()

        then:
        value == null

        when:
        propertyState.set(provider)
        value = propertyState.getOrNull()

        then:
        1 * provider.getOrNull() >> true
        value == true
    }

    def "does not allow a null provider"() {
        given:
        PropertyState<Boolean> propertyState = createBooleanPropertyState()
        when:
        propertyState.set((Provider)null)
        then:
        thrown(NullPointerException)
    }

    def "get only calls provider once"() {
        given:
        def provider = Mock(Provider)
        PropertyState<Boolean> propertyState = createBooleanPropertyState()
        propertyState.set(provider)
        when:
        propertyState.get()
        then:
        1 * provider.get() >> true
        0 * _
    }

    def "getOrNull only calls provider once"() {
        given:
        def provider = Mock(Provider)
        PropertyState<Boolean> propertyState = createBooleanPropertyState()
        propertyState.set(provider)
        when:
        propertyState.getOrNull()
        then:
        1 * provider.getOrNull() >> true
        0 * _
    }

    private PropertyState<Boolean> createBooleanPropertyState() {
        new DefaultPropertyState<Boolean>(Boolean)
    }

    private PropertyState<Boolean> createBooleanPropertyState(Boolean value) {
        PropertyState<Boolean> propertyState = createBooleanPropertyState()
        propertyState.set(value)
        propertyState
    }
}
