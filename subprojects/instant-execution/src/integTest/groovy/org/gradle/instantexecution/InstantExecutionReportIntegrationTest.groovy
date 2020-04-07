/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.instantexecution

import org.gradle.api.Project
import org.gradle.api.internal.project.DefaultProject
import org.gradle.api.invocation.Gradle
import org.gradle.invocation.DefaultGradle
import spock.lang.Unroll


class InstantExecutionReportIntegrationTest extends AbstractInstantExecutionIntegrationTest {

    def "state serialization errors are reported and fail the build"() {

        given:
        def instant = newInstantExecutionFixture()
        def stateSerializationProblems = withStateSerializationProblems()
        def stateSerializationErrors = withStateSerializationErrors()
        def errorSpec = problems.newErrorSpec(stateSerializationErrors.first()) {
            withUniqueProblems(stateSerializationProblems)
            withProblemsWithStackTraceCount(0)
        }

        when:
        instantFails 'taskWithStateSerializationProblems', 'taskWithStateSerializationError'

        then:
        notExecuted('taskWithStateSerializationProblems', 'taskWithStateSerializationError')

        and:
        instant.assertStateStoreFailed()
        problems.assertFailureHasError(failure, errorSpec)
        failure.assertHasFileName("Build file '${buildFile.absolutePath}'")
        failure.assertHasLineNumber(9)
        failure.assertHasCause("BOOM")

        when:
        problems.withDoNotFailOnProblems()
        instantFails 'taskWithStateSerializationProblems', 'taskWithStateSerializationError'

        then:
        notExecuted('taskWithStateSerializationProblems', 'taskWithStateSerializationError')

        and:
        instant.assertStateStoreFailed()
        problems.assertFailureHasError(failure, errorSpec)
        failure.assertHasFileName("Build file '${buildFile.absolutePath}'")
        failure.assertHasLineNumber(9)
        failure.assertHasCause("BOOM")
    }

    def "problems are reported and fail the build by default"() {

        given:
        def instantExecution = newInstantExecutionFixture()
        def stateSerializationProblems = withStateSerializationProblems()
        def taskExecutionProblems = withTaskExecutionProblems()

        when:
        instantFails 'taskWithStateSerializationProblems', 'a', 'b'

        then:
        executed(':taskWithStateSerializationProblems', ':a', ':b')
        problems.assertFailureHasProblems(failure) {
            withUniqueProblems(taskExecutionProblems + stateSerializationProblems)
            withProblemsWithStackTraceCount(2)
        }

        when:
        problems.withDoNotFailOnProblems()
        instantRun 'taskWithStateSerializationProblems', 'a', 'b'

        then:
        executed(':taskWithStateSerializationProblems', ':a', ':b')
        instantExecution.assertStateStored()
        problems.assertResultHasProblems(result) {
            withUniqueProblems(taskExecutionProblems + stateSerializationProblems)
            withProblemsWithStackTraceCount(2)
        }

        when:
        instantFails 'taskWithStateSerializationProblems', 'a', 'b'

        then:
        executed(':taskWithStateSerializationProblems', ':a', ':b')
        instantExecution.assertStateLoaded()
        problems.assertFailureHasProblems(failure) {
            withUniqueProblems(taskExecutionProblems)
            withProblemsWithStackTraceCount(2)
        }
    }

