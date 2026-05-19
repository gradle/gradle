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
import org.gradle.api.internal.provider.AbstractMinimalProvider
import org.gradle.api.internal.provider.ChangingValue
import org.gradle.api.internal.provider.ChangingValueHandler
import org.gradle.api.internal.provider.CollectionProviderInternal
import org.gradle.api.internal.provider.DefaultPropertyFactory
import org.gradle.api.internal.provider.PropertyHost
import org.gradle.api.internal.provider.ProviderInternal
import org.gradle.api.internal.provider.Providers
import org.gradle.api.internal.provider.ValueSupplier
import spock.lang.Specification

abstract class ElementSourceSpec extends Specification {

    abstract ElementSource<CharSequence> getSource()

    abstract List<CharSequence> iterationOrder(CharSequence... values)

    def "does not run side effects of pending providers when realizing pending elements"() {
        given:
        def sideEffect1 = Mock(ValueSupplier.SideEffect)
        def sideEffect2 = Mock(ValueSupplier.SideEffect)
        def sideEffect3 = Mock(ValueSupplier.SideEffect)
        def sideEffect4 = Mock(ValueSupplier.SideEffect)
        def sideEffect5 = Mock(ValueSupplier.SideEffect)
        def propertyFactory = new DefaultPropertyFactory(Stub(PropertyHost))
        def source = getSource()

        when:
        def provider1 = Providers.of("v1").withSideEffect(sideEffect1)
        source.addPending(provider1)
        def provider2 = Providers.of("v2").withSideEffect(sideEffect2)
        source.addPending(provider2)
        def provider3 = Providers.of("v3").withSideEffect(sideEffect3)
        source.addPending(provider3)
        def provider4 = propertyFactory.listProperty(String).with {
            it.add(Providers.of("v4").withSideEffect(sideEffect4))
            it
        }
        source.addPendingCollection(provider4)
        def provider5 = propertyFactory.listProperty(String).with {
            it.add(Providers.of("v5").withSideEffect(sideEffect5))
            it
        }
        source.addPendingCollection(provider5)

        then:
        0 * _ // no side effects until elements are realized

        when:
        source.removePending(provider2)
        source.removePendingCollection(provider4)

        then:
        0 * _ // can remove pending without running side effects

        when:
        source.realizePending()

        then:
        0 * sideEffect1.execute("v1")

        then: // ensure ordering
        0 * sideEffect3.execute("v3")

        then: // ensure ordering
        0 * sideEffect5.execute("v5")

        // side effects of removed providers do not execute
        0 * sideEffect2.execute(_)
        0 * sideEffect4.execute(_)

        when:
        source.realizePending()

        then:
        0 * _ // realizing again does not run side effects
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

    def "can add a provider of iterable"() {
        when:
        source.addPendingCollection(setProvider("foo", "bar"))

        then:
        source.size() == 2
        source.containsAll("foo", "bar")
    }

    def "iterates elements in the correct order"() {
        when:
        source.addPending(provider("foo"))
        source.add("bar")
        source.add("baz")
        source.addPending(provider("fizz"))
        source.addPendingCollection(setProvider("fuzz", "bazz"))

        then:
        source.iteratorNoFlush().collect() == iterationOrder("bar", "baz")

        and:
        source.iterator().collect() == iterationOrder("foo", "bar", "baz", "fizz", "fuzz", "bazz")
    }

    def "once realized, provided values appear like realized values"() {
        when:
        source.addPending(provider("foo"))
        source.add("bar")
        source.add("baz")
        source.addPending(provider("fizz"))
        source.addPendingCollection(setProvider("fuzz", "bazz"))

        then:
        source.iteratorNoFlush().collect() == iterationOrder("bar", "baz")

        when:
        source.realizePending()

        then:
        source.iteratorNoFlush().collect() == iterationOrder("foo", "bar", "baz", "fizz", "fuzz", "bazz")
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
        source.iterator().collect() == iterationOrder("foo", "bar", "baz", "fizz")
    }

    def "can add only realized providers"() {
        when:
        source.add("foo")
        source.add("bar")
        source.add("baz")
        source.add("fizz")

        then:
        source.iteratorNoFlush().collect() == iterationOrder("foo", "bar", "baz", "fizz")

        and:
        source.iterator().collect() == iterationOrder("foo", "bar", "baz", "fizz")
    }

    def "can add only providers of iterable"() {
        when:
        source.addPendingCollection(setProvider("foo", "bar"))
        source.addPendingCollection(setProvider("baz", "fizz", "fuzz"))
        source.addPendingCollection(setProvider("buzz"))

        then:
        source.iteratorNoFlush().collect() == []

        and:
        source.iterator().collect() == iterationOrder("foo", "bar", "baz", "fizz", "fuzz", "buzz")
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
        source.iterator().collect() == iterationOrder("bar", "baz")

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
        source.iterator().collect() == iterationOrder("foo", "baz")
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
        source.iterator().collect() == iterationOrder("foo", "baz")
    }

    def "can handle elements with changing values"() {
        def provider1 = setProvider("baz", "fooz")
        def provider2 = provider("bar")

        when:
        source.add("foo")
        source.addPendingCollection(provider1)
        source.addPending(provider2)
        source.add("fizz")

        then:
        source.iteratorNoFlush().collect() == iterationOrder("foo", "fizz")

        when:
        provider1.value = ["fuzz", "buzz"]

        then:
        source.iterator().collect() == iterationOrder("foo", "fuzz", "buzz", "bar", "fizz")

        when:
        provider1.value = ["baz"]

        then:
        source.iterator().collect() == iterationOrder("foo", "baz", "bar", "fizz")

        when:
        provider2.value = "fooz"

        then:
        source.iterator().collect() == iterationOrder("foo", "baz", "fooz", "fizz")
    }

    def "comodification with iterator causes an exception"() {
        given:
        def provider = provider("baz")
        def providerOfSet = setProvider("fuzz", "buzz")
        source.add("foo")
        source.addPending(provider)
        source.addPendingCollection(providerOfSet)

        when:
        def iterator = source.iteratorNoFlush()
        source.add("bar")
        iterator.next()

        then:
        thrown(ConcurrentModificationException)

        when:
        iterator = source.iteratorNoFlush()
        source.remove("bar")
        iterator.next()

        then:
        thrown(ConcurrentModificationException)

        when:
        iterator = source.iteratorNoFlush()
        source.realizePending()
        iterator.next()

        then:
        thrown(ConcurrentModificationException)

        when:
        iterator = source.iteratorNoFlush()
        iterator.next()
        source.remove("foo")
        iterator.remove()

        then:
        thrown(ConcurrentModificationException)

        when:
        iterator = source.iteratorNoFlush()
        providerOfSet.value = ["fizz"]
        iterator.next()

        then:
        thrown(ConcurrentModificationException)
    }

    ProviderInternal<? extends String> provider(String value) {
        return new TypedProvider(String, value)
    }

    ProviderInternal<? extends StringBuffer> provider(StringBuffer value) {
        return new TypedProvider(StringBuffer, value)
    }

    CollectionProviderInternal<? extends String, Set<? extends String>> setProvider(String... values) {
        return new TypedProviderOfSet(String, values as LinkedHashSet)
    }

    CollectionProviderInternal<? extends StringBuffer, Set<? extends StringBuffer>> setProvider(StringBuffer... values) {
        return new TypedProviderOfSet(StringBuffer, values as LinkedHashSet)
    }

    private static class TypedProvider<T> extends AbstractMinimalProvider<T> implements ChangingValue<T> {
        final Class<T> type
        T value
        final ChangingValueHandler<T> changingValue = new ChangingValueHandler<T>()

        TypedProvider(Class<T> type, T value) {
            this.type = type
            this.value = value
        }

        @Override
        Class<T> getType() {
            return type
        }

        @Override
        protected Value<T> calculateOwnValue(ValueConsumer consumer) {
            return Value.of(value)
        }

        void setValue(T value) {
            T previousValue = this.value
            this.value = value
            changingValue.handle(previousValue)
        }

        @Override
        void onValueChange(Action<T> action) {
            changingValue.onValueChange(action)
        }
    }

    private static class TypedProviderOfSet<T> extends AbstractMinimalProvider<Set<T>> implements CollectionProviderInternal<T, Set<T>>, ChangingValue<Iterable<T>> {
        final Class<T> type
        Set<T> value
        final ChangingValueHandler<Iterable<T>> changingValue = new ChangingValueHandler<Iterable<T>>()

        TypedProviderOfSet(Class<T> type, Set<T> value) {
            this.type = type
            this.value = value
        }

        @Override
        Class<? extends T> getElementType() {
            return type
        }

        @Override
        protected Value<? extends Set<T>> calculateOwnValue(ValueConsumer consumer) {
            return Value.of(value)
        }

        @Override
        int size() {
            return value.size()
        }

        void setValue(Set<T> value) {
            Set<T> previousValue = this.value
            this.value = value
            changingValue.handle(previousValue)
        }

        @Override
        void onValueChange(Action<Iterable<T>> action) {
            changingValue.onValueChange(action)
        }
    }
}
