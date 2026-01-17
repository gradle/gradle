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
import org.gradle.util.TestUtil
import spock.lang.Specification

class CompositeDomainObjectSetTest extends Specification {

    Class type = String

    protected collection(Object... entries) {
        def collection = new DefaultDomainObjectSet(type, CollectionCallbackActionDecorator.NOOP)
        entries.each { collection.add(it) }
        collection
    }

    private collectionProvider(Object... entries) {
        return TestUtil.providerFactory().provider { collection(entries) }
    }

    protected composite(DomainObjectCollection... collections) {
        CompositeDomainObjectSet.create(type, *collections)
    }

    def "empty composite contains no elements"() {
        expect:
        composite().empty
    }

    def "composite containing one collection"() {
        expect:
        composite(collection("a", "b")).toList() == ["a", "b"]
    }

    def "composite containing two collections"() {
        expect:
        composite(collection("a", "b"), collection("c", "d")).toList() == ["a", "b", "c", "d"]
    }

    def "can create composite from collection providers"() {
        when:
        def composite = composite()
        composite.addCollectionProvider(collectionProvider("a", "b"))
        composite.addCollectionProvider(collectionProvider("c", "d"))

        then:
        composite.toList() == ["a", "b", "c", "d"]
    }

    def "can mix collection providers and collections in composite"() {
        when:
        def composite = composite()
        composite.addCollectionProvider(collectionProvider("a", "b"))
        composite.addCollection(collection("c", "d"))

        then:
        composite.toList() == ["a", "b", "c", "d"]
    }

