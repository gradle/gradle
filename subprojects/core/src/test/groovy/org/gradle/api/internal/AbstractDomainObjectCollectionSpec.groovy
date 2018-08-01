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

package org.gradle.api.internal

import org.gradle.api.Action
import org.gradle.api.DomainObjectCollection
import org.gradle.api.internal.provider.CollectionProviderInternal
import org.gradle.api.internal.provider.ProviderInternal
import spock.lang.Specification

import static org.gradle.util.WrapUtil.toList

abstract class AbstractDomainObjectCollectionSpec<T> extends Specification {
    abstract DomainObjectCollection<T> getContainer()

    abstract T getA()

    abstract T getB()

    abstract T getC()

    abstract T getD()

    Class<T> getType() {
        return a.class
    }

    Class<T> getOtherType() {
        return d.class
    }

    List<T> iterationOrder(T... elements) {
        return elements
    }

    def setup() {
        // Verify some assumptions
        assert !type.isAssignableFrom(otherType) && !otherType.isAssignableFrom(type)
        assert type.isInstance(a)
        assert otherType.isInstance(d)
    }

    def "can get all domain objects for empty collection"() {
        expect:
        container.isEmpty()
        container.size() == 0
    }

    def "can iterate over empty collection"() {
        expect:
        def iterator = container.iterator()
        !iterator.hasNext()
    }

    def "element added using provider is not realized when added"() {
        def provider = Mock(ProviderInternal)

        when:
        container.addLater(provider)

        then:
        _ * provider.type >> type
        0 * provider._

        and:
        container.size() == 1
        !container.empty
    }

    def "elements added using provider of iterable are not realized when added"() {
        def provider = Mock(CollectionProviderInternal)
        _ * provider.size() >> 2

        when:
        container.addAllLater(provider)

        then:
        _ * provider.elementType >> type
        0 * provider._

        and:
        container.size() == 2
        !container.empty
    }

    def "provider of elements are not queried when another element added"() {
        def provider = Mock(ProviderInternal)
        def providerOfIterable = Mock(CollectionProviderInternal)
        _ * providerOfIterable.size() >> 2

        given:
        container.add(a)
        container.addLater(provider)
        container.addAllLater(providerOfIterable)

        when:
        container.add(b)

        then:
        0 * provider._
        0 * providerOfIterable._

        and:
        container.size() == 5
        !container.empty
    }

    def "can check for membership"() {
        given:
        container.add(b)
        container.add(a)

        expect:
        !container.contains(c)
        container.contains(a)
    }

    def "provider for element is queried when membership checked"() {
        def provider = Mock(ProviderInternal)

        given:
        container.add(b)
        container.addLater(provider)

        when:
        def result = container.contains(c)

        then:
        !result

        and:
        1 * provider.get() >> a
    }

    def "provider for iterable of elements is queried when membership checked"() {
        def provider = Mock(CollectionProviderInternal)

        given:
        container.add(b)
        container.addAllLater(provider)

        when:
        def result = container.contains(c)

        then:
        !result

        and:
        1 * provider.get() >> [a, d]
    }

    def "can get all domain objects ordered by order added"() {
        given:
        container.add(b)
        container.add(a)
        container.add(c)

        expect:
        toList(container) == iterationOrder(b, a, c)
    }

    def "can iterate over domain objects ordered by order added"() {
        container.add(b)
        container.add(a)
        container.add(c)

        expect:
        def seen = []
        def iterator = container.iterator()
        seen << iterator.next()
        seen << iterator.next()
        seen << iterator.next()
        !iterator.hasNext()
        seen == iterationOrder(b, a, c)
    }

    def "provider for element is queried when elements iterated and insertion order is retained"() {
        def provider1 = Mock(ProviderInternal)
        def provider2 = Mock(ProviderInternal)

        given:
        container.add(b)
        container.addLater(provider1)
        container.addLater(provider2)
        container.add(c)

        when:
        def result = toList(container)

        then:
        result == iterationOrder(b, a, d, c)

        and:
        1 * provider1.get() >> a
        1 * provider2.get() >> d
        0 * _
    }

