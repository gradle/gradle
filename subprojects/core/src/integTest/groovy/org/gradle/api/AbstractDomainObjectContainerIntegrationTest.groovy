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
    abstract String getContainerUnderTest()

    abstract String getBaseElementType()

    abstract String disallowMutationMessage(String assertingMethod)

    Map<String, Object> getQueryCodeUnderTest() {
        [
            "getByName(String)":    "${containerUnderTest}.getByName('unrealized')",
            "named(String)":        "${containerUnderTest}.named('unrealized')",
            "findAll(Closure)":     "${containerUnderTest}.findAll { it.name == 'unrealized' }",
            "findByName(String)":   "${containerUnderTest}.findByName('unrealized')",
            "TaskProvider.get()":   "unrealized.get()",
            "iterator()":           "for ($baseElementType element : ${containerUnderTest}) { println element.name }",
        ]
    }

    List<Object> getConfigurationHookUnderTest() {
        [
            "${containerUnderTest}.configureEach",
            "${containerUnderTest}.withType($baseElementType).configureEach",
            "toBeRealized.configure",
            "realized.configure",
        ]
    }

    Map<String, Object> getMutationCodeUnderTest() {
        [
            "create(String)":   ["${containerUnderTest}.create('c')", "add(T)"],
            "register(String)": ["${containerUnderTest}.register('c')", "addLater(Provider)"],
        ]
    }

    def getDisallowedMutationMethodCallFromConfiguration() {
        return GroovyCollections
            .combinations(mutationCodeUnderTest.entrySet().collect { e -> [e.key] + e.value }, configurationHookUnderTest)
            .collect { it.flatten() }
    }

    def getAllowedQueryMethodCallFromConfiguration() {
        return GroovyCollections
            .combinations(queryCodeUnderTest.entrySet().collect { e -> [e.key, e.value] }, configurationHookUnderTest)
            .collect { it.flatten() }
    }

    @Unroll
    def "can execute query method #description from #configurationHookUnderTest(Closure) configuration action"() {
        buildFile << """
            def toBeRealized = ${containerUnderTest}.register('toBeRealized')
            def unrealized = ${containerUnderTest}.register('unrealized')
            def realized = ${containerUnderTest}.register('realized'); realized.get()
            ${configurationHookUnderTest} {
                ${codeUnderTest}
            }
            toBeRealized.get()
        """

        expect:
        succeeds "help"

        where:
        [description, codeUnderTest, configurationHookUnderTest] << getAllowedQueryMethodCallFromConfiguration()
    }

    @Unroll
    def "cannot execute mutation method #description from #configurationHookUnderTest(Closure) configuration action"() {
        buildFile << """
            def toBeRealized = ${containerUnderTest}.register('toBeRealized')
            def unrealized = ${containerUnderTest}.register('unrealized')
            def realized = ${containerUnderTest}.register('realized'); realized.get()
            ${configurationHookUnderTest} {
                ${codeUnderTest}
            }
            toBeRealized.get()
        """

        expect:
        fails "help"
        failure.assertHasCause(disallowMutationMessage(assertingMethod))

        where:
        [description, codeUnderTest, assertingMethod, configurationHookUnderTest] << getDisallowedMutationMethodCallFromConfiguration()
    }
}
