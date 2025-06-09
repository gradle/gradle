/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.internal.cc.impl

import org.gradle.integtests.fixtures.configurationcache.ConfigurationCacheFixture
import spock.lang.Issue

class ConfigurationCacheTaskExecutionIntegrationTest extends AbstractConfigurationCacheIntegrationTest {

    def configurationCache = new ConfigurationCacheFixture(this)

    def "task fails and configuration is not cached due to #invocation access in annotated task action"() {
        buildFile """
            abstract class MyTask extends DefaultTask {
                @TaskAction
                def action() {
                    $code
                    throw new IllegalStateException("UNREACHABLE")
                }
            }

            tasks.register("broken", MyTask)
        """

        when:
        configurationCacheFails "broken"

        then:
        failureDescriptionStartsWith("Execution failed for task ':broken'.")
        failureCauseContains("Invocation of '$invocation' by task ':broken' at execution time is unsupported with the configuration cache.")
        outputDoesNotContain("UNREACHABLE")

        and:
        configurationCache.assertStateStoredAndDiscarded {
            hasStoreFailure = false
            reportedOutsideBuildFailure = true
            problem "Build file 'build.gradle': line 5: invocation of '$invocation' at execution time is unsupported with the configuration cache."
        }

        where:
        invocation              | code
        'Task.project'          | 'project.name'
        'Task.dependsOn'        | 'dependsOn'
        'Task.taskDependencies' | 'taskDependencies'
        'Task.shouldRunAfter'   | 'shouldRunAfter'
        'Task.mustRunAfter'     | 'mustRunAfter'
        'Task.finalizedBy'      | 'finalizedBy'
    }

    def "task fails and configuration is not cached due to Task.#invocation access in #method #desc action"() {
        buildFile """
            class MyAction implements Action<Task> {
                void execute(Task task) {
                    task.$invocation
                    throw new IllegalStateException("UNREACHABLE")
                }
            }

            tasks.register("broken") {
                $method$code
            }
        """

        when:
        configurationCacheFails "broken"

        then:
        failureDescriptionStartsWith("Execution failed for task ':broken'.")
        failureCauseContains("Invocation of 'Task.$invocation' by task ':broken' at execution time is unsupported with the configuration cache.")
        outputDoesNotContain("UNREACHABLE")

        and:
        configurationCache.assertStateStoredAndDiscarded {
            hasStoreFailure = false
            reportedOutsideBuildFailure = true
            problem "Build file 'build.gradle': line 4: invocation of 'Task.$invocation' at execution time is unsupported with the configuration cache."
        }

        where:
        [invocation, method, [desc, code]] << [
            ['project', 'dependsOn', 'taskDependencies'],
            ['doFirst', 'doLast'],
            [
                ['custom', '(new MyAction())'],
                ['lambda', '{ new MyAction().execute(it) }'],
            ]
        ].combinations()
    }

    def "in warning mode, task does not fail and configuration can be reused despite execution-time problem"() {
        buildFile """
            version = "foo-version"

            tasks.register('broken') {
                doLast {
                    println("At execution: version='\${project.version}'")
                }
            }
        """

        when:
        configurationCacheRunLenient "broken"

        then:
        executed(":broken")
        outputContains("At execution: version='unspecified'")
        outputDoesNotContain("foo-version")

        and:
        configurationCache.assertStateStoredWithProblems {
            problem("Build file 'build.gradle': line 6: invocation of 'Task.project' at execution time is unsupported with the configuration cache.")
        }

        when: "running again"
        configurationCacheRunLenient "broken"

        then: "observing the same execution result"
        executed(":broken")
        outputContains("At execution: version='unspecified'")
        outputDoesNotContain("foo-version")

        and: "configuration cache is reused"
        configurationCache.assertStateLoadedWithProblems {
            problem("Build file 'build.gradle': line 6: invocation of 'Task.project' at execution time is unsupported with the configuration cache.")
        }
    }