    def "problems are reported and fail the build when failOnProblems is false but maxProblems is reached"() {

        given:
        def stateSerializationProblems = withStateSerializationProblems()
        def taskExecutionProblems = withTaskExecutionProblems()

        when:
        instantFails 'taskWithStateSerializationProblems', 'a', 'b',
            "-D${SystemProperties.failOnProblems}=false", "-D${SystemProperties.maxProblems}=2"

        then:
        notExecuted(':taskWithStateSerializationProblems', ':a', ':b')
        problems.assertFailureHasTooManyProblems(failure) {
            withUniqueProblems(stateSerializationProblems)
            withProblemsWithStackTraceCount(0)
        }

        when:
        instantFails 'taskWithStateSerializationProblems', 'a', 'b',
            "-D${SystemProperties.failOnProblems}=false", "-D${SystemProperties.maxProblems}=4"

        then:
        executed(':taskWithStateSerializationProblems', ':a', ':b')
        problems.assertFailureHasTooManyProblems(failure) {
            withRootCauseDescription("Execution failed for task ':b'.")
            withUniqueProblems(taskExecutionProblems + stateSerializationProblems)
            withProblemsWithStackTraceCount(2)
        }
    }

    def "problems not causing build failure are reported"() {

        given:
        settingsFile << "rootProject.name = 'test'"
        def expectedProblems = withStateSerializationProblems()
        buildFile << """
            taskWithStateSerializationProblems.doFirst { throw new Exception("BOOM") }
        """

        when:
        problems.withDoNotFailOnProblems()
        instantFails 'taskWithStateSerializationProblems'

        then:
        problems.assertResultHasProblems(result) {
            withUniqueProblems(expectedProblems)
            withProblemsWithStackTraceCount(0)
        }

        and:
        failure.assertHasDescription("Execution failed for task ':taskWithStateSerializationProblems'.")
        failure.assertHasCause("java.lang.Exception: BOOM")
    }

    private List<String> withStateSerializationErrors() {
        buildFile << """
            class BrokenSerializable implements java.io.Serializable {
                private void writeObject(java.io.ObjectOutputStream out) throws IOException {
                    throw new RuntimeException("BOOM")
                }
            }

            task taskWithStateSerializationError {
                inputs.property 'brokenProperty', new BrokenSerializable()
                inputs.property 'otherBrokenProperty', new BrokenSerializable()
            }
        """
        return [
            "input property 'brokenProperty' of ':taskWithStateSerializationError': error writing value of type 'BrokenSerializable'"
        ]
    }

    private List<String> withStateSerializationProblems() {
        buildFile << """
            task taskWithStateSerializationProblems {
                inputs.property 'brokenProperty', project
                inputs.property 'otherBrokenProperty', project
            }
        """
        return [
            "input property 'brokenProperty' of ':taskWithStateSerializationProblems': cannot serialize object of type '${DefaultProject.name}', a subtype of '${Project.name}', as these are not supported with instant execution.",
            "input property 'otherBrokenProperty' of ':taskWithStateSerializationProblems': cannot serialize object of type '${DefaultProject.name}', a subtype of '${Project.name}', as these are not supported with instant execution.",
        ]
    }

    private List<String> withTaskExecutionProblems() {
        buildFile << """
            abstract class MyTask extends DefaultTask {
                @TaskAction
                def action() {
                    println("project:${'\$'}{project.name}")
                }
            }

            tasks.register("a", MyTask)
            tasks.register("b", MyTask)
        """
        return [
            "task `:a` of type `MyTask`: invocation of 'Task.project' at execution time is unsupported.",
            "task `:b` of type `MyTask`: invocation of 'Task.project' at execution time is unsupported."
        ]
    }

    @Unroll
    def "reports #invocation access during execution"() {

        def instantExecution = newInstantExecutionFixture()

        given:
        settingsFile << "rootProject.name = 'root'"
        buildFile << """
            abstract class MyTask extends DefaultTask {
                @TaskAction
                def action() {
                    println($code)
                }
            }

            tasks.register("a", MyTask)
            tasks.register("b", MyTask)
        """

        and:
        def expectedProblems = [
            "task `:a` of type `MyTask`: invocation of '$invocation' at execution time is unsupported.",
            "task `:b` of type `MyTask`: invocation of '$invocation' at execution time is unsupported."
        ]

        when:
        instantFails "a", "b"

        then:
        instantExecution.assertStateStored()
        problems.assertFailureHasProblems(failure) {
            withUniqueProblems(expectedProblems)
            withProblemsWithStackTraceCount(2)
        }

        when:
        problems.withDoNotFailOnProblems()
        instantRun "a", "b"

        then:
        instantExecution.assertStateLoaded()

        and:
        problems.assertResultHasProblems(result) {
            withUniqueProblems(expectedProblems)
            withProblemsWithStackTraceCount(2)
        }

        where:
        invocation              | code
        'Task.project'          | 'project.name'
        'Task.dependsOn'        | 'dependsOn'
        'Task.taskDependencies' | 'taskDependencies'
    }

