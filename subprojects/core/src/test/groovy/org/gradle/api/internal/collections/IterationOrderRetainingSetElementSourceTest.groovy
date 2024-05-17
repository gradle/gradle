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

class IterationOrderRetainingSetElementSourceTest extends AbstractIterationOrderRetainingElementSourceTest {
    IterationOrderRetainingSetElementSource<CharSequence> source = new IterationOrderRetainingSetElementSource<>()

    def "can add the same provider twice"() {
        def provider = provider("foo")

        when:
        source.addPending(provider)
        source.addPending(provider)

        then:
        source.size() == 1
        source.contains("foo")
    }

    def "an element added as both a provider and a realized value is not duplicated"() {
        when:
        source.add("foo")
        source.addPending(provider("foo"))

        then:
        source.iterator().collect() == ["foo"]
    }

    def "an element added as both a provider and a realized value has the correct order"() {
        when:
        source.addPending(provider("foo"))
        source.add("bar")
        source.add("foo")

        then:
        source.iterator().collect() == ["foo", "bar"]
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

    def "can re-add an ordinary value after removal"() {
        when:
        source.add("a")
        source.add("b")
        source.remove("a")

        then:
        source.iterator().collect() == ["b"]

        when:
        source.add("a")

        then:
        source.iterator().collect() == ["b", "a"]
    }

    def "can re-add an ordinary value after clear"() {
        when:
        source.add("a")
        source.add("b")
        source.clear()

        then:
        source.iterator().collect() == []

        when:
        source.add("a")
        source.add("b")

        then:
        source.iterator().collect() == ["a", "b"]

        when:
        source.clear()
        source.add("b")
        source.add("a")

        then:
        source.iterator().collect() == ["b", "a"]
    }

    def "duplicates are handled when values change"() {
        def provider1 = setProvider("foo", "bar", "baz")

        when:
        source.add("foo")
        source.addPendingCollection(provider1)

        then:
        source.iterator().collect() == ["foo", "bar", "baz"]

        when:
        provider1.value = ["buzz", "fizz", "foo"]

        then:
        source.iterator().collect() == ["foo", "buzz", "fizz"]
    }

    def "adding is not ignored when a realized provider currently has the same value"() {
        def provider1 = setProvider("foo")

        when:
        source.addPendingCollection(provider1)
        source.realizePending()
        boolean addResult = source.add("foo")
        provider1.value = []

        then:
        addResult
        source.iterator().collect() == ["foo"]
    }

    def "adding is not ignored when an unrealized provider currently has the same value"() {
        def provider1 = setProvider("foo")

        when:
        source.addPendingCollection(provider1)
        boolean addResult = source.add("foo")
        provider1.value = []

        then:
        addResult
        source.iterator().collect() == ["foo"]
    }
}
