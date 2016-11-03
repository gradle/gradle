/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.testkit.runner

import org.gradle.testkit.runner.fixtures.NonCrossVersion

import static org.gradle.testkit.runner.TaskOutcome.*
/**
 * Tests the behavior of a task with a FROM_CACHE result
 */
@NonCrossVersion
class GradleRunnerCacheIntegrationTest extends BaseGradleRunnerIntegrationTest {

    def "cacheable task marked as up-to-date or from-cache"() {
        given:
        buildFile << """
            apply plugin: 'base'

            task cacheable {
                def outputFile = new File(buildDir, "output")
                inputs.file("input")
                outputs.file(outputFile)
                outputs.cacheIf { true }

                doLast {
                    outputFile.parentFile.mkdirs()
                    outputFile.text = "done"
                }
            }
"""
        file("gradle.properties") << """
            org.gradle.cache.tasks=true
"""
        file("input").text = "input file"

        when:
        def result = runner('cacheable').forwardOutput().build()

        then:
        result.tasks.collect { it.path } == [':cacheable']
        result.taskPaths(SUCCESS) == [':cacheable']
        result.taskPaths(SKIPPED).empty
        result.taskPaths(UP_TO_DATE).empty
        result.taskPaths(FAILED).empty
        result.taskPaths(FROM_CACHE).empty

        when:
        file("build").deleteDir()
        and:
        result = runner('cacheable').forwardOutput().build()
        then:
        result.tasks.collect { it.path } == [':cacheable']
        result.taskPaths(SUCCESS).empty
        result.taskPaths(SKIPPED).empty
        result.taskPaths(UP_TO_DATE).empty
        result.taskPaths(FAILED).empty
        result.taskPaths(FROM_CACHE) == [':cacheable']
    }
}
