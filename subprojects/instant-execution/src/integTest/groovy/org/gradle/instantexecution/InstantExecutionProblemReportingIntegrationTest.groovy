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

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.internal.project.DefaultProject
import org.gradle.api.invocation.Gradle
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.invocation.DefaultGradle
import spock.lang.IgnoreIf
import spock.lang.Unroll


@IgnoreIf({ GradleContextualExecuter.isNoDaemon() })
class InstantExecutionProblemReportingIntegrationTest extends AbstractInstantExecutionIntegrationTest {

    def "state serialization errors always halt the build and invalidate the cache"() {
        given:
        def instant = newInstantExecutionFixture()

        buildFile << """
            class BrokenSerializable implements java.io.Serializable {
                private void writeObject(java.io.ObjectOutputStream out) throws IOException {
                    throw new RuntimeException("BOOM")
                }
            }

            class BrokenTaskType extends DefaultTask {
                final prop = new BrokenSerializable()
            }

            task broken(type: BrokenTaskType)
        """

        when:
        instantFails 'broken'

        then:
        failure.assertTasksExecuted()

        and:
        instant.assertStateStoreFailed()
        outputContains("Configuration cache entry discarded.")
        failure.assertHasFailures(1)
        failure.assertHasFileName("Build file '${buildFile.absolutePath}'")
        failure.assertHasLineNumber(4)
        failure.assertHasDescription("Configuration cache state could not be cached: field 'prop' from type 'BrokenTaskType': error writing value of type 'BrokenSerializable'")
        failure.assertHasCause("BOOM")
        problems.assertResultHasProblems(failure) {
        }

        when:
        instantFails WARN_PROBLEMS_CLI_OPT, 'broken'

        then:
        failure.assertTasksExecuted()

        and:
        instant.assertStateStoreFailed()
        outputContains("Configuration cache entry discarded.")
        failure.assertHasFailures(1)
        failure.assertHasFileName("Build file '${buildFile.absolutePath}'")
        failure.assertHasLineNumber(4)
        failure.assertHasDescription("Configuration cache state could not be cached: field 'prop' from type 'BrokenTaskType': error writing value of type 'BrokenSerializable'")
        failure.assertHasCause("BOOM")
        problems.assertResultHasProblems(failure) {
        }
    }

    def "state serialization errors always halt the build and earlier problems reported"() {
        given:
        def instant = newInstantExecutionFixture()

        buildFile << """
            class BrokenSerializable implements java.io.Serializable {
                private void writeObject(java.io.ObjectOutputStream out) throws IOException {
                    throw new RuntimeException("BOOM")
                }
            }

            class BrokenTaskType extends DefaultTask {
                final prop = new BrokenSerializable()
            }

            task problems {
                inputs.property 'brokenProperty', project
                inputs.property 'otherBrokenProperty', project
            }

            task broken(type: BrokenTaskType)
        """

        when:
        instantFails 'problems', 'broken'

        then:
        failure.assertTasksExecuted()

        and:
        instant.assertStateStoreFailed()
        outputContains("Configuration cache entry discarded with 2 problems.")
        failure.assertHasFailures(1)
        failure.assertHasFileName("Build file '${buildFile.absolutePath}'")
        failure.assertHasLineNumber(4)
        failure.assertHasDescription("Configuration cache state could not be cached: field 'prop' from type 'BrokenTaskType': error writing value of type 'BrokenSerializable'")
        failure.assertHasCause("BOOM")
        problems.assertResultHasProblems(failure) {
            withProblem("input property 'brokenProperty' of ':problems': cannot serialize object of type 'org.gradle.api.internal.project.DefaultProject', a subtype of 'org.gradle.api.Project', as these are not supported with the configuration cache.")
            withProblem("input property 'otherBrokenProperty' of ':problems': cannot serialize object of type 'org.gradle.api.internal.project.DefaultProject', a subtype of 'org.gradle.api.Project', as these are not supported with the configuration cache.")
            problemsWithStackTraceCount = 0
        }

        when:
        instantFails WARN_PROBLEMS_CLI_OPT, 'problems', 'broken'

        then:
        failure.assertTasksExecuted()

        and:
        instant.assertStateStoreFailed()
        outputContains("Configuration cache entry discarded with 2 problems.")
        failure.assertHasFailures(1)
        failure.assertHasFileName("Build file '${buildFile.absolutePath}'")
        failure.assertHasLineNumber(4)
        failure.assertHasDescription("Configuration cache state could not be cached: field 'prop' from type 'BrokenTaskType': error writing value of type 'BrokenSerializable'")
        failure.assertHasCause("BOOM")
        problems.assertResultHasProblems(failure) {
            withProblem("input property 'brokenProperty' of ':problems': cannot serialize object of type 'org.gradle.api.internal.project.DefaultProject', a subtype of 'org.gradle.api.Project', as these are not supported with the configuration cache.")
            withProblem("input property 'otherBrokenProperty' of ':problems': cannot serialize object of type 'org.gradle.api.internal.project.DefaultProject', a subtype of 'org.gradle.api.Project', as these are not supported with the configuration cache.")
            problemsWithStackTraceCount = 0
        }
    }

