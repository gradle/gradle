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
}
