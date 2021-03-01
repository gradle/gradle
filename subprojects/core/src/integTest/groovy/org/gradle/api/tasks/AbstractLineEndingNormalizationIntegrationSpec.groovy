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

package org.gradle.api.tasks

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Unroll

abstract class AbstractLineEndingNormalizationIntegrationSpec extends AbstractIntegrationSpec {
    abstract String getStatusForReusedOutput()

    abstract void execute(String... tasks)

    abstract void cleanWorkspace()

    @Unroll
    def "tasks are not sensitive to line endings in known source files by default (#fileType files, #sensitivity.name() path sensitivity)"() {
        createTaskWithNormalization(sensitivity)

        buildFile << """
            taskWithInputs {
                sources.from(project.files("foo"))
                outputFile = project.file("\${buildDir}/output.txt")
            }
        """
        file("foo/Changing.${fileType}") << "\nhere's a line\nhere's another line\n\n"
        file('foo/Changing.other') << "\nhere's a line\nhere's another line\n\n"

        when:
        execute("taskWithInputs")

        then:
        executedAndNotSkipped(":taskWithInputs")

        when:
        file("foo/Changing.${fileType}").text = file("foo/Changing.${fileType}").text.replaceAll('\\n', '\r\n')
        cleanWorkspace()
        execute("taskWithInputs")

        then:
        reused(":taskWithInputs")

        when:
        file('foo/Changing.other').text = file('foo/Changing.other').text.replaceAll('\\n', '\r\n')
        cleanWorkspace()
        execute("taskWithInputs")

        then:
        executedAndNotSkipped(":taskWithInputs")

        where:
        [fileType, sensitivity] << [['java', 'groovy', 'kotlin'], PathSensitivity.values()].combinations()
    }

    def reused(String taskPath) {
        assert result.groupedOutput.task(taskPath).outcome == statusForReusedOutput
        return true
    }

    def createTaskWithNormalization(PathSensitivity pathSensitivity) {
        buildFile << """
            task taskWithInputs(type: TaskWithInputs)

            @CacheableTask
            class TaskWithInputs extends DefaultTask {
                @InputFiles
                @PathSensitive(PathSensitivity.${pathSensitivity.name()})
                FileCollection sources

                @OutputFile
                File outputFile

                public TaskWithInputs() {
                    sources = project.files()
                }

                @TaskAction
                void doSomething() {
                    outputFile.withWriter { writer ->
                        sources.each { writer.println it }
                    }
                }
            }
        """
    }
}
