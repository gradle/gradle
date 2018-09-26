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

package org.gradle.api

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Unroll

abstract class AbstractDomainObjectContainerIntegrationTest extends AbstractIntegrationSpec {
    abstract String makeContainer()

    abstract String disallowMutationMessage(String assertingMethod)

    def setup() {
        buildFile << """
            def testContainer = ${makeContainer()}
            def toBeRealized = testContainer.register('toBeRealized')
            def unrealized = testContainer.register('unrealized')
            def realized = testContainer.register('realized')
            realized.get()
        """
    }

    Map<String, String> getQueryMethods() {
        [
            "getByName(String)": "testContainer.getByName('unrealized')",
            "named(String)": "testContainer.named('unrealized')",
            "named(String, Class)": "testContainer.named('unrealized', testContainer.type)",
            "findAll(Closure)": "testContainer.findAll { it.name == 'unrealized' }",
            "findByName(String)": "testContainer.findByName('unrealized')",
            "TaskProvider.get()": "unrealized.get()",
            "iterator()": "for (def element : testContainer) { println element.name }",
        ]
    }

    @Unroll
    def "can execute query method #queryMethod.key from configureEach"() {
        buildFile << """
            testContainer.configureEach {
                ${queryMethod.value}
            }
            toBeRealized.get()
        """

        expect:
        succeeds "help"

        where:
        queryMethod << getQueryMethods()
    }

    @Unroll
    def "can execute query method #queryMethod.key from withType.configureEach"() {
        buildFile << """
            testContainer.withType(testContainer.type).configureEach {
                ${queryMethod.value}
            }
            toBeRealized.get()
        """

        expect:
        succeeds "help"

        where:
        queryMethod << getQueryMethods()
    }

    @Unroll
    def "can execute query method #queryMethod.key from matching.configureEach"() {
        buildFile << """
            testContainer.matching({ it in testContainer.type }).configureEach {
                ${queryMethod.value}
            }
            toBeRealized.get()
        """

        expect:
        succeeds "help"

        where:
        queryMethod << getQueryMethods()
    }

    @Unroll
    def "can execute query method #queryMethod.key from Provider.configure"() {
        buildFile << """
            toBeRealized.configure {
                ${queryMethod.value}
            }
            toBeRealized.get()
        """

        expect:
        succeeds "help"

        where:
        queryMethod << getQueryMethods()
    }

    @Unroll
    def "can execute query method #queryMethod.key from Provider.configure (realized)"() {
        buildFile << """
            realized.configure {
                ${queryMethod.value}
            }
            toBeRealized.get()
        """

        expect:
        succeeds "help"

        where:
        queryMethod << getQueryMethods()
    }

    def "can execute query method #queryMethod.key from register"() {
        buildFile << """
            testContainer.register("a") {
                ${queryMethod.value}
            }.get()
        """

        expect:
        succeeds "help"

        where:
        queryMethod << getQueryMethods()
    }

    Map<String, String> getMutationMethods() {
        [
            "create(String)": "testContainer.create('mutate')",
            "register(String)": "testContainer.register('mutate')",
            "getByName(String, Action)": "testContainer.getByName('realized') {}",
            "configureEach(Action)": "testContainer.configureEach {}",
            "NamedDomainObjectProvider.configure(Action)": "toBeRealized.configure {}",
            "named(String, Action)": "testContainer.named('realized') {}"
        ]
    }

    @Unroll
    def "cannot execute mutation method #mutationMethod.key from configureEach"() {
        buildFile << """
            testContainer.configureEach {
                ${mutationMethod.value}
            }
            toBeRealized.get()
        """

        expect:
        fails "help"
        failure.assertHasCause(disallowMutationMessage(mutationMethod.key))

        where:
        mutationMethod << getMutationMethods()
    }

    @Unroll
    def "cannot execute mutation method #mutationMethod.key from withType.configureEach"() {
        buildFile << """
            testContainer.withType(testContainer.type).configureEach {
                ${mutationMethod.value}
            }
            toBeRealized.get()
        """

        expect:
        fails "help"
        failure.assertHasCause(disallowMutationMessage(mutationMethod.key))

        where:
        mutationMethod << getMutationMethods()
    }

    @Unroll
    def "cannot execute mutation method #mutationMethod.key from matching.configureEach"() {
        buildFile << """
            testContainer.matching({ it in testContainer.type }).configureEach {
                ${mutationMethod.value}
            }
            toBeRealized.get()
        """

        expect:
        fails "help"
        failure.assertHasCause(disallowMutationMessage(mutationMethod.key))

        where:
        mutationMethod << getMutationMethods()
    }

    @Unroll
    def "cannot execute mutation method #mutationMethod.key from Provider.configure"() {
        buildFile << """
            toBeRealized.configure {
                ${mutationMethod.value}
            }
            toBeRealized.get()
        """

        expect:
        fails "help"
        failure.assertHasCause(disallowMutationMessage(mutationMethod.key))

        where:
        mutationMethod << getMutationMethods()
    }

    @Unroll
    def "cannot execute mutation method #mutationMethod.key from Provider.configure (realized)"() {
        buildFile << """
            realized.configure {
                ${mutationMethod.value}
            }
            toBeRealized.get()
        """

        expect:
        fails "help"
        failure.assertHasCause(disallowMutationMessage(mutationMethod.key))

        where:
        mutationMethod << getMutationMethods()
    }

    @Unroll
    def "cannot execute mutation method #mutationMethod.key from register"() {
        buildFile << """
            testContainer.register("a") {
                ${mutationMethod.value}
            }.get()
        """

        expect:
        fails "help"
        failure.assertHasCause(disallowMutationMessage(mutationMethod.key))

        where:
        mutationMethod << getMutationMethods()
    }

    @Unroll
    def "can execute query and mutating methods #method.key from all"() {
        buildFile << """
            testContainer.all {
                if (it.name == "realized") {
                    ${method.value}
                }
            }
        """

        expect:
        succeeds "help"

        where:
        method << getQueryMethods() + getMutationMethods()
    }

    @Unroll
    def "can execute query and mutating methods #method.key from withType.all"() {
        buildFile << """
            testContainer.withType(testContainer.type).all {
                if (it.name == "realized") {
                    ${method.value}
                }
            }
        """

        expect:
        succeeds "help"

        where:
        method << getQueryMethods() + getMutationMethods()
    }

    @Unroll
    def "can execute query and mutation methods #method.key from getByName"() {
        buildFile << """
            testContainer.getByName("realized") {
                ${method.value}
            }
        """

        expect:
        succeeds "help"

        where:
        method << getQueryMethods() + getMutationMethods()
    }

    @Unroll
    def "can execute query and mutation methods #method.key from withType.getByName"() {
        buildFile << """
            testContainer.withType(testContainer.type).getByName("realized") {
                ${method.value}
            }
        """

        expect:
        succeeds "help"

        where:
        method << getQueryMethods() + getMutationMethods()
    }

    @Unroll
    def "can execute query and mutating methods #method.key from create(String)"() {
        buildFile << """
            testContainer.create("a") {
                ${method.value}
            }
        """

        expect:
        succeeds "help"

        where:
        method << getQueryMethods() + getMutationMethods()
    }
}
