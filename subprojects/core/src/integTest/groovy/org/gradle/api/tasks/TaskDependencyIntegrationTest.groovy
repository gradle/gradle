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

package org.gradle.api.tasks

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class TaskDependencyIntegrationTest extends AbstractIntegrationSpec {

    def "removing is not supported by task dependencies set"() {
        given:
        buildFile << """
            task t
            task foo { dependsOn t }
            foo.dependsOn.$removalMethod
        """

        when:
        fails()

        then:
        failure.assertHasCause('Removing a task dependency from a task instance is not supported.')

        where:
        removalMethod   | _
        'remove(t)'     | _
        'removeAll([])' | _
        'retainAll([])' | _
    }

    def "can use a closure as a task dependency"() {
        buildFile << """
            def bar = tasks.register("bar")
            tasks.register("foo") {
                dependsOn {
                    bar
                }
            }
        """

        when:
        succeeds("foo")

        then:
        executed(":bar")
    }

    def "accessing task provided to task dependency closure is deprecated"() {
        buildFile << """
            def bar = tasks.register("bar")
            tasks.register("foo") {
                dependsOn { task ->
                    task.getName()
                    bar
                }
            }
        """

        expect:
        executer.expectDocumentedDeprecationWarning("Accessing tasks provided to task dependency closures has been deprecated. This will fail with an error in Gradle 10. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_9.html#task_in_task_dependency_closure")
        succeeds("foo")
    }

    def "accessing task provided to task dependency closure using it is deprecated"() {
        buildFile << """
            def bar = tasks.register("bar")
            tasks.register("foo") {
                dependsOn {
                    it.getName()
                    bar
                }
            }
        """

        expect:
        executer.expectDocumentedDeprecationWarning("Accessing tasks provided to task dependency closures has been deprecated. This will fail with an error in Gradle 10. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_9.html#task_in_task_dependency_closure")
        succeeds("foo")
    }

    def "cannot pass a Java Function to dependsOn"() {
        buildFile << """
            def bar = tasks.register("bar")
            tasks.register("foo") {
                java.util.function.Function<Task, Object> function = { task ->
                    task.getName()
                    bar
                }
                dependsOn(function)
            }
        """

        when:
        fails("foo")

        then:
        failure.assertHasDescription("Could not determine the dependencies of task ':foo'")
    }

    def "cannot pass a Kotlin Function to dependsOn"() {
        buildKotlinFile << """
            val bar = tasks.register("bar")
            tasks.register("foo") {
                val function: (Task) -> Any = { task ->
                    task.getName()
                    bar
                }
                dependsOn(function)
            }
        """

        when:
        fails("foo")

        then:
        failure.assertHasDescription("Could not determine the dependencies of task ':foo'")
    }

    def "cannot pass a Kotlin closure to dependsOn"() {
        buildKotlinFile << """
            val bar = tasks.register("bar")
            tasks.register("foo") {
                dependsOn({ task: Task ->
                    task.getName()
                    bar
                })
            }
        """

        when:
        fails("foo")

        then:
        failure.assertHasDescription("Could not determine the dependencies of task ':foo'")
    }

}
