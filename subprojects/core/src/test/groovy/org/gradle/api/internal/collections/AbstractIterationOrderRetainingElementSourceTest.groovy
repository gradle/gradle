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

import org.gradle.api.internal.provider.AbstractProvider
import org.gradle.api.internal.provider.ChangingValue
import org.gradle.api.internal.provider.ChangingValueHandler
import org.gradle.api.internal.provider.CollectionProviderInternal
import org.gradle.api.internal.provider.ProviderInternal
import spock.lang.Specification

abstract class AbstractIterationOrderRetainingElementSourceTest extends Specification {
    abstract AbstractIterationOrderRetainingElementSource<CharSequence> getSource()

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

    def "can add a provider of iterable"() {
        when:
        source.addPendingCollection(setProvider("foo", "bar"))

        then:
        source.size() == 2
        source.containsAll("foo", "bar")
    }

    def "iterates elements in the order they were added"() {
        when:
        source.addPending(provider("foo"))
        source.add("bar")
        source.add("baz")
        source.addPending(provider("fizz"))
        source.addPendingCollection(setProvider("fuzz", "bazz"))

        then:
        source.iteratorNoFlush().collect() == ["bar", "baz"]

        and:
        source.iterator().collect() == ["foo", "bar", "baz", "fizz", "fuzz", "bazz"]
    }

    def "once realized, provided values appear like realized values"() {
        when:
        source.addPending(provider("foo"))
        source.add("bar")
        source.add("baz")
        source.addPending(provider("fizz"))
        source.addPendingCollection(setProvider("fuzz", "bazz"))

        then:
        source.iteratorNoFlush().collect() == ["bar", "baz"]

        when:
        source.realizePending()

        then:
        source.iteratorNoFlush().collect() == ["foo", "bar", "baz", "fizz", "fuzz", "bazz"]
    }

