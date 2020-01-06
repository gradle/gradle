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
import org.gradle.internal.state.ManagedFactory

class DefaultPropertyTest extends PropertySpec<String> {
    DefaultProperty<String> property() {
        return new DefaultProperty<String>(String)
    }

    @Override
    DefaultProperty<String> propertyWithNoValue() {
        return property()
    }

    @Override
    DefaultProperty<String> propertyWithDefaultValue() {
        return property()
    }

    @Override
    DefaultProperty<String> providerWithValue(String value) {
        def p = property()
        p.set(value)
        return p
    }

    @Override
    Class<String> type() {
        return String
    }

    @Override
    String someValue() {
        return "value1"
    }

    @Override
    String someOtherValue() {
        return "value2"
    }

    @Override
    ManagedFactory managedFactory() {
        return new ManagedFactories.PropertyManagedFactory()
    }

    def "has no value by default"() {
        expect:
        def property = property()
        !property.present
        property.getOrNull() == null
        property.getOrElse(someValue()) == someValue()

        when:
        property.get()

        then:
        def e = thrown(IllegalStateException)
        e.message == "No value has been specified for ${displayName}."
    }

    def "toString() does not realize value"() {
        given:
        def propertyWithBadValue = property()
        propertyWithBadValue.set(new DefaultProvider<String>({
            assert false : "never called"
        }))

        expect:
        propertyWithBadValue.toString() == "property(class java.lang.String, map(provider(?)))"
        providerWithNoValue().toString() == "property(class java.lang.String, undefined)"
    }

    def "can set to null value to discard value"() {
        given:
        def property = property()
        property.set(someValue())
        property.set(null)

        expect:
        !property.present
        property.getOrNull() == null
        property.getOrElse(someValue()) == someValue()
        property.getOrElse(null) == null
    }

    def "fails when value is set using incompatible type"() {
        def property = new DefaultProperty<Boolean>(Boolean)

        when:
        property.set(12)

        then:
        IllegalArgumentException e = thrown()
        e.message == "Cannot set the value of a property of type java.lang.Boolean using an instance of type java.lang.Integer."

        and:
        !property.present
    }

    def "fails when value set using provider whose type is known to be incompatible"() {
        def property = new DefaultProperty<Boolean>(Boolean)
        def other = new DefaultProperty<Number>(Number)

        when:
        property.set(other)

        then:
        IllegalArgumentException e = thrown()
        e.message == "Cannot set the value of a property of type java.lang.Boolean using a provider of type java.lang.Number."

        and:
        !property.present
    }

    def "can set value to a provider whose type is compatible"() {
        def supplier = Mock(ScalarSupplier)
        def provider = Mock(ProviderInternal)

        given:
        provider.asSupplier(_, _, _) >> supplier
        supplier.get(_) >>> [1, 2, 3]

        def property = new DefaultProperty<Number>(Number)

        when:
        property.set(provider)

        then:
        property.get() == 1
        property.get() == 2
        property.get() == 3
    }

    def "fails when provider produces an incompatible value"() {
        def provider = new DefaultProvider({ 12 })

        given:
        def property = new DefaultProperty<Boolean>(Boolean)
        property.set(provider)

        when:
        property.get()

        then:
        def e = thrown(IllegalArgumentException)
        e.message == 'Cannot get the value of a property of type java.lang.Boolean as the provider associated with this property returned a value of type java.lang.Integer.'

        when:
        property.getOrNull()

        then:
        def e2 = thrown(IllegalArgumentException)
        e2.message == 'Cannot get the value of a property of type java.lang.Boolean as the provider associated with this property returned a value of type java.lang.Integer.'
    }

    def "fails when convention is set using incompatible value"() {
        def property = new DefaultProperty<Boolean>(Boolean)

        when:
        property.convention(12)

        then:
        IllegalArgumentException e = thrown()
        e.message == "Cannot set the value of a property of type java.lang.Boolean using an instance of type java.lang.Integer."

        and:
        !property.present
    }

    def "fails when convention is set using provider whose value is known to be incompatible"() {
        def property = new DefaultProperty<Boolean>(Boolean)
        def other = new DefaultProperty<Number>(Number)

        when:
        property.convention(other)

        then:
        IllegalArgumentException e = thrown()
        e.message == "Cannot set the value of a property of type java.lang.Boolean using a provider of type java.lang.Number."

        and:
        !property.present
    }

    def "fails when convention is set using provider that returns incompatible value"() {
        def provider = new DefaultProvider({ 12 })

        given:
        def property = new DefaultProperty<Boolean>(Boolean)
        property.convention(provider)

        when:
        property.get()

        then:
        def e = thrown(IllegalArgumentException)
        e.message == 'Cannot get the value of a property of type java.lang.Boolean as the provider associated with this property returned a value of type java.lang.Integer.'
    }

    def "mapped provider is live"() {
        def transformer = Mock(Transformer)
        def provider = provider("abc")

        def property = new DefaultProperty<String>(String)

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
        0 * _

        when:
        def r2 = p.get()

        then:
        r2 == "cba"
        1 * transformer.transform("abc") >> "cba"
        0 * _
    }
}
