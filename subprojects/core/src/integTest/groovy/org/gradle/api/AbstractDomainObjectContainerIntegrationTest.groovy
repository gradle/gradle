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
    abstract String getContainerStringRepresentation()

    String disallowMutationMessage(String assertingMethod) {
        return "$assertingMethod on ${targetStringRepresentation(assertingMethod)} cannot be executed in the current context."
    }

    private String targetStringRepresentation(String method) {
        if (method.startsWith("Project")) {
            return "root project 'root'"
        } else if (method.startsWith("Gradle")) {
            return "build 'root'"
        } else {
            return containerStringRepresentation
        }
    }

    def setup() {
        settingsFile << """
            rootProject.name = 'root'
            gradle.projectsLoaded {
                it.rootProject {
                    ext.testContainer = ${makeContainer()}
                    ext.toBeRealized = testContainer.register('toBeRealized')
                    ext.unrealized = testContainer.register('unrealized')
                    ext.realized = testContainer.register('realized')
                    realized.get()
                }
            }
        """
    }

    Map<String, String> getQueryMethods() {
        [
            "${containerType}#getByName(String)":    "testContainer.getByName('unrealized')",
            "${containerType}#named(String)":        "testContainer.named('unrealized')",
            "${containerType}#named(String, Class)": "testContainer.named('unrealized', testContainer.type)",
            "${containerType}#findAll(Closure)":     "testContainer.findAll { it.name == 'unrealized' }",
            "${containerType}#findByName(String)":   "testContainer.findByName('unrealized')",
            "${containerType}#TaskProvider.get()":   "unrealized.get()",
            "${containerType}#iterator()":           "for (def element : testContainer) { println element.name }",
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
            "${containerType}#create(String)": "testContainer.create('mutate')",
            "${containerType}#register(String)": "testContainer.register('mutate')",
            "${containerType}#getByName(String, Action)": "testContainer.getByName('realized') {}",
            "${containerType}#configureEach(Action)": "testContainer.configureEach {}",
            "${containerType}#NamedDomainObjectProvider.configure(Action)": "toBeRealized.configure {}",
            "${containerType}#named(String, Action)": "testContainer.named('realized') {}",
            "${containerType}#whenObjectAdded(Action)": "testContainer.whenObjectAdded {}",
            "${containerType}#withType(Class, Action)": "testContainer.withType(testContainer.type) {}",
            "${containerType}#all(Action)": "testContainer.all {}",
            "Project#afterEvaluate(Closure)": "afterEvaluate {}",
            "Project#beforeEvaluate(Closure)": "beforeEvaluate {}",
            "Gradle#beforeProject(Closure)": "gradle.beforeProject {}",
            "Gradle#afterProject(Closure)": "gradle.afterProject {}",
            "Gradle#projectsLoaded(Closure)": "gradle.projectsLoaded {}",
            "Gradle#projectsEvaluated(Closure)": "gradle.projectsEvaluated {}",
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

    def "can mutate containers inside Project hooks"() {
        settingsFile << """
            include 'nested'
        """
        buildFile << """
            project(':nested').afterEvaluate {
                testContainer.create("afterEvaluate")
            }

            project(':nested').beforeEvaluate {
                testContainer.create("beforeEvaluate")
            }

            task verify {
                doLast {
                    assert testContainer.findByName("afterEvaluate") != null
                    assert testContainer.findByName("beforeEvaluate") != null
                }
            }
        """

        expect:
        succeeds "verify"
    }

    def "can mutate containers inside Gradle hooks"() {
        settingsFile << """
            include 'nested'
            gradle.projectsLoaded {
                it.rootProject {
                    testContainer.create("projectsLoaded")
                }
            }
        """
        buildFile << """
            gradle.beforeProject {
                if (it.name == 'nested') {
                    testContainer.create("beforeProject")
                }
            }

            gradle.afterProject {
                if (it.name == 'nested') {
                    testContainer.create("afterProject")
                }
            }

            gradle.projectsEvaluated {
                testContainer.create("projectsEvaluated")
            }

            task verify {
                doLast {
                    assert testContainer.findByName("beforeProject") != null
                    assert testContainer.findByName("afterProject") != null
                    assert testContainer.findByName("projectsLoaded") != null
                    assert testContainer.findByName("projectsEvaluated") != null
                }
            }
        """

        expect:
        succeeds "verify"
    }

    def "can mutate other containers"() {
        buildFile << """
            class SomeOtherType implements Named {
                final String name
                SomeOtherType(String name) {
                    this.name = name
                }
            }

            def otherContainer = project.container(SomeOtherType)
            testContainer.configureEach {
                if (it.name != "verify") {
                    otherContainer.create(it.name)
                }
            }
            toBeRealized.get()

            task verify {
                doLast {
                    assert otherContainer.findByName("realized") != null
                    assert otherContainer.findByName("toBeRealized") != null
                }
            }
        """

        expect:
        succeeds "verify"
    }
}