    def "provider for iterable of elements is queried when elements iterated and insertion order is retained"() {
        def provider1 = Mock(CollectionProviderInternal)

        given:
        container.add(b)
        container.addAllLater(provider1)
        container.add(c)

        when:
        def result = toList(container)

        then:
        result == iterationOrder(b, a, d, c)

        and:
        _ * provider1.size() >> 2
        1 * provider1.get() >> [a, d]
        0 * _
    }

    def "can execute action for all elements in a collection"() {
        def action = Mock(Action)

        container.add(c)
        container.add(d)

        when:
        container.all(action)

        then:
        1 * action.execute(c)
        1 * action.execute(d)
        0 * action._

        when:
        container.add(a)

        then:
        1 * action.execute(a)
        0 * action._
    }

    def "queries provider for element when registering action for all elements in a collection"() {
        def action = Mock(Action)
        def provider1 = Mock(ProviderInternal)
        def provider2 = Mock(ProviderInternal)

        container.addLater(provider1)

        when:
        container.all(action)

        then:
        1 * action.execute(c)
        _ * provider1.type >> type
        1 * provider1.get() >> c
        0 * _

        when:
        container.addLater(provider2)

        then:
        1 * action.execute(a)
        _ * provider2.type >> type
        1 * provider2.get() >> a
        0 * _
    }

    def "queries provider for iterable of elements when registering action for all elements in a collection"() {
        def action = Mock(Action)
        def provider1 = Mock(CollectionProviderInternal)
        def provider2 = Mock(CollectionProviderInternal)

        container.addAllLater(provider1)

        when:
        container.all(action)

        then:
        1 * action.execute(c)
        _ * provider1.elementType >> type
        1 * provider1.get() >> [c]
        _ * provider1.size() >> 1
        0 * _

        when:
        container.addAllLater(provider2)

        then:
        1 * action.execute(a)
        1 * action.execute(b)
        _ * provider2.elementType >> type
        1 * provider2.get() >> [a, b]
        0 * _
    }

    def "can get filtered collection containing all objects which have type"() {
        container.add(c)
        container.add(a)
        container.add(d)

        expect:
        toList(container.withType(type)) == iterationOrder(c, a)
        toList(container.withType(otherType)) == iterationOrder(d)
    }

    def "provider for element is queried when filtered collection with matching type created"() {
        def provider = Mock(ProviderInternal)

        container.add(c)
        container.addLater(provider)
        container.add(d)

        when:
        def filtered = container.withType(type)

        then:
        0 * provider._

        when:
        def result = toList(filtered)

        then:
        result == iterationOrder(c, a)
        _ * provider.type >> type
        1 * provider.get() >> a
        0 * provider._

        when:
        def result2 = toList(container)

        then:
        result2 == iterationOrder(c, a, d)
        0 * provider._
    }

    def "provider for iterable of elements is queried when filtered collection with matching type created"() {
        def provider = Mock(CollectionProviderInternal)
        _ * provider.elementType >> type

        container.add(c)
        container.addAllLater(provider)
        container.add(d)

        when:
        def filtered = container.withType(type)

        then:
        0 * provider._

        when:
        def result = toList(filtered)

        then:
        result == iterationOrder(c, a, b)
        _ * provider.size() >> 2
        1 * provider.get() >> [a, b]
        0 * provider._

        when:
        def result2 = toList(container)

        then:
        result2 == iterationOrder(c, a, b, d)
        0 * provider._
    }

    def "provider for element is not queried when filtered collection with non matching type created"() {
        def provider = Mock(ProviderInternal)
        _ * provider.type >> type

        container.add(c)
        container.addLater(provider)
        container.add(d)

        when:
        def filtered = container.withType(otherType)

        then:
        0 * provider._

        when:
        def result = toList(filtered)

        then:
        result == iterationOrder(d)
        0 * provider._

        when:
        def result2 = toList(container)

        then:
        result2 == iterationOrder(c, a, d)
        1 * provider.get() >> a
        0 * provider._
    }