    def "serialization problems are reported and fail the build by default and do not invalidate cache"() {
        given:
        def instantExecution = newInstantExecutionFixture()

        buildFile << """
            class BrokenTaskType extends DefaultTask {
                final prop = project
                final anotherProp = project.configurations
            }

            task problems {
                inputs.property 'brokenProperty', project
                inputs.property 'otherBrokenProperty', project
            }

            task moreProblems(type: BrokenTaskType)

            task ok

            task all {
                dependsOn 'problems', 'moreProblems', 'ok'
            }
        """

        when:
        instantFails 'all'

        then:
        executed(':problems', ':moreProblems', ':ok', ':all')
        instantExecution.assertStateStored() // does not fail
        outputContains("Configuration cache entry stored with 4 problems.")
        problems.assertFailureHasProblems(failure) {
            withProblem("field 'anotherProp' from type 'BrokenTaskType': cannot serialize object of type 'org.gradle.api.internal.artifacts.configurations.DefaultConfigurationContainer', a subtype of 'org.gradle.api.artifacts.ConfigurationContainer', as these are not supported with the configuration cache.")
            withProblem("field 'prop' from type 'BrokenTaskType': cannot serialize object of type 'org.gradle.api.internal.project.DefaultProject', a subtype of 'org.gradle.api.Project', as these are not supported with the configuration cache.")
            withProblem("input property 'brokenProperty' of ':problems': cannot serialize object of type 'org.gradle.api.internal.project.DefaultProject', a subtype of 'org.gradle.api.Project', as these are not supported with the configuration cache.")
            withProblem("input property 'otherBrokenProperty' of ':problems': cannot serialize object of type 'org.gradle.api.internal.project.DefaultProject', a subtype of 'org.gradle.api.Project', as these are not supported with the configuration cache.")
            problemsWithStackTraceCount = 0
        }
        failure.assertHasFailures(1)

        when:
        instantRunLenient 'all'

        then:
        executed(':problems', ':moreProblems', ':ok', ':all')
        instantExecution.assertStateLoaded()
        problems.assertResultHasProblems(result) {
            withProblem("field 'anotherProp' from type 'BrokenTaskType': cannot deserialize object of type 'org.gradle.api.artifacts.ConfigurationContainer' as these are not supported with the configuration cache.")
            withProblem("field 'prop' from type 'BrokenTaskType': cannot deserialize object of type 'org.gradle.api.Project' as these are not supported with the configuration cache.")
            withProblem("input property 'brokenProperty' of ':problems': cannot deserialize object of type 'org.gradle.api.Project' as these are not supported with the configuration cache.")
            withProblem("input property 'otherBrokenProperty' of ':problems': cannot deserialize object of type 'org.gradle.api.Project' as these are not supported with the configuration cache.")
            problemsWithStackTraceCount = 0
        }
        postBuildOutputContains("Configuration cache entry reused with 4 problems.")

        when:
        instantFails 'all'

        then:
        executed(':problems', ':moreProblems', ':ok', ':all')
        instantExecution.assertStateLoaded()
        problems.assertFailureHasProblems(failure) {
            withProblem("field 'anotherProp' from type 'BrokenTaskType': cannot deserialize object of type 'org.gradle.api.artifacts.ConfigurationContainer' as these are not supported with the configuration cache.")
            withProblem("field 'prop' from type 'BrokenTaskType': cannot deserialize object of type 'org.gradle.api.Project' as these are not supported with the configuration cache.")
            withProblem("input property 'brokenProperty' of ':problems': cannot deserialize object of type 'org.gradle.api.Project' as these are not supported with the configuration cache.")
            withProblem("input property 'otherBrokenProperty' of ':problems': cannot deserialize object of type 'org.gradle.api.Project' as these are not supported with the configuration cache.")
            problemsWithStackTraceCount = 0
        }
        outputContains("Configuration cache entry reused with 4 problems.")
    }

