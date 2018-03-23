/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.integtests

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.util.Requires
import spock.lang.Unroll

import static org.gradle.util.TestPrecondition.KOTLIN_SCRIPT

class TaskDefinitionIntegrationSpec extends AbstractIntegrationSpec {
    private static final String CUSTOM_TASK_WITH_CONSTRUCTOR_ARGS = """
        import javax.inject.Inject

        class CustomTask extends DefaultTask {
            final String message
            final int number

            @Inject
            CustomTask(String message, int number) {
                this.message = message
                this.number = number
            }

            @TaskAction
            void printIt() {
                println("\$message \$number")
            }
        }
    """

    private static final String KOTLIN_TASK_CONTAINER_EXTENSION = '''
        inline
        fun <reified T : Task> TaskContainer.create(name: String, vararg arguments: Any) =
            create(name, T::class.java, *arguments)
    '''

    def "unsupported task parameter fails with decent error message"() {
        buildFile << "task a(Type:Copy)"
        when:
        fails 'a'
        then:
        failure.assertHasCause("Could not create task 'a': Unknown argument(s) in task definition: [Type]")
    }

    def "renders deprecation message when using left shift operator to define action"() {
        given:
        String taskName = 'helloWorld'
        String message = 'Hello world!'

        buildFile << """
            task $taskName << {
                println '$message'
            }
        """

        when:
        executer.expectDeprecationWarning()
        succeeds taskName

        then:
        output.contains(message)
        output.contains("The Task.leftShift(Closure) method has been deprecated and is scheduled to be removed in Gradle 5.0. Please use Task.doLast(Action) instead.")
    }

    def "can construct a custom task without constructor arguments"() {
        given:
        buildFile << """
            class CustomTask extends DefaultTask {
                @TaskAction
                void printIt() {
                    println("hello world")
                }
            }

            task myTask(type: CustomTask)
        """

        when:
        run 'myTask'

        then:
        result.output.contains('hello world')
    }

    def "can construct a custom task with constructor arguments"() {
        given:
        buildFile << CUSTOM_TASK_WITH_CONSTRUCTOR_ARGS
        buildFile << "tasks.create('myTask', CustomTask, 'hello', 42)"

        when:
        run 'myTask'

        then:
        result.output.contains("hello 42")
    }

    @Unroll
    def "can construct a custom task with constructor arguments as #description via Map"() {
        given:
        buildFile << CUSTOM_TASK_WITH_CONSTRUCTOR_ARGS
        buildFile << "task myTask(type: CustomTask, constructorArgs: ${constructorArgs})"

        when:
        run 'myTask'

        then:
        result.output.contains("hello 42")

        where:
        description | constructorArgs
        'List'      | "['hello', 42]"
        'Object[]'  | "(['hello', 42] as Object[])"
    }

    @Unroll
    def "fails to create custom task using #description if constructor arguments are missing"() {
        given:
        buildFile << CUSTOM_TASK_WITH_CONSTRUCTOR_ARGS
        buildFile << script

        when:
        fails 'myTask'

        then:
        result.output.contains("java.lang.IllegalArgumentException: Unable to determine argument #1: no service of type int, or missing parameter value of type int")

        where:
        description   | script
        'Map'         | "task myTask(type: CustomTask, constructorArgs: ['hello'])"
        'direct call' | "tasks.create('myTask', CustomTask, 'hello')"
    }

    @Unroll
    def "fails to create custom task using #description if all constructor arguments missing"() {
        given:
        buildFile << CUSTOM_TASK_WITH_CONSTRUCTOR_ARGS
        buildFile << script

        when:
        fails 'myTask'

        then:
        result.output.contains("java.lang.IllegalArgumentException: Unable to determine argument #0: no service of type class java.lang.String, or missing parameter value of type class java.lang.String")

        where:
        description   | script
        'Map'         | "task myTask(type: CustomTask)"
        'Map (null)'  | "task myTask(type: CustomTask, constructorArgs: null)"
        'direct call' | "tasks.create('myTask', CustomTask)"
    }

    @Unroll
    def "fails when constructorArgs not list or Object[], but #description"() {
        given:
        buildFile << CUSTOM_TASK_WITH_CONSTRUCTOR_ARGS
        buildFile << "task myTask(type: CustomTask, constructorArgs: ${constructorArgs})"

        when:
        fails 'myTask'

        then:
        result.output.contains("constructorArgs must be a List or Object[]")

        where:
        description | constructorArgs
        'Set'       | '[1, 2, 1] as Set'
        'Map'       | '[a: 1, b: 2]'
        'String'    | '"abc"'
        'primitive' | '123'
    }

