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

import org.gradle.api.DomainObjectCollection
import org.gradle.api.provider.Provider
import spock.lang.Specification

import static org.gradle.util.WrapUtil.toList

abstract class AbstractDomainObjectCollectionSpec<T> extends Specification {
    abstract DomainObjectCollection<T> getContainer()

    abstract T getA()

    abstract T getB()

    abstract T getC()

    abstract T getD()

    def canGetAllDomainObjectsForEmptyCollection() {
        expect:
        container.isEmpty()
        container.size() == 0
    }

    def canIterateOverEmptyCollection() {
        expect:
        def iterator = container.iterator()
        !iterator.hasNext()
    }

    def elementAddedUsingProviderIsNotRealizedWhenAdded() {
        def provider = Mock(Provider)

        when:
        container.addLater(provider)

        then:
        0 * provider._

        and:
        container.size() == 1
        !container.empty
    }

    def providerForElementIsNotQueriedWhenAnotherElementAdded() {
        def provider = Mock(Provider)

        given:
        container.add(a)
        container.addLater(provider)

        when:
        container.add(b)

        then:
        0 * provider._

        and:
        container.size() == 3
        !container.empty
    }

    def canCheckForMembership() {
        given:
        container.add(b)
        container.add(a)

        expect:
        !container.contains(c)
        container.contains(a)
    }

    def providerForElementIsQueriedWhenMembershipChecked() {
        def provider = Mock(Provider)

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

    def canGetAllDomainObjectsOrderedByOrderAdded() {
        given:
        container.add(b)
        container.add(a)
        container.add(c)

        expect:
        toList(container) == [b, a, c]
    }

    def canIterateOverDomainObjectsOrderedByOrderAdded() {
        container.add(b)
        container.add(a)
        container.add(c)

        expect:
        def iterator = container.iterator()
        iterator.next() == b
        iterator.next() == a
        iterator.next() == c
        !iterator.hasNext()
    }

    def providerForElementIsQueriedWhenElementsIteratedButInsertionOrderIsNotRetained() {
        def provider1 = Mock(Provider)
        def provider2 = Mock(Provider)

        given:
        container.add(b)
        container.addLater(provider1)
        container.addLater(provider2)
        container.add(c)

        when:
        def result = toList(container)

        then:
        result == [b, c, a, d]

        and:
        1 * provider1.get() >> a
        1 * provider2.get() >> d
        0 * _
    }
}