    def "configuration time problems are reported and fail the build by default only when configuration is executed and do not invalidate the cache"() {
        given:
        def instantExecution = newInstantExecutionFixture()

        buildFile << """
            gradle.buildFinished { }
            gradle.buildFinished { }

            task all
        """

        when:
        instantFails 'all'

        then:
        executed(':all')
        instantExecution.assertStateStored() // does not fail
        outputContains("Configuration cache entry stored with 2 problems.")
        problems.assertFailureHasProblems(failure) {
            withProblem("build file 'build.gradle': registration of listener on 'Gradle.buildFinished' is unsupported")
            withTotalProblemsCount(2)
        }
        failure.assertHasFailures(1)

        when:
        instantRunLenient 'all'

        then:
        executed(':all')
        instantExecution.assertStateLoaded()
        postBuildOutputContains("Configuration cache entry reused.")
        problems.assertResultHasProblems(result) {
            // TODO - should give some indication to the user that the build may not work correctly
        }

        when:
        instantRun 'all'

        then:
        executed(':all')
        instantExecution.assertStateLoaded()
        postBuildOutputContains("Configuration cache entry reused.")
        problems.assertResultHasProblems(result) {
            // TODO - should fail and give some indication to the user why
        }
    }

    def "task execution problems are reported and fail the build by default and do not invalidate cache"() {
        given:
        def instantExecution = newInstantExecutionFixture()

        buildFile << """
            task broken {
                doLast {
                    println("project = " + project.name)
                }
            }
            task anotherBroken {
                doLast {
                    println("configurations = " + project.configurations.all)
                }
            }

            task all {
                dependsOn 'broken', 'anotherBroken'
            }
        """

        when:
        instantFails 'all'

        then:
        executed(':all')
        instantExecution.assertStateStored() // does not fail
        outputContains("Configuration cache entry stored with 2 problems.")
        problems.assertFailureHasProblems(failure) {
            withProblem("build file 'build.gradle': invocation of 'Task.project' at execution time is unsupported.")
            withTotalProblemsCount(2)
        }
        failure.assertHasFailures(1)

        when:
        instantRunLenient 'all'

        then:
        executed(':all')
        instantExecution.assertStateLoaded()
        postBuildOutputContains("Configuration cache entry reused with 2 problems.")
        problems.assertResultHasProblems(result) {
            // TODO - retain the location information
            withProblem("task `:anotherBroken` of type `org.gradle.api.DefaultTask`: invocation of 'Task.project' at execution time is unsupported.")
            withProblem("task `:broken` of type `org.gradle.api.DefaultTask`: invocation of 'Task.project' at execution time is unsupported.")
        }

        when:
        instantFails 'all'

        then:
        executed(':all')
        instantExecution.assertStateLoaded()
        outputContains("Configuration cache entry reused with 2 problems.")
        problems.assertFailureHasProblems(failure) {
            withProblem("task `:anotherBroken` of type `org.gradle.api.DefaultTask`: invocation of 'Task.project' at execution time is unsupported.")
            withProblem("task `:broken` of type `org.gradle.api.DefaultTask`: invocation of 'Task.project' at execution time is unsupported.")
        }
    }

