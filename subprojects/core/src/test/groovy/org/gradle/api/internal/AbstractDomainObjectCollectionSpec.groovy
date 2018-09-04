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
import org.gradle.api.internal.plugins.DslObject
import org.gradle.api.internal.provider.CollectionProviderInternal
import org.gradle.api.internal.provider.ProviderInternal
import org.junit.Assume
import spock.lang.Specification
import spock.lang.Unroll

import static org.gradle.api.internal.DomainObjectCollectionConfigurationFactories.CallAddAllFactory
import static org.gradle.api.internal.DomainObjectCollectionConfigurationFactories.CallAddAllLaterFactory
import static org.gradle.api.internal.DomainObjectCollectionConfigurationFactories.CallAddFactory
import static org.gradle.api.internal.DomainObjectCollectionConfigurationFactories.CallAddLaterFactory
import static org.gradle.api.internal.DomainObjectCollectionConfigurationFactories.CallClearFactory
import static org.gradle.api.internal.DomainObjectCollectionConfigurationFactories.CallRemoveAllFactory
import static org.gradle.api.internal.DomainObjectCollectionConfigurationFactories.CallRemoveFactory
import static org.gradle.api.internal.DomainObjectCollectionConfigurationFactories.CallRemoveOnIteratorFactory
import static org.gradle.api.internal.DomainObjectCollectionConfigurationFactories.CallRetainAllFactory
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

    abstract boolean isExternalProviderAllowed()

    void containerAllowsExternalProviders() {
        Assume.assumeTrue("the container doesn't allow external provider to be added", isExternalProviderAllowed())
    }

    Class<? extends DomainObjectCollection<T>> getContainerPublicType() {
        return new DslObject(container).publicType.concreteClass
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
        containerAllowsExternalProviders()
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
        containerAllowsExternalProviders()
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
        containerAllowsExternalProviders()
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
        containerAllowsExternalProviders()
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
        containerAllowsExternalProviders()
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
        containerAllowsExternalProviders()
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
        containerAllowsExternalProviders()
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
        containerAllowsExternalProviders()
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
        containerAllowsExternalProviders()
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
        containerAllowsExternalProviders()
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
        containerAllowsExternalProviders()
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
        containerAllowsExternalProviders()
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
        containerAllowsExternalProviders()
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
        containerAllowsExternalProviders()
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
        containerAllowsExternalProviders()
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
        containerAllowsExternalProviders()
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
        containerAllowsExternalProviders()
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
        containerAllowsExternalProviders()
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
        containerAllowsExternalProviders()
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
        containerAllowsExternalProviders()
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
        containerAllowsExternalProviders()
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
        containerAllowsExternalProviders()
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
        containerAllowsExternalProviders()
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
        containerAllowsExternalProviders()
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
        containerAllowsExternalProviders()
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
        containerAllowsExternalProviders()
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

    def "can remove external providers without realizing them"() {
        containerAllowsExternalProviders()
        def provider1 = Mock(ProviderInternal)
        def provider2 = Mock(ProviderInternal)

        given:
        _ * provider1.type >> type
        _ * provider2.type >> type
        container.addLater(provider1)
        container.addLater(provider2)

        when:
        def didRemoved = container.remove(provider1)

        then:
        didRemoved

        and:
        0 * provider1.get()
        0 * provider2.get()

        when:
        def result = toList(container)

        then:
        0 * provider1.get()
        1 * provider2.get() >> b
        result == iterationOrder(b)
    }

    def "returns false when removing external providers a second time"() {
        containerAllowsExternalProviders()
        def provider1 = Mock(ProviderInternal)

        given:
        _ * provider1.type >> type
        _ * provider1.present >> true
        container.addLater(provider1)

        when:
        def didRemovedFirstTime = container.remove(provider1)

        then:
        didRemovedFirstTime
        toList(container) == []

        and:
        0 * provider1.get()

        when:
        def didRemovedSecondTime = container.remove(provider1)

        then:
        !didRemovedSecondTime
        toList(container) == []

        and:
        1 * provider1.present >> false
        0 * provider1.get()
    }

    def "can remove realized external providers without realizing more providers"() {
        containerAllowsExternalProviders()
        def provider1 = Mock(ProviderInternal)
        def provider2 = Mock(ProviderInternal)
        def provider3 = Mock(ProviderInternal)

        given:
        _ * provider1.type >> type
        _ * provider1.get() >> a
        _ * provider1.present >> true
        _ * provider2.type >> type
        _ * provider2.get() >> b
        _ * provider2.present >> true
        _ * provider3.type >> otherType
        container.addLater(provider1)
        container.addLater(provider2)
        container.addLater(provider3)

        // Realize all object of type `type`
        toList(container.withType(type))

        when:
        def didRemoved1 = container.remove(provider1)

        then:
        didRemoved1

        and:
        1 * provider1.get() >> a
        0 * provider2.get()
        0 * provider3.get()

        when:
        def didRemoved2 = container.remove(provider2)

        then:
        didRemoved2

        and:
        0 * provider1.get()
        1 * provider2.get() >> b
        0 * provider3.get()
    }

    def "can remove realized external elements via instance"() {
        containerAllowsExternalProviders()
        def provider1 = Mock(ProviderInternal)
        def provider2 = Mock(ProviderInternal)

        given:
        _ * provider1.type >> type
        _ * provider1.get() >> a
        _ * provider2.type >> otherType
        container.addLater(provider1)
        container.addLater(provider2)

        // Realize all object of type `type`
        def element = container.withType(type).iterator().next()

        when:
        def didRemoved = container.remove(element)

        then:
        didRemoved

        and:
        0 * provider1.get()
        0 * provider2.get()
    }

    def "will execute remove action when removing external provider only for realized elements"() {
        containerAllowsExternalProviders()
        def provider1 = Mock(ProviderInternal)
        def provider2 = Mock(ProviderInternal)
        def action = Mock(Action)

        given:
        _ * provider1.type >> type
        _ * provider1.get() >> a
        _ * provider1.present >> true
        _ * provider2.type >> otherType
        container.addLater(provider1)
        container.addLater(provider2)
        container.whenObjectRemoved(action)

        // Realize all object of type `type`
        toList(container.withType(type))

        when:
        def didRemoved1 = container.remove(provider1)

        then:
        didRemoved1

        and:
        1 * action.execute(a)
        0 * action.execute(_)

        when:
        def didRemoved2 = container.remove(provider2)

        then:
        didRemoved2

        and:
        0 * action.execute(_)
    }

    def "will execute remove action when clearing the container only for realized external providers"() {
        containerAllowsExternalProviders()
        def provider1 = Mock(ProviderInternal)
        def provider2 = Mock(ProviderInternal)
        def action = Mock(Action)

        given:
        _ * provider1.type >> type
        _ * provider1.get() >> a
        _ * provider2.type >> otherType
        container.addLater(provider1)
        container.addLater(provider2)
        container.whenObjectRemoved(action)

        // Realize all object of type `type`
        toList(container.withType(type))

        when:
        container.clear()

        then:
        1 * action.execute(a)
        0 * action.execute(_)

        when:
        def result = toList(container)

        then:
        result == iterationOrder()
    }

    def "will not query external provider when clearing"() {
        containerAllowsExternalProviders()
        def provider1 = Mock(ProviderInternal)
        def provider2 = Mock(ProviderInternal)

        given:
        _ * provider1.type >> type
        _ * provider2.type >> type
        container.addLater(provider1)
        container.addLater(provider2)

        when:
        container.clear()

        then:
        0 * provider1.get()
        0 * provider2.get()

        when:
        def result = toList(container)

        then:
        0 * provider1.get()
        0 * provider2.get()
        result == iterationOrder()
    }

    def "will execute remove action when not retaining external providers for all elements"() {
        containerAllowsExternalProviders()
        def provider1 = Mock(ProviderInternal)
        def provider2 = Mock(ProviderInternal)
        def action = Mock(Action)

        given:
        _ * provider1.type >> type
        _ * provider1.get() >> a
        _ * provider2.type >> otherType
        _ * provider2.get() >> d
        container.addLater(provider1)
        container.addLater(provider2)
        container.whenObjectRemoved(action)

        // Realize all object of type `type`
        toList(container.withType(type))

        when:
        def didRetained = container.retainAll([])

        then:
        didRetained

        and:
        1 * action.execute(a)
        1 * action.execute(d)
        0 * action.execute(_)

        when:
        def result = toList(container)

        then:
        result == iterationOrder()
    }

    def "will query external providers when not retaining them"() {
        containerAllowsExternalProviders()
        def provider1 = Mock(ProviderInternal)
        def provider2 = Mock(ProviderInternal)

        given:
        _ * provider1.type >> type
        _ * provider2.type >> type
        container.addLater(provider1)
        container.addLater(provider2)

        when:
        def didRetained = container.retainAll([b])

        then:
        didRetained

        and:
        1 * provider1.get() >> a
        1 * provider2.get() >> b

        when:
        def result = toList(container)

        then:
        0 * provider1.get()
        0 * provider2.get()
        result == iterationOrder(b)
    }

    def "will query retaining provider when retaining realized external provider"() {
        containerAllowsExternalProviders()
        def provider1 = Mock(ProviderInternal)
        def provider2 = Mock(ProviderInternal)

        given:
        _ * provider1.type >> type
        _ * provider1.get() >> a
        _ * provider2.type >> otherType
        _ * provider2.get() >> d
        container.addLater(provider1)
        container.addLater(provider2)

        // Realize all object of type `type`
        toList(container.withType(type))

        when:
        def didRetained = container.retainAll([a])

        then:
        didRetained

        and:
        0 * provider1.get()
        1 * provider2.get() >> d

        when:
        def result = toList(container)

        then:
        result == iterationOrder(a)
    }

    def "provider of iterable is queried but elements not configured when lazy action is registered on non-matching filter"() {
        containerAllowsExternalProviders()
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

    def "will realize all external provider when querying the iterator"() {
        containerAllowsExternalProviders()
        def provider1 = Mock(ProviderInternal)
        def provider2 = Mock(ProviderInternal)

        given:
        _ * provider1.type >> type
        _ * provider2.type >> otherType
        container.addLater(provider1)
        container.addLater(provider2)

        when:
        container.withType(type).iterator()

        then:
        1 * provider1.get() >> a
        0 * provider2.get()

        when:
        def result = toList(container)

        then:
        0 * provider1.get()
        1 * provider2.get() >> b
        result == iterationOrder(a, b)
    }

    def "will execute remove action when removing realized external provider using iterator"() {
        containerAllowsExternalProviders()
        def provider1 = Mock(ProviderInternal)
        def provider2 = Mock(ProviderInternal)
        def action = Mock(Action)

        given:
        _ * provider1.type >> type
        _ * provider1.get() >> a
        _ * provider2.type >> type
        _ * provider2.get() >> b
        container.addLater(provider1)
        container.addLater(provider2)
        container.whenObjectRemoved(action)

        when:
        def iterator = container.iterator()
        iterator.next()
        iterator.remove()

        then:
        def result = toList(container)
        result == iterationOrder(b)

        and:
        1 * action.execute(a)
        0 * action.execute(_)
    }

    def "will execute remove action when removing a collection of external provider only for realized elements"() {
        containerAllowsExternalProviders()
        def provider1 = Mock(ProviderInternal)
        def provider2 = Mock(ProviderInternal)
        def action = Mock(Action)

        given:
        _ * provider1.type >> type
        _ * provider1.get() >> a
        _ * provider1.present >> true
        _ * provider2.type >> otherType
        container.addLater(provider1)
        container.addLater(provider2)
        container.whenObjectRemoved(action)

        // Realize all object of type `type`
        toList(container.withType(type))

        when:
        def didRemoved = container.removeAll([provider1, provider2])

        then:
        didRemoved
        container.empty

        and:
        1 * action.execute(a)
        0 * action.execute(_)
    }

    protected def getInvalidCallFromLazyConfiguration() {
        return [
            ["add(T)"               , CallAddFactory.AsAction],
            ["add(T)"               , CallAddFactory.AsClosure],
            ["addLater(Provider)"   , CallAddLaterFactory.AsAction],
            ["addLater(Provider)"   , CallAddLaterFactory.AsClosure],
            ["addAllLater(Provider)", CallAddAllLaterFactory.AsAction],
            ["addAllLater(Provider)", CallAddAllLaterFactory.AsClosure],
            ["addAll(Collection)"   , CallAddAllFactory.AsAction],
            ["addAll(Collection)"   , CallAddAllFactory.AsClosure],
            ["clear()"              , CallClearFactory.AsAction],
            ["clear()"              , CallClearFactory.AsClosure],
            ["remove(Object)"       , CallRemoveFactory.AsAction],
            ["remove(Object)"       , CallRemoveFactory.AsClosure],
            ["removeAll(Collection)", CallRemoveAllFactory.AsAction],
            ["removeAll(Collection)", CallRemoveAllFactory.AsClosure],
            ["retainAll(Collection)", CallRetainAllFactory.AsAction],
            ["retainAll(Collection)", CallRetainAllFactory.AsClosure],
            ["iterator().remove()"  , CallRemoveOnIteratorFactory.AsAction],
            ["iterator().remove()"  , CallRemoveOnIteratorFactory.AsClosure],
        ]
    }

    @Unroll
    def "disallow mutating when configureEach(#factoryClass.configurationType.simpleName) calls #description"() {
        def factory = factoryClass.newInstance()
        if (factory.isUseExternalProviders()) {
            containerAllowsExternalProviders()
        }

        when:
        container.configureEach(factory.create(container, b))
        container.add(a)

        then:
        def ex = thrown(IllegalStateException)
        ex.message == "${containerPublicType.simpleName}#${description} on ${container.toString()} cannot be executed in the current context."

        where:
        [description, factoryClass] << getInvalidCallFromLazyConfiguration()
    }
}
