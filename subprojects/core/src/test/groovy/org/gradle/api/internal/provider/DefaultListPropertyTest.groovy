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

    def "defaults to empty list"() {
        expect:
        def property = property()
        property.present
        property.get() == []
        property.getOrNull() == []
        property.getOrElse(["abc"]) == []
    }

    def "returns immutable copy of value"() {
        expect:
        def property = property()
        property.set(["abc"])

        property.present
        def v = property.get()
        v instanceof ImmutableList
        v == ["abc"]

        property.set(["123"])

        def v2 = property.get()
        v2 instanceof ImmutableList
        v2 == ["123"]
    }

    def "get returns a snapshot of the current value of the source list"() {
        expect:
        def property = property()
        def l = ["abc"]
        property.set(l)

        def v = property.get()
        v == ["abc"]

        l.add("ignore me")
        v == ["abc"]

        def v2 = property.get()
        v2 instanceof ImmutableList
        v2 == ["abc", "ignore me"]
    }

    def "returns immutable copy of provider value"() {
        def provider = Stub(ProviderInternal)
        provider.get() >>> [["123"], ["abc"]]

        expect:
        def property = property()
        property.set(provider)

        def v = property.get()
        v instanceof ImmutableList
        v == ["123"]

        def v2 = property.get()
        v2 instanceof ImmutableList
        v2 == ["abc"]
    }

    def "mapped provider returns immutable copy of result"() {
        def transformer = Mock(Transformer)

        given:
        def property = property()
        property.set(["abc"])
        def provider = property.map(transformer)

        when:
        def r = provider.get()

        then:
        r instanceof ImmutableList
        r == ["123"]

        1 * transformer.transform(_) >> {
            List<String> src = it[0]
            assert src == ["abc"]
            assert src instanceof ImmutableList
            ["123"]
        }
        0 * _
    }

    def "can add value to empty property"() {
        expect:
        def property = property()

        def v = property.get()
        v instanceof ImmutableList
        v == []

        property.add("123")

        def v2 = property.get()
        v2 instanceof ImmutableList
        v2 == ["123"]

        property.add(Providers.of("456"))

        def v3 = property.get()
        v3 instanceof ImmutableList
        v3 == ["123", "456"]

        property.addAll(Providers.of(["789"]))

        def v4 = property.get()
        v4 instanceof ImmutableList
        v4 == ["123", "456", "789"]
    }

    def "can add value to property"() {
        expect:
        def property = property()
        property.set(["abc"])

        property.present
        def v = property.get()
        v instanceof ImmutableList
        v == ["abc"]

        property.add("123")

        def v2 = property.get()
        v2 instanceof ImmutableList
        v2 == ["abc", "123"]

        property.add(Providers.of("456"))

        def v3 = property.get()
        v3 instanceof ImmutableList
        v3 == ["abc", "123", "456"]

        property.addAll(Providers.of(["789"]))

        def v4 = property.get()
        v4 instanceof ImmutableList
        v4 == ["abc", "123", "456", "789"]
    }

    def "can add provider to empty property"() {
        def delegateProvider = Stub(ProviderInternal)
        delegateProvider.get() >>> ["123", "abc"]
        def provider = new DefaultProvider({ delegateProvider.get() })

        expect:
        def property = property()
        property.add(provider)

        def v = property.get()
        v instanceof ImmutableList
        v == ["123"]

        def v2 = property.get()
        v2 instanceof ImmutableList
        v2 == ["abc"]
    }

    def "can add provider to property"() {
        def delegateProvider = Stub(ProviderInternal)
        delegateProvider.get() >>> ["123", "abc"]
        def provider = new DefaultProvider({ delegateProvider.get() })

        expect:
        def property = property()
        property.add(provider)

        def v = property.get()
        v instanceof ImmutableList
        v == ["123"]

        def v2 = property.get()
        v2 instanceof ImmutableList
        v2 == ["abc"]
    }

    def "can add provider of a collection to empty property"() {
        def provider = Stub(ProviderInternal)
        provider.get() >>> [["123"], ["abc"]]

        expect:
        def property = property()
        property.addAll(provider)

        def v = property.get()
        v instanceof ImmutableList
        v == ["123"]

        def v2 = property.get()
        v2 instanceof ImmutableList
        v2 == ["abc"]
    }

    def "can add provider of a collection to the property"() {
        def provider = Stub(ProviderInternal)
        provider.get() >>> [["123"], ["abc"]]

        expect:
        def property = property()
        property.set(["aaa"])
        property.addAll(provider)

        def v = property.get()
        v instanceof ImmutableList
        v == ["aaa", "123"]

        def v2 = property.get()
        v2 instanceof ImmutableList
        v2 == ["aaa", "abc"]
    }

    def "can set null value to remove any added values"() {
        def property = property()
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

    def "throws IllegalStateException when getting after adding values to a no value list property"() {
        def property = property()
        property.set(null)
        property.addAll(Providers.of(["123"]))

        when:
        property.get()

        then:
        def ex = thrown(IllegalStateException)
        ex.message == Providers.NULL_VALUE
    }

    def "throws IllegalStateException when getting after adding providers that resolve to no value"() {
        def property = property()
        property.addAll(Providers.notDefined())

        when:
        property.get()

        then:
        def ex = thrown(IllegalStateException)
        ex.message == Providers.NULL_VALUE
    }

    def "can add provider of empty list to empty property"() {
        def property = property()

        expect:
        property.addAll(Providers.of([]))

        def v = property.get()
        v instanceof ImmutableList
        v == []
    }

    def "can add provider of empty list to property"() {
        def property = property()
        property.set(["aaa"])

        expect:
        property.addAll(Providers.of([]))

        def v = property.get()
        v instanceof ImmutableList
        v == ["aaa"]
    }

    def "can set value to override added values"() {
        def property = property()
        property.add("abc")
        property.add(Providers.of("def"))
        property.addAll(Providers.of(["hij"]))

        expect:
        property.set(["123", "456"])

        def v = property.get()
        v instanceof ImmutableList
        v == ["123", "456"]
    }

    def "throws NullPointerException when adding a null value to the property"() {
        def property = property()

        when:
        property.add(null)

        then:
        def ex = thrown(NullPointerException)
        ex.message == "Cannot add a null value to the property."
    }
}
