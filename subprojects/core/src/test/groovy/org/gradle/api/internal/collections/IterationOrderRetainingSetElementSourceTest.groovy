/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal.collections

import org.gradle.api.Action
import org.gradle.api.internal.provider.AbstractProvider
import org.gradle.api.internal.provider.ChangingValue
import org.gradle.api.internal.provider.CollectionProviderInternal
import org.gradle.api.internal.provider.DefaultSetProperty
import org.gradle.api.internal.provider.ProviderInternal
import org.gradle.api.provider.Provider
import spock.lang.Specification

import javax.annotation.Nullable

class IterationOrderRetainingSetElementSourceTest extends Specification {
    IterationOrderRetainingSetElementSource<CharSequence> source = new IterationOrderRetainingSetElementSource<>()

    def setup() {
        source.onRealize(new Action<CollectionProviderInternal<CharSequence, Set<CharSequence>>>() {
            @Override
            void execute(CollectionProviderInternal<CharSequence, Set<CharSequence>> provider) {
                provider.get().each { source.add(it) }
            }
        })
    }

    def "can add a realized element"() {
        when:
        source.add("foo")

        then:
        source.size() == 1
        source.contains("foo")
    }

    def "can add a provider"() {
        when:
        source.addPending(provider("foo"))

        then:
        source.size() == 1
        source.contains("foo")
    }

    def "can add a provider of a set"() {
        when:
        source.addPendingCollection(providerOfSet("foo", "bar", "baz"))

        then:
        source.size() == 3
        source.iterator().collect() == ["foo", "bar", "baz"]
    }

    def "iterates elements in the order they were added"() {
        when:
        source.addPending(provider("foo"))
        source.add("bar")
        source.add("baz")
        source.addPending(provider("fizz"))

        then:
        source.iteratorNoFlush().collect() == ["bar", "baz"]

        and:
        source.iterator().collect() == ["foo", "bar", "baz", "fizz"]
    }

    def "once realized, provided values appear like realized values"() {
        when:
        source.addPending(provider("foo"))
        source.add("bar")
        source.add("baz")
        source.addPending(provider("fizz"))

        then:
        source.iteratorNoFlush().collect() == ["bar", "baz"]

        when:
        source.realizePending()

        then:
        source.iteratorNoFlush().collect() == ["foo", "bar", "baz", "fizz"]
    }

    def "can add only providers"() {
        when:
        source.addPending(provider("foo"))
        source.addPending(provider("bar"))
        source.addPendingCollection(providerOfSet("baz", "fizz"))

        then:
        source.iteratorNoFlush().collect() == []

        and:
        source.iterator().collect() == ["foo", "bar", "baz", "fizz"]
    }

    def "can add only realized providers"() {
        when:
        source.add("foo")
        source.add("bar")
        source.add("baz")
        source.add("fizz")

        then:
        source.iteratorNoFlush().collect() == ["foo", "bar", "baz", "fizz"]

        and:
        source.iterator().collect() == ["foo", "bar", "baz", "fizz"]
    }

    def "can add the same element multiple times"() {
        when:
        3.times { source.add("foo") }
        3.times { source.addPending(provider("bar")) }
        3.times { source.addPendingCollection(providerOfSet("baz", "fizz")) }

        then:
        source.iteratorNoFlush().collect() == ["foo"]

        and:
        source.iterator().collect() == ["foo", "bar", "baz", "fizz"]
    }

    def "can remove a realized element"() {
        given:
        source.add("foo")
        source.addPending(provider("bar"))
        source.add("baz")

        expect:
        source.remove("foo")

        and:
        source.size() == 2
        source.iterator().collect() == ["bar", "baz"]

        and:
        !source.remove("foo")
    }

    def "can remove a provider"() {
        given:
        def bar = provider("bar")
        source.add("foo")
        source.addPending(bar)
        source.add("baz")

        expect:
        source.removePending(bar)

        and:
        source.size() == 2
        source.iterator().collect() == ["foo", "baz"]
    }

