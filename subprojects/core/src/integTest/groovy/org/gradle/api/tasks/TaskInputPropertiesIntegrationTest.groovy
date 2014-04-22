/*
 * Copyright 2014 the original author or authors.
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



package org.gradle.api.tasks

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class TaskInputPropertiesIntegrationTest extends AbstractIntegrationSpec {

    def "supports custom task input properties so long they are Serializable"() {
        buildFile << """
            def value = new Foo(x: 1)
            task foo {
                inputs.property "foo", value
                outputs.file "foo.txt"
                doLast { file("foo.txt") << "x" }
            }

            class Foo implements Serializable {
                int x
                boolean equals(Object other) { return x == other.x }
                int hashCode() { return 1 }
            }
        """

        when: run "foo"
        then: result.assertTaskNotSkipped(":foo")

        when:
        buildFile << "value.x = 1 \n" //same value, should still be up-to-date
        run "foo", "-i"

        then: result.assertTasksSkipped(":foo")

        when:
        buildFile << "value.x = 99 \n" //different value, out-of-date
        run "foo"

        then: result.assertTaskNotSkipped(":foo")
    }

    def "reports which properties are not serializable"() {
        buildFile << """
            task foo {
                inputs.property "a", "hello"
                inputs.property "b", new Foo()
                outputs.file "foo.txt"
                doLast { file("foo.txt") << "" }
            }

            class Foo {
                int x
                String toString() { "xxx" }
            }
        """

        when: fails "foo"
        then: failure.assertHasCause("Unable to store task input properties. Property 'b' with value 'xxx")
    }

    def "deals gracefully with not serializable contents of GStrings"() {
        buildFile << """
            task foo {
                inputs.property "a", "hello \${new Foo()}"
                outputs.file "foo.txt"
                doLast { file("foo.txt") << "" }
            }

            class Foo {
                int x
                String toString() { "xxx" }
            }
        """

        expect:
        run("foo").assertTaskNotSkipped(":foo")
        run("foo").assertTaskSkipped(":foo")
    }
}
