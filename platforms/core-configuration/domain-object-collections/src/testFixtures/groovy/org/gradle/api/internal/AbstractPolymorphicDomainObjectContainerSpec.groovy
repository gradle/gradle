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


import org.gradle.api.PolymorphicDomainObjectContainer
import org.gradle.internal.Actions

abstract class AbstractPolymorphicDomainObjectContainerSpec<T> extends AbstractNamedDomainObjectContainerSpec<T> {
    abstract PolymorphicDomainObjectContainer<T> getContainer()

    @Override
    protected Map<String, Closure> getMutatingMethods() {
        return super.getMutatingMethods() + [
            "create(String, Class)": { container.create("b", container.type) },
            "create(String, Class, Action)": { container.create("b", container.type, Actions.doNothing()) },
            "register(String, Class)": { container.register("b", container.type) },
            "register(String, Class, Action)": { container.register("b", container.type, Actions.doNothing()) },
        ]
    }

    def "allow query and mutating methods from create with type using #methods.key"() {
        setupContainerDefaults()
        String methodUnderTest = methods.key
        Closure method = bind(methods.value)

        when:
        container.create("a", container.type, method)
        then:
        noExceptionThrown()

        where:
        methods << getQueryMethods() + getMutatingMethods()
    }

    def "disallow mutating from register with type actions using #mutatingMethods.key"() {
        setupContainerDefaults()
        String methodUnderTest = mutatingMethods.key
        Closure method = bind(mutatingMethods.value)

        when:
        container.register("a", container.type, method).get()
        then:
        def ex = thrown(Throwable)
        assertDoesNotAllowMethod(ex, methodUnderTest)

        where:
        mutatingMethods << getMutatingMethods()
    }

    def "allow query methods from register with type using #queryMethods.key"() {
        setupContainerDefaults()
        String methodUnderTest = queryMethods.key
        Closure method = bind(queryMethods.value)

        when:
        container.register("a", container.type, method).get()
        then:
        noExceptionThrown()

        where:
        queryMethods << getQueryMethods()
    }
}