    def "problems are reported and fail the build when there are other build failures"() {
        given:
        def instantExecution = newInstantExecutionFixture()

        buildFile << """
            task broken {
                doLast {
                    println("project = " + project.name)
                    throw new RuntimeException("BOOM")
                }
            }

            task all {
                dependsOn 'broken'
            }
        """

        when:
        instantFails 'all'

        then:
        instantExecution.assertStateStored() // does not fail
        outputContains("Configuration cache entry stored with 1 problem.")
        problems.assertFailureHasProblems(failure) {
            withProblem("build file 'build.gradle': invocation of 'Task.project' at execution time is unsupported.")
        }
        failure.assertHasDescription("Execution failed for task ':broken'.")
        failure.assertHasCause("BOOM")
        failure.assertHasFailures(2)

        when:
        instantFails WARN_PROBLEMS_CLI_OPT, 'all'

        then:
        instantExecution.assertStateLoaded()
        outputContains("Configuration cache entry reused with 1 problem.")
        problems.assertResultHasProblems(result) {
            withProblem("task `:broken` of type `org.gradle.api.DefaultTask`: invocation of 'Task.project' at execution time is unsupported.")
        }
        failure.assertHasDescription("Execution failed for task ':broken'.")
        failure.assertHasCause("BOOM")
        failure.assertHasFailures(1)

        when:
        instantFails 'all'

        then:
        instantExecution.assertStateLoaded()
        outputContains("Configuration cache entry reused with 1 problem.")
        problems.assertFailureHasProblems(failure) {
            withProblem("task `:broken` of type `org.gradle.api.DefaultTask`: invocation of 'Task.project' at execution time is unsupported.")
        }
        failure.assertHasDescription("Execution failed for task ':broken'.")
        failure.assertHasCause("BOOM")
        failure.assertHasFailures(2)
    }

    def "problems are reported and fail the build when failOnProblems is false but maxProblems is reached"() {
        given:
        def instantExecution = newInstantExecutionFixture()

        buildFile << """
            class BrokenTask extends DefaultTask {
                @Internal
                final broken = project

                @TaskAction
                def go() {
                    try {
                        println("project = " + project)
                    } catch(Exception e) {
                        // should not happen, problems are collected
                        throw new RuntimeException("broken")
                    }
                }
            }

            task problems(type: BrokenTask)
            task moreProblems(type: BrokenTask)

            task all {
                dependsOn('problems', 'moreProblems')
            }
        """

        when:
        instantFails WARN_PROBLEMS_CLI_OPT, "$MAX_PROBLEMS_SYS_PROP=2", 'all'

        then:
        executed(':problems', ':moreProblems', ':all')
        instantExecution.assertStateStored() // does not fail
        outputContains("Configuration cache entry stored with 4 problems.")
        problems.assertFailureHasTooManyProblems(failure) {
            withProblem("task `:moreProblems` of type `BrokenTask`: invocation of 'Task.project' at execution time is unsupported.")
            withProblem("field 'broken' from type 'BrokenTask': cannot serialize object of type 'org.gradle.api.internal.project.DefaultProject', a subtype of 'org.gradle.api.Project', as these are not supported with the configuration cache.")
            withProblem("task `:problems` of type `BrokenTask`: invocation of 'Task.project' at execution time is unsupported.")
            totalProblemsCount = 4
            problemsWithStackTraceCount = 2
        }

        when:
        instantFails WARN_PROBLEMS_CLI_OPT, "$MAX_PROBLEMS_SYS_PROP=3", 'all'

        then:
        executed(':problems', ':moreProblems', ':all')
        instantExecution.assertStateLoaded()
        outputContains("Configuration cache entry reused with 4 problems.")
        problems.assertFailureHasTooManyProblems(failure) {
            withProblem("task `:moreProblems` of type `BrokenTask`: invocation of 'Task.project' at execution time is unsupported.")
            withProblem("field 'broken' from type 'BrokenTask': cannot deserialize object of type 'org.gradle.api.Project' as these are not supported with the configuration cache.")
            withProblem("task `:problems` of type `BrokenTask`: invocation of 'Task.project' at execution time is unsupported.")
            totalProblemsCount = 4
            problemsWithStackTraceCount = 2
        }
        failure.assertHasFailures(1)

        when:
        instantRun WARN_PROBLEMS_CLI_OPT, "$MAX_PROBLEMS_SYS_PROP=4", 'all'

        then:
        executed(':problems', ':moreProblems', ':all')
        instantExecution.assertStateLoaded()
        postBuildOutputContains("Configuration cache entry reused with 4 problems.")
        problems.assertResultHasProblems(result) {
            withProblem("task `:moreProblems` of type `BrokenTask`: invocation of 'Task.project' at execution time is unsupported.")
            withProblem("field 'broken' from type 'BrokenTask': cannot deserialize object of type 'org.gradle.api.Project' as these are not supported with the configuration cache.")
            withProblem("task `:problems` of type `BrokenTask`: invocation of 'Task.project' at execution time is unsupported.")
            totalProblemsCount = 4
            problemsWithStackTraceCount = 2
        }
    }

