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

abstract class AbstractElementSourceTest extends AbstractPendingSourceSpec {
    abstract ElementSource<CharSequence> getSource()

    abstract List<CharSequence> iterationOrder(CharSequence... values)

    def "can add a realized element"() {
        when:
        source.add("foo")

        then:
        source.size() == 1
        source.contains("foo")
    }

    def "can query existance of value provided"() {
        given:
        source.addPending(provider("foo"))

        expect:
        source.iteratorNoFlush().collect() == []
        source.contains("foo")
        source.iteratorNoFlush().collect() == ["foo"]
    }

    def "can query existance of values provided"() {
        given:
        source.addPendingCollection(setProvider("foo", "bar"))

        expect:
        source.iteratorNoFlush().collect() == []
        source.containsAll("foo", "bar")
        source.iteratorNoFlush().collect() == iterationOrder("foo", "bar")
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

    def "can realize specific providers"() {
        def provider1 = provider("foo")
        def provider2 = setProvider("fuzz", "bazz")

        when:
        source.addPending(provider1)
        source.add("bar")
        source.add("baz")
        source.addPending(provider("fizz"))
        source.addPendingCollection(provider2)

        then:
        source.iteratorNoFlush().collect() == iterationOrder("bar", "baz")

        when:
        source.realizePending(provider1)

        then:
        source.iteratorNoFlush().collect() == iterationOrder("foo", "bar", "baz")

        when:
        source.realizePending(provider2)

        then:
        source.iteratorNoFlush().collect() == iterationOrder("foo", "bar", "baz", "fuzz", "bazz")
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

    def "comodification with iterator causes an exception" () {
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
}
