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
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider

abstract class PropertySpec<T> extends ProviderSpec<T> {
    abstract Property<T> property()

    abstract T someValue()

    abstract T someOtherValue()

    abstract Class<T> type()

    def "can set to null value to remove value"() {
        given:
        def property = property()
        property.set(null)

        expect:
        !property.present
        property.getOrNull() == null
        property.getOrElse(someValue()) == someValue()
        property.getOrElse(null) == null
    }

    def "cannot get value when it has none"() {
        given:
        def property = property()
        property.set(null)

        when:
        property.get()

        then:
        def t = thrown(IllegalStateException)
        t.message == "No value has been specified for this provider."

        when:
        property.set(someValue())
        property.get()

        then:
        noExceptionThrown()
    }

    def "can set value"() {
        given:
        def property = property()
        property.set(someValue())

        expect:
        property.present
        property.get() == someValue()
        property.getOrNull() == someValue()
        property.getOrElse(someOtherValue()) == someValue()
        property.getOrElse(null) == someValue()
    }

    def "can set value using provider"() {
        def provider = Mock(ProviderInternal)

        given:
        provider.type >> type()

        def property = property()
        property.set(provider)

        when:
        def r = property.present

        then:
        r
        1 * provider.present >> true
        0 * _

        when:
        def r2 = property.get()

        then:
        r2 == someValue()
        1 * provider.get() >> someValue()
        0 * _

        when:
        def r3 = property.getOrNull()

        then:
        r3 == someOtherValue()
        1 * provider.getOrNull() >> someOtherValue()
        0 * _

        when:
        def r4 = property.getOrElse(someOtherValue())

        then:
        r4 == someValue()
        1 * provider.getOrNull() >> someValue()
        0 * _
    }

    def "tracks value of provider"() {
        def provider = Mock(ProviderInternal)

        given:
        provider.type >> type()
        provider.get() >>> [someValue(), someOtherValue(), someValue()]

        def property = property()
        property.set(provider)

        expect:
        property.get() == someValue()
        property.get() == someOtherValue()
        property.get() == someValue()
    }

    def "does not allow a null provider"() {
        given:
        def property = property()

        when:
        property.set((Provider) null)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == 'Cannot set the value of a property using a null provider.'
    }

    def "can set untyped using null"() {
        given:
        def property = property()
        property.setFromAnyValue(null)

        expect:
        !property.present
        property.getOrNull() == null
        property.getOrElse(someOtherValue()) == someOtherValue()
    }

    def "can set untyped using value"() {
        given:
        def property = property()
        property.setFromAnyValue(someValue())

        expect:
        property.present
        property.get() == someValue()
        property.getOrNull() == someValue()
        property.getOrElse(someOtherValue()) == someValue()
        property.getOrElse(null) == someValue()
    }

    def "fails when untyped value is set using incompatible type"() {
        def property = property()

        when:
        property.setFromAnyValue(new Thing())

        then:
        def e = thrown IllegalArgumentException
        e.message == "Cannot set the value of a property of type ${type().name} using an instance of type ${Thing.name}."
    }

    def "can set untyped using provider"() {
        def provider = Stub(ProviderInternal)

        given:
        provider.type >> type()
        provider.get() >> someValue()
        provider.present >> true

        def property = property()
        property.setFromAnyValue(provider)

        when:
        def r = property.present
        def r2 = property.get()

        then:
        r
        r2 == someValue()
    }

    def "can map value using a transformation"() {
        def transformer = Mock(Transformer)
        def property = property()

        when:
        def provider = property.map(transformer)

        then:
        0 * _

        when:
        property.set(someValue())
        def r1 = provider.get()

        then:
        r1 == someOtherValue()
        1 * transformer.transform(someValue()) >> someOtherValue()
        0 * _

        when:
        def r2 = provider.get()

        then:
        r2 == someValue()
        1 * transformer.transform(someValue()) >> someValue()
        0 * _
    }

    def "transformation is provided with the current value of the property each time the value is queried"() {
        def transformer = Mock(Transformer)
        def property = property()

        when:
        def provider = property.map(transformer)

        then:
        0 * _

        when:
        property.set(someValue())
        def r1 = provider.get()

        then:
        r1 == 123
        1 * transformer.transform(someValue()) >> 123
        0 * _

        when:
        property.set(someOtherValue())
        def r2 = provider.get()

        then:
        r2 == 456
        1 * transformer.transform(someOtherValue()) >> 456
        0 * _
    }

    def "can map value to some other type"() {
        def transformer = Mock(Transformer)
        def property = property()

        when:
        def provider = property.map(transformer)

        then:
        0 * _

        when:
        property.set(someValue())
        def r1 = provider.get()

        then:
        r1 == 12
        1 * transformer.transform(someValue()) >> 12
        0 * _

        when:
        def r2 = provider.get()

        then:
        r2 == 10
        1 * transformer.transform(someValue()) >> 10
        0 * _
    }

    def "mapped provider has no value when property has no value and transformer is not invoked"() {
        def transformer = Mock(Transformer)
        def property = property()
        property.set(null)

        when:
        def provider = property.map(transformer)

        then:
        !provider.present
        provider.getOrNull() == null
        provider.getOrElse(someOtherValue()) == someOtherValue()
        0 * _

        when:
        provider.get()

        then:
        def e = thrown(IllegalStateException)
        e.message == 'No value has been specified for this provider.'
    }

    def "mapped provider fails when transformer returns null"() {
        def transformer = Mock(Transformer)
        transformer.transform(_) >> null

        def property = property()
        property.set(someValue())
        def p = property.map(transformer)

        when:
        p.get()

        then:
        def e = thrown(IllegalStateException)
        e.message == 'Transformer for this provider returned a null value.'
    }

    static class Thing { }
}
