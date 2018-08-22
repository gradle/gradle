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

import org.gradle.api.Named
import org.gradle.api.NamedDomainObjectCollection
import org.gradle.api.UnknownDomainObjectException
import org.gradle.api.internal.provider.ProviderInternal

import static org.gradle.util.WrapUtil.toList

abstract class AbstractNamedDomainObjectCollectionSpec<T> extends AbstractDomainObjectCollectionSpec<T> {
    abstract NamedDomainObjectCollection<T> getContainer()

    def "can reference external named unrealized provider by name without realizing them"() {
        containerAllowsExternalProviders()
        def provider = Mock(NamedProviderInternal)

        given:
        _ * provider.type >> type
        _ * provider.name >> "a"

        when:
        container.addLater(provider)

        then:
        0 * provider.get()

        when:
        def domainObjectProvider = container.named("a")

        then:
        domainObjectProvider.name == "a"
        domainObjectProvider.present
        domainObjectProvider.type == type

        when:
        def r = domainObjectProvider.get()

        then:
        r == a

        and:
        _ * provider.get() >> a

        when:
        def result = toList(container)

        then:
        result == iterationOrder(a)

        and:
        0 * provider.get()
    }

    def "cannot reference external non-named unrealized provider by name"() {
        containerAllowsExternalProviders()
        def provider = Mock(ProviderInternal)

        given:
        _ * provider.type >> type

        when:
        container.addLater(provider)

        then:
        0 * provider.get()

        when:
        container.named("a")

        then:
        def ex = thrown(UnknownDomainObjectException)
        ex.message == "${container.type.simpleName} with name 'a' not found."

        and:
        0 * provider.get()

        when:
        def result = toList(container)

        then:
        result == iterationOrder(a)

        and:
        1 * provider.get() >> a
    }

    def "can iterate through container containing unrealized external named provider"() {
        containerAllowsExternalProviders()
        def provider = Mock(NamedProviderInternal)

        given:
        _ * provider.type >> type
        _ * provider.name >> "a"
        container.addLater(provider)

        when:
        def result = toList(container)

        then:
        noExceptionThrown()
        result == iterationOrder(a)

        and:
        1 * provider.get() >> a
    }

    def "can iterate through filtered container containing unrealized external named provider"() {
        containerAllowsExternalProviders()
        def provider = Mock(NamedProviderInternal)

        given:
        _ * provider.type >> type
        _ * provider.name >> "a"
        container.addLater(provider)

        when:
        def result = toList(container.withType(type))

        then:
        noExceptionThrown()
        result == iterationOrder(a)

        and:
        1 * provider.get() >> a
    }

    interface NamedProviderInternal extends Named, ProviderInternal {}
}
