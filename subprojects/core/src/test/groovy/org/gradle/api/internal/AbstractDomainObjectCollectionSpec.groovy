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
import org.gradle.api.internal.provider.ValueSupplier
import org.gradle.internal.Actions
import org.gradle.internal.code.DefaultUserCodeApplicationContext
import org.gradle.internal.code.UserCodeApplicationContext
import org.gradle.internal.code.UserCodeApplicationId
import org.gradle.internal.code.UserCodeSource
import org.gradle.internal.metaobject.ConfigureDelegate
import org.gradle.internal.operations.TestBuildOperationExecutor
import org.gradle.util.TestUtil
import org.gradle.util.internal.ConfigureUtil
import org.hamcrest.CoreMatchers
import spock.lang.Specification

import static org.gradle.util.Matchers.hasMessage
import static org.gradle.util.internal.WrapUtil.toList
import static org.hamcrest.CoreMatchers.startsWith
import static org.hamcrest.MatcherAssert.assertThat
import static org.junit.Assume.assumeTrue

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

    abstract boolean isDirectElementAdditionAllowed()

    abstract boolean isElementRemovalAllowed()

    void containerAllowsElementAddition() {
        assumeTrue("the container doesn't allow direct element addition", isDirectElementAdditionAllowed())
    }

    void containerAllowsElementRemoval() {
        assumeTrue("the container doesn't allow element removal", isElementRemovalAllowed())
    }

    void containerAllowsExternalProviders() {
        assumeTrue("the container doesn't allow external provider to be added", isExternalProviderAllowed())
    }

    void containerSupportsBuildOperations() {
        assumeTrue("the container doesn't support build operations", isSupportsBuildOperations())
    }

    protected void addToContainer(T element) {
        container.add(element)
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
        containerAllowsElementAddition()

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
        addToContainer(a)
        container.addLater(provider)
        container.addAllLater(providerOfIterable)

        when:
        addToContainer(b)

        then:
        0 * provider._
        0 * providerOfIterable._

        and:
        container.size() == 5
        !container.empty
    }

    def "can check for membership"() {
        given:
        addToContainer(b)
        addToContainer(a)

        expect:
        !container.contains(c)
        container.contains(a)
    }

    def "provider for element is queried when membership checked"() {
        containerAllowsExternalProviders()
        def provider = Mock(ProviderInternal)

        given:
        addToContainer(b)
        container.addLater(provider)

        when:
        def result = container.contains(c)

        then:
        !result

        and:
        1 * provider.calculateValue(_) >> ValueSupplier.Value.of(a)
        0 * _
    }

    def "provider for iterable of elements is queried when membership checked"() {
        containerAllowsExternalProviders()
        def provider = Mock(CollectionProviderInternal)

        given:
        addToContainer(b)
        container.addAllLater(provider)

        when:
        def result = container.contains(c)

        then:
        !result

        and:
        _ * provider.size() >> 2
        1 * provider.calculateValue(_) >> ValueSupplier.Value.of([a, d])
        0 * _
    }

    def "provider for iterable of elements is queried when .all configuration is added first"() {
        containerAllowsExternalProviders()
        def provider = Mock(CollectionProviderInternal)
        def seen = []

        given:
        addToContainer(b)
        container.all {
            seen << it
        }

        when:
        container.addAllLater(provider)

        then:
        _ * provider.getElementType() >> getType()
        _ * provider.size() >> 2
        _ * provider.calculateValue(_) >> ValueSupplier.Value.of([a, d])
        0 * _
        seen == [b, a, d]
    }

    def "can get all domain objects ordered by order added"() {
        given:
        addToContainer(b)
        addToContainer(a)
        addToContainer(c)

        expect:
        toList(container) == iterationOrder(b, a, c)
    }

    def "can iterate over domain objects ordered by order added"() {
        addToContainer(b)
        addToContainer(a)
        addToContainer(c)

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
        addToContainer(b)
        container.addLater(provider1)
        container.addLater(provider2)
        addToContainer(c)

        when:
        def result = toList(container)

        then:
        result == iterationOrder(b, a, d, c)

        and:
        1 * provider1.calculateValue(_) >> ValueSupplier.Value.of(a)
        1 * provider2.calculateValue(_) >> ValueSupplier.Value.of(d)
        0 * _
    }

    def "provider for iterable of elements is queried when elements iterated and insertion order is retained"() {
        containerAllowsExternalProviders()
        def provider1 = Mock(CollectionProviderInternal)

        given:
        addToContainer(b)
        container.addAllLater(provider1)
        addToContainer(c)

        when:
        def result = toList(container)

        then:
        result == iterationOrder(b, a, d, c)

        and:
        _ * provider1.size() >> 2
        1 * provider1.calculateValue(_) >> ValueSupplier.Value.of([a, d])
        0 * _
    }

    def "can execute action for all elements in a collection"() {
        def action = Mock(Action)

        addToContainer(c)
        addToContainer(d)

        when:
        container.all(action)

        then:
        1 * action.execute(c)
        1 * action.execute(d)
        0 * action._

        when:
        addToContainer(a)

        then:
        1 * action.execute(a)
        0 * action._
    }

    def "can add action to execute only when object added"() {
        def action = Mock(Action)

        addToContainer(c)

        when:
        container.whenObjectAdded(action)

        then:
        // Does not fire for existing elements
        0 * action._

        when:
        addToContainer(a)

        then:
        1 * action.execute(a)
        0 * action._

        when:
        addToContainer(a)

        then:
        0 * action._

        when:
        containerAllowsElementAddition()
        containerAllowsElementRemoval()

        and:
        container.remove(c)
        container.addAll(a, b, c, d)

        then:
        1 * action.execute(b)
        1 * action.execute(c)
        1 * action.execute(d)
        0 * action._
    }

    def "can add action to execute only when object removed"() {
        containerAllowsElementRemoval()

        def action = Mock(Action)

        addToContainer(c)

        when:
        container.whenObjectRemoved(action)

        then:
        // Does not fire for existing elements
        0 * action._

        when:
        addToContainer(a)
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
        1 * provider1.calculateValue(_) >> ValueSupplier.Value.of(c)
        0 * _

        when:
        container.addLater(provider2)

        then:
        1 * action.execute(a)
        _ * provider2.type >> type
        1 * provider2.calculateValue(_) >> ValueSupplier.Value.of(a)
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
        1 * provider1.calculateValue(_) >> ValueSupplier.Value.of([c])
        _ * provider1.size() >> 1
        0 * _

        when:
        container.addAllLater(provider2)

        then:
        1 * action.execute(a)
        1 * action.execute(b)
        _ * provider2.elementType >> type
        1 * provider2.calculateValue(_) >> ValueSupplier.Value.of([a, b])
        _ * provider2.size() >> 2
        0 * _
    }

    def "can get filtered collection containing all objects which have type"() {
        addToContainer(c)
        addToContainer(a)
        addToContainer(d)

        expect:
        toList(container.withType(type)) == iterationOrder(c, a)
        toList(container.withType(otherType)) == iterationOrder(d)
    }

    def "provider for element is queried when filtered collection with matching type created"() {
        containerAllowsExternalProviders()
        def provider = Mock(ProviderInternal)

        addToContainer(c)
        container.addLater(provider)
        addToContainer(d)

        when:
        def filtered = container.withType(type)

        then:
        0 * provider._

        when:
        def result = toList(filtered)

        then:
        result == iterationOrder(c, a)
        _ * provider.type >> type
        1 * provider.calculateValue(_) >> ValueSupplier.Value.of(a)
        0 * _

        when:
        def result2 = toList(container)

        then:
        result2 == iterationOrder(c, a, d)
        0 * _
    }

    def "provider for iterable of elements is queried when filtered collection with matching type created"() {
        containerAllowsExternalProviders()
        def provider = Mock(CollectionProviderInternal)
        _ * provider.elementType >> type

        addToContainer(c)
        container.addAllLater(provider)
        addToContainer(d)

        when:
        def filtered = container.withType(type)

        then:
        0 * provider._

        when:
        def result = toList(filtered)

        then:
        result == iterationOrder(c, a, b)
        _ * provider.size() >> 2
        1 * provider.calculateValue(_) >> ValueSupplier.Value.of([a, b])
        0 * _

        when:
        def result2 = toList(container)

        then:
        result2 == iterationOrder(c, a, b, d)
        0 * _
    }

    def "provider for element is not queried when filtered collection with non matching type created"() {
        containerAllowsExternalProviders()
        def provider = Mock(ProviderInternal)
        _ * provider.type >> type

        addToContainer(c)
        container.addLater(provider)
        addToContainer(d)

        when:
        def filtered = container.withType(otherType)

        then:
        0 * _

        when:
        def result = toList(filtered)

        then:
        result == iterationOrder(d)
        0 * _

        when:
        def result2 = toList(container)

        then:
        result2 == iterationOrder(c, a, d)
        1 * provider.calculateValue(_) >> ValueSupplier.Value.of(a)
        0 * _
    }

    def "provider for iterable of elements is not queried when filtered collection with non matching type created"() {
        containerAllowsExternalProviders()
        def provider = Mock(CollectionProviderInternal)
        _ * provider.elementType >> type

        addToContainer(c)
        container.addAllLater(provider)
        addToContainer(d)

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
        1 * provider.calculateValue(_) >> ValueSupplier.Value.of([a, b])
        0 * _
    }

    def "can execute action for all elements in a type filtered collection"() {
        def action = Mock(Action)

        addToContainer(c)
        addToContainer(d)

        when:
        container.withType(type, action)

        then:
        1 * action.execute(c)
        0 * action._

        when:
        addToContainer(a)

        then:
        1 * action.execute(a)
        0 * action._
    }

    def "can execute closure for all elements in a type filtered collection"() {
        def seen = []
        def closure = { seen << it }

        addToContainer(c)
        addToContainer(d)

        when:
        container.withType(type, closure)

        then:
        seen == [c]

        when:
        addToContainer(a)

        then:
        seen == [c, a]
    }

    def "action for all elements in a type filtered collection can add more elements"() {
        containerAllowsElementAddition()

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
        addToContainer(d)

        when:
        container.withType(type, action)

        then:
        _ * provider1.type >> type
        1 * provider1.calculateValue(_) >> ValueSupplier.Value.of(c)
        1 * action.execute(c)
        0 * _

        when:
        container.addLater(provider2)

        then:
        _ * provider2.type >> type
        1 * provider2.calculateValue(_) >> ValueSupplier.Value.of(a)
        1 * action.execute(a)
        0 * _
    }

    def "provider for iterable of elements is queried and action executed for filtered collection with matching type"() {
        containerAllowsExternalProviders()
        def action = Mock(Action)
        def provider1 = Mock(CollectionProviderInternal)
        def provider2 = Mock(CollectionProviderInternal)

        container.addAllLater(provider1)
        addToContainer(d)

        when:
        container.withType(type, action)

        then:
        _ * provider1.elementType >> type
        _ * provider1.size() >> 1
        1 * provider1.calculateValue(_) >> ValueSupplier.Value.of([c])
        1 * action.execute(c)
        0 * _

        when:
        container.addAllLater(provider2)

        then:
        _ * provider2.elementType >> type
        _ * provider2.size() >> 2
        1 * provider2.calculateValue(_) >> ValueSupplier.Value.of([a, b])
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
        addToContainer(d)

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
        addToContainer(d)

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
        1 * provider1.calculateValue(_) >> ValueSupplier.Value.of(a)
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
        1 * provider2.calculateValue(_) >> ValueSupplier.Value.of(c)
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
        1 * provider3.calculateValue(_) >> ValueSupplier.Value.of([b, d])
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
        _ * provider.calculateValue(_) >> ValueSupplier.Value.of(a)
        _ * providerOfIterable.calculateValue(_) >> ValueSupplier.Value.of([b, c])
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
        containerAllowsElementAddition()

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
        1 * provider1.calculateValue(_) >> ValueSupplier.Value.of(a)
        1 * action.execute(a)
        _ * provider2.type >> otherType
        1 * provider2.calculateValue(_) >> ValueSupplier.Value.of(d)
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
        1 * provider1.calculateValue(_) >> ValueSupplier.Value.of([a, c])
        1 * action.execute(a)
        1 * action.execute(c)
        _ * provider2.elementType >> otherType
        _ * provider2.size() >> 1
        1 * provider2.calculateValue(_) >> ValueSupplier.Value.of([d])
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
        0 * _

        when:
        def result = toList(filtered)

        then:
        1 * provider1.calculateValue(_) >> ValueSupplier.Value.of(a)
        1 * provider2.calculateValue(_) >> ValueSupplier.Value.of(b)
        0 * _

        and:
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
        _ * provider1.size() >> 1
        _ * provider2.size() >> 2
        0 * _

        when:
        def result = toList(filtered)

        then:
        _ * provider1.size() >> 1
        _ * provider2.size() >> 2
        1 * provider1.calculateValue(_) >> ValueSupplier.Value.of([a])
        1 * provider2.calculateValue(_) >> ValueSupplier.Value.of([c, b])
        0 * _

        and:
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
        0 * _

        when:
        def result = toList(filtered)

        then:
        0 * provider1.get() >> a
        1 * provider2.calculateValue(_) >> ValueSupplier.Value.of(b)
        0 * _

        and:
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
        _ * provider1.size() >> 1
        _ * provider2.size() >> 2
        0 * _

        when:
        def result = toList(filtered)

        then:
        _ * provider1.size() >> 1
        _ * provider2.size() >> 2
        0 * provider1.get()
        1 * provider2.calculateValue(_) >> ValueSupplier.Value.of([b, c])
        0 * _

        and:
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
        0 * _

        when:
        def result = toList(filtered)

        then:
        1 * provider1.calculateValue(_) >> ValueSupplier.Value.of(a)
        1 * provider2.calculateValue(_) >> ValueSupplier.Value.of(b)
        0 * _

        and:
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
        0 * _

        when:
        def result = toList(container)

        then:
        1 * provider2.calculateValue(_) >> ValueSupplier.Value.of(b)
        0 * _

        and:
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
        _ * provider1.type >> type
        0 * _

        when:
        def didRemovedSecondTime = container.remove(provider1)

        then:
        !didRemovedSecondTime
        toList(container) == []

        and:
        _ * provider1.type >> type
        1 * provider1.present >> false
        0 * _
    }

    def "can remove realized external providers without realizing more providers"() {
        containerAllowsExternalProviders()
        def provider1 = Mock(ProviderInternal)
        def provider2 = Mock(ProviderInternal)
        def provider3 = Mock(ProviderInternal)

        given:
        _ * provider1.type >> type
        _ * provider1.calculateValue(_) >> ValueSupplier.Value.of(a)
        _ * provider1.present >> true
        _ * provider2.type >> type
        _ * provider2.calculateValue(_) >> ValueSupplier.Value.of(b)
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
        _ * provider1.type >> type
        1 * provider1.present >> true
        1 * provider1.get() >> a
        0 * _

        when:
        def didRemoved2 = container.remove(provider2)

        then:
        didRemoved2

        and:
        _ * provider2.type >> type
        1 * provider2.present >> true
        1 * provider2.get() >> b
        0 * _
    }

    def "can remove realized external elements via instance"() {
        containerAllowsExternalProviders()
        def provider1 = Mock(ProviderInternal)
        def provider2 = Mock(ProviderInternal)

        given:
        _ * provider1.type >> type
        _ * provider1.calculateValue(_) >> ValueSupplier.Value.of(a)
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
        0 * _
    }

    def "will execute remove action when removing external provider only for realized elements"() {
        containerAllowsExternalProviders()
        def provider1 = Mock(ProviderInternal)
        def provider2 = Mock(ProviderInternal)
        def action = Mock(Action)

        given:
        _ * provider1.type >> type
        _ * provider1.calculateValue(_) >> ValueSupplier.Value.of(a)
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
        _ * provider1.type >> type
        1 * provider1.present >> true
        1 * provider1.get() >> a

        and:
        1 * action.execute(a)
        0 * _

        when:
        def didRemoved2 = container.remove(provider2)

        then:
        didRemoved2

        and:
        0 * _
    }

    def "will execute remove action when clearing the container only for realized external providers"() {
        containerAllowsExternalProviders()
        def provider1 = Mock(ProviderInternal)
        def provider2 = Mock(ProviderInternal)
        def action = Mock(Action)

        given:
        _ * provider1.type >> type
        _ * provider1.calculateValue(_) >> ValueSupplier.Value.of(a)
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
        0 * _

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
        0 * _

        when:
        def result = toList(container)

        then:
        0 * _

        and:
        result == iterationOrder()
    }

    def "will execute remove action when not retaining external providers for all elements"() {
        containerAllowsExternalProviders()
        def provider1 = Mock(ProviderInternal)
        def provider2 = Mock(ProviderInternal)
        def action = Mock(Action)

        given:
        _ * provider1.type >> type
        _ * provider1.calculateValue(_) >> ValueSupplier.Value.of(a)
        _ * provider2.type >> otherType
        _ * provider2.calculateValue(_) >> ValueSupplier.Value.of(d)
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
        1 * provider2.calculateValue(_) >> ValueSupplier.Value.of(d)
        1 * action.execute(a)
        1 * action.execute(d)
        0 * _

        when:
        def result = toList(container)

        then:
        result == iterationOrder()

        and:
        0 * _
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
        1 * provider1.calculateValue(_) >> ValueSupplier.Value.of(a)
        1 * provider2.calculateValue(_) >> ValueSupplier.Value.of(b)
        0 * _

        when:
        def result = toList(container)

        then:
        0 * _

        and:
        result == iterationOrder(b)
    }

    def "will query retaining provider when retaining realized external provider"() {
        containerAllowsExternalProviders()
        def provider1 = Mock(ProviderInternal)
        def provider2 = Mock(ProviderInternal)

        given:
        _ * provider1.type >> type
        _ * provider1.calculateValue(_) >> ValueSupplier.Value.of(a)
        _ * provider2.type >> otherType
        _ * provider2.calculateValue(_) >> ValueSupplier.Value.of(d)
        container.addLater(provider1)
        container.addLater(provider2)

        // Realize all object of type `type`
        toList(container.withType(type))

        when:
        def didRetained = container.retainAll([a])

        then:
        didRetained

        and:
        1 * provider2.calculateValue(_) >> ValueSupplier.Value.of(d)
        0 * _

        when:
        def result = toList(container)

        then:
        0 * _

        and:
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
        _ * provider1.size() >> 1
        _ * provider2.size() >> 2
        0 * _

        when:
        def result = toList(filtered)

        then:
        _ * provider1.size() >> 1
        _ * provider2.size() >> 2
        1 * provider1.calculateValue(_) >> ValueSupplier.Value.of([a])
        1 * provider2.calculateValue(_) >> ValueSupplier.Value.of([b, c])
        0 * _

        and:
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
        1 * provider1.calculateValue(_) >> ValueSupplier.Value.of(a)
        0 * _

        when:
        def result = toList(container)

        then:
        0 * provider1.get()
        1 * provider2.calculateValue(_) >> ValueSupplier.Value.of(b)
        0 * _

        and:
        result == iterationOrder(a, b)
    }

    def "will execute remove action when removing realized external provider using iterator"() {
        containerAllowsExternalProviders()
        def provider1 = Mock(ProviderInternal)
        def provider2 = Mock(ProviderInternal)
        def action = Mock(Action)

        given:
        _ * provider1.type >> type
        _ * provider1.calculateValue(_) >> ValueSupplier.Value.of(a)
        _ * provider2.type >> type
        _ * provider2.calculateValue(_) >> ValueSupplier.Value.of(b)
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
        1 * provider1.calculateValue(_) >> ValueSupplier.Value.of(a)
        1 * provider2.calculateValue(_) >> ValueSupplier.Value.of(b)
        1 * action.execute(a)
        0 * _
    }

    def "will execute remove action when removing a collection of external provider only for realized elements"() {
        containerAllowsExternalProviders()
        def provider1 = Mock(ProviderInternal)
        def provider2 = Mock(ProviderInternal)
        def action = Mock(Action)

        given:
        _ * provider1.type >> type
        _ * provider1.calculateValue(_) >> ValueSupplier.Value.of(a)
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
        _ * provider1.type >> type
        1 * provider1.present >> true
        1 * provider1.get() >> a
        1 * action.execute(a)
        0 * _
    }

    void setupContainerDefaults() {}

    def "disallow mutating from common methods when #mutatingMethods.key"() {
        setupContainerDefaults()
        addToContainer(a)
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
        def methods = [:]

        if (isDirectElementAdditionAllowed()) {
            methods += [
                "add(T)": { container.add(b) },
                "addAll(Collection<T>)": { container.addAll([b]) }
            ]
        }

        if (isElementRemovalAllowed()) {
            methods += [
                "clear()": { container.clear() },
                "remove(Object)": { container.remove(b) },
                "removeAll(Collection)": { container.removeAll([b]) },
                "retainAll(Collection)": { container.retainAll([b]) },
                "iterator().remove()": { def iter = container.iterator(); iter.next(); iter.remove() },
                "configureEach(Action)": { container.configureEach(Actions.doNothing()) }
            ]
        }

        return methods + [
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

    def "allow common querying methods when #queryMethods.key"() {
        setupContainerDefaults()
        addToContainer(a)
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

    def "allow common querying and mutating methods when #methods.key"() {
        setupContainerDefaults()
        addToContainer(a)
        Closure method = bind(methods.value)

        when:
        container.all(noReentry(method))
        then:
        noExceptionThrown()

        where:
        methods << getQueryMethods() + getMutatingMethods()
    }

    def "allow common querying and mutating methods when #methods.key on filtered container by type"() {
        setupContainerDefaults()
        addToContainer(a)
        Closure method = bind(methods.value)

        when:
        container.withType(container.type).all(noReentry(method))
        then:
        noExceptionThrown()

        where:
        methods << getQueryMethods() + getMutatingMethods()
    }

    def "allow common querying and mutating methods when #methods.key on filtered container by spec"() {
        setupContainerDefaults()
        addToContainer(a)
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
        userCodeApplicationContext.apply(Stub(UserCodeSource)) {
            id1 = it
            container.whenObjectAdded {
                assert userCodeApplicationContext.current().id == id1
            }
        }

        when:
        addToContainer(a)

        then:
        def callbacks1 = buildOperationExecutor.log.all(ExecuteDomainObjectCollectionCallbackBuildOperationType)
        callbacks1.size() == 1
        callbacks1.first().details.applicationId == id1.longValue()

        when:
        UserCodeApplicationId id2 = null
        userCodeApplicationContext.apply(Stub(UserCodeSource)) {
            id2 = it
            container.whenObjectAdded {
                assert userCodeApplicationContext.current().id == id2
            }
        }

        and:
        addToContainer(b)

        then:
        def callbacks = buildOperationExecutor.log.all(ExecuteDomainObjectCollectionCallbackBuildOperationType)
        callbacks.size() == 3
        callbacks[1].details.applicationId == id1.longValue()
        callbacks[2].details.applicationId == id2.longValue()
    }

    def "does not fire build operation if callback is filtered out by type"() {
        given:
        containerSupportsBuildOperations()

        userCodeApplicationContext.apply(Stub(UserCodeSource)) {
            container.withType(otherType).whenObjectAdded {
                throw new IllegalStateException()
            }
        }

        when:
        addToContainer(a)

        then:
        buildOperationExecutor.log.all(ExecuteDomainObjectCollectionCallbackBuildOperationType).empty
    }

    def "does not fire build operation if callback is filtered out by condition"() {
        given:
        containerSupportsBuildOperations()

        userCodeApplicationContext.apply(Stub(UserCodeSource)) {
            container.matching { !it.is(a) }.whenObjectAdded {
                throw new IllegalStateException()
            }
        }

        when:
        addToContainer(a)

        then:
        buildOperationExecutor.log.all(ExecuteDomainObjectCollectionCallbackBuildOperationType).empty
    }

    def "fires build operation for existing elements"() {
        given:
        containerSupportsBuildOperations()

        addToContainer(a)
        addToContainer(b)

        when:
        UserCodeApplicationId id = null
        List<UserCodeApplicationId> ids = []
        userCodeApplicationContext.apply(Stub(UserCodeSource)) {
            id = it
            container.matching { !it.is(a) }.all {
                ids << userCodeApplicationContext.current().id
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
        addToContainer(a)

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
        userCodeApplicationContext.apply(Stub(UserCodeSource)) {
            id1 = it
            container.all {
                ids << userCodeApplicationContext.current()
                if (it.is(a)) {
                    userCodeApplicationContext.apply(Stub(UserCodeSource)) {
                        id2 = it
                        container.all {
                            ids << userCodeApplicationContext.current()
                        }
                    }
                }
            }
        }
        addToContainer(a)
        addToContainer(b)

        then:
        def ops = buildOperationExecutor.log.all(ExecuteDomainObjectCollectionCallbackBuildOperationType)
        ops.size() == 4
        ops[0].details.applicationId == id1.longValue()
        ops[1].details.applicationId == id2.longValue()
        ops[2].details.applicationId == id1.longValue()
        ops[3].details.applicationId == id2.longValue()
    }

    def "can add list properties to container"() {
        containerAllowsExternalProviders()

        given:
        def property = TestUtil.objectFactory().listProperty(type)

        when:
        container.addAllLater(property)
        property.add(a)
        property.addAll([b, c])

        then:
        toList(container) == [a, b, c]
    }


    def "can add set properties to container"() {
        containerAllowsExternalProviders()

        given:
        def property = TestUtil.objectFactory().setProperty(type)

        when:
        container.addAllLater(property)
        property.add(a)
        property.addAll([b, c])

        then:
        toList(container) == [a, b, c]
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
