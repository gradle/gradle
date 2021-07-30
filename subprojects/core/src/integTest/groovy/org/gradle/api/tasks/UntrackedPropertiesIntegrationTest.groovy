/*
 * Copyright 2021 the original author or authors.
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

class UntrackedPropertiesIntegrationTest extends AbstractIntegrationSpec {

    def "can annotate inputs and outputs with Untracked"() {
        buildFile("""
            abstract class MyTask extends DefaultTask {
                @Untracked
                @InputFile
                abstract RegularFileProperty getInputFile()
                @Untracked
                @OutputFile
                abstract RegularFileProperty getOutputFile()

                @TaskAction
                void doStuff() {
                    outputFile.get().asFile.text = inputFile.get().asFile.text
                }
            }

            tasks.register("myTask", MyTask) {
                inputFile = file("input.txt")
                outputFile = project.layout.buildDirectory.file("output.txt")
            }
        """)
        file("input.txt").text = "input"

        when:
        run("myTask")
        then:
        executedAndNotSkipped(":myTask")
    }
}
