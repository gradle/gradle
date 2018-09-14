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

import org.gradle.api.NamedDomainObjectCollection
import spock.lang.Unroll

import static org.gradle.api.internal.DomainObjectCollectionConfigurationFactories.*

abstract class AbstractNamedDomainObjectCollectionSpec<T> extends AbstractDomainObjectCollectionSpec<T> {
    abstract NamedDomainObjectCollection<T> getContainer()

    @Override
    protected def getValidCallFromLazyConfiguration() {
        def result = []
        result.addAll(super.getValidCallFromLazyConfiguration())
        result.add(["getByName(String)", CallGetByNameFactory.AsAction])
        result.add(["getByName(String)", CallGetByNameFactory.AsClosure])
        return result
    }

    @Unroll
    def "disallow mutating when NamedDomainObjectProvider.configure(#factoryClass.configurationType.simpleName) calls #description"() {
        def factory = factoryClass.newInstance()
        if (factory.isUseExternalProviders()) {
            containerAllowsExternalProviders()
        }

        when:
        container.add(a)
        container.named("a").configure(factory.create(container, b))

        then:
        def ex = thrown(IllegalStateException)
        ex.message == "${containerPublicType.simpleName}#${description} on ${container.toString()} cannot be executed in the current context."

        where:
        [description, factoryClass] << getInvalidCallFromLazyConfiguration()
    }

    @Unroll
    def "allow querying when NamedDomainObjectProvider.configure(#factoryClass.configurationType.simpleName) calls #description"() {
        def factory = factoryClass.newInstance()
        if (factory.isUseExternalProviders()) {
            containerAllowsExternalProviders()
        }

        when:
        container.add(a)
        container.named("a").configure(factory.create(container, b))

        then:
        noExceptionThrown()

        where:
        [description, factoryClass] << getValidCallFromLazyConfiguration()
    }
}
