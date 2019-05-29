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
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.TextUtil
import org.gradle.work.InputChanges
import org.intellij.lang.annotations.Language
import spock.lang.Issue

class IncrementalTaskWithNormalizedInputsIntegrationTest extends AbstractIntegrationSpec {

    @Issue("https://github.com/gradle/gradle/issues/9320")
    def "incremental task with NAME_ONLY input (matching file names and content) detects changed input (type: #taskChangeType.simpleName)"() {
        def taskName = "incrementalTask"
        def inputs = folderNames().collect { file("${it}/input.txt").createFile() }
        def modifiedInput = inputs[1]

        @Language("Groovy") script = """
            class IncrementalTask extends DefaultTask {
                @InputFiles
                @PathSensitive(PathSensitivity.NAME_ONLY)
                def inputFiles = project.files(${asFileList(inputs)})
            
                @TaskAction
                def action(${taskChangeType.name} changes) {}
            }
            
            task ${taskName}(type: IncrementalTask) {}
        """
        buildScript script
        run taskName, "--info"

        when:
        modifiedInput.text = "changed"
        println "Modified ${modifiedInput} to '${modifiedInput.text}'!"

        run taskName, "--info"
        then:
        outputContains "${modifiedInput} has changed."

        when:
        modifiedInput.text = ""
        println "Modified ${modifiedInput} to '${modifiedInput.text}'!"
        run taskName, "--info"
        then:
        outputContains "${modifiedInput} has changed."

        where:
        taskChangeType << [IncrementalTaskInputs, InputChanges]
    }

    @Issue("https://github.com/gradle/gradle/issues/9320")
    def "incremental task with NAME_ONLY input (matching file names and content) detects moved files (type: #taskChangeType.simpleName)"() {
        def taskName = "incrementalTask"
        def inputs = folderNames().collect { file("${it}/input.txt").createFile() }
        def movableInput = inputs[1]
        def renamedInput = file("moved/${movableInput.name}")

        @Language("Groovy") script = """
            class IncrementalTask extends DefaultTask {
                @InputFiles
                @PathSensitive(PathSensitivity.NAME_ONLY)
                def inputFiles = project.files(${asFileList(inputs)}, '${TextUtil.escapeString(renamedInput.absolutePath)}')
            
                @TaskAction
                def action(${taskChangeType.name} changes) {}
            }
            
            task ${taskName}(type: IncrementalTask) {}
        """
        buildScript script
        run taskName, "--info"

        when:
        println "Moving ${movableInput.absolutePath} to '${renamedInput.absolutePath}'!"
        renamedInput.text = movableInput.text
        movableInput.delete()

        run taskName, "--info"
        then:
        !movableInput.exists()
        renamedInput.exists()
        outputContains "is up-to-date"

        where:
        taskChangeType << [IncrementalTaskInputs, InputChanges]
    }

    @Issue("https://github.com/gradle/gradle/issues/9320")
    def "incremental task with NAME_ONLY inputs (matching file names and content) detects deleted file (type: #taskChangeType.simpleName)"() {
        def taskName = "incrementalTask"
        def inputs = folderNames().collect { file("${it}/input.txt").createFile() }

        @Language("Groovy") script = """
            class IncrementalTask extends DefaultTask {
                @InputFiles
                @PathSensitive(PathSensitivity.NAME_ONLY)
                def inputFiles = project.files(${asFileList(inputs)})
            
                @TaskAction
                def action(${taskChangeType.name} changes) {}
            }
            
            task ${taskName}(type: IncrementalTask) {}
        """
        buildScript script
        run taskName

        when:
        inputs[1..2]*.delete()
        println "${inputs} exists: ${inputs*.exists()}"
        run taskName, "--info"
        then:
        outputDoesNotContain "${inputs[0]} has been removed."
        outputContains "${inputs[1]} has been removed."
        outputContains "${inputs[2]} has been removed."

        where:
        taskChangeType << [IncrementalTaskInputs, InputChanges]
    }

    private static Range<String> folderNames() { 'a'..'c' }

    private static String asFileList(List<TestFile> inputs) {
        inputs.collect { "'${TextUtil.escapeString(it.absolutePath)}'" }.join(", ")
    }
}