    def "fails regardless of maxProblems when failOnProblems is true"() {
        given:
        def instantExecution = newInstantExecutionFixture()

        buildFile << """
            class BrokenTask extends DefaultTask {
                @Internal
                final broken = project

                @TaskAction
                def go() {
                    println("project = " + project)
                }
            }

            task problems(type: BrokenTask)
            task moreProblems(type: BrokenTask)

            task all {
                dependsOn('problems', 'moreProblems')
            }
        """

        when:
        instantFails "$MAX_PROBLEMS_SYS_PROP=2", 'all'

        then:
        executed(':problems', ':moreProblems', ':all')
        instantExecution.assertStateStored() // does not fail
        outputContains("Configuration cache entry stored with 4 problems.")
        problems.assertFailureHasProblems(failure) {
            withProblem("task `:moreProblems` of type `BrokenTask`: invocation of 'Task.project' at execution time is unsupported.")
            withProblem("field 'broken' from type 'BrokenTask': cannot serialize object of type 'org.gradle.api.internal.project.DefaultProject', a subtype of 'org.gradle.api.Project', as these are not supported with the configuration cache.")
            withProblem("task `:problems` of type `BrokenTask`: invocation of 'Task.project' at execution time is unsupported.")
            totalProblemsCount = 4
            problemsWithStackTraceCount = 2
        }

        when:
        instantFails "$MAX_PROBLEMS_SYS_PROP=2", 'all'

        then:
        executed(':problems', ':moreProblems', ':all')
        instantExecution.assertStateLoaded()
        outputContains("Configuration cache entry reused with 4 problems.")
        problems.assertFailureHasProblems(failure) {
            withProblem("task `:moreProblems` of type `BrokenTask`: invocation of 'Task.project' at execution time is unsupported.")
            withProblem("field 'broken' from type 'BrokenTask': cannot deserialize object of type 'org.gradle.api.Project' as these are not supported with the configuration cache.")
            withProblem("task `:problems` of type `BrokenTask`: invocation of 'Task.project' at execution time is unsupported.")
            totalProblemsCount = 4
            problemsWithStackTraceCount = 2
        }
        failure.assertHasFailures(1)

        when:
        instantFails "$MAX_PROBLEMS_SYS_PROP=2000", 'all'

        then:
        executed(':problems', ':moreProblems', ':all')
        instantExecution.assertStateLoaded()
        outputContains("Configuration cache entry reused with 4 problems.")
        problems.assertFailureHasProblems(failure) {
            withProblem("task `:moreProblems` of type `BrokenTask`: invocation of 'Task.project' at execution time is unsupported.")
            withProblem("field 'broken' from type 'BrokenTask': cannot deserialize object of type 'org.gradle.api.Project' as these are not supported with the configuration cache.")
            withProblem("task `:problems` of type `BrokenTask`: invocation of 'Task.project' at execution time is unsupported.")
            totalProblemsCount = 4
            problemsWithStackTraceCount = 2
        }
    }