    def "with execution-time problems, serialization problems are reported as a separate build failure"() {
        buildFile """
            def capturedProject = project
            tasks.register("broken") {
                doLast {
                    println("use captured project: " + (capturedProject != null)) // serialization problem
                    println("use task.project: " + project) // execution-time problem
                    throw new RuntimeException("UNREACHABLE")
                }
            }
        """

        when:
        configurationCacheFails "broken"

        then:
        outputContains("use captured project: true")
        failure.assertHasFailures(2)

        and:
        failure.assertHasFileName("Build file '${buildFile.absolutePath}'")
        failure.assertHasLineNumber(6)
        failure.assertHasDescription("Execution failed for task ':broken'.")
        failure.assertHasCause("Invocation of 'Task.project' by task ':broken' at execution time is unsupported with the configuration cache.")

        and:
        // TODO:configuration-cache provide a better (less misleading) location for the end-of-build CC build failure
        // Currently, the end-of-build build failure will to the same location as the task failure,
        // if there are no deferred problems with location displayable by the build failure
        failure.assertHasFileName("Build file '${buildFile.absolutePath}'")
        failure.assertHasLineNumber(6)
        failure.assertHasDescription("Configuration cache problems found in this build.")

        configurationCache.assertStateStoredAndDiscarded {
            loadsAfterStore = false // because of store-time problems
            problem "Task `:broken` of type `org.gradle.api.DefaultTask`: cannot serialize object of type 'org.gradle.api.internal.project.DefaultProject', a subtype of 'org.gradle.api.Project', as these are not supported with the configuration cache."
            serializationProblem "Build file 'build.gradle': line 6: invocation of 'Task.project' at execution time is unsupported with the configuration cache."
        }
    }

    def "tasks that access project through #providerChain emit no problems"() {
        given:
        buildFile """
            tasks.register("reliesOnSerialization") { task ->
                def projectProvider = $providerChain
                doLast {
                    println projectProvider.get()
                }
            }
        """

        when:
        configurationCacheRun("reliesOnSerialization")

        then:
        result.assertTasksExecuted(":reliesOnSerialization")

        where:
        providerChain                               || _
        "provider { task.project.name }"            || _
        "provider { task.project }.map { it.name }" || _
    }

    def "fails tasks that access project through provider created at execution time"() {
        given:
        buildFile """
            tasks.register("broken") {
                def providerFactory = providers
                doLast { task ->
                    def projectProvider = providerFactory.provider { task.project.name }
                    println projectProvider.get()
                }
            }
        """

        when:
        configurationCacheFails "broken"

        then:
        failureDescriptionStartsWith("Execution failed for task ':broken'")
        failureCauseContains("Invocation of 'Task.project' by task ':broken' at execution time is unsupported with the configuration cache.")

        configurationCache.assertStateStoredAndDiscarded {
            hasStoreFailure = false
            reportedOutsideBuildFailure = true
            problem "Build file 'build.gradle': line 5: invocation of 'Task.project' at execution time is unsupported with the configuration cache."
        }
    }

    def "fails tasks that access project through indirect provider created at execution time"() {
        given:
        buildFile """
            tasks.register("broken") {
                def valueProvider = providers.provider { "value" }
                doLast { task ->
                    println valueProvider.map { it + task.project.name }.get()
                }
            }
        """

        when:
        configurationCacheFails "broken"

        then:
        failureDescriptionStartsWith("Execution failed for task ':broken'")
        failureCauseContains("Invocation of 'Task.project' by task ':broken' at execution time is unsupported with the configuration cache.")

        configurationCache.assertStateStoredAndDiscarded {
            hasStoreFailure = false
            reportedOutsideBuildFailure = true
            problem "Build file 'build.gradle': line 5: invocation of 'Task.project' at execution time is unsupported with the configuration cache."
        }
    }

    def "fails tasks that access project through mapped changing provider"() {
        given:
        buildFile """
            tasks.register("broken") { task ->
                def projectProvider = providers.systemProperty("some.property").map { it + task.project.name }
                doLast {
                    println projectProvider.get()
                }
            }
        """

        when:
        configurationCacheFails "broken", "-Dsome.property=value"

        then:
        failureDescriptionStartsWith("Execution failed for task ':broken'")
        failureCauseContains("Invocation of 'Task.project' by task ':broken' at execution time is unsupported with the configuration cache.")

        configurationCache.assertStateStoredAndDiscarded {
            hasStoreFailure = false
            reportedOutsideBuildFailure = true
            problem "Build file 'build.gradle': line 3: invocation of 'Task.project' at execution time is unsupported with the configuration cache."
        }
    }

    def "fails tasks that access project through mapped changing value source provider"() {
        given:
        buildFile """
            import org.gradle.api.provider.*

            abstract class ChangingSource implements ValueSource<String, ValueSourceParameters.None> {
                @Override
                 String obtain(){
                    return "some string"
                }
            }

            tasks.register("broken") { task ->
                def projectProvider = providers.of(ChangingSource) {}.map { it + task.project.name }
                doLast {
                    println projectProvider.get()
                }
            }
        """

        when:
        configurationCacheFails "broken"

        then:
        failureDescriptionStartsWith("Execution failed for task ':broken'")
        failureCauseContains("Invocation of 'Task.project' by task ':broken' at execution time is unsupported with the configuration cache.")

        configurationCache.assertStateStoredAndDiscarded {
            hasStoreFailure = false
            reportedOutsideBuildFailure = true
            problem "Build file 'build.gradle': line 12: invocation of 'Task.project' at execution time is unsupported with the configuration cache."
        }
    }