    def "can add only providers"() {
        when:
        source.addPending(provider("foo"))
        source.addPending(provider("bar"))
        source.addPending(provider("baz"))
        source.addPending(provider("fizz"))

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

    def "can add only providers of iterable"() {
        when:
        source.addPendingCollection(setProvider("foo", "bar"))
        source.addPendingCollection(setProvider("baz", "fizz", "fuzz"))
        source.addPendingCollection(setProvider("buzz"))

        then:
        source.iteratorNoFlush().collect() == []

        and:
        source.iterator().collect() == ["foo", "bar", "baz", "fizz", "fuzz", "buzz"]
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

    def "can remove a provider of iterable"() {
        given:
        def barBazzFizz = setProvider("bar", "bazz", "fizz")
        source.add("foo")
        source.addPendingCollection(barBazzFizz)
        source.add("baz")

        expect:
        source.removePendingCollection(barBazzFizz)

        and:
        source.size() == 2
        source.iterator().collect() == ["foo", "baz"]
    }

    def "can realize a filtered set of providers and order is retained"() {
        when:
        source.addPending(provider("foo"))
        source.addPending(provider(new StringBuffer("bar")))
        source.addPending(provider(new StringBuffer("baz")))
        source.addPending(provider("fizz"))
        source.addPendingCollection(setProvider(new StringBuffer("fuzz"), new StringBuffer("bazz")))

        then:
        source.iteratorNoFlush().collect() == []

        when:
        source.realizePending(StringBuffer.class)

        then:
        source.iteratorNoFlush().collect { it.toString() } == ["bar", "baz", "fuzz", "bazz"]

        and:
        source.iterator().collect { it.toString() } == ["foo", "bar", "baz", "fizz", "fuzz", "bazz"]
    }

    def "can remove elements using iteratorNoFlush"() {
        source.add("foo")
        source.addPending(provider("bar"))
        source.addPendingCollection(setProvider("baz", "fooz"))
        source.add("fizz")

        when:
        def iterator = source.iteratorNoFlush()
        iterator.remove()

        then:
        thrown(IllegalStateException)

        when:
        def next = iterator.next()

        then:
        next == "foo"

        when:
        iterator.remove()

        then:
        source.iteratorNoFlush().collect() == ["fizz"]

        when:
        source.addPending(provider("fuzz"))
        iterator = source.iteratorNoFlush()
        next = iterator.next()

        then:
        next == "fizz"

        when:
        iterator.remove()

        then:
        !iterator.hasNext()
        source.iteratorNoFlush().collect() == []

        when:
        source.add("buzz")
        iterator = source.iteratorNoFlush()
        next = iterator.next()

        then:
        next == "buzz"

        when:
        iterator.remove()

        then:
        !iterator.hasNext()

        when:
        source.add("bazz")
        source.add("bizz")
        iterator = source.iteratorNoFlush()
        while(iterator.hasNext()) {
            iterator.next()
            iterator.remove()
        }

        then:
        source.iteratorNoFlush().collect() == []

        and:
        source.iterator().collect() == ["bar", "baz", "fooz", "fuzz"]
    }

    def "can remove elements using iterator"() {
        source.add("foo")
        source.addPending(provider("bar"))
        source.addPendingCollection(setProvider("baz", "fooz"))
        source.add("fizz")

        when:
        def iterator = source.iterator()
        iterator.remove()

        then:
        thrown(IllegalStateException)

        when:
        def next = iterator.next()

        then:
        next == "foo"

        when:
        iterator.remove()

        then:
        source.iterator().collect() == ["bar", "baz", "fooz", "fizz"]

        when:
        source.addPending(provider("fuzz"))
        iterator = source.iterator()
        next = iterator.next()

        then:
        next == "bar"

        when:
        iterator.remove()

        then:
        iterator.hasNext()
        source.iterator().collect() == ["baz", "fooz", "fizz", "fuzz"]

        when:
        source.add("buzz")
        iterator = source.iterator()
        next = iterator.next()

        then:
        next == "baz"

        when:
        iterator.remove()

        then:
        iterator.hasNext()
        source.iterator().collect() == ["fooz", "fizz", "fuzz", "buzz"]

        when:
        iterator = source.iterator()
        while(iterator.hasNext()) {
            iterator.next()
            iterator.remove()
        }

        then:
        source.iterator().collect() == []
    }

    def "can handle elements with changing values"() {
        def provider1 = setProvider("baz", "fooz")

        when:
        source.add("foo")
        source.addPending(provider("bar"))
        source.addPendingCollection(provider1)
        source.add("fizz")

        then:
        source.iteratorNoFlush().collect() == ["foo", "fizz"]

        when:
        provider1.value = ["fuzz", "buzz"]

        then:
        source.iterator().collect() == ["foo", "bar", "fuzz", "buzz", "fizz"]

        when:
        provider1.value = ["baz"]

        then:
        source.iterator().collect() == ["foo", "bar", "baz", "fizz"]
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

    CollectionProviderInternal<? extends String, Set<? extends String>> setProvider(String... values) {
        return new TypedProviderOfSet<String>(String, values as LinkedHashSet)
    }

    CollectionProviderInternal<? extends StringBuffer, Set<? extends StringBuffer>> setProvider(StringBuffer... values) {
        return new TypedProviderOfSet<StringBuffer>(StringBuffer, values as LinkedHashSet)
    }

    private static class TypedProviderOfSet<T> extends AbstractProvider<Set<T>> implements CollectionProviderInternal<T, Set<T>>, ChangingValue {
        final Class<T> type
        Set<T> value
        final ChangingValueHandler changingValue = new ChangingValueHandler()

        TypedProviderOfSet(Class<T> type, Set<T> value) {
            this.type = type
            this.value = value
        }

        @Override
        Class<? extends T> getElementType() {
            return type
        }

        @Override
        Set<T> getOrNull() {
            return value
        }

        @Override
        int size() {
            return value.size()
        }

        void setValue(Set<T> value) {
            this.value = value
            changingValue.handle()
        }

        @Override
        void onValueChange(Runnable action) {
            changingValue.onValueChange(action)
        }
    }
}
