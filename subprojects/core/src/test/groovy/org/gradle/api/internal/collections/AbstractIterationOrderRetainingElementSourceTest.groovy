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

abstract class AbstractIterationOrderRetainingElementSourceTest extends ElementSourceSpec {
    @Override
    List<CharSequence> iterationOrder(CharSequence... values) {
        return values as List
    }

    def "can realize a filtered set of providers and order is correct"() {
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
        source.iteratorNoFlush().collect { it.toString() } == iterationOrder("bar", "baz", "fuzz", "bazz")

        and:
        source.iterator().collect { it.toString() } == iterationOrder("foo", "bar", "baz", "fizz", "fuzz", "bazz")
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
        source.iteratorNoFlush().collect() == iterationOrder("fizz")

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
        source.iterator().collect() == iterationOrder("bar", "baz", "fooz", "fuzz")
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
        source.iterator().collect() == iterationOrder("baz", "fooz", "fizz", "fuzz")

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

    def "comodification of pending elements causes exception"() {
        given:
        def provider = provider("bar")
        source.add("foo")
        source.addPending(provider)

        when:
        def iterator = source.iteratorNoFlush()
        source.removePending(provider)
        iterator.next()

        then:
        thrown(ConcurrentModificationException)
    }
}
