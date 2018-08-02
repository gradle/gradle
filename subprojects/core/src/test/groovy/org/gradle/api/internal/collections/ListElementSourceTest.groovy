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
import org.gradle.api.internal.provider.CollectionProviderInternal


class ListElementSourceTest extends AbstractIterationOrderRetainingElementSourceTest {
    ListElementSource<CharSequence> source = new ListElementSource<>()

    def "can add the same provider twice"() {
        def provider = provider("foo")

        when:
        source.addPending(provider)
        source.addPending(provider)

        then:
        source.size() == 2
        source.iterator().collect() == ["foo", "foo"]
    }

    def "can add an element as both a provider and a realized value"() {
        when:
        source.add("foo")
        source.addPending(provider("foo"))

        then:
        source.iterator().collect() == ["foo", "foo"]
    }

    def "can add the same element multiple times"() {
        when:
        3.times { source.add("foo") }
        3.times { source.addPending(provider("bar")) }

        then:
        source.iteratorNoFlush().collect() == ["foo", "foo", "foo"]

        and:
        source.iterator().collect() == ["foo", "foo", "foo", "bar", "bar", "bar"]
    }

    def "a provider of iterable can provide the same element multiple times"() {
        when:
        source.add("foo")
        source.addPendingCollection(listProvider("foo", "foo", "foo"))

        then:
        source.iteratorNoFlush().collect() == ["foo"]

        and:
        source.iterator().collect() == ["foo", "foo", "foo", "foo"]
    }

    def "listIterator can get previous"() {
        given:
        source.add("foo")
        source.addPending(provider("bar"))
        source.addPendingCollection(listProvider("baz", "buzz"))
        source.add("fizz")
        source.add("fuzz")

        when:
        def iterator = source.listIterator()
        iterator.next()
        iterator.next()
        iterator.next()

        then:
        iterator.previous() == "fuzz"
        iterator.previous() == "fizz"
        iterator.previous() == "foo"
        !iterator.hasPrevious()

        and:
        iterator.next() == "foo"
        iterator.next() == "fizz"
        iterator.next() == "fuzz"
        !iterator.hasNext()

        and:
        iterator.previous() == "fuzz"
        iterator.previous() == "fizz"
        iterator.next() == "fizz"
        iterator.previous() == "fizz"
    }

    def "listIterator can get previous on realized collection"() {
        given:
        source.add("foo")
        source.addPending(provider("bar"))
        source.addPendingCollection(listProvider("baz", "buzz"))
        source.add("fizz")
        source.add("fuzz")
        source.realizePending()

        when:
        def iterator = source.listIterator()

        then:
        iterator.next() == "foo"
        iterator.next() == "bar"
        iterator.next() == "baz"

        then:
        iterator.previous() == "baz"
        iterator.previous() == "bar"
        iterator.previous() == "foo"
        !iterator.hasPrevious()

        and:
        iterator.next() == "foo"
        iterator.next() == "bar"
        iterator.next() == "baz"
        iterator.next() == "buzz"
        iterator.next() == "fizz"
        iterator.next() == "fuzz"
        !iterator.hasNext()

        and:
        iterator.previous() == "fuzz"
        iterator.previous() == "fizz"
        iterator.next() == "fizz"
        iterator.previous() == "fizz"
    }

    def "listIterator can remove elements"() {
        given:
        source.add("foo")
        source.addPending(provider("bar"))
        source.addPendingCollection(listProvider("baz", "buzz"))
        source.add("fizz")
        source.add("fuzz")

        when:
        def iterator = source.listIterator()
        iterator.next()
        iterator.remove()

        then:
        source.iteratorNoFlush().collect() == ["fizz", "fuzz"]

        when:
        iterator.next()
        iterator.next()
        iterator.previous()
        iterator.remove()

        then:
        source.iteratorNoFlush().collect() == ["fuzz"]

        when:
        iterator.next()
        iterator.remove()

        then:
        !iterator.hasNext()
        !iterator.hasPrevious()
        source.iteratorNoFlush().collect() == []

        and:
        source.iterator().collect() == ["bar", "baz", "buzz"]
    }

    def "listIterator can remove elements from realized collection"() {
        given:
        source.add("foo")
        source.addPending(provider("bar"))
        source.addPendingCollection(listProvider("baz", "buzz"))
        source.add("fizz")
        source.add("fuzz")
        source.realizePending()

        when:
        def iterator = source.listIterator()
        iterator.next()
        iterator.remove()

        then:
        source.iteratorNoFlush().collect() == ["bar", "baz", "buzz", "fizz", "fuzz"]

        when:
        iterator.next()
        iterator.next()
        iterator.previous()
        iterator.remove()

        then:
        source.iteratorNoFlush().collect() == ["baz", "buzz", "fizz", "fuzz"]

        when:
        iterator.next()
        iterator.remove()

        then:
        source.iteratorNoFlush().collect() == ["buzz", "fizz", "fuzz"]

        when:
        while (iterator.hasNext()) {
            iterator.next()
            iterator.remove()
        }

        then:
        !iterator.hasNext()
        !iterator.hasPrevious()
        source.iteratorNoFlush().collect() == []
    }

