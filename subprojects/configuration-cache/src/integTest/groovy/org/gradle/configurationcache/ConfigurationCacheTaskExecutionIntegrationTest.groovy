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

package org.gradle.configurationcache

import spock.lang.Issue

class ConfigurationCacheTaskExecutionIntegrationTest extends AbstractConfigurationCacheIntegrationTest {

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
            withProblem("Build file 'build.gradle': line 11: invocation of 'Task.project' at execution time is unsupported.")
            withProblem("Build file 'build.gradle': line 4: execution of task ':offender' caused invocation of 'Task.project' in other task at execution time which is unsupported.")
        }
    }

    @Issue('https://github.com/gradle/gradle/issues/22522')
    def "reports problem for extra property accessed at execution time"() {
        given:
        buildKotlinFile '''
            tasks.register("report") {
                extra["outputFile"] = file("$buildDir/output.txt")
                outputs.files(extra["outputFile"])
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
            withProblem "Task `:report` of type `org.gradle.api.DefaultTask`: invocation of 'Task.extensions' at execution time is unsupported."
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
}
