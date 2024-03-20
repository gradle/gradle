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

import groovy.transform.SelfType
import spock.lang.Issue

@SelfType(AbstractDomainObjectContainerIntegrationTest)
trait AbstractTaskContainerIntegrationTest {
    String makeContainer() {
        return "tasks"
    }

    String getContainerStringRepresentation() {
        return "task set"
    }

    static String getContainerType() {
        return "DefaultTaskContainer"
    }
}

class TaskContainerIntegrationTest extends AbstractDomainObjectContainerIntegrationTest implements AbstractTaskContainerIntegrationTest {

    @Issue("https://github.com/gradle/gradle/issues/28347")
    def "filtering is lazy (`#filtering` + `#configAction`)"() {
        given:
        buildFile """
            tasks.configureEach { println("configured \$path") }

            tasks.$filtering.$configAction

            tasks.register("foo", Copy)
            tasks.register("bar", Delete)
        """

        when:
        succeeds "help"

        then:
        // help task is realized and configured
        outputContains("configured :help")

        // are "built-in" tasks realized and configured?
        if (realizesBuiltInTasks) {
            outputContains("configured :tasks")
            outputContains("configured :projects")
        } else {
            outputDoesNotContain("configured :tasks")
            outputDoesNotContain("configured :projects")
        }

        // are explicitly registered tasks realized and configured?
        if (realizesExplicitTasks) {
            outputContains("configured :foo")
            outputContains("configured :bar")
        } else {
            outputDoesNotContain("configured :foo")
            outputDoesNotContain("configured :bar")
        }

        where:
        filtering                           | configAction        | realizesBuiltInTasks  | realizesExplicitTasks

        "named { it == \"help\" }"          | "all {}"            | true                  | true
        "named { it == \"help\" }"          | "forEach {}"        | true                  | false
        "named { it == \"help\" }"          | "configureEach {}"  | false                 | false
        "named { it == \"help\" }"          | "toList()"          | true                  | false
        "named { it == \"help\" }"          | "iterator()"        | true                  | false
        // TODO: no other tasks should be realized, that was the intent of having the new `named()` method

        "matching { it.name == \"help\" }"  | "all {}"            | true                  | true
        "matching { it.name == \"help\" }"  | "forEach {}"        | true                  | false
        "matching { it.name == \"help\" }"  | "configureEach {}"  | false                 | false
        "matching { it.name == \"help\" }"  | "toList()"          | true                  | false
        "matching { it.name == \"help\" }"  | "iterator()"        | true                  | false

        "matching { it.group == \"help\" }" | "all {}"            | true                  | true
        "matching { it.group == \"help\" }" | "forEach {}"        | true                  | false
        "matching { it.group == \"help\" }" | "configureEach {}"  | false                 | false
        "matching { it.group == \"help\" }" | "toList()"          | true                  | false
        "matching { it.group == \"help\" }" | "iterator()"        | true                  | false

    }

    def "chained lookup of tasks.withType.matching"() {
        buildFile """
            tasks.withType(Copy).matching({ it.name.endsWith("foo") }).all { task ->
                assert task.path in [':foo']
            }

            tasks.register("foo", Copy)
            tasks.register("bar", Copy)
            tasks.register("foobar", Delete)
            tasks.register("barfoo", Delete)
        """
        expect:
        succeeds "help"
    }

    @Issue("https://github.com/gradle/gradle/issues/9446")
    def "chained lookup of tasks.matching.withType"() {
        buildFile """
            tasks.matching({ it.name.endsWith("foo") }).withType(Copy).all { task ->
                assert task.path in [':foo']
            }

            tasks.register("foo", Copy)
            tasks.register("bar", Copy)
            tasks.register("foobar", Delete)
            tasks.register("barfoo", Delete)
        """
        expect:
        succeeds "help"
    }
}
