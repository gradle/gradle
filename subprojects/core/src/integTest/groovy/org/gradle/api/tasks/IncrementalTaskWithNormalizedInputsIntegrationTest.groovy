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

package org.gradle.api.tasks

import org.gradle.api.tasks.incremental.IncrementalTaskInputs
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.util.internal.TextUtil
import org.gradle.work.InputChanges
import spock.lang.Issue
import spock.lang.Unroll

@Unroll
class IncrementalTaskWithNormalizedInputsIntegrationTest extends AbstractIntegrationSpec {

    private static final String INCREMENTAL_TASK_NAME = "incrementalTask"

    @Issue("https://github.com/gradle/gradle/issues/9320")
    def "incremental task with NAME_ONLY input (matching file names and content) detects changed input (type: #taskChangeType.simpleName)"() {
        def inputs = folderNames().collect { file("${it}/input.txt").createFile() }
        def modifiedInput = inputs[1]

        buildFile << incrementalTaskWithNameOnlyInputFiles(taskChangeType, inputs)
        run INCREMENTAL_TASK_NAME, "--info"

        when:
        modifiedInput.text = "changed"
        println "Modified ${modifiedInput} to '${modifiedInput.text}'!"

        run INCREMENTAL_TASK_NAME, "--info"
        then:
        outputContains "${modifiedInput} has changed."

        when:
        modifiedInput.text = ""
        println "Modified ${modifiedInput} to '${modifiedInput.text}'!"
        run INCREMENTAL_TASK_NAME, "--info"
        then:
        outputContains "${modifiedInput} has changed."

        where:
        taskChangeType << [IncrementalTaskInputs, InputChanges]
    }

    @Issue("https://github.com/gradle/gradle/issues/9320")
    def "incremental task with NAME_ONLY input (matching file names and content) detects moved files (type: #taskChangeType.simpleName)"() {
        def inputs = folderNames().collect { file("${it}/input.txt").createFile() }
        def movableInput = inputs[1]
        def renamedInput = file("moved/${movableInput.name}")

        buildFile << incrementalTaskWithNameOnlyInputFiles(taskChangeType, inputs + renamedInput)

        run INCREMENTAL_TASK_NAME, "--info"

        when:
        println "Moving ${movableInput.absolutePath} to '${renamedInput.absolutePath}'!"
        renamedInput.text = movableInput.text
        movableInput.delete()

        run INCREMENTAL_TASK_NAME, "--info"
        then:
        !movableInput.exists()
        renamedInput.exists()
        outputContains "is up-to-date"

        where:
        taskChangeType << [IncrementalTaskInputs, InputChanges]
    }

    @Issue("https://github.com/gradle/gradle/issues/9320")
    def "incremental task with NAME_ONLY inputs (matching file names and content) detects deleted file (type: #taskChangeType.simpleName)"() {
        def inputs = folderNames().collect { file("${it}/input.txt").createFile() }

        buildFile << incrementalTaskWithNameOnlyInputFiles(taskChangeType, inputs)
        run INCREMENTAL_TASK_NAME

        when:
        inputs[1..2]*.delete()
        println "${inputs} exists: ${inputs*.exists()}"
        run INCREMENTAL_TASK_NAME, "--info"
        then:
        outputDoesNotContain "${inputs[0]} has been removed."
        outputContains "${inputs[1]} has been removed."
        outputContains "${inputs[2]} has been removed."

        where:
        taskChangeType << [IncrementalTaskInputs, InputChanges]
    }

    private static Range<String> folderNames() { 'a'..'c' }

    private static String incrementalTaskWithNameOnlyInputFiles(Class<?> incrementalChangesType, List<? extends File> inputs) {
        """
            abstract class IncrementalTask extends DefaultTask {
                @InputFiles
                @PathSensitive(PathSensitivity.NAME_ONLY)
                def inputFiles = project.files(${asFileList(inputs)})

                @Optional
                @OutputFile
                abstract RegularFileProperty getOutputFile()

                @TaskAction
                def action(${incrementalChangesType.name} changes) {}
            }

            task ${INCREMENTAL_TASK_NAME}(type: IncrementalTask) {}
        """
    }

    private static String asFileList(List<? extends File> inputs) {
        inputs.collect { "'${TextUtil.escapeString(it.absolutePath)}'" }.join(", ")
    }
}
