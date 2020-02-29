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
import org.gradle.integtests.fixtures.ToBeFixedForInstantExecution

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

    @ToBeFixedForInstantExecution
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
}
