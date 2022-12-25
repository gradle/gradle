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
import org.gradle.internal.Actions

abstract class AbstractNamedDomainObjectCollectionSpec<T> extends AbstractDomainObjectCollectionSpec<T> {
    abstract NamedDomainObjectCollection<T> getContainer()

    @Override
    protected Map<String, Closure> getQueryMethods() {
        return super.getQueryMethods() + [
            "getByName(String)": { container.getByName("a") },
        ]
    }

    @Override
    protected Map<String, Closure> getMutatingMethods() {
        return super.getMutatingMethods() + [
            "getByName(String, Action)": { container.getByName("a", Actions.doNothing()) }
        ]
    }

    def "disallow mutating from named actions using #mutatingMethods.key"() {
        setupContainerDefaults()
        addToContainer(a)
        String methodUnderTest = mutatingMethods.key
        Closure method = bind(mutatingMethods.value)

        when:
        container.named("a").configure(method)
        then:
        def ex = thrown(Throwable)
        assertDoesNotAllowMethod(ex, methodUnderTest)

        when:
        container.named("a", method)
        then:
        ex = thrown(Throwable)
        assertDoesNotAllowMethod(ex, methodUnderTest)

        when:
        container.named("a", getType(), method)
        then:
        ex = thrown(Throwable)
        assertDoesNotAllowMethod(ex, methodUnderTest)

        when:
        container.withType(container.type).named("a").configure(method)
        then:
        ex = thrown(Throwable)
        assertDoesNotAllowMethod(ex, methodUnderTest)

        when:
        container.withType(container.type).named("a", method)
        then:
        ex = thrown(Throwable)
        assertDoesNotAllowMethod(ex, methodUnderTest)

        when:
        container.withType(container.type).named("a", getType(), method)
        then:
        ex = thrown(Throwable)
        assertDoesNotAllowMethod(ex, methodUnderTest)

        when:
        container.matching({ it in container.type }).named("a").configure(method)
        then:
        ex = thrown(Throwable)
        assertDoesNotAllowMethod(ex, methodUnderTest)

        when:
        container.matching({ it in container.type }).named("a", method)
        then:
        ex = thrown(Throwable)
        assertDoesNotAllowMethod(ex, methodUnderTest)

        when:
        container.matching({ it in container.type }).named("a", getType(), method)
        then:
        ex = thrown(Throwable)
        assertDoesNotAllowMethod(ex, methodUnderTest)

        where:
        mutatingMethods << getMutatingMethods()
    }

    def "allow query methods from named using #queryMethods.key"() {
        setupContainerDefaults()
        addToContainer(a)
        String methodUnderTest = queryMethods.key
        Closure method = bind(queryMethods.value)

        when:
        container.named("a").configure(method)
        then:
        noExceptionThrown()

        when:
        container.named("a", method)
        then:
        noExceptionThrown()

        when:
        container.named("a", getType(), method)
        then:
        noExceptionThrown()

        when:
        container.withType(container.type).named("a").configure(method)
        then:
        noExceptionThrown()

        when:
        container.withType(container.type).named("a", method)
        then:
        noExceptionThrown()

        when:
        container.withType(container.type).named("a", getType(), method)
        then:
        noExceptionThrown()

        when:
        container.matching({ it in container.type }).named("a").configure(method)
        then:
        noExceptionThrown()

        when:
        container.matching({ it in container.type }).named("a", method)
        then:
        noExceptionThrown()

        when:
        container.matching({ it in container.type }).named("a", getType(), method)
        then:
        noExceptionThrown()

        where:
        queryMethods << getQueryMethods()
    }

    def "allow common querying and mutating methods when #methods.key within getByName configuration action"() {
        setupContainerDefaults()
        addToContainer(a)
        Closure method = bind(methods.value)

        when:
        container.getByName("a", noReentry(method))
        then:
        noExceptionThrown()

        where:
        methods << getQueryMethods() + getMutatingMethods()
    }

    def "allow common querying and mutating methods when #methods.key within getByName configuration action on filtered container by type"() {
        setupContainerDefaults()
        addToContainer(a)
        Closure method = bind(methods.value)

        when:
        container.withType(container.type).getByName("a", noReentry(method))
        then:
        noExceptionThrown()

        where:
        methods << getQueryMethods() + getMutatingMethods()
    }

    def "allow common querying and mutating methods when #methods.key within getByName configuration action on filtered container by spec"() {
        setupContainerDefaults()
        addToContainer(a)
        Closure method = bind(methods.value)

        when:
        container.matching({ it in container.type }).getByName("a", noReentry(method))
        then:
        noExceptionThrown()

        where:
        methods << getQueryMethods() + getMutatingMethods()
    }
}
