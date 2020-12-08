/*
 * Copyright 2017 the original author or authors.
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
import org.gradle.integtests.fixtures.DirectoryBuildCacheFixture

class BuildResultLoggerIntegrationTest extends AbstractIntegrationSpec implements DirectoryBuildCacheFixture {
    def setup() {

        file("input.txt") << "data"
        buildFile << """
            task adHocTask {
                outputs.cacheIf { true }
                def outputFile = file("\$buildDir/output.txt")
                inputs.file(file("input.txt"))
                outputs.file(outputFile)
                doLast {
                    outputFile.parentFile.mkdirs()
                    outputFile.text = file("input.txt").text
                }
            }

            task executedTask {
                doLast {
                    println "Hello world"
                }
            }

            task noActions(dependsOn: executedTask) {}
        """
    }

    def "task outcome statistics are reported"() {
        when:
        run "adHocTask", "executedTask"

        then:
        output.contains "2 actionable tasks: 2 executed"

        when:
        run "adHocTask", "executedTask"

        then:
        output.contains "2 actionable tasks: 1 executed, 1 up-to-date"
    }

    def "cached task outcome statistics are reported"() {
        when:
        withBuildCache().run "adHocTask", "executedTask"

        then:
        output.contains "2 actionable tasks: 2 executed"

        when:
        file("build").deleteDir()
        withBuildCache().run "adHocTask", "executedTask"

        then:
        output.contains "2 actionable tasks: 1 executed, 1 from cache"
    }

    def "tasks with no actions are not counted in stats"() {
        when:
        run "noActions"

        then:
        output.contains "1 actionable task: 1 executed"
    }

    def "skipped tasks are not counted"() {
        given:
        executer.withArguments "-x", "executedTask"

        when:
        run "noActions"

        then:
        // No stats are reported because no tasks had any actions
        !output.contains("actionable tasks")
    }

    def "work validation warnings are mentioned in summary"() {
        buildFile << """
            class InvalidTask extends DefaultTask {
                @Optional @Input File inputFile

                @TaskAction void execute() {
                    // Do nothing
                }
            }

            tasks.register("invalid", InvalidTask)
        """

        executer.expectDocumentedDeprecationWarning("Property 'inputFile' has @Input annotation used on property of type 'File'. " +
            "This behaviour has been deprecated and is scheduled to be removed in Gradle 7.0. " +
            "Execution optimizations are disabled due to the failed validation. " +
            "See https://docs.gradle.org/current/userguide/more_about_tasks.html#sec:up_to_date_checks for more details.")

        when:
        run "invalid"

        then:
        outputContains "Execution optimizations have been disabled for 1 invalid unit(s) of work during the build. Consult deprecation warnings for more information."
    }
}
