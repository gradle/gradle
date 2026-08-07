/*
 * Copyright 2023 the original author or authors.
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
package org.gradle.testretry.internal.executer

import spock.lang.Specification
import spock.lang.Subject

class TestNamesTest extends Specification {

    @Subject
    def testNames = new TestNames()

    def "removing testName works properly"() {
        given:
        testNames.addAll("TestClass", ["test1()", "test2()", "test10()"] as Set)

        when:
        testNames.remove("TestClass", "test2()")

        then:
        methodsFor("TestClass") ==~ ["test1()", "test10()"]
    }

    def "removing testName via predicate works properly"() {
        given:
        testNames.addAll("TestClass", ["test1()", "test2()", "test10()"] as Set)

        when:
        testNames.remove("TestClass", testMethod -> testMethod.contains("test1"))

        then:
        methodsFor("TestClass") ==~ ["test2()"]
    }

    def "hasClassesWithoutTestNames works properly"() {
        when:
        testNames.add("TestClass", "test()")

        then:
        !testNames.hasClassesWithoutTestNames()

        when:
        testNames.addClass("TestClassWithNoMethods")

        then:
        testNames.hasClassesWithoutTestNames()
    }

    private Set<String> methodsFor(String testClass) {
        def entry = testNames.stream()
            .filter { it.key == testClass }
            .findFirst()

        assert entry.isPresent()

        entry.get().value as Set
    }
}