    def "listIterator can add elements"() {
        given:
        source.add("foo")
        source.addPending(provider("bar"))
        source.addPendingCollection(listProvider("baz", "buzz"))
        source.add("fizz")

        when:
        def iterator = source.listIterator()
        iterator.next()
        iterator.add("fuzz")

        then:
        source.iteratorNoFlush().collect() == ["foo", "fuzz", "fizz"]

        when:
        iterator = source.listIterator()
        while(iterator.hasNext()) { iterator.next() }
        iterator.add("buzz")

        then:
        source.iteratorNoFlush().collect() == ["foo", "fuzz", "fizz", "buzz"]

        when:
        iterator = source.listIterator()
        while(iterator.hasNext()) { iterator.next() }
        iterator.previous()
        iterator.add("bazz")

        then:
        source.iteratorNoFlush().collect() == ["foo", "fuzz", "fizz", "bazz", "buzz"]

        when:
        iterator = source.listIterator()
        iterator.add("bizz")

        then:
        source.iteratorNoFlush().collect() == ["bizz", "foo", "fuzz", "fizz", "bazz", "buzz"]

        and:
        source.iterator().collect() == ["bizz", "foo", "bar", "baz", "buzz", "fuzz", "fizz", "bazz", "buzz"]
    }

    def "listIterator can set elements"() {
        given:
        source.add("foo")
        source.addPending(provider("bar"))
        source.addPendingCollection(listProvider("baz", "buzz"))
        source.add("fizz")

        when:
        def iterator = source.listIterator()
        iterator.set("fuzz")

        then:
        thrown(IllegalStateException)

        when:
        iterator.next()
        iterator.set("fuzz")

        then:
        source.iteratorNoFlush().collect() == ["fuzz", "fizz"]

        when:
        iterator.next()
        iterator.set("buzz")

        then:
        source.iteratorNoFlush().collect() == ["fuzz", "buzz"]

        when:
        iterator.previous()
        iterator.set("bazz")

        then:
        source.iteratorNoFlush().collect() == ["bazz", "buzz"]

        and:
        source.iterator().collect() == ["bazz", "bar", "baz", "buzz", "buzz"]
    }

    def "listIterator provides accurate indexes"() {
        given:
        source.add("foo")
        source.addPending(provider("bar"))
        source.addPendingCollection(listProvider("baz", "buzz"))
        source.add("fizz")

        when:
        def iterator = source.listIterator()

        then:
        iterator.nextIndex() == 0
        iterator.previousIndex() == -1

        when:
        iterator.next()

        then:
        iterator.nextIndex() == 1
        iterator.previousIndex() == 0

        when:
        iterator.next()

        then:
        iterator.nextIndex() == 2
        iterator.previousIndex() == 1
        !iterator.hasNext()

        when:
        iterator.previous()

        then:
        iterator.nextIndex() == 1
        iterator.previousIndex() == 0

        when:
        iterator.previous()

        then:
        iterator.nextIndex() == 0
        iterator.previousIndex() == -1
    }

    def "can set value at specified index"() {
        given:
        source.add("foo")
        source.addPending(provider("bar"))
        source.addPendingCollection(listProvider("baz", "buzz"))
        source.add("fizz")
        source.add("fuzz")

        expect:
        source.set(1, "buzz") == "fizz"

        and:
        source.iteratorNoFlush().collect() == ["foo", "buzz", "fuzz"]

        and:
        source.set(0, "bazz") == "foo"

        and:
        source.iteratorNoFlush().collect() == ["bazz", "buzz", "fuzz"]

        and:
        source.set(2, "foo") == "fuzz"

        and:
        source.iteratorNoFlush().collect() == ["bazz", "buzz", "foo"]

        and:
        source.iterator().collect() == ["bazz", "bar", "baz", "buzz", "buzz", "foo"]
    }

    def "cannot set value at invalid index"() {
        given:
        source.add("foo")
        source.addPending(provider("bar"))

        when:
        source.set(1, "baz")

        then:
        thrown(IndexOutOfBoundsException)
    }

    def "can add value at specified index"() {
        given:
        source.add("foo")
        source.addPending(provider("bar"))
        source.addPendingCollection(listProvider("baz", "buzz"))
        source.add("fizz")

        when:
        source.add(0, "fuzz")

        then:
        source.iteratorNoFlush().collect() == ["fuzz", "foo", "fizz"]

        when:
        source.add(3, "buzz")

        then:
        source.iteratorNoFlush().collect() == ["fuzz", "foo", "fizz", "buzz"]

        when:
        source.add(1, "bazz")

        then:
        source.iteratorNoFlush().collect() == ["fuzz", "bazz", "foo", "fizz", "buzz"]

        and:
        source.iterator().collect() == ["fuzz", "bazz", "foo", "bar", "baz", "buzz", "fizz", "buzz"]
    }

    CollectionProviderInternal<? extends String, List<? extends String>> listProvider(String... values) {
        return new TypedProviderOfList<String>(String, values as List)
    }

    CollectionProviderInternal<? extends StringBuffer, List<? extends StringBuffer>> listProvider(StringBuffer... values) {
        return new TypedProviderOfList<StringBuffer>(StringBuffer, values as List)
    }

    private static class TypedProviderOfList<T> extends AbstractProvider<List<T>> implements CollectionProviderInternal<T, List<T>> {
        final Class<T> type
        final List<T> value

        TypedProviderOfList(Class<T> type, List<T> value) {
            this.type = type
            this.value = value
        }

        @Override
        Class<? extends T> getElementType() {
            return type
        }

        @Override
        List<T> getOrNull() {
            return value
        }

        @Override
        int size() {
            return value.size()
        }
    }
}
