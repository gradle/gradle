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

import org.gradle.initialization.StartParameterBuildOptions
import org.gradle.testkit.runner.fixtures.NonCrossVersion
import org.gradle.util.internal.TextUtil

import static org.gradle.testkit.runner.TaskOutcome.FAILED
import static org.gradle.testkit.runner.TaskOutcome.FROM_CACHE
import static org.gradle.testkit.runner.TaskOutcome.SKIPPED
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS
import static org.gradle.testkit.runner.TaskOutcome.UP_TO_DATE

/**
 * Tests the behavior of a task with a FROM_CACHE result
 */
@NonCrossVersion
class GradleRunnerCacheIntegrationTest extends BaseGradleRunnerIntegrationTest {

    def "cacheable task marked as up-to-date or from-cache"() {
        given:
        buildFile << """
            apply plugin: 'base'

            @CacheableTask
            class CustomTask extends DefaultTask {
                @OutputFile
                File outputFile

                @InputFile
                @PathSensitive(PathSensitivity.NONE)
                File inputFile

                @TaskAction
                public void makeOutput() {
                    outputFile.text = "done"
                }
            }

            task cacheable(type: CustomTask) {
                inputFile = file("input")
                outputFile = new File(buildDir, "output")
            }
            """
        def cacheDir = file("task-output-cache")
        settingsFile << """
            buildCache {
                local {
                    directory = "${TextUtil.escapeString(cacheDir.absolutePath)}"
                }
            }
        """
        file("gradle.properties") << """
            ${StartParameterBuildOptions.BuildCacheOption.GRADLE_PROPERTY}=true
        """
        file("input").text = "input file"

        when:
        def result = runner('cacheable').build()

        then:
        result.tasks.collect { it.path } == [':cacheable']
        result.taskPaths(SUCCESS) == [':cacheable']
        result.taskPaths(SKIPPED).empty
        result.taskPaths(UP_TO_DATE).empty
        result.taskPaths(FAILED).empty
        result.taskPaths(FROM_CACHE).empty

        when:
        file("build").deleteDir()
        result = runner('cacheable').build()
        then:
        file("build/output").text == "done"
        result.tasks.collect { it.path } == [':cacheable']
        result.taskPaths(SUCCESS).empty
        result.taskPaths(SKIPPED).empty
        result.taskPaths(UP_TO_DATE).empty
        result.taskPaths(FAILED).empty
        result.taskPaths(FROM_CACHE) == [':cacheable']
    }
}