    def "provider for iterable of elements is not queried when filtered collection with non matching type created"() {
        def provider = Mock(CollectionProviderInternal)
        _ * provider.elementType >> type

        container.add(c)
        container.addAllLater(provider)
        container.add(d)

        when:
        def filtered = container.withType(otherType)

        then:
        0 * provider._

        when:
        def result = toList(filtered)

        then:
        result == iterationOrder(d)
        _ * provider.size() >> 2
        0 * provider._

        when:
        def result2 = toList(container)

        then:
        result2 == iterationOrder(c, a, b, d)
        _ * provider.size() >> 2
        1 * provider.get() >> [a, b]
        0 * provider._
    }

    def "can execute action for all elements in a type filtered collection"() {
        def action = Mock(Action)

        container.add(c)
        container.add(d)

        when:
        container.withType(type, action)

        then:
        1 * action.execute(c)
        0 * action._

        when:
        container.add(a)

        then:
        1 * action.execute(a)
        0 * action._
    }

    def "can execute closure for all elements in a type filtered collection"() {
        def seen = []
        def closure = { seen << it }

        container.add(c)
        container.add(d)

        when:
        container.withType(type, closure)

        then:
        seen == [c]

        when:
        container.add(a)

        then:
        seen == [c, a]
    }

    def "action for all elements in a type filtered collection can add more elements"() {
        def action = Mock(Action)

        container.add(c)

        when:
        container.withType(type, action)

        then:
        1 * action.execute(c) >> {
            container.add(d)
            container.add(a)
        }
        1 * action.execute(a)
        0 * action._
    }

    def "provider for element is queried and action executed for filtered collection with matching type"() {
        def action = Mock(Action)
        def provider1 = Mock(ProviderInternal)
        def provider2 = Mock(ProviderInternal)

        container.addLater(provider1)
        container.add(d)

        when:
        container.withType(type, action)

        then:
        _ * provider1.type >> type
        1 * provider1.get() >> c
        1 * action.execute(c)
        0 * _

        when:
        container.addLater(provider2)

        then:
        _ * provider2.type >> type
        1 * provider2.get() >> a
        1 * action.execute(a)
        0 * _
    }

    def "provider for iterable of elements is queried and action executed for filtered collection with matching type"() {
        def action = Mock(Action)
        def provider1 = Mock(CollectionProviderInternal)
        def provider2 = Mock(CollectionProviderInternal)

        container.addAllLater(provider1)
        container.add(d)

        when:
        container.withType(type, action)

        then:
        _ * provider1.elementType >> type
        _ * provider1.size() >> 1
        1 * provider1.get() >> [c]
        1 * action.execute(c)
        0 * _

        when:
        container.addAllLater(provider2)

        then:
        _ * provider2.elementType >> type
        1 * provider2.get() >> [a, b]
        1 * action.execute(a)
        1 * action.execute(b)
        0 * _
    }

    def "provider for element is not queried and action executed for filtered collection with non matching type"() {
        def action = Mock(Action)
        def provider1 = Mock(ProviderInternal)
        def provider2 = Mock(ProviderInternal)
        _ * provider1.type >> type

        container.addLater(provider1)
        container.add(d)

        when:
        container.withType(otherType, action)

        then:
        1 * action.execute(d)
        0 * _

        when:
        container.addLater(provider2)

        then:
        _ * provider2.type >> type
        0 * _
    }

    def "provider for iterable of elements is not queried and action executed for filtered collection with non matching type"() {
        def action = Mock(Action)
        def provider1 = Mock(CollectionProviderInternal)
        def provider2 = Mock(CollectionProviderInternal)
        _ * provider1.elementType >> type

        container.addAllLater(provider1)
        container.add(d)

        when:
        container.withType(otherType, action)

        then:
        1 * action.execute(d)
        _ * provider1.size() >> 1
        0 * _

        when:
        container.addAllLater(provider2)

        then:
        _ * provider2.elementType >> type
        0 * _
    }