    def "summarizes unsupported properties"() {
        given:
        buildFile << """
            class SomeBean {
                Gradle gradle
                def nested = new NestedBean()
            }

            class NestedBean {
                Gradle gradle
                Project project
            }

            class SomeTask extends DefaultTask {
                private final bean = new SomeBean()

                SomeTask() {
                    bean.gradle = project.gradle
                    bean.nested.gradle = project.gradle
                    bean.nested.project = project
                }

                @TaskAction
                void run() {
                }
            }

            // ensure there are multiple warnings for the same properties
            task a(type: SomeTask)
            task b(type: SomeTask)
            task c(dependsOn: [a, b])
        """

        when:
        problems.withDoNotFailOnProblems()
        instantRun "c"

        then:
        problems.assertResultHasProblems(result) {
            withTotalProblemsCount(6)
            withUniqueProblems(
                "field 'gradle' from type 'SomeBean': cannot serialize object of type '${DefaultGradle.name}', a subtype of '${Gradle.name}', as these are not supported with instant execution.",
                "field 'gradle' from type 'NestedBean': cannot serialize object of type '${DefaultGradle.name}', a subtype of '${Gradle.name}', as these are not supported with instant execution.",
                "field 'project' from type 'NestedBean': cannot serialize object of type '${DefaultProject.name}', a subtype of '${Project.name}', as these are not supported with instant execution."
            )
            withProblemsWithStackTraceCount(0)
        }
    }

    @Unroll
    def "can limit the number of problems to #maxProblems"() {
        given:
        buildFile << """
            class Bean {
                Project p1
                Project p2
                Project p3
            }

            class FooTask extends DefaultTask {
                private final bean = new Bean()

                FooTask() {
                    bean.with {
                        p1 = project
                        p2 = project
                        p3 = project
                    }
                }

                @TaskAction
                void run() {
                }
            }

            task foo(type: FooTask)
        """

        when:
        problems.withDoNotFailOnProblems()
        instantFails "foo", "-D${SystemProperties.maxProblems}=$maxProblems"

        then:
        def expectedProblems = (1..expectedNumberOfProblems).collect {
            "field 'p$it' from type 'Bean': cannot serialize object of type '${DefaultProject.name}', a subtype of '${Project.name}', as these are not supported with instant execution."
        }
        problems.assertFailureHasTooManyProblems(failure) {
            withTotalProblemsCount(expectedNumberOfProblems)
            withUniqueProblems(expectedProblems)
            withProblemsWithStackTraceCount(0)
        }

        where:
        maxProblems << [0, 1, 2]
        expectedNumberOfProblems = Math.max(1, maxProblems)
    }

    def "can request to not fail on problems"() {
        given:
        buildFile << """
            class Bean { Project p1 }

            class FooTask extends DefaultTask {
                private final bean = new Bean()
                FooTask() { bean.p1 = project }
                @TaskAction void run() {}
            }

            task foo(type: FooTask)
        """

        when:
        instantRun "foo", "-D${SystemProperties.failOnProblems}=false"

        then:
        problems.assertResultHasProblems(result) {
            withUniqueProblems(
                "field 'p1' from type 'Bean': cannot serialize object of type '${DefaultProject.name}', a subtype of '${Project.name}', as these are not supported with instant execution."
            )
            withProblemsWithStackTraceCount(0)
        }
    }
}
