/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.execution.plan

import spock.lang.Specification

class LazySortedReferenceHashSetTest extends Specification {

    def set = new NodeSets.LazySortedReferenceHashSet<String>({ s1, s2 -> s1.compareTo(s2) })

    def "reference set behavior"() {
        expect:
        set.add("foo")
        set.size() == 1
        set.contains("foo")
        !set.contains("bar")
        set.add("bar")
        set.size() == 2
        set.contains("foo")
        set.contains("bar")
        set.containsAll(["foo", "bar"])
        !set.add("foo")
        !set.add("bar")
        set.toList() == ["bar", "foo"]
        set.remove("foo")
        set.size() == 1
        !set.contains("foo")
        set.toList() == ["bar"]
        // set is identity based
        set.add(new String("bar"))
        set.toList() == ["bar", "bar"]
    }

    def "iterators can remove elements"() {
        given:
        set.addAll(["1", "2", "3"])

        expect:
        def it = set.iterator()
        it.next() == "1"
        it.remove()
        !set.contains("1")
        it.next() == "2"
        it.next() == "3"
        it.remove()
        !set.contains("3")
        set.contains("2")
        !it.hasNext()
    }

    def "iterators throw ConcurrentModificationException on direct set modification"() {
        given:
        set.addAll(["1", "2", "3"])
        def it = set.iterator()

        when:
        set.add("4")
        it.next()

        then:
        thrown(ConcurrentModificationException)
    }

    def "iterators throw ConcurrentModificationException on iterator set modification"() {
        given:
        set.addAll(["1", "2", "3"])
        def it1 = set.iterator()
        def it2 = set.iterator()
        assert it1.next() == "1"
        assert it2.next() == "1"
        it1.remove()

        when:
        it2.next()

        then:
        thrown(ConcurrentModificationException)
    }
}