    def "can execute action to configure element when element is realized"() {
        def action = Mock(Action)
        def provider1 = Mock(ProviderInternal)
        def provider2 = Mock(ProviderInternal)
        def provider3 = Mock(CollectionProviderInternal)

        given:
        container.addLater(provider1)

        when:
        container.configureEach(action)

        then:
        0 * _

        when:
        toList(container)

        then:
        1 * provider1.get() >> a
        1 * action.execute(a)
        0 * _

        when:
        container.addLater(provider2)

        then:
        _ * provider2.type >> type
        0 * _

        when:
        toList(container)

        then:
        1 * provider2.get() >> c
        1 * action.execute(c)
        0 * _

        when:
        container.addAllLater(provider3)

        then:
        _ * provider3.elementType >> type
        0 * _

        when:
        toList(container)

        then:
        _ * provider3.size() >> 2
        1 * provider3.get() >> [b, d]
        1 * action.execute(b)
        1 * action.execute(d)
        0 * _
    }

    def "runs configure element action immediately when element already realized"() {
        def action = Mock(Action)
        def provider = Mock(ProviderInternal)
        def providerOfIterable = Mock(CollectionProviderInternal)

        given:
        _ * provider.get() >> a
        _ * providerOfIterable.get() >> [b, c]
        container.addLater(provider)
        container.addAllLater(providerOfIterable)
        toList(container)

        when:
        container.configureEach(action)

        then:
        _ * providerOfIterable.size() >> 2
        1 * action.execute(a)
        1 * action.execute(b)
        1 * action.execute(c)
        0 * _
    }

    def "runs configure element action immediately when element added directly"() {
        def action = Mock(Action)

        given:
        container.configureEach(action)

        when:
        container.add(a)

        then:
        1 * action.execute(a)
        0 * _
    }

    def "can execute action to configure element of given type when element is realized"() {
        def action = Mock(Action)
        def provider1 = Mock(ProviderInternal)
        def provider2 = Mock(ProviderInternal)

        given:
        container.addLater(provider1)
        container.addLater(provider2)

        when:
        container.withType(type).configureEach(action)

        then:
        0 * _

        when:
        toList(container)

        then:
        _ * provider1.type >> type
        1 * provider1.get() >> a
        1 * action.execute(a)
        _ * provider2.type >> otherType
        1 * provider2.get() >> d
        0 * _

        when:
        toList(container)

        then:
        0 * _
    }

    def "can execute action to configure elements of given type when iterable of elements is realized"() {
        def action = Mock(Action)
        def provider1 = Mock(CollectionProviderInternal)
        def provider2 = Mock(CollectionProviderInternal)

        given:
        container.addAllLater(provider1)
        container.addAllLater(provider2)

        when:
        container.withType(type).configureEach(action)

        then:
        _ * provider1.size() >> 2
        _ * provider2.size() >> 1
        0 * _

        when:
        toList(container)

        then:
        _ * provider1.elementType >> type
        _ * provider1.size() >> 2
        1 * provider1.get() >> [a, c]
        1 * action.execute(a)
        1 * action.execute(c)
        _ * provider2.elementType >> otherType
        _ * provider2.size() >> 1
        1 * provider2.get() >> [d]
        0 * _

        when:
        toList(container)

        then:
        0 * _
    }

    def "provider is not queried or element configured until collection is realized when lazy action is registered on type-filtered collection"() {
        def action = Mock(Action)
        def provider1 = Mock(ProviderInternal)
        def provider2 = Mock(ProviderInternal)

        given:
        _ * provider1.type >> type
        _ * provider2.type >> type
        container.addLater(provider1)
        container.addLater(provider2)
        def filtered = container.withType(type)

        when:
        filtered.configureEach(action)

        then:
        0 * provider1.get()
        0 * provider2.get()
        0 * action.execute(_)

        when:
        def result = toList(filtered)

        then:
        1 * provider1.get() >> a
        1 * provider2.get() >> b
        result == iterationOrder(a, b)

        and:
        1 * action.execute(a)
        1 * action.execute(b)
    }

