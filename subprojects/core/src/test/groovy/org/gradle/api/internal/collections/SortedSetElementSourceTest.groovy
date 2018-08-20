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


class SortedSetElementSourceTest extends AbstractElementSourceTest {
    ElementSource source = new SortedSetElementSource<CharSequence>()

    def setup() {
        source.onRealize(new Action<CharSequence>() {
            @Override
            void execute(CharSequence t) {
                source.addRealized(t)
            }
        })
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

    @Override
    List<CharSequence> iterationOrder(CharSequence... values) {
        return (values as List).sort()
    }
}
