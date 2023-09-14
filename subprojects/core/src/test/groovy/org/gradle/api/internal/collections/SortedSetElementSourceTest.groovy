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


import org.gradle.api.internal.provider.ProviderInternal
import org.gradle.api.internal.provider.ValueSupplier

class SortedSetElementSourceTest extends ElementSourceSpec {

    def provider1 = Mock(ProviderInternal)
    def provider2 = Mock(ProviderInternal)
    def provider3 = Mock(ProviderInternal)

    ElementSource source = new SortedSetElementSource<CharSequence>()

    def setup() {
        _ * provider1.calculateValue(_) >> ValueSupplier.Value.of("provider1")
        _ * provider2.calculateValue(_) >> ValueSupplier.Value.of("provider2")
        _ * provider3.calculateValue(_) >> ValueSupplier.Value.of("provider3")
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
        next == "fizz"

        when:
        iterator.remove()

        then:
        source.iteratorNoFlush().collect() == iterationOrder("foo")

        when:
        source.addPending(provider("fuzz"))
        iterator = source.iteratorNoFlush()
        next = iterator.next()

        then:
        next == "foo"

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
        next == "bar"

        when:
        iterator.remove()

        then:
        source.iterator().collect() == iterationOrder("foo", "baz", "fooz", "fizz")

        when:
        source.addPending(provider("fuzz"))
        iterator = source.iterator()
        next = iterator.next()

        then:
        next == "baz"

        when:
        iterator.remove()

        then:
        iterator.hasNext()
        source.iterator().collect() == iterationOrder("foo", "fooz", "fizz", "fuzz")

        when:
        source.add("buzz")
        iterator = source.iterator()
        next = iterator.next()

        then:
        next == "buzz"

        when:
        iterator.remove()

        then:
        iterator.hasNext()
        source.iterator().collect() == iterationOrder("foo", "fooz", "fizz", "fuzz")

        when:
        iterator = source.iterator()
        while(iterator.hasNext()) {
            iterator.next()
            iterator.remove()
        }

        then:
        source.iterator().collect() == []
    }

    def "realizes pending elements on flush"() {
        when:
        source.addPending(provider1)
        source.addPending(provider2)
        source.addPending(provider3)
        source.realizePending()

        then:
        source.iteratorNoFlush().collect() == ["provider1", "provider2", "provider3"]
    }

    def "realizes only pending elements with a given type"() {
        given:
        _ * provider1.getType() >> SomeType.class
        _ * provider2.getType() >> SomeOtherType.class
        _ * provider3.getType() >> SomeType.class

        when:
        source.addPending(provider1)
        source.addPending(provider2)
        source.addPending(provider3)
        source.realizePending(SomeType.class)

        then:
        source.iteratorNoFlush().collect() == ["provider1", "provider3"]
    }

    def "can remove pending elements"() {
        when:
        source.addPending(provider1)
        source.addPending(provider2)
        source.addPending(provider3)
        source.removePending(provider1)

        then:
        source.size() == 2

        when:
        source.realizePending()

        then:
        source.iteratorNoFlush().collect() == ["provider2", "provider3"]
    }

    def "can clear pending elements"() {
        when:
        source.addPending(provider1)
        source.addPending(provider2)
        source.addPending(provider3)
        source.clear()

        then:
        source.isEmpty()

        when:
        source.realizePending()

        then:
        source.iterator().collect() == []
    }

    class BaseType {}
    class SomeType extends BaseType {}
    class SomeOtherType extends BaseType {}

    @Override
    List<CharSequence> iterationOrder(CharSequence... values) {
        return (values as List).sort()
    }
}
