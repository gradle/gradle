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
        def property = new DefaultListProperty<String>(String)
        property.present
        property.get() == []
        property.getOrNull() == []
        property.getOrElse(["abc"]) == []
    }

    def "returns immutable copy of value"() {
        expect:
        def property = new DefaultListProperty<String>(String)
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
        def property = new DefaultListProperty<String>(String)
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
        def property = new DefaultListProperty<String>(String)
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
        def property = new DefaultListProperty<String>(String)
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

    def "can add value to the provider"() {
        expect:
        def property = new DefaultListProperty<String>(String)
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

    def "can add provider to an empty provider"() {
        def provider = Stub(ProviderInternal)
        provider.get() >>> ["123", "abc"]

        expect:
        def property = new DefaultListProperty<String>(String)
        property.add(provider)

        def v = property.get()
        v instanceof ImmutableList
        v == ["123"]

        def v2 = property.get()
        v2 instanceof ImmutableList
        v2 == ["abc"]
    }

    def "can add provider of a collection to an empty provider"() {
        def provider = Stub(ProviderInternal)
        provider.get() >>> [["123"], ["abc"]]

        expect:
        def property = new DefaultListProperty<String>(String)
        property.addAll(provider)

        def v = property.get()
        v instanceof ImmutableList
        v == ["123"]

        def v2 = property.get()
        v2 instanceof ImmutableList
        v2 == ["abc"]
    }

    def "can add provider of a collection to the provider"() {
        def provider = Stub(ProviderInternal)
        provider.get() >>> [["123"], ["abc"]]

        expect:
        def property = new DefaultListProperty<String>(String)
        property.set(["aaa"])
        property.addAll(provider)

        def v = property.get()
        v instanceof ImmutableList
        v == ["aaa", "123"]

        def v2 = property.get()
        v2 instanceof ImmutableList
        v2 == ["aaa", "abc"]
    }

    def "can set to null value to remove value after adding values"() {
        given:
        def property = property()
        property.add("abc")
        property.set(null)

        expect:
        !property.present
        property.getOrNull() == null
        property.getOrElse(someValue()) == someValue()
        property.getOrElse(null) == null
    }

    def "can add to the property after set to null"() {
        given:
        def provider = Stub(ProviderInternal)
        provider.get() >> ["123"]
        def property = property()
        property.set(null)
        property.addAll(provider)

        expect:
        def v = property.get()
        v instanceof ImmutableList
        v == ["123"]
    }

    def "can set to value to override added values"() {
        given:
        def property = property()
        property.add("abc")
        property.add(Providers.of("def"))
        property.addAll(Providers.of(["hij"]))
        property.set(["123", "456"])

        expect:
        def v = property.get()
        v instanceof ImmutableList
        v == ["123", "456"]
    }
}
