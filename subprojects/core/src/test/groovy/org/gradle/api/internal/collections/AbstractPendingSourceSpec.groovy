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
import org.gradle.api.internal.provider.ChangingValueHandler
import org.gradle.api.internal.provider.CollectionProviderInternal
import org.gradle.api.internal.provider.ProviderInternal
import spock.lang.Specification

abstract class AbstractPendingSourceSpec extends Specification {
    def realize = Mock(Action)

    Action<CharSequence> defaultRealizeAction = new Action<CharSequence>() {
        @Override
        void execute(CharSequence t) {
            realize.execute(t.toString())
        }
    }

    Action<CharSequence> getRealizeAction() {
        return defaultRealizeAction;
    }

    abstract PendingSource<CharSequence> getSource()

    def setup() {
        source.onRealize(realizeAction)
    }

    def "can add a provider"() {
        when:
        source.addPending(provider("foo"))

        then:
        source.size() == 1
        !source.isEmpty()
    }

    def "can add a provider of iterable"() {
        when:
        source.addPendingCollection(setProvider("foo", "bar"))

        then:
        source.size() == 2
        !source.isEmpty()
    }

    def "can realize all pending elements"() {
        given:
        source.addPending(provider("provider1"))
        source.addPending(provider(new StringBuffer("provider2")))
        source.addPending(provider("provider3"))
        source.addPendingCollection(setProvider("provider4[0]", "provider4[1]"))
        source.addPendingCollection(setProvider(new StringBuffer("provider5[0]"), new StringBuffer("provider5[1]")))

        when:
        source.realizePending()

        then:
        1 * realize.execute("provider1")
        1 * realize.execute("provider2")
        1 * realize.execute("provider3")
        1 * realize.execute("provider4[0]")
        1 * realize.execute("provider4[1]")
        1 * realize.execute("provider5[0]")
        1 * realize.execute("provider5[1]")
        0 * realize.execute(_)
    }

    def "can realize all pending elements with a given type"() {
        given:
        source.addPending(provider("provider1"))
        source.addPending(provider(new StringBuffer("provider2")))
        source.addPending(provider("provider3"))
        source.addPendingCollection(setProvider("provider4[0]", "provider4[1]"))
        source.addPendingCollection(setProvider(new StringBuffer("provider5[0]"), new StringBuffer("provider5[1]")))

        when:
        source.realizePending(String.class)

        then:
        1 * realize.execute("provider1")
        0 * realize.execute("provider2")
        1 * realize.execute("provider3")
        1 * realize.execute("provider4[0]")
        1 * realize.execute("provider4[1]")
        0 * realize.execute("provider5[0]")
        0 * realize.execute("provider5[1]")
        0 * realize.execute(_)
    }

    def "realizes only the specified pending element"() {
        def provider1 = provider("provider1")
        def provider5 = setProvider(new StringBuffer("provider5[0]"), new StringBuffer("provider5[1]"))

        given:
        source.addPending(provider1)
        source.addPending(provider(new StringBuffer("provider2")))
        source.addPending(provider("provider3"))
        source.addPendingCollection(setProvider("provider4[0]", "provider4[1]"))
        source.addPendingCollection(provider5)

        when:
        source.realizePending(provider1)

        then:
        1 * realize.execute("provider1")
        0 * realize.execute("provider2")
        0 * realize.execute("provider3")
        0 * realize.execute("provider4[0]")
        0 * realize.execute("provider4[1]")
        0 * realize.execute("provider5[0]")
        0 * realize.execute("provider5[1]")
        0 * realize.execute(_)

        when:
        source.realizePending(provider5)

        then:
        0 * realize.execute("provider1")
        0 * realize.execute("provider2")
        0 * realize.execute("provider3")
        0 * realize.execute("provider4[0]")
        0 * realize.execute("provider4[1]")
        1 * realize.execute("provider5[0]")
        1 * realize.execute("provider5[1]")
        0 * realize.execute(_)
    }

    def "returns previous realize action upon setting a new one"() {
        when:
        def action = source.onRealize(Mock(Action))

        then:
        action == realizeAction
    }

    def "can remove pending elements"() {
        def provider1 = provider("provider1")
        def provider5 = setProvider(new StringBuffer("provider5[0]"), new StringBuffer("provider5[1]"))

        when:
        source.addPending(provider1)
        source.addPending(provider(new StringBuffer("provider2")))
        source.addPending(provider("provider3"))
        source.addPendingCollection(setProvider("provider4[0]", "provider4[1]"))
        source.addPendingCollection(provider5)

        then:
        source.size() == 7

        when:
        source.removePending(provider1)

        then:
        source.size() == 6

        when:
        source.removePending(provider5)

        then:
        source.size() == 4

        when:
        source.realizePending()

        then:
        0 * realize.execute("provider1")
        1 * realize.execute("provider2")
        1 * realize.execute("provider3")
        1 * realize.execute("provider4[0]")
        1 * realize.execute("provider4[1]")
        0 * realize.execute("provider5[0]")
        0 * realize.execute("provider5[1]")
        0 * realize.execute(_)
    }

    def "can clear pending elements"() {
        when:
        source.addPending(provider("provider1"))
        source.addPending(provider(new StringBuffer("provider2")))
        source.addPending(provider("provider3"))
        source.addPendingCollection(setProvider("provider4[0]", "provider4[1]"))
        source.addPendingCollection(setProvider(new StringBuffer("provider5[0]"), new StringBuffer("provider5[1]")))

        then:
        source.size() == 7

        when:
        source.clear()

        then:
        source.size() == 0
        source.isEmpty()

        when:
        source.realizePending()

        then:
        0 * realize.execute()
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

    private static class TypedProvider<T> extends AbstractProvider<T> implements ChangingValue<T> {
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
        T getOrNull() {
            return value
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

    private static class TypedProviderOfSet<T> extends AbstractProvider<Set<T>> implements CollectionProviderInternal<T, Set<T>>, ChangingValue<Iterable<T>> {
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
        Set<T> getOrNull() {
            return value
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
