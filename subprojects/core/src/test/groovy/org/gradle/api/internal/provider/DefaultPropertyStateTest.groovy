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

import org.gradle.api.Transformer
import org.gradle.api.provider.PropertyState
import org.gradle.api.provider.Provider
import spock.lang.Specification
import spock.lang.Unroll

class DefaultPropertyStateTest extends Specification {

    @Unroll
    def "can compare string representation with other instance returning value #value"() {
        given:
        boolean immutablePropertyStateValue1 = true
        def property1 = createBooleanPropertyState(immutablePropertyStateValue1)
        def property2 = createBooleanPropertyState(value)

        expect:
        (property1.toString() == property2.toString()) == stringRepresentation
        property1.toString() == "value: $immutablePropertyStateValue1"
        property2.toString() == "value: $value"

        where:
        value | stringRepresentation
        true  | true
        false | false
        null  | false
    }

    def "fails when get method is called when the property has no value"() {
        def property = new DefaultPropertyState<String>(String)

        when:
        property.get()

        then:
        def t = thrown(IllegalStateException)
        t.message == "No value has been specified for this provider."

        when:
        property.set("123")

        then:
        property.get()

        when:
        property.set(null)
        property.get()

        then:
        t = thrown(IllegalStateException)
        t.message == "No value has been specified for this provider."
    }

    def "returns value or null for get or null method"() {
        when:
        def property = new DefaultPropertyState<String>(String)

        then:
        !property.isPresent()
        property.getOrNull() == null
        property.getOrElse("123") == "123"

        when:
        property.set("abc")

        then:
        property.isPresent()
        property.getOrNull() == "abc"
        property.getOrElse("123") == "abc"
    }

    def "fails when value is set using incompatible type"() {
        def property = new DefaultPropertyState<Boolean>(Boolean)

        when:
        property.set(12)

        then:
        IllegalArgumentException e = thrown()
        e.message == "Cannot set the value of a property of type java.lang.Boolean using an instance of type java.lang.Integer."

        and:
        !property.present
    }

    def "fails when value set using provider whose type is known to be incompatible"() {
        def property = new DefaultPropertyState<Boolean>(Boolean)
        def other = new DefaultPropertyState<Number>(Number)

        when:
        property.set(other)

        then:
        IllegalArgumentException e = thrown()
        e.message == "Cannot set the value of a property of type java.lang.Boolean using a provider of type java.lang.Number."

        and:
        !property.present
    }

    def "can set value to a provider whose type is not known"() {
        def provider = Mock(ProviderInternal)

        given:
        provider.get() >>> ["a", "b", "c"]
        provider.map(_) >> provider

        def propertyState = new DefaultPropertyState<String>(String)

        when:
        propertyState.set(provider)

        then:
        propertyState.get() == "a"
        propertyState.get() == "b"
        propertyState.get() == "c"
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
        def transform = null

        given:
        provider.map(_) >> { transform = it[0]; provider }
        provider.get() >> { transform.transform(12) }
        provider.getOrNull() >> { transform.transform(12) }

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
        provider.type >> Boolean

        def property = new DefaultPropertyState<Boolean>(Boolean)
        property.set(provider)

        when:
        property.get()

        then:
        1 * provider.get() >> true
        0 * _
    }

    def "getOrNull only calls provider once"() {
        given:
        def provider = Mock(ProviderInternal)
        provider.type >> Boolean

        def property = new DefaultPropertyState<Boolean>(Boolean)
        property.set(provider)

        when:
        property.getOrNull()

        then:
        1 * provider.getOrNull() >> true
        0 * _
    }

    def "getOrElse only calls provider once"() {
        given:
        def provider = Mock(ProviderInternal)
        provider.type >> Boolean

        def property = new DefaultPropertyState<Boolean>(Boolean)
        property.set(provider)

        when:
        property.getOrElse(false)

        then:
        1 * provider.getOrNull() >> true
        0 * _
    }

    def "mapped provider is live"() {
        def transformer = Mock(Transformer)
        def provider = Mock(ProviderInternal)

        def property = new DefaultPropertyState<String>(String)

        when:
        def p = property.map(transformer)

        then:
        !p.present
        0 * _

        when:
        property.set("123")

        then:
        p.present
        0 * _

        when:
        def r = p.get()

        then:
        r == "321"
        1 * transformer.transform("123") >> "321"
        0 * _

        when:
        property.set(provider)

        then:
        _ * provider.type >> String
        0 * _

        when:
        def r2 = p.get()

        then:
        r2 == "cba"
        1 * provider.get() >> "abc"
        1 * transformer.transform("abc") >> "cba"
        0 * _
    }

    def "mapped provider fails when transformer returns null"() {
        def transformer = Mock(Transformer)
        transformer.transform(_) >> null

        def property = new DefaultPropertyState<String>(String)
        property.set("123")
        def p = property.map(transformer)

        when:
        p.get()

        then:
        def e = thrown(IllegalStateException)
        e.message == 'Transformer for this provider returned a null value.'
    }

    private PropertyState<Boolean> createBooleanPropertyState(Boolean value) {
        PropertyState<Boolean> propertyState = new DefaultPropertyState<Boolean>(Boolean)
        propertyState.set(value)
        propertyState
    }
}
