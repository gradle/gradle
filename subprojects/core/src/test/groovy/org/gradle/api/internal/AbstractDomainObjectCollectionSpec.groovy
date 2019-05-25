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
import org.gradle.configuration.internal.DefaultUserCodeApplicationContext
import org.gradle.configuration.internal.UserCodeApplicationContext
import org.gradle.configuration.internal.UserCodeApplicationId
import org.gradle.internal.Actions
import org.gradle.internal.metaobject.ConfigureDelegate
import org.gradle.internal.operations.TestBuildOperationExecutor
import org.gradle.util.ConfigureUtil
import org.hamcrest.CoreMatchers
import org.junit.Assume
import spock.lang.Specification
import spock.lang.Unroll

import static org.gradle.util.Matchers.hasMessage
import static org.gradle.util.WrapUtil.toList
import static org.hamcrest.CoreMatchers.startsWith
import static org.junit.Assert.assertThat

abstract class AbstractDomainObjectCollectionSpec<T> extends Specification {

    TestBuildOperationExecutor buildOperationExecutor = new TestBuildOperationExecutor()
    UserCodeApplicationContext userCodeApplicationContext = new DefaultUserCodeApplicationContext()
    CollectionCallbackActionDecorator callbackActionDecorator = new DefaultCollectionCallbackActionDecorator(buildOperationExecutor, userCodeApplicationContext)

    abstract boolean isSupportsBuildOperations()

    abstract DomainObjectCollection<T> getContainer()

    abstract T getA()

    abstract T getB()

    abstract T getC()

    abstract <S extends T> S getD()

    Class<T> getType() {
        return a.class
    }

    @SuppressWarnings("GrUnnecessaryPublicModifier")
    public <S extends T> Class<S> getOtherType() {
        return d.class as Class<S>
    }

    List<T> iterationOrder(T... elements) {
        return elements
    }

    abstract boolean isExternalProviderAllowed()

    void containerAllowsExternalProviders() {
        Assume.assumeTrue("the container doesn't allow external provider to be added", isExternalProviderAllowed())
    }

