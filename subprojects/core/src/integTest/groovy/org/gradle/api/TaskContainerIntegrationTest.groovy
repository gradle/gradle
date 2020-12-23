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


import spock.lang.Issue

class TaskContainerIntegrationTest extends AbstractDomainObjectContainerIntegrationTest {
    @Override
    String makeContainer() {
        return "tasks"
    }

    @Override
    String getContainerStringRepresentation() {
        return "task set"
    }

    static String getContainerType() {
        return "DefaultTaskContainer"
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
