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

import org.gradle.api.file.FileSystemOperations
import org.gradle.api.file.ProjectLayout
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ProviderFactory
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.process.ExecOperations
import org.gradle.util.Requires
import org.gradle.workers.WorkerExecutor
import spock.lang.Unroll

import static org.gradle.util.TestPrecondition.KOTLIN_SCRIPT

class TaskServiceInjectionIntegrationTest extends AbstractIntegrationSpec {
    def "can construct a task with @Inject services constructor arg"() {
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
        outputContains("got it")
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
        outputContains("got it 15")
    }

    def "can construct a task with @Inject service getter"() {
        given:
        buildFile << """
            import org.gradle.workers.WorkerExecutor
            import javax.inject.Inject

            class CustomTask extends DefaultTask {
                @Inject
                WorkerExecutor getExecutor() { }

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
        outputContains("got it")
    }

    def "can use @Inject service getter from constructor"() {
        given:
        buildFile << """
            import org.gradle.workers.WorkerExecutor
            import javax.inject.Inject

            class CustomTask extends DefaultTask {
                CustomTask() {
                    println(executor != null ? "got it" : "NOT IT")
                }

                @Inject
                WorkerExecutor getExecutor() { }
            }

            task myTask(type: CustomTask)
        """

        when:
        run 'myTask'

        then:
        outputContains("got it")
    }

    def "fails when task constructor with args is not annotated with @Inject"() {
        given:
        buildFile << """
            import org.gradle.workers.WorkerExecutor

            class CustomTask extends DefaultTask {
                private final WorkerExecutor executor

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
        fails 'myTask'

        then:
        failure.assertHasCause("Could not create task ':myTask'.")
        failure.assertHasCause("Could not create task of type 'CustomTask'.")
        failure.assertHasCause("The constructor for type CustomTask should be annotated with @Inject.")
    }

    def "task creation fails when service getter is not public or protected"() {
        given:
        buildFile << """
            import org.gradle.workers.WorkerExecutor
            import javax.inject.Inject
            import groovy.transform.PackageScope

            class CustomTask extends DefaultTask {
                @Inject @PackageScope
                WorkerExecutor getExecutor() { }

                @TaskAction
                void printIt() {
                    println(executor != null ? "got it" : "NOT IT")
                }
            }

            task myTask(type: CustomTask)
        """

        when:
        fails 'myTask'

        then:
        failure.assertHasCause("Could not create task ':myTask'.")
        failure.assertHasCause("Could not create task of type 'CustomTask'.")
        failure.assertHasCause("Cannot use @Inject annotation on method CustomTask.getExecutor() as it is not public or protected.")
    }

    @Requires(KOTLIN_SCRIPT)
    def "can construct a task in Kotlin with @Inject services constructor arg"() {
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
        outputContains("got it")
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

            open class CustomTask @Inject constructor(private val number: Int, private val executor: WorkerExecutor) : DefaultTask() {
                @TaskAction fun run() = println(if (executor != null) "got it \$number" else "\$number NOT IT")
            }

            tasks.create<CustomTask>("myTask", 15)
        """

        when:
        run 'myTask'

        then:
        outputContains("got it 15")
    }

    @Unroll
    def "service of type #serviceType is available for injection into task"() {
        given:
        buildFile << """
            import javax.inject.Inject

            class CustomTask extends DefaultTask {
                private final ${serviceType} service

                @Inject
                CustomTask(${serviceType} service) {
                    this.service = service
                }

                @TaskAction
                void printIt() {
                    println(service != null ? "got it" : "NOT IT")
                }
            }

            task myTask(type: CustomTask)
        """

        when:
        run 'myTask'

        then:
        outputContains("got it")

        when:
        run 'myTask'

        then:
        outputContains("got it")

        where:
        serviceType << [
            ObjectFactory,
            ProjectLayout,
            ProviderFactory,
            WorkerExecutor,
            FileSystemOperations,
            ExecOperations,
        ].collect { it.name }
    }
}