    def "combined collection contains additions and removals"() {
        given:
        def component1 = collection("a", "b")
        def component2 = collection("c", "d")
        def composite = composite(component1, component2)

        expect:
        composite.toList() == ["a", "b", "c", "d"]

        when:
        component1.add("e")
        component2.remove("d")

        then:
        composite.toList() == ["a", "b", "e", "c"]
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

    def "all action called for all items in provided collections"() {
        given:
        def composite = composite(collection("a", "b"))
        composite.addCollectionProvider(collectionProvider("c", "d"))

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
        component1.add("c")
        component2.add("d")

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
        component1.add("c")
        component2.add("d")

        then:
        calledFor == ["c", "d"]
    }

    def "added callback called when items realized in provided component of composite"() {
        given:
        def component1 = collectionProvider("a")
        def component2 = collectionProvider("b")
        def composite = composite()
        composite.addCollectionProvider(component1)
        composite.addCollectionProvider(component2)

        when:
        def calledFor = []
        composite.whenObjectAdded { calledFor << it }

        then:
        calledFor == []

        when:
        composite.iterator()

        then:
        calledFor == ["a", "b"]
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
        component1.remove("b")
        component2.remove("d")

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
        filtered.toList() == ["j", "k"]

        when:
        component1.add("c")
        component1.add("l")
        component2.add("d")
        component2.add("m")

        then:
        filtered.toList() == ["j", "l", "k", "m"]

        when:
        component1.remove("c")
        component1.remove("l")
        component2.remove("d")
        component2.remove("m")

        then:
        filtered.toList() == ["j", "k"]

        when:
        composite.addCollection collection("c", "e")

        then:
        filtered.toList() == ["j", "k", "e"]
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
        component1.add("c")
        component1.add("l")
        component2.add("d")
        component2.add("m")

        then:
        calledFor == ["l", "m"]

        when:
        calledFor.clear()

        and:
        filtered.whenObjectRemoved { calledFor << it }
        component1.remove("c")
        component1.remove("l")
        component2.remove("d")
        component2.remove("m")

        then:
        calledFor == ["l", "m"]

        when:
        calledFor.clear()
        composite.addCollection collection("c", "e")

        then:
        calledFor == ["e"]
    }

    def "callbacks called for composite of composites"() {
        given:
        def component1 = collection("a")
        def component2 = collection("b")
        def component3 = collection("c")
        def component4 = collection("d")

        def composite1 = composite(component1, component2)
        def composite2 = composite(component3, component4)

        def superComposite = composite(composite1, composite2)

        def calledFor = []

        expect:
        superComposite.toList() == ["a", "b", "c", "d"]

        when:
        superComposite.all { calledFor << it }

        then:
        calledFor == ["a", "b", "c", "d"]

        when:
        calledFor.clear()
        component1.add("j")
        component2.add("k")
        component3.add("l")
        component4.add("m")

        then:
        calledFor == ["j", "k", "l", "m"]

        and:
        superComposite.toSet() == ["a", "j", "b", "k", "c", "l", "d", "m"].toSet()

        when:
        superComposite.whenObjectRemoved { calledFor << it }

        and:
        calledFor.clear()
        component1.remove("j")
        component2.remove("k")
        component3.remove("l")
        component4.remove("m")

        then:
        calledFor == ["j", "k", "l", "m"]

        and:
        superComposite.toList() == ["a", "b", "c", "d"]
    }

    def "filtered composite of composites is live"() {
        given:
        def component1 = collection("a")
        def component2 = collection("b")
        def component3 = collection("j")
        def component4 = collection("k")

        def composite1 = composite(component1, component2)
        def composite2 = composite(component3, component4)

        def superComposite = composite(composite1, composite2)
        def filtered = superComposite.matching { it < "g" }

        expect:
        filtered.toList() == ["a", "b"]

        when:
        component1.add("j")
        component2.add("k")
        component3.add("c")
        component4.add("d")

        then:
        filtered.toList() == ["a", "b", "c", "d"]

        when:
        component1.remove("j")
        component2.remove("k")
        component3.remove("c")
        component4.remove("d")

        then:
        filtered.toList() == ["a", "b"]
    }

    def "duplicates in composite are flattened"() {
        expect:
        composite(collection("a", "b"), collection("b", "c"), collection("c", "d"))*.toString() == ["a", "b", "c", "d"]
    }

    def "add notifications are only fired for new in composite"() {
        given:
        def component1 = collection("a")
        def component2 = collection("b")
        def composite = composite(component1, component2)
        def calledFor = []

        when:
        composite.whenObjectAdded { calledFor << it }

        and:
        component1 << "a" << "c"

        then:
        calledFor == ["c"]

        when:
        component2 << "a" << "d" << "a"

        then:
        calledFor == ["c", "d"]
    }

    def "all notifications are only fired once for each provided item in composite"() {
        given:
        def component1 = collection("a")
        def component2 = collectionProvider("a", "b")
        def composite = composite(component1)
        composite.addCollectionProvider(component2)
        def calledFor = []

        when:
        composite.all {
            calledFor << it
        }

        then:
        calledFor == ["a", "b"]

        when:
        component1 << "a" << "c"
        composite.iterator()

        then:
        calledFor == ["a", "b", "c"]
    }

    def "remove notifications are only fired for removed in composite"() {
        given:
        def component1 = collection("a", "b")
        def component2 = collection("a", "b", "c")
        def composite = composite(component1, component2)
        def calledFor = []

        when:
        composite.whenObjectRemoved { calledFor << it }

        and:
        component1.remove("a")
        component2.remove("b")
        component2.remove("c")

        then:
        calledFor == ["c"]
    }

    def "remove notifications are only fired for removed in composite with providers"() {
        given:
        def component1 = collectionProvider("a", "b")
        def component2 = collection("a", "b", "c")
        def composite = composite()
        composite.addCollectionProvider(component1)
        composite.addCollection(component2)
        def calledFor = []

        when:
        composite.whenObjectRemoved { calledFor << it }

        and:
        component2.remove("b")
        component2.remove("c")

        then:
        calledFor == ["c"]
    }

    def "composite is immutable"() {
        when:
        composite(collection("a")).add("b")

        then:
        thrown UnsupportedOperationException
    }

    def "behaves when the same collection added"() {
        def same = collection("a", "b")
        def sameProvider = collectionProvider("a", "b")
        def composite = composite(same, same, same)
        composite.addCollectionProvider(sameProvider)

        expect:
        composite.toList() == ['a', 'b']

        when:
        same << 'c'

        then:
        composite.toList() == ['a', 'b', 'c']
    }

    def "removing collection removes all instances"() {
        def instance = collection("a", "b")
        def composite = composite(instance, instance)

        when:
        composite.removeCollection(instance)

        then:
        composite.toList() == []
    }

    def "removing collection provider fires whenObjectRemoved in provided collections when realized"() {
        def collection = collection("a", "b")
        def collectionProvider = collectionProvider("c", "d")
        def composite = composite()
        def removed = []
        def added = []
        composite.whenObjectAdded { added << it }
        composite.whenObjectRemoved { removed << it }
        composite.addCollection(collection)
        composite.addCollectionProvider(collectionProvider)

        when:
        composite.iterator()
        composite.removeCollectionProvider(collectionProvider)

        then:
        composite.toList() == ["a", "b"]
        added == ["a", "b", "c", "d"]
        removed == ["c", "d"]
    }

    def "removing collection provider does not fire whenObjectRemoved in provided collections when not realized"() {
        def collection = collection("a", "b")
        def collectionProvider = collectionProvider("c", "d")
        def composite = composite()
        def removed = []
        def added = []
        composite.whenObjectAdded { added << it }
        composite.whenObjectRemoved { removed << it }
        composite.addCollection(collection)
        composite.addCollectionProvider(collectionProvider)

        when:
        composite.removeCollectionProvider(collectionProvider)

        then:
        composite.toList() == ["a", "b"]
        added == ["a", "b"]
        removed == []
    }
}
