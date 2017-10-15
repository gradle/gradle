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

import com.google.common.collect.ImmutableList
import org.gradle.api.Transformer
import spock.lang.Unroll

class DefaultListPropertyTest extends PropertySpec<List<String>> {
    @Override
    DefaultListProperty<String> property() {
        return new DefaultListProperty<String>(String)
    }

    @Override
    Class<List<String>> type() {
        return List
    }

    @Override
    List<String> someValue() {
        return ["value"]
    }

    @Override
    List<String> someOtherValue() {
        return ["value2"]
    }

    def property = property()

    def "defaults to empty list"() {
        expect:
        property.present
        property.get() == []
        property.getOrNull() == []
        property.getOrElse(["abc"]) == []
    }

    def "returns immutable copy of value"() {
        expect:
        property.set(["abc"])
        assertValueIs(["abc"])
    }

    def "queries initial value for every call to get()"() {
        expect:
        def initialValue = ["abc"]
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

    def "mapped provider returns immutable copy of result"() {
        given:
        property.set(["abc"])
        def provider = property.map(new Transformer<List<String>, List<String>>() {
            @Override
            List<String> transform(List<String> value) {
                assert value instanceof ImmutableList
                assert value == ["abc"]
                return ["123"]
            }
        })

        expect:
        def actual = provider.get()
        actual instanceof ImmutableList
        actual == ["123"]
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
        property.set(["123"])

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
        assertValueIs(["123"])
    }

    def "appends value from provider during `add` to property"() {
        expect:
        property.add(Providers.of("123"))
        assertValueIs(["123"])
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

    def "providers only called once per property.get()"() {
        def addProvider = Spy(DefaultProvider, constructorArgs: [{ "123" }])
        def addAllProvider = Spy(DefaultProvider, constructorArgs: [{ ["abc"] }])

        when:
        property.add(addProvider)
        property.addAll(addAllProvider)
        assertValueIs(["123", "abc"])

        then:
        1 * addProvider.get()
        1 * addAllProvider.get()
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
        property.set(["123", "456"])
        assertValueIs(["123", "456"])
    }

    def "throws IllegalStateException when list property has no value"() {
        when:
        property.set(null)
        property.get()
        then:
        def ex = thrown(IllegalStateException)
        ex.message == Providers.NULL_VALUE

        when:
        property.addAll(Providers.of(["123"]))
        property.get()
        then:
        ex = thrown(IllegalStateException)
        ex.message == Providers.NULL_VALUE
    }

    def "throws NullPointerException when provider returns list with null to property"() {
        when:
        property.addAll(Providers.of([null]))
        property.get()

        then:
        def ex = thrown(NullPointerException)
        ex.message == null
    }

    def "throws NullPointerException when adding a null value to the property"() {
        when:
        property.add(null)

        then:
        def ex = thrown(NullPointerException)
        ex.message == "Cannot add a null value to a list property."
    }

    private void assertValueIs(List<String> expected) {
        def actual = property.get()
        assert actual instanceof ImmutableList
        assert actual == expected
        assert property.present
    }
}
