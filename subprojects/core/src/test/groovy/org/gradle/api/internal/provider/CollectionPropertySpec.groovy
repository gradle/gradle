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

import com.google.common.collect.ImmutableCollection
import org.gradle.api.Transformer
import org.gradle.api.provider.Provider
import spock.lang.Unroll

abstract class CollectionPropertySpec<C extends Collection<String>> extends PropertySpec<C> {
    def property = property()

    protected void assertValueIs(Collection<String> expected) {
        def actual = property.get()
        assert actual instanceof ImmutableCollection
        assert immutableCollectionType.isInstance(actual)
        assert actual == toImmutable(expected)
        assert property.present
    }

    protected abstract C toImmutable(Collection<String> values)

    protected abstract C toMutable(Collection<String> values)

    protected abstract Class<? extends ImmutableCollection<?>> getImmutableCollectionType()

    def "defaults to empty collection"() {
        expect:
        property.present
        property.get() as List == []
        property.getOrNull() as List == []
        property.getOrElse(someValue()) as List == []
    }

    def "can set value to empty collection"() {
        expect:
        property.set(toMutable([]))
        assertValueIs([])
    }

    def "returns immutable copy of value"() {
        expect:
        property.set(toMutable(["abc"]))
        assertValueIs(["abc"])
    }

    def "queries initial value for every call to get()"() {
        expect:
        def initialValue = toMutable(["abc"])
        property.set(initialValue)
        assertValueIs(["abc"])
        initialValue.add("added")
        assertValueIs(["abc", "added"])
    }

    def "queries underlying provider for every call to get()"() {
        def provider = Stub(ProviderInternal)
        provider.get() >>> [["123"], ["abc"]]
        provider.present >> true

        expect:
        property.set(provider)
        assertValueIs(["123"])
        assertValueIs(["abc"])
    }

    def "mapped provider is presented with immutable copy of value"() {
        given:
        property.set(toMutable(["abc"]))
        def provider = property.map(new Transformer() {
            def transform(def value) {
                assert immutableCollectionType.isInstance(value)
                assert value == toImmutable(["abc"])
                return toMutable(["123"])
            }
        })

        expect:
        def actual = provider.get()
        actual == toMutable(["123"])
    }

    def "can add values to property with all methods"() {
        expect:
        property.add("abc")
        assertValueIs(["abc"])

        property.add(Providers.of("def"))
        assertValueIs(["abc", "def"])

        property.addAll(Providers.of(["hij"]))
        assertValueIs(["abc", "def", "hij"])

        property.add("klm")
        assertValueIs(["abc", "def", "hij", "klm"])

        property.add(Providers.of("nop"))
        assertValueIs(["abc", "def", "hij", "klm", "nop"])
    }

    def "can add values to property with initial value"() {
        property.set(toMutable(["123"]))

        expect:
        property.add("abc")
        assertValueIs(["123", "abc"])

        property.add(Providers.of("def"))
        assertValueIs(["123", "abc", "def"])

        property.addAll(Providers.of(["hij"]))
        assertValueIs(["123", "abc", "def", "hij"])

        property.add("klm")
        assertValueIs(["123", "abc", "def", "hij", "klm"])

        property.add(Providers.of("nop"))
        assertValueIs(["123", "abc", "def", "hij", "klm", "nop"])
    }

    def "appends value during `add` to property"() {
        expect:
        property.add("123")
        property.add("456")
        assertValueIs(["123", "456"])
    }

    def "appends value from provider during `add` to property"() {
        expect:
        property.add(Providers.of("123"))
        property.add(Providers.of("456"))
        assertValueIs(["123", "456"])
    }

    @Unroll
    def "appends values from provider during `addAll` to property"() {
        expect:
        property.addAll(value)
        assertValueIs(expectedValue)

        where:
        value                               | expectedValue
        Providers.of([])                    | []
        Providers.of(["aaa"])               | ["aaa"]
        Providers.of(["aaa", "bbb", "ccc"]) | ["aaa", "bbb", "ccc"]
    }

    def "providers only called once per query"() {
        def addProvider = Mock(Provider)
        def addAllProvider = Mock(Provider)

        given:
        property.add(addProvider)
        property.addAll(addAllProvider)

        when:
        property.present

        then:
        1 * addProvider.present >> true
        1 * addAllProvider.present >> true
        0 * _

        when:
        property.get()

        then:
        1 * addProvider.get() >> "123"
        1 * addAllProvider.get() >> ["abc"]
        0 * _

        when:
        property.getOrNull()

        then:
        1 * addProvider.getOrNull() >> "123"
        1 * addAllProvider.getOrNull() >> ["abc"]
        0 * _
    }

    def "property has no value when set to null and other values appended"() {
        given:
        property.set(null)
        property.add("123")
        property.add(Providers.of("456"))
        property.addAll(Providers.of(["789"]))

        expect:
        !property.present
        property.getOrNull() == null
        property.getOrElse(toMutable(["other"])) == toMutable(["other"])

        when:
        property.get()

        then:
        def e = thrown(IllegalStateException)
        e.message == Providers.NULL_VALUE
    }

    def "property has no value when set to provider with no value and other values appended"() {
        given:
        property.set(Providers.notDefined())
        property.add("123")
        property.add(Providers.of("456"))
        property.addAll(Providers.of(["789"]))

        expect:
        !property.present
        property.getOrNull() == null
        property.getOrElse(toMutable(["other"])) == toMutable(["other"])

        when:
        property.get()

        then:
        def e = thrown(IllegalStateException)
        e.message == Providers.NULL_VALUE
    }

    def "property has no value when adding an element provider with no value"() {
        given:
        property.set(toMutable(["123"]))
        property.add("456")
        property.add(Providers.notDefined())

        expect:
        !property.present
        property.getOrNull() == null
        property.getOrElse(toMutable(["other"])) == toMutable(["other"])

        when:
        property.get()

        then:
        def e = thrown(IllegalStateException)
        e.message == Providers.NULL_VALUE
    }

    def "property has no value when adding an collection provider with no value"() {
        given:
        property.set(toMutable(["123"]))
        property.add("456")
        property.addAll(Providers.notDefined())

        expect:
        !property.present
        property.getOrNull() == null
        property.getOrElse(toMutable(["other"])) == toMutable(["other"])

        when:
        property.get()

        then:
        def e = thrown(IllegalStateException)
        e.message == Providers.NULL_VALUE
    }

    def "can set null value to remove any added values"() {
        property.add("abc")
        property.add(Providers.of("def"))
        property.addAll(Providers.of(["hij"]))

        expect:
        property.set(null)

        !property.present
        property.getOrNull() == null
        property.getOrElse(someValue()) == someValue()
        property.getOrElse(null) == null
    }

    def "can set value to override added values"() {
        property.add("abc")
        property.add(Providers.of("def"))
        property.addAll(Providers.of(["hij"]))

        expect:
        property.set(toMutable(["123", "456"]))
        assertValueIs(["123", "456"])
    }

    def "throws NullPointerException when provider returns list with null to property"() {
        when:
        property.addAll(Providers.of([null]))
        property.get()

        then:
        def ex = thrown(NullPointerException)
    }

    def "throws NullPointerException when adding a null value to the property"() {
        when:
        property.add(null)

        then:
        def ex = thrown(NullPointerException)
        ex.message == "Cannot add a null element to a property of type ${type().simpleName}."
    }
}
