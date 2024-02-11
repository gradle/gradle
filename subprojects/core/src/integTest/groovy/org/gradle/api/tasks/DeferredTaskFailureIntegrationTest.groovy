/*
 * Copyright 2021 the original author or authors.
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

class DeferredTaskFailureIntegrationTest extends AbstractDeferredTaskDefinitionIntegrationTest {
    def "reports failure in task constructor when task realized"() {
        createDirs("child")
        settingsFile << """
            include "child"
        """
        file("child/build.gradle") << """
            class Broken extends DefaultTask {
                Broken() {
                    throw new RuntimeException("broken task")
                }
            }
            tasks.register("broken", Broken)
        """

        expect:
        fails("broken")
        failure.assertHasDescription("A problem occurred configuring project ':child'.")
        failure.assertHasCause("Could not create task ':child:broken'.")
        failure.assertHasCause("Could not create task of type 'Broken'.")
        failure.assertHasCause("broken task")
    }

    def "reports failure in task configuration block when task created"() {
        createDirs("child")
        settingsFile << """
            include "child"
        """
        file("child/build.gradle") << """
            tasks.register("broken") {
                throw new RuntimeException("broken task")
            }
        """

        expect:
        fails("broken")
        failure.assertHasDescription("A problem occurred configuring project ':child'.")
        failure.assertHasCause("Could not create task ':child:broken'.")
        failure.assertHasCause("broken task")
    }

    def "reports failure in configure block when task created"() {
        createDirs("child")
        settingsFile << """
            include "child"
        """
        file("child/build.gradle") << """
            tasks.configureEach {
                throw new RuntimeException("broken task")
            }
            tasks.register("broken")
        """

        expect:
        fails("broken")
        failure.assertHasDescription("A problem occurred configuring project ':child'.")
        failure.assertHasCause("Could not create task ':child:broken'.")
        failure.assertHasCause("broken task")
    }

    def "fails to get a task by name when it does not match the filtered type"() {
        buildFile << '''
            tasks.register("task1", SomeTask) {
                println "Configure ${path}"
            }

            tasks.create("other") {
                dependsOn tasks.withType(SomeOtherTask).getByName("task1")
            }
        '''

        when:
        fails "other"

        then:
        outputDoesNotContain("Create :task1")
        outputDoesNotContain("Configure :task1")
        failure.assertHasCause("Task with name 'task1' not found")
    }

    def "fails to get a task by name when it does not match the collection filter"() {
        buildFile << '''
            tasks.register("task1", SomeTask) {
                println "Configure ${path}"
            }

            tasks.create("other") {
                dependsOn tasks.matching { it.name.contains("foo") }.getByName("task1")
            }
        '''

        when:
        fails "other"

        then:
        outputContains("Create :task1")
        outputContains("Configure :task1")
        failure.assertHasCause("Task with name 'task1' not found")
    }

    def "fails to create custom task if constructor arguments are missing"() {
        given:
        buildFile << CUSTOM_TASK_WITH_CONSTRUCTOR_ARGS
        buildFile << "tasks.register('myTask', CustomTask, 'hello')"

        when:
        fails 'myTask'

        then:
        failure.assertHasCause("Could not create task ':myTask'.")
        failure.assertHasCause("Could not create task of type 'CustomTask'.")
        failure.assertHasCause("Unable to determine constructor argument #2: missing parameter of type int, or no service of type int")
    }

    def "fails to create custom task if all constructor arguments missing"() {
        given:
        buildFile << CUSTOM_TASK_WITH_CONSTRUCTOR_ARGS
        buildFile << "tasks.register('myTask', CustomTask)"

        when:
        fails 'myTask'

        then:
        failure.assertHasCause("Could not create task ':myTask'.")
        failure.assertHasCause("Could not create task of type 'CustomTask'.")
        failure.assertHasCause("Unable to determine constructor argument #1: missing parameter of type String, or no service of type String")
    }

    def "fails when #description constructor argument is wrong type"() {
        given:
        buildFile << CUSTOM_TASK_WITH_CONSTRUCTOR_ARGS
        buildFile << "tasks.register('myTask', CustomTask, $constructorArgs)"

        when:
        fails 'myTask'

        then:
        failure.assertHasCause("Could not create task ':myTask'.")
        failure.assertHasCause("Could not create task of type 'CustomTask'.")

        where:
        description | constructorArgs | argumentNumber | outputType
        'first'     | '123, 234'      | 1              | 'class java.lang.String'
        'last'      | '"abc", "123"'  | 2              | 'int'
    }

    def "fails to create when null passed as a constructor argument value at #position"() {
        given:
        buildFile << CUSTOM_TASK_WITH_CONSTRUCTOR_ARGS
        buildFile << script

        when:
        fails 'myTask'

        then:
        failure.assertHasCause("Could not create task ':myTask'.")

        where:
        position | script
        1        | "tasks.register('myTask', CustomTask, null, 1)"
        2        | "tasks.register('myTask', CustomTask, 'abc', null)"
    }

    def "cannot execute #description during lazy task creation action execution"() {
        createDirs("nested")
        settingsFile << "include 'nested'"
        buildFile << """
            tasks.register("foo") {
                ${code}
            }
        """

        expect:
        fails "foo"
        failure.assertHasCause("Could not create task ':foo'.")
        failure.assertHasCause(mutationExceptionFor(description))

        where:
        [description, code] << INVALID_CALL_FROM_LAZY_CONFIGURATION
    }

    def "cannot execute #description during lazy task configuration action execution"() {
        createDirs("nested")
        settingsFile << "include 'nested'"
        buildFile << """
            tasks.register("foo").configure {
                ${code}
            }
        """

        expect:
        fails "foo"
        failure.assertHasCause("Could not create task ':foo'.")
        failure.assertHasCause(mutationExceptionFor(description))

        where:
        [description, code] << INVALID_CALL_FROM_LAZY_CONFIGURATION
    }

    def "cannot execute #description on another project during lazy task creation action execution"() {
        createDirs("nested", "other")
        settingsFile << "include 'nested', 'other'"
        buildFile << """
            project(":other") {
                tasks.register("foo") {
                    rootProject.${code}
                }
            }
        """

        expect:
        fails "foo"
        failure.assertHasCause("Could not create task ':other:foo'.")
        failure.assertHasCause(mutationExceptionFor(description))

        where:
        [description, code] << INVALID_CALL_FROM_LAZY_CONFIGURATION
    }

    def "cannot execute #description on another project during lazy task configuration action execution"() {
        createDirs("nested", "other")
        settingsFile << "include 'nested', 'other'"
        buildFile << """
            project(":other") {
                tasks.register("foo").configure {
                    rootProject.${code}
                }
            }
        """

        expect:
        fails "foo"
        failure.assertHasCause("Could not create task ':other:foo'.")
        failure.assertHasCause(mutationExceptionFor(description))

        where:
        [description, code] << INVALID_CALL_FROM_LAZY_CONFIGURATION
    }

    def "gets useful message when using improper type for named using #api"() {
        buildFile << """
            class CustomTask extends DefaultTask {
            }
            class AnotherTask extends DefaultTask {
            }

            tasks.${api}("foo", CustomTask)

            tasks.named("foo", AnotherTask) // should fail
        """
        expect:
        fails("help")
        failure.assertHasCause("The task 'foo' (CustomTask) is not a subclass of the given type (AnotherTask).")

        where:
        api << ["create", "register"]
    }
}
