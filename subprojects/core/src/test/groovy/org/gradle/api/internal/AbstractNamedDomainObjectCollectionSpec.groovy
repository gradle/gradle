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

abstract class AbstractNamedDomainObjectCollectionSpec<T> extends AbstractDomainObjectCollectionSpec<T> {
    abstract NamedDomainObjectCollection<T> getContainer()

    @Override
    protected Map<String, Closure> getQueryMethods() {
        return super.getQueryMethods() + [
            "getByName(String)": { it.container.getByName("a") },
        ]
    }

    @Unroll
    def "disallow mutating from named actions using #mutatingMethods.key"() {
        setupContainerDefaults()
        container.add(a)
        String methodUnderTest = mutatingMethods.key
        Closure method = mutatingMethods.value
        when:
        container.named("a").configure {
            method(this)
        }
        then:
        def ex = thrown(Throwable)
        assertDoesNotAllowMethod(ex, methodUnderTest)

        when:
        container.named("a") {
            method(this)
        }
        then:
        ex = thrown(Throwable)
        assertDoesNotAllowMethod(ex, methodUnderTest)

        when:
        container.named("a", getType()) {
            method(this)
        }
        then:
        ex = thrown(Throwable)
        assertDoesNotAllowMethod(ex, methodUnderTest)

        where:
        mutatingMethods << getMutatingMethods()
    }

    @Unroll
    def "allow query methods from named using #queryMethods.key"() {
        setupContainerDefaults()
        container.add(a)
        String methodUnderTest = queryMethods.key
        Closure method = queryMethods.value
        when:
        container.named("a").configure {
            method(this)
        }
        then:
        noExceptionThrown()

        when:
        container.named("a") {
            method(this)
        }
        then:
        noExceptionThrown()

        when:
        container.named("a", getType()) {
            method(this)
        }
        then:
        noExceptionThrown()

        where:
        queryMethods << getQueryMethods()
    }
}