    def "can remove a provider of set"() {
        given:
        def barBaz = providerOfSet("bar", "baz")
        source.add("foo")
        source.addPendingCollection(barBaz)
        source.add("fizz")

        expect:
        source.removePendingCollection(barBaz)

        and:
        source.size() == 2
        source.iterator().collect() == ["foo", "fizz"]
    }

    def "can remove an element from a provider of set"() {
        given:
        def barBaz = providerOfSet("bar", "baz")
        source.add("foo")
        source.addPendingCollection(barBaz)
        source.add("fizz")
        source.iterator()

        expect:
        source.remove("bar")

        and:
        source.size() == 3
        source.iterator().collect() == ["foo", "baz", "fizz"]
    }

    def "can remove a realized provider"() {
        given:
        source.add("foo")
        source.addPending(provider("bar"))
        source.add("baz")

        expect:
        source.iterator()
        source.remove("bar")

        and:
        source.size() == 2
        source.iteratorNoFlush().collect() == ["foo", "baz"]
        source.iterator().collect() == ["foo", "baz"]
    }

    def "can realize filtered providers and order is retained"() {
        when:
        source.addPending(provider("foo"))
        source.addPending(provider(new StringBuffer("bar")))
        source.addPending(provider(new StringBuffer("baz")))
        source.addPending(provider("fizz"))

        then:
        source.iteratorNoFlush().collect() == []

        when:
        source.realizePending(StringBuffer.class)

        then:
        source.iteratorNoFlush().collect { it.toString() } == ["bar", "baz"]

        and:
        source.iterator().collect { it.toString() } == ["foo", "bar", "baz", "fizz"]
    }

    def "can realize filtered providers of sets and order is retained"() {
        when:
        source.addPending(provider("foo"))
        source.addPendingCollection(providerOfSet(new StringBuffer("bar"), new StringBuffer("baz")))
        source.addPending(provider("fizz"))

        then:
        source.iteratorNoFlush().collect() == []

        when:
        source.realizePending(StringBuffer.class)

        then:
        source.iteratorNoFlush().collect { it.toString() } == ["bar", "baz"]

        and:
        source.iterator().collect { it.toString() } == ["foo", "bar", "baz", "fizz"]
    }

    def "correctly handles providers of changing values"() {
        given:
        def provider = providerOfSet("foo", "bar")

        when:
        source.addPendingCollection(provider)

        then:
        source.iterator().collect() == ["foo", "bar"]

        when:
        provider.set(["baz"])

        then:
        source.iterator().collect() == ["baz"]
    }

    ProviderInternal<? extends String> provider(String value) {
        return new TypedProvider<String>(String, value)
    }

    ProviderInternal<? extends StringBuffer> provider(StringBuffer value) {
        return new TypedProvider<StringBuffer>(StringBuffer, value)
    }

    private static class TypedProvider<T> extends AbstractProvider<T> {
        final Class<T> type
        final T value

        TypedProvider(Class<T> type, T value) {
            this.type = type
            this.value = value
        }

        @Override
        Class<T> getType() {
            return type
        }

        @Override
        T getOrNull() {
            return value
        }
    }

    CollectionProviderInternal<? extends String, Set<? extends String>> providerOfSet(String... values) {
        CollectionProviderInternal<? extends String, Set<? extends String>> setProvider = new ChangingSetProvider<String>(String.class)
        values.each { setProvider.add(it) }
        return setProvider
    }

    CollectionProviderInternal<? extends StringBuffer, Set<? extends StringBuffer>> providerOfSet(StringBuffer... values) {
        CollectionProviderInternal<? extends StringBuffer, Set<? extends StringBuffer>> setProvider = new ChangingSetProvider<StringBuffer>(StringBuffer.class)
        values.each { setProvider.add(it) }
        return setProvider
    }

    private static class ChangingSetProvider<T> extends DefaultSetProperty<T> implements ChangingValue<Set<T>> {
        Action<Provider<Set<T>>> action

        ChangingSetProvider(Class<T> elementType) {
            super(elementType)
        }

        @Override
        void onValueChange(Action<Provider<Set<T>>> action) {
            this.action = action
        }

        @Override
        void set(@Nullable Iterable<? extends T> value) {
            super.set(value)
            assert action != null
            action.execute(this)
        }
    }
}