    @Unroll
    def "fails when #description constructor argument is wrong type"() {
        given:
        buildFile << CUSTOM_TASK_WITH_CONSTRUCTOR_ARGS
        buildFile << "tasks.create('myTask', CustomTask, $constructorArgs)"

        when:
        fails 'myTask'

        then:
        result.output.contains("java.lang.IllegalArgumentException: Unable to determine argument #$argumentNumber: no service of type $outputType, or value 123 not assignable to type $outputType")

        where:
        description | constructorArgs | argumentNumber | outputType
        'first'     | '123, 234'      | 0              | 'class java.lang.String'
        'last'      | '"abc", "123"'  | 1              | 'int'
    }

    def "fails when null passed as a constructor argument value"() {
        given:
        buildFile << CUSTOM_TASK_WITH_CONSTRUCTOR_ARGS
        buildFile << "tasks.create('myTask', CustomTask, null, 1)"

        when:
        fails 'myTask'

        then:
        result.output.contains("java.lang.IllegalArgumentException: Unable to determine argument #0: no service of type class java.lang.String, or value null not assignable to type class java.lang.String")
    }

    def "can construct a task with @Inject services"() {
        given:
        buildFile << """
            import org.gradle.workers.WorkerExecutor
            import javax.inject.Inject

            class CustomTask extends DefaultTask {
                private final WorkerExecutor executor

                @Inject
                CustomTask(WorkerExecutor executor) {
                    this.executor = executor
                }

                @TaskAction
                void printIt() {
                    println(executor != null ? "got it" : "NOT IT")
                }
            }

            task myTask(type: CustomTask)
        """

        when:
        run 'myTask'

        then:
        result.output.contains("got it")
    }

    def "can construct a task with @Inject services and constructor args"() {
        given:
        buildFile << """
            import org.gradle.workers.WorkerExecutor
            import javax.inject.Inject

            class CustomTask extends DefaultTask {
                private final int number
                private final WorkerExecutor executor

                @Inject
                CustomTask(int number, WorkerExecutor executor) {
                    this.number = number
                    this.executor = executor
                }

                @TaskAction
                void printIt() {
                    println(executor != null ? "got it \$number" : "\$number NOT IT")
                }
            }

            tasks.create('myTask', CustomTask, 15)
        """

        when:
        run 'myTask'

        then:
        result.output.contains("got it 15")
    }

    @Requires(KOTLIN_SCRIPT)
    def 'can run custom task with constructor arguments via Kotlin friendly DSL'() {
        given:
        settingsFile << "rootProject.buildFileName = 'build.gradle.kts'"
        file("build.gradle.kts") << """
            import org.gradle.api.*
            import org.gradle.api.tasks.*
            import javax.inject.Inject

            $KOTLIN_TASK_CONTAINER_EXTENSION

            open class CustomTask @Inject constructor(private val message: String, private val number: Int) : DefaultTask() {
                @TaskAction fun run() = println("\$message \$number")
            }

            tasks.create<CustomTask>("myTask", "hello", 42)
        """

        when:
        run 'myTask'

        then:
        result.output.contains('hello 42')
    }

    @Requires(KOTLIN_SCRIPT)
    def "can construct a task in Kotlin with @Inject services"() {
        given:
        settingsFile << "rootProject.buildFileName = 'build.gradle.kts'"
        file("build.gradle.kts") << """
            import org.gradle.api.*
            import org.gradle.api.tasks.*
            import org.gradle.workers.WorkerExecutor
            import javax.inject.Inject

            open class CustomTask @Inject constructor(private val executor: WorkerExecutor) : DefaultTask() {
                @TaskAction fun run() = println(if (executor != null) "got it" else "NOT IT")
            }

            tasks.create<CustomTask>("myTask")
        """

        when:
        run 'myTask'

        then:
        result.output.contains("got it")
    }

    @Requires(KOTLIN_SCRIPT)
    def "can construct a task with @Inject services and constructor args via Kotlin friendly DSL"() {
        given:
        settingsFile << "rootProject.buildFileName = 'build.gradle.kts'"
        file("build.gradle.kts") << """
            import org.gradle.api.*
            import org.gradle.api.tasks.*
            import org.gradle.workers.WorkerExecutor
            import javax.inject.Inject

            $KOTLIN_TASK_CONTAINER_EXTENSION

            open class CustomTask @Inject constructor(private val number: Int, private val executor: WorkerExecutor) : DefaultTask() {
                @TaskAction fun run() = println(if (executor != null) "got it \$number" else "\$number NOT IT")
            }

            tasks.create<CustomTask>("myTask", 15)
        """

        when:
        run 'myTask'

        then:
        result.output.contains("got it 15")
    }
}