    @Issue("https://github.com/gradle/gradle/issues/24411")
    def "task marked not compatible may cause configuration of other tasks without failing build"() {
        given:
        buildFile("""
            tasks.register("innocent") { task ->
                // This task calls getProject() as part of its configuration.
                println("Materialized task \${task.name} from project \${task.project.name}")
            }

            tasks.register("offender") {
                notCompatibleWithConfigurationCache("calls getProject")
                // This task reaches out to other task at execution time and triggers its configuration.
                doLast { task ->
                    println(task.project.tasks.named("innocent").get())
                }
            }
        """)

        when:
        configurationCacheRun("offender")

        then:
        problems.assertResultHasProblems(result) {
            withProblemsWithStackTraceCount(2)
            withProblem("Build file 'build.gradle': line 11: invocation of 'Task.project' at execution time is unsupported with the configuration cache.")
            withProblem("Build file 'build.gradle': line 4: execution of task ':offender' caused invocation of 'Task.project' in other task at execution time which is unsupported with the configuration cache.")
        }
    }

    @Issue('https://github.com/gradle/gradle/issues/22522')
    def "reports problem for extra property accessed at execution time"() {
        given:
        buildKotlinFile '''
            tasks.register("report") {
                extra["outputFile"] = file("$buildDir/output.txt")
                outputs.files(extra["outputFile"] as File)
                doLast {
                    val outputFile: File by extra
                    outputFile.writeText("")
                }
            }
        '''

        when:
        configurationCacheFails(WARN_PROBLEMS_CLI_OPT, 'report')

        then:
        problems.assertResultHasProblems(failure) {
            withProblem "Task `:report` of type `org.gradle.api.DefaultTask`: invocation of 'Task.extensions' at execution time is unsupported with the configuration cache."
        }
    }

    def "honors task up-to-date spec"() {
        buildFile << """
            abstract class TaskWithComplexInputs extends DefaultTask {
                @OutputFile
                abstract RegularFileProperty getOutputFile()

                TaskWithComplexInputs() {
                    def result = name == "never"
                    outputs.upToDateWhen { !result }
                }

                @TaskAction
                def go() {
                    outputFile.get().asFile.text = "some-derived-value"
                }
            }

            task never(type: TaskWithComplexInputs) {
                outputFile = layout.buildDirectory.file("never.txt")
            }
            task always(type: TaskWithComplexInputs) {
                outputFile = layout.buildDirectory.file("always.txt")
            }
        """

        when:
        configurationCacheRun("never", "always")
        configurationCacheRun("never", "always")

        then:
        result.assertTaskSkipped(":always")
        result.assertTasksNotSkipped(":never")
    }

    def "shouldRunAfter doesn't imply dependency"() {
        given:
        buildFile << '''
            task a
            task b { shouldRunAfter a }
        '''

        when:
        configurationCacheRun 'b'

        then:
        result.assertTasksExecuted ':b'

        when:
        configurationCacheRun 'b'

        then:
        result.assertTasksExecuted ':b'
    }

    def "mustRunAfter doesn't imply dependency"() {
        given:
        buildFile << '''
            task a
            task b { mustRunAfter a }
        '''

        when:
        configurationCacheRun 'b'

        then:
        result.assertTasksExecuted ':b'

        when:
        configurationCacheRun 'b'

        then:
        result.assertTasksExecuted ':b'
    }

    def "finalizedBy implies dependency"() {
        given:
        buildFile << '''
            task a
            task b { finalizedBy a }
        '''

        when:
        configurationCacheRun 'b'

        then:
        result.assertTasksExecuted ':b', ':a'

        when:
        configurationCacheRun 'b'

        then:
        result.assertTasksExecuted ':b', ':a'
    }

    def "clean task is scheduled correctly in the presence of finalizers with dependencies"() {
        given:
        buildFile '''
            plugins {
                id 'java'
            }

            abstract class TaskReader extends DefaultTask {
                @InputFile abstract RegularFileProperty getFile()
                @TaskAction def action() {
                    assert(file.get().asFile.text == "foo")
                }
            }

            abstract class TaskWriter extends DefaultTask {
                @OutputFile abstract RegularFileProperty getFile()
                @TaskAction def action() {
                    file.get().asFile.text = "foo"
                }
            }

            tasks.register("a", DefaultTask) {
                finalizedBy("b")
            }

            def taskWriter = tasks.register("c", TaskWriter) {
                file = project.layout.buildDirectory.file("output.txt")
            }

            tasks.register("b", TaskReader) {
                file = taskWriter.get().file
            }
        '''

        expect:
        2.times {
            configurationCacheRun 'clean', 'a'
        }
    }
}
