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
import org.gradle.api.internal.provider.DefaultProvider
import org.gradle.api.internal.provider.ProviderInternal
import spock.lang.Specification

import java.util.concurrent.Callable

class IterationOrderRetainingSetElementSourceTest extends Specification {
    IterationOrderRetainingSetElementSource<String> source = new IterationOrderRetainingSetElementSource<>()

    def setup() {
        source.onRealize(new Action<ProviderInternal<? extends String>>() {
            @Override
            void execute(ProviderInternal<? extends String> providerInternal) {
                source.add(providerInternal.get())
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

    def "can add the same element multiple times"() {
        when:
        3.times { source.add("foo") }
        3.times { source.addPending(provider("bar"))}

        then:
        source.iteratorNoFlush().collect() == ["foo"]

        and:
        source.iterator().collect() == ["foo", "bar"]
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

    def "can realize a filtered set of providers and order is retained"() {
        when:
        source.addPending(provider("foo"))
        source.addPending(subtypeProvider("bar"))
        source.addPending(subtypeProvider("baz"))
        source.addPending(provider("fizz"))

        then:
        source.iteratorNoFlush().collect() == []

        when:
        source.realizePending(SubtypeProvider.class)

        then:
        source.iteratorNoFlush().collect() == ["bar", "baz"]

        and:
        source.iterator().collect() == ["foo", "bar", "baz", "fizz"]
    }

    ProviderInternal<? extends String> provider(String value) {
        return new DefaultProvider<String>({ return value })
    }

    SubtypeProvider<? extends String> subtypeProvider(String value) {
        return new SubtypeProvider<String>({ return value })
    }

    private static class SubtypeProvider<T> extends DefaultProvider<T> {
        SubtypeProvider(Callable<? extends T> value) {
            super(value)
        }
    }
}
