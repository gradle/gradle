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

import org.gradle.api.NamedDomainObjectContainer
import org.gradle.internal.Actions
import org.gradle.internal.code.UserCodeApplicationId
import org.gradle.internal.code.UserCodeSource

abstract class AbstractNamedDomainObjectContainerSpec<T> extends AbstractNamedDomainObjectCollectionSpec<T> {
    abstract NamedDomainObjectContainer<T> getContainer()

    @Override
    protected Map<String, Closure> getMutatingMethods() {
        return super.getMutatingMethods() + [
            "create(String)": { container.create("b") },
            "create(String, Action)": { container.create("b", Actions.doNothing()) },
            "register(String)": { container.register("b") },
            "register(String, Action)": { container.register("b", Actions.doNothing()) },
            "NamedDomainObjectProvider.configure(Action)": { container.named("a").configure(Actions.doNothing()) }
        ]
    }

    def "allow query and mutating methods from create using #methods.key"() {
        setupContainerDefaults()
        Closure method = bind(methods.value)

        when:
        container.create("a", method)
        then:
        noExceptionThrown()

        where:
        methods << getQueryMethods() + getMutatingMethods()
    }

    def "disallow mutating from register actions using #mutatingMethods.key"() {
        setupContainerDefaults()
        String methodUnderTest = mutatingMethods.key
        Closure method = bind(mutatingMethods.value)

        when:
        container.register("a", method).get()

        then:
        def ex = thrown(Throwable)
        assertDoesNotAllowMethod(ex, methodUnderTest)

        where:
        mutatingMethods << getMutatingMethods()
    }

    def "allow query methods from register using #queryMethods.key"() {
        setupContainerDefaults()
        Closure method = bind(queryMethods.value)

        when:
        container.register("a", method).get()
        then:
        noExceptionThrown()

        where:
        queryMethods << getQueryMethods()
    }

    def "deferred configuration methods emit operations"() {
        containerSupportsBuildOperations()

        when:
        setupContainerDefaults()
        UserCodeApplicationId id1 = null
        UserCodeApplicationId id2 = null
        List<UserCodeApplicationId> ids = []
        userCodeApplicationContext.apply(Stub(UserCodeSource)) {
            id1 = it
            container.register("a") {
                ids << userCodeApplicationContext.current()
            }
        }
        userCodeApplicationContext.apply(Stub(UserCodeSource)) {
            id2 = it
            container.named("a").configure {
                ids << userCodeApplicationContext.current()
            }
        }

        then:
        buildOperationExecutor.log.all(ExecuteDomainObjectCollectionCallbackBuildOperationType).empty

        when:
        container.getByName("a")

        then:
        def ops = buildOperationExecutor.log.all(ExecuteDomainObjectCollectionCallbackBuildOperationType)
        ops.size() == 2
        ids.size() == 2
        ops[0].details.applicationId == id1.longValue()
        ops[1].details.applicationId == id2.longValue()
    }
}