    def "provider of iterable is not queried or elements configured until collection is realized when lazy action is registered on type-filtered collection"() {
        def action = Mock(Action)
        def provider1 = Mock(CollectionProviderInternal)
        def provider2 = Mock(CollectionProviderInternal)

        given:
        _ * provider1.elementType >> type
        _ * provider2.elementType >> type
        container.addAllLater(provider1)
        container.addAllLater(provider2)
        def filtered = container.withType(type)

        when:
        filtered.configureEach(action)

        then:
        0 * provider1.get()
        0 * provider2.get()
        0 * action.execute(_)

        when:
        def result = toList(filtered)

        then:
        _ * provider1.size() >> 1
        _ * provider2.size() >> 2
        1 * provider1.get() >> [a]
        1 * provider2.get() >> [c, b]
        result == iterationOrder(a, c, b)

        and:
        1 * action.execute(a)
        1 * action.execute(c)
        1 * action.execute(b)
    }

    def "only realized elements of given type are configured when lazy action is registered on type-filtered collection"() {
        def action = Mock(Action)
        def provider1 = Mock(ProviderInternal)
        def provider2 = Mock(ProviderInternal)

        given:
        _ * provider1.type >> otherType
        _ * provider2.type >> type
        container.addLater(provider1)
        container.addLater(provider2)
        def filtered = container.withType(type)

        when:
        filtered.configureEach(action)

        then:
        0 * provider1.get()
        0 * provider2.get()
        0 * action.execute(_)

        when:
        def result = toList(filtered)

        then:
        0 * provider1.get() >> a
        1 * provider2.get() >> b
        result == iterationOrder(b)

        and:
        0 * action.execute(a)
        1 * action.execute(b)
    }

    def "only realized elements of iterable with given type are configured when lazy action is registered on type-filtered collection"() {
        def action = Mock(Action)
        def provider1 = Mock(CollectionProviderInternal)
        def provider2 = Mock(CollectionProviderInternal)

        given:
        _ * provider1.elementType >> otherType
        _ * provider2.type >> type
        container.addAllLater(provider1)
        container.addAllLater(provider2)
        def filtered = container.withType(type)

        when:
        filtered.configureEach(action)

        then:
        0 * provider1.get()
        0 * provider2.get()
        0 * action.execute(_)

        when:
        def result = toList(filtered)

        then:
        _ * provider1.size() >> 1
        _ * provider2.size() >> 2
        0 * provider1.get() >> [a]
        1 * provider2.get() >> [b, c]
        result == iterationOrder(b, c)

        and:
        0 * action.execute(a)
        1 * action.execute(b)
        1 * action.execute(c)
    }

    def "provider is queried but element not configured when lazy action is registered on non-matching filter"() {
        def action = Mock(Action)
        def provider1 = Mock(ProviderInternal)
        def provider2 = Mock(ProviderInternal)

        given:
        _ * provider1.type >> type
        _ * provider2.type >> type
        container.addLater(provider1)
        container.addLater(provider2)
        def filtered = container.matching { it == b }

        when:
        filtered.configureEach(action)

        then:
        0 * provider1.get()
        0 * provider2.get()

        when:
        def result = toList(filtered)

        then:
        1 * provider1.get() >> a
        1 * provider2.get() >> b
        result == iterationOrder(b)

        and:
        0 * action.execute(a)
        1 * action.execute(b)
    }

    def "can mutate the container inside a configureEach action"() {
        given:
        container.add(a)
        container.add(b)
        container.add(c)

        when:
        container.configureEach {
            container.add(d)
        }

        then:
        toList(container) == [a, b, c, d]
    }

    def "provider of iterable is queried but elements not configured when lazy action is registered on non-matching filter"() {
        def action = Mock(Action)
        def provider1 = Mock(CollectionProviderInternal)
        def provider2 = Mock(CollectionProviderInternal)

        given:
        _ * provider1.elementType >> type
        _ * provider2.elementType >> type
        container.addAllLater(provider1)
        container.addAllLater(provider2)
        def filtered = container.matching { it == b }

        when:
        filtered.configureEach(action)

        then:
        0 * provider1.get()
        0 * provider2.get()

        when:
        def result = toList(filtered)

        then:
        _ * provider1.size() >> 1
        _ * provider2.size() >> 2
        1 * provider1.get() >> [a]
        1 * provider2.get() >> [b, c]
        result == iterationOrder(b)

        and:
        0 * action.execute(a)
        0 * action.execute(c)
        1 * action.execute(b)
    }
}