    void containerSupportsBuildOperations() {
        Assume.assumeTrue("the container doesn't support build operations", isSupportsBuildOperations())
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

    def "does not add elements with duplicate name"() {
        given:
        container.add(a)

        when:
        container.add(a)

        then:
        toList(container) == [a]

        when:
        container.addAll([a, b])

        then:
        toList(container) == [a, b]
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

    def "can add action to execute only when object added"() {
        def action = Mock(Action)

        container.add(c)

        when:
        container.whenObjectAdded(action)

        then:
        // Does not fire for existing elements
        0 * action._

        when:
        container.add(a)

        then:
        1 * action.execute(a)
        0 * action._

        when:
        container.add(a)

        then:
        0 * action._

        when:
        container.remove(c)
        container.addAll(a, b, c, d)

        then:
        1 * action.execute(b)
        1 * action.execute(c)
        1 * action.execute(d)
        0 * action._
    }

    def "can add action to execute only when object removed"() {
        def action = Mock(Action)

        container.add(c)

        when:
        container.whenObjectRemoved(action)

        then:
        // Does not fire for existing elements
        0 * action._

        when:
        container.add(a)
        container.remove(c)
        container.remove(a)

        then:
        1 * action.execute(c)
        1 * action.execute(a)
        0 * action._

        when:
        container.remove(d)

        then:
        // Not fired when unknown object removed
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

    void setupContainerDefaults() {}

    @Unroll
    def "disallow mutating from common methods when #mutatingMethods.key"() {
        setupContainerDefaults()
        container.add(a)
        String methodUnderTest = mutatingMethods.key
        Closure method = bind(mutatingMethods.value)

        when:
        container.configureEach(method)
        then:
        def ex = thrown(Throwable)
        assertDoesNotAllowMethod(ex, methodUnderTest)

        when:
        container.withType(container.type).configureEach(method)
        then:
        ex = thrown(Throwable)
        assertDoesNotAllowMethod(ex, methodUnderTest)

        when:
        container.matching({ it in container.type }).configureEach(method)
        then:
        ex = thrown(Throwable)
        assertDoesNotAllowMethod(ex, methodUnderTest)

        where:
        mutatingMethods << getMutatingMethods()
    }

    protected Map<String, Closure> getMutatingMethods() {
        // TODO:
        // "addLater(Provider)"    | { it.addLater(Providers.of(b)) }
        // "addAllLater(Provider)" | { it.addAllLater(Providers.collectionOf(b)) }
        return [
            "add(T)": { container.add(b) },
            "addAll(Collection<T>)": { container.addAll([b]) },
            "clear()": { container.clear() },
            "remove(Object)": { container.remove(b) },
            "removeAll(Collection)": { container.removeAll([b]) },
            "retainAll(Collection)": { container.retainAll([b]) },
            "iterator().remove()": { def iter = container.iterator(); iter.next(); iter.remove() },
            "configureEach(Action)": { container.configureEach(Actions.doNothing()) },
            "whenObjectAdded(Action)": { container.whenObjectAdded(Actions.doNothing()) },
            "withType(Class, Action)": { container.withType(type, Actions.doNothing()) },
            "all(Action)": { container.all(Actions.doNothing()) },
        ]
    }

    protected void assertDoesNotAllowMethod(Throwable exception, String methodUnderTest) {
        String message = "${containerPublicType.simpleName}#${methodUnderTest} on ${container.toString()} cannot be executed in the current context."
        List<Throwable> causes = new ArrayList<Throwable>()
        while (exception != null) {
            causes.add(exception)
            exception = exception.cause
        }
        assertThat(causes, CoreMatchers.hasItem(hasMessage(startsWith(message))))
    }

    @Unroll
    def "allow common querying methods when #queryMethods.key"() {
        setupContainerDefaults()
        container.add(a)
        Closure method = bind(queryMethods.value)

        when:
        container.configureEach(method)
        then:
        noExceptionThrown()

        when:
        container.withType(container.type).configureEach(method)
        then:
        noExceptionThrown()

        when:
        container.matching({ it in container.type }).configureEach(method)
        then:
        noExceptionThrown()

        where:
        queryMethods << getQueryMethods()
    }

    @Unroll
    def "allow common querying and mutating methods when #methods.key"() {
        setupContainerDefaults()
        container.add(a)
        Closure method = bind(methods.value)

        when:
        container.all(noReentry(method))
        then:
        noExceptionThrown()

        where:
        methods << getQueryMethods() + getMutatingMethods()
    }

    @Unroll
    def "allow common querying and mutating methods when #methods.key on filtered container by type"() {
        setupContainerDefaults()
        container.add(a)
        Closure method = bind(methods.value)

        when:
        container.withType(container.type).all(noReentry(method))
        then:
        noExceptionThrown()

        where:
        methods << getQueryMethods() + getMutatingMethods()
    }

    @Unroll
    def "allow common querying and mutating methods when #methods.key on filtered container by spec"() {
        setupContainerDefaults()
        container.add(a)
        Closure method = bind(methods.value)

        when:
        container.matching({ it in container.type }).all(noReentry(method))
        then:
        noExceptionThrown()

        where:
        methods << getQueryMethods() + getMutatingMethods()
    }

    def "fires build operation when emitting added callback and reestablishes user code context"() {
        given:
        containerSupportsBuildOperations()

        UserCodeApplicationId id1 = null
        userCodeApplicationContext.apply {
            id1 = it
            container.whenObjectAdded {
                assert userCodeApplicationContext.current() == id1
            }
        }

        when:
        container.add(a)

        then:
        def callbacks1 = buildOperationExecutor.log.all(ExecuteDomainObjectCollectionCallbackBuildOperationType)
        callbacks1.size() == 1
        callbacks1.first().details.applicationId == id1.longValue()

        when:
        UserCodeApplicationId id2 = null
        userCodeApplicationContext.apply {
            id2 = it
            container.whenObjectAdded {
                assert userCodeApplicationContext.current() == id2
            }
        }

        and:
        container.add(b)

        then:
        def callbacks = buildOperationExecutor.log.all(ExecuteDomainObjectCollectionCallbackBuildOperationType)
        callbacks.size() == 3
        callbacks[1].details.applicationId == id1.longValue()
        callbacks[2].details.applicationId == id2.longValue()
    }

    def "does not fire build operation if callback is filtered out by type"() {
        given:
        containerSupportsBuildOperations()

        userCodeApplicationContext.apply {
            container.withType(otherType).whenObjectAdded {
                throw new IllegalStateException()
            }
        }

        when:
        container.add(a)

        then:
        buildOperationExecutor.log.all(ExecuteDomainObjectCollectionCallbackBuildOperationType).empty
    }

    def "does not fire build operation if callback is filtered out by condition"() {
        given:
        containerSupportsBuildOperations()

        userCodeApplicationContext.apply {
            container.matching { !it.is(a) }.whenObjectAdded {
                throw new IllegalStateException()
            }
        }

        when:
        container.add(a)

        then:
        buildOperationExecutor.log.all(ExecuteDomainObjectCollectionCallbackBuildOperationType).empty
    }

    def "fires build operation for existing elements"() {
        given:
        containerSupportsBuildOperations()

        container.add(a)
        container.add(b)

        when:
        UserCodeApplicationId id = null
        List<UserCodeApplicationId> ids = []
        userCodeApplicationContext.apply {
            id = it
            container.matching { !it.is(a) }.all {
                ids << userCodeApplicationContext.current()
            }
        }

        then:
        ids.size() == 1
        ids.first() == id
        def ops = buildOperationExecutor.log.all(ExecuteDomainObjectCollectionCallbackBuildOperationType)
        ops.size() == 1
        ops.first().details.applicationId == id.longValue()
    }

    def "does not fire op if no user code application id"() {
        given:
        containerSupportsBuildOperations()

        when:
        def ids = []
        container.all {
            ids << userCodeApplicationContext.current()
        }
        container.add(a)

        then:
        ids.size() == 1
        ids.first() == null
        buildOperationExecutor.log.all(ExecuteDomainObjectCollectionCallbackBuildOperationType).empty
    }

    def "handles nested listener registration"() {
        given:
        containerSupportsBuildOperations()

        when:
        UserCodeApplicationId id1 = null
        UserCodeApplicationId id2 = null
        List<UserCodeApplicationId> ids = []
        userCodeApplicationContext.apply {
            id1 = it
            container.all {
                ids << userCodeApplicationContext.current()
                if (it.is(a)) {
                    userCodeApplicationContext.apply {
                        id2 = it
                        container.all {
                            ids << userCodeApplicationContext.current()
                        }
                    }
                }
            }
        }
        container.add(a)
        container.add(b)

        then:
        def ops = buildOperationExecutor.log.all(ExecuteDomainObjectCollectionCallbackBuildOperationType)
        ops.size() == 4
        ops[0].details.applicationId == id1.longValue()
        ops[1].details.applicationId == id2.longValue()
        ops[2].details.applicationId == id1.longValue()
        ops[3].details.applicationId == id2.longValue()
    }

    protected Map<String, Closure> getQueryMethods() {
        return [
            "contains(Object)": { container.contains(b) },
            "iterator().next()": { def iter = container.iterator(); iter.next() },
        ]
    }

    protected Closure bind(Closure delegateClosure) {
        def thiz = this
        return {
            ConfigureUtil.configureSelf(delegateClosure, it, new ConfigureDelegate(delegateClosure, thiz))
        }
    }

    protected Closure noReentry(Closure delegateClosure) {
        boolean entryAllowed = true
        return {
            if (entryAllowed) {
                boolean oldEntryAllowed = entryAllowed
                entryAllowed = false
                try {
                    ConfigureUtil.configure(delegateClosure, it)
                } finally {
                    entryAllowed = oldEntryAllowed
                }
            }
        }
    }
}
