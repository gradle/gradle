/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal

import org.gradle.api.DomainObjectCollection

import spock.lang.*

class CompositeDomainObjectCollectionTest extends Specification {

    Class type = String

    protected collection(Object... entries) {
        def collection = new MutableDomainObjectContainer(type)
        entries.each { collection.addObject(it) }
        collection
    }

    protected composite(DomainObjectCollection... collections) {
        new CompositeDomainObjectCollection(type, *collections)
    }

    def "empty composite contains no elements"() {
        expect:
        composite().all.empty
    }

    def "composite containing one collection"() {
        expect:
        composite(collection("a", "b")).all.toList() == ["a", "b"]
    }

    def "composite containing two collections"() {
        expect:
        composite(collection("a", "b"), collection("c", "d")).all.toList() == ["a", "b", "c", "d"]
    }

    def "combined collection contains additions and removals"() {
        given:
        def component1 = collection("a", "b")
        def component2 = collection("c", "d")
        def composite = composite(component1, component2)

        expect:
        composite.all.toList() == ["a", "b", "c", "d"]

        when:
        component1.addObject("e")
        component2.removeObject("d")

        then:
        composite.all.toList() == ["a", "b", "e", "c"]
    }

    def "all action called for all existing items"() {
        given:
        def composite = composite(collection("a", "b"), collection("c", "d"))

        when:
        def calledFor = []
        composite.all { calledFor << it }

        then:
        calledFor == ["a", "b", "c", "d"]
    }

    def "all callback called when items added to component of composite"() {
        given:
        def component1 = collection("a")
        def component2 = collection("b")
        def composite = composite(component1, component2)

        when:
        def calledFor = []
        composite.all { calledFor << it }

        then:
        calledFor == ["a", "b"]

        when:
        component1.addObject("c")
        component2.addObject("d")

        then:
        calledFor == ["a", "b", "c", "d"]
    }

    def "added callback called when items added to component of composite"() {
        given:
        def component1 = collection("a")
        def component2 = collection("b")
        def composite = composite(component1, component2)

        when:
        def calledFor = []
        composite.whenObjectAdded { calledFor << it }

        then:
        calledFor == []

        when:
        component1.addObject("c")
        component2.addObject("d")

        then:
        calledFor == ["c", "d"]
    }

    def "all callback called when component added to composite"() {
        given:
        def component1 = collection("a", "b")
        def component2 = collection("c", "d")
        def composite = composite(component1)

        when:
        def calledFor = []
        composite.all { calledFor << it }

        then:
        calledFor == ["a", "b"]

        when:
        composite.addCollection(component2)

        then:
        calledFor == ["a", "b", "c", "d"]
    }

    def "removed callback called when removed from composite"() {
        given:
        def component1 = collection("a", "b")
        def component2 = collection("c", "d")
        def composite = composite(component1, component2)

        when:
        def calledFor = []
        composite.whenObjectRemoved { calledFor << it }

        then:
        calledFor == []

        when:
        component1.removeObject("b")
        component2.removeObject("d")

        then:
        calledFor == ["b", "d"]
    }

    def "filtered collection is live"() {
        given:
        def component1 = collection("a", "j")
        def component2 = collection("b", "k")
        def composite = composite(component1, component2)
        def filtered = composite.matching { it > "d" }

        expect:
        filtered.all.toList() == ["j", "k"]

        when:
        component1.addObject("c")
        component1.addObject("l")
        component2.addObject("d")
        component2.addObject("m")

        then:
        filtered.all.toList() == ["j", "l", "k", "m"]

        when:
        component1.removeObject("c")
        component1.removeObject("l")
        component2.removeObject("d")
        component2.removeObject("m")

        then:
        filtered.all.toList() == ["j", "k"]

        when:
        composite.addCollection collection("c", "e")

        then:
        filtered.all.toList() == ["j", "k", "e"]
    }

    def "filtered collection callbacks live"() {
        given:
        def component1 = collection("a", "j")
        def component2 = collection("b", "k")
        def composite = composite(component1, component2)
        def filtered = composite.matching { it > "d" }
        def calledFor = []

        when:
        filtered.all { calledFor << it }

        then:
        calledFor == ["j", "k"]

        when:
        calledFor.clear()
        component1.addObject("c")
        component1.addObject("l")
        component2.addObject("d")
        component2.addObject("m")

        then:
        calledFor == ["l", "m"]

        when:
        calledFor.clear()

        and:
        filtered.whenObjectRemoved { calledFor << it }
        component1.removeObject("c")
        component1.removeObject("l")
        component2.removeObject("d")
        component2.removeObject("m")

        then:
        calledFor == ["l", "m"]

        when:
        calledFor.clear()
        composite.addCollection collection("c", "e")

        then:
        calledFor == ["e"]
    }

}