    def "report does not include configuration and runtime problems from buildSrc"() {
        file("buildSrc/build.gradle") << """
            // These should not be reported, as neither of these are serialized
            gradle.buildFinished { }
            classes.doLast { t -> t.project }
        """
        buildFile << """
            gradle.addListener(new BuildAdapter())
            task broken {
                inputs.property('p', project)
            }
        """

        when:
        instantFails("broken")

        then:
        outputContains("Configuration cache entry stored with 2 problems.")
        problems.assertFailureHasProblems(failure) {
            withProblem("build file 'build.gradle': registration of listener on 'Gradle.addListener' is unsupported")
            withProblem("input property 'p' of ':broken': cannot serialize object of type 'org.gradle.api.internal.project.DefaultProject', a subtype of 'org.gradle.api.Project', as these are not supported with the configuration cache.")
            problemsWithStackTraceCount = 1
        }
        failure.assertHasFailures(1)

        when:
        instantRunLenient("broken")

        then:
        postBuildOutputContains("Configuration cache entry reused with 1 problem.")
        problems.assertResultHasProblems(result) {
            withProblem("input property 'p' of ':broken': cannot deserialize object of type 'org.gradle.api.Project' as these are not supported with the configuration cache.")
            problemsWithStackTraceCount = 0
        }
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

            class MyAction implements Action<Task> {
                void execute(Task task) {
                    task.$code
                }
            }

            tasks.register("a", MyTask)
            tasks.register("b", MyTask) {
                doLast(new MyAction())
            }
            tasks.register("c") {
                doFirst(new MyAction())
            }
            tasks.register("d") {
                doFirst { $code }
            }
        """

        when:
        instantFails "a", "b", "c", "d"

        then:
        instantExecution.assertStateStored()
        outputContains("Configuration cache entry stored with 5 problems.")
        problems.assertFailureHasProblems(failure) {
            withProblem("build file 'build.gradle': invocation of '$invocation' at execution time is unsupported.")
            withProblem("task `:a` of type `MyTask`: invocation of '$invocation' at execution time is unsupported.")
            withProblem("task `:b` of type `MyTask`: invocation of '$invocation' at execution time is unsupported.")
            withProblem("task `:c` of type `org.gradle.api.DefaultTask`: invocation of '$invocation' at execution time is unsupported.")
            withTotalProblemsCount(5)
        }

        when:
        instantRunLenient "a", "b", "c", "d"

        then:
        instantExecution.assertStateLoaded()
        postBuildOutputContains("Configuration cache entry reused with 5 problems.")

        and:
        problems.assertResultHasProblems(result) {
            withProblem("task `:a` of type `MyTask`: invocation of '$invocation' at execution time is unsupported.")
            withProblem("task `:b` of type `MyTask`: invocation of '$invocation' at execution time is unsupported.")
            withProblem("task `:c` of type `org.gradle.api.DefaultTask`: invocation of '$invocation' at execution time is unsupported.")
            // TODO - should retain the location information
            withProblem("task `:d` of type `org.gradle.api.DefaultTask`: invocation of '$invocation' at execution time is unsupported.")
            withTotalProblemsCount(5)
        }

        where:
        invocation              | code
        'Task.project'          | 'project.name'
        'Task.dependsOn'        | 'dependsOn'
        'Task.taskDependencies' | 'taskDependencies'
    }

    @Unroll
    def "report build listener registration on #registrationPoint"() {

        given:
        buildFile << code

        when:
        executer.noDeprecationChecks()
        instantFails 'help'

        then:
        outputContains("Configuration cache entry stored with 1 problem.")
        problems.assertFailureHasProblems(failure) {
            withUniqueProblems("build file 'build.gradle': registration of listener on '$registrationPoint' is unsupported")
        }

        where:
        registrationPoint                             | code
        "Gradle.addBuildListener"                     | "gradle.addBuildListener(new BuildAdapter())"
        "Gradle.addListener"                          | "gradle.addListener(new BuildAdapter())"
        "Gradle.buildStarted"                         | "gradle.buildStarted {}"
        "Gradle.buildFinished"                        | "gradle.buildFinished {}"
        "TaskExecutionGraph.addTaskExecutionListener" | "gradle.taskGraph.addTaskExecutionListener(new TaskExecutionAdapter())"
        "TaskExecutionGraph.beforeTask"               | "gradle.taskGraph.beforeTask {}"
        "TaskExecutionGraph.afterTask"                | "gradle.taskGraph.afterTask {}"
    }

    @Unroll
    def "does not report problems on configuration listener registration on #registrationPoint"() {

        given:
        buildFile << """

            class ProjectEvaluationAdapter implements ProjectEvaluationListener {
                void beforeEvaluate(Project project) {}
                void afterEvaluate(Project project, ProjectState state) {}
            }

            $code
        """

        expect:
        instantRun 'help'
        postBuildOutputContains("Configuration cache entry stored.")

        where:
        registrationPoint                     | code
        "Gradle.addProjectEvaluationListener" | "gradle.addProjectEvaluationListener(new ProjectEvaluationAdapter())"
        "Gradle.addListener"                  | "gradle.addListener(new ProjectEvaluationAdapter())"
        "Gradle.beforeSettings"               | "gradle.beforeSettings {}"
        "Gradle.settingsEvaluated"            | "gradle.settingsEvaluated {}"
        "Gradle.projectsLoaded"               | "gradle.projectsLoaded {}"
        "Gradle.beforeProject"                | "gradle.beforeProject {}"
        "Gradle.afterProject"                 | "gradle.afterProject {}"
        "Gradle.projectsEvaluated"            | "gradle.projectsEvaluated {}"
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
        instantRunLenient "c"

        then:
        postBuildOutputContains("Configuration cache entry stored with 6 problems.")
        problems.assertResultHasProblems(result) {
            withTotalProblemsCount(6)
            withUniqueProblems(
                "field 'gradle' from type 'SomeBean': cannot serialize object of type '${DefaultGradle.name}', a subtype of '${Gradle.name}', as these are not supported with the configuration cache.",
                "field 'gradle' from type 'NestedBean': cannot serialize object of type '${DefaultGradle.name}', a subtype of '${Gradle.name}', as these are not supported with the configuration cache.",
                "field 'project' from type 'NestedBean': cannot serialize object of type '${DefaultProject.name}', a subtype of '${Project.name}', as these are not supported with the configuration cache."
            )
            problemsWithStackTraceCount = 0
        }
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
        run ENABLE_CLI_OPT, WARN_PROBLEMS_CLI_OPT, "foo"

        then:
        postBuildOutputContains("Configuration cache entry stored with 1 problem.")
        problems.assertResultHasProblems(result) {
            withUniqueProblems(
                "field 'p1' from type 'Bean': cannot serialize object of type '${DefaultProject.name}', a subtype of '${Project.name}', as these are not supported with the configuration cache."
            )
            problemsWithStackTraceCount = 0
        }
    }

    def "reports problems from container all { } block"() {
        file("script.gradle") << """
            tasks.all {
                System.getProperty("PROP")
                gradle.buildFinished { }
            }
        """
        buildFile << """
            apply from: 'script.gradle'
            task ok
        """

        when:
        instantFails("ok", "-DPROP=12")

        then:
        outputContains("Configuration cache entry stored with 26 problems.")
        // TODO - use fixture. Need to be able to accept a range of expected problem counts
        failure.assertThatDescription(containsNormalizedString("script 'script.gradle': read system property 'PROP'"))
        failure.assertThatDescription(containsNormalizedString("script 'script.gradle': registration of listener on 'Gradle.buildFinished' is unsupported"))
    }

    def "reports problems from deferred task configuration action block"() {
        file("script.gradle") << """
            tasks.configureEach {
                System.getProperty("PROP")
                gradle.buildFinished { }
            }
        """
        buildFile << """
            apply from: 'script.gradle'
            task ok
        """

        when:
        instantFails("ok", "-DPROP=12")

        then:
        outputContains("Configuration cache entry stored with 2 problems.")
        problems.assertFailureHasProblems(failure) {
            withProblem("script 'script.gradle': read system property 'PROP'")
            withProblem("script 'script.gradle': registration of listener on 'Gradle.buildFinished' is unsupported")
        }
    }

    def "reports problems from afterEvaluate { } block"() {
        file("script.gradle") << """
            afterEvaluate {
                System.getProperty("PROP")
                gradle.buildFinished { }
            }
        """
        buildFile << """
            apply from: 'script.gradle'
            task ok
        """

        when:
        instantFails("ok", "-DPROP=12")

        then:
        outputContains("Configuration cache entry stored with 2 problems.")
        problems.assertFailureHasProblems(failure) {
            withProblem("script 'script.gradle': read system property 'PROP'")
            withProblem("script 'script.gradle': registration of listener on 'Gradle.buildFinished' is unsupported")
        }
    }

    def "reports problems in project build scripts"() {
        settingsFile << """
            include 'a'
        """
        file("a/build.gradle") << """
            gradle.buildFinished { }
            task broken {
                doFirst {
                    println project.name
                }
            }
        """

        when:
        instantFails("broken")

        then:
        outputContains("Configuration cache entry stored with 2 problems.")
        problems.assertFailureHasProblems(failure) {
            withProblem("build file '${relativePath('a/build.gradle')}': registration of listener on 'Gradle.buildFinished' is unsupported")
            withProblem("build file '${relativePath('a/build.gradle')}': invocation of 'Task.project' at execution time is unsupported.")
        }
    }

    def "reports problems in project Kotlin build scripts"() {
        settingsFile << """
            include 'a'
        """
        file("a/build.gradle.kts") << """
            gradle.buildFinished { }
            tasks.register("broken") {
                doFirst {
                    println(project.name)
                }
            }
        """

        when:
        instantFails("broken")

        then:
        outputContains("Configuration cache entry stored with 2 problems.")
        problems.assertFailureHasProblems(failure) {
            withProblem("build file '${relativePath('a/build.gradle.kts')}': registration of listener on 'Gradle.buildFinished' is unsupported")
            withProblem("task `:a:broken` of type `org.gradle.api.DefaultTask`: invocation of 'Task.project' at execution time is unsupported.")
        }
    }

    def "reports problems in buildSrc plugin"() {
        file("buildSrc/src/main/java/SneakyPlugin.java") << """
            import ${Project.name};
            import ${Plugin.name};

            public class SneakyPlugin implements Plugin<Project> {
                public void apply(Project project) {
                    project.getGradle().buildFinished(r -> {});
                    project.getTasks().register("broken", t -> {
                        t.doLast(t2 -> {
                            System.out.println(t2.getProject().getName());
                        });
                    });
                }
            }
        """
        file("buildSrc/src/main/resources/META-INF/gradle-plugins/sneaky.properties") << "implementation-class: SneakyPlugin"
        buildFile << """
            plugins { id("sneaky") }
        """

        when:
        instantFails("broken")

        then:
        outputContains("Configuration cache entry stored with 2 problems.")
        problems.assertFailureHasProblems(failure) {
            withProblem("plugin 'sneaky': registration of listener on 'Gradle.buildFinished' is unsupported")
            withProblem("task `:broken` of type `org.gradle.api.DefaultTask`: invocation of 'Task.project' at execution time is unsupported.")
        }
    }

    def "stops reporting problems at certain limits"() {
        buildFile << """
            task all
        """
        for (i in 1..530) {
            buildFile << """
                task broken$i {
                    doLast({ println ("project = " + project) } as Action)
                }
                tasks.all.dependsOn("broken$i")
            """
        }

        when:
        instantFails("all")

        then:
        outputContains("Configuration cache entry stored with 530 problems.")
        problems.assertFailureHasProblems(failure) {
            withProblem("task `:broken100` of type `org.gradle.api.DefaultTask`: invocation of 'Task.project' at execution time is unsupported.")
            withProblem("task `:broken101` of type `org.gradle.api.DefaultTask`: invocation of 'Task.project' at execution time is unsupported.")
            withProblem("task `:broken102` of type `org.gradle.api.DefaultTask`: invocation of 'Task.project' at execution time is unsupported.")
            withProblem("task `:broken103` of type `org.gradle.api.DefaultTask`: invocation of 'Task.project' at execution time is unsupported.")
            withProblem("task `:broken104` of type `org.gradle.api.DefaultTask`: invocation of 'Task.project' at execution time is unsupported.")
            withProblem("task `:broken105` of type `org.gradle.api.DefaultTask`: invocation of 'Task.project' at execution time is unsupported.")
            withProblem("task `:broken106` of type `org.gradle.api.DefaultTask`: invocation of 'Task.project' at execution time is unsupported.")
            withProblem("task `:broken107` of type `org.gradle.api.DefaultTask`: invocation of 'Task.project' at execution time is unsupported.")
            withProblem("task `:broken108` of type `org.gradle.api.DefaultTask`: invocation of 'Task.project' at execution time is unsupported.")
            withProblem("task `:broken109` of type `org.gradle.api.DefaultTask`: invocation of 'Task.project' at execution time is unsupported.")
            withProblem("task `:broken10` of type `org.gradle.api.DefaultTask`: invocation of 'Task.project' at execution time is unsupported.")
            withProblem("task `:broken110` of type `org.gradle.api.DefaultTask`: invocation of 'Task.project' at execution time is unsupported.")
            withProblem("task `:broken111` of type `org.gradle.api.DefaultTask`: invocation of 'Task.project' at execution time is unsupported.")
            withProblem("task `:broken112` of type `org.gradle.api.DefaultTask`: invocation of 'Task.project' at execution time is unsupported.")
            withProblem("task `:broken113` of type `org.gradle.api.DefaultTask`: invocation of 'Task.project' at execution time is unsupported.")
            totalProblemsCount = 530
        }
        failure.assertHasFailure("Configuration cache problems found in this build.") { failure ->
            failure.assertHasCauses(5)
        }
    }

}
