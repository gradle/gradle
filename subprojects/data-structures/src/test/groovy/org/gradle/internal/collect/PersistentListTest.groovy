/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.internal.collect

import spock.lang.Specification

class PersistentListTest extends Specification {

    def "empty lists are the same"() {
        expect:
        PersistentList.of() == PersistentList.of()
    }

    def "lists with elements #elements are the same"() {
        expect:
        listOf(elements) == listOf(elements)

        where:
        elements << [["a"], ["a", "b"]]
    }

    def "has a nice toString method"() {
        expect:
        PersistentList.of().toString() == "Nil"
        PersistentList.of("a").toString() == "a"
        PersistentList.of("a", "b").toString() == "a : b"
        PersistentList.of("a", "b", "c").toString() == "a : b : c"
        PersistentList.of("a", "b", "c", "d").toString() == "a : b : c : d"
    }

    def "forEach iterates the elements #elements"() {
        when:
        def newList = []
        listOf(elements).forEach { newList.add(it) }
        then:
        newList == elements
        listOf(elements) == PersistentList.of(*elements)

        where:
        elements << [
            [],
            ["a"],
            ["a", "b", "c"],
            ["a", "b", "c", "d"]
        ]
    }

    private static PersistentList<String> listOf(List<String> elements) {
        return elements.reverse().inject(PersistentList.<String>of()) { acc, val -> acc + val }
    }

}
