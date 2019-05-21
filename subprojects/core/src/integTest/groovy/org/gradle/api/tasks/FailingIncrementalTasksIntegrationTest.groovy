/*
 * Copyright 2013 the original author or authors.
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
import spock.lang.Unroll

@Unroll
class FailingIncrementalTasksIntegrationTest extends AbstractIntegrationSpec {

    def "consecutively failing task has correct up-to-date status and failure"() {
        buildFile << """
            task foo {
                outputs.file("out.txt")
                doLast {
                    if (project.file("out.txt").exists()) {
                        throw new RuntimeException("Boo!")
                    }
                    project.file("out.txt") << "xxx"
                }
            }
        """

        expect:
        succeeds "foo"

        when:
        file("out.txt") << "force rerun"
        fails "foo"
        then:
        failureHasCause "Boo!"

        when:
        fails "foo", "--info"
        then:
        failureHasCause "Boo!"
        output.contains "Task has failed previously."
        //this exposes an issue we used to have with in-memory cache.
    }

    def "incremental task after previous failure #description"() {
        file("src/input.txt") << "input"
        buildFile << """
            class IncrementalTask extends DefaultTask {
                @InputDirectory File sourceDir
                @OutputDirectory File destinationDir
                
                @TaskAction
                void process(IncrementalTaskInputs inputs) {
                    project.file("\$destinationDir/output.txt").text = "output"
                    if (project.hasProperty("modifyOutputs")) {
                        switch (project.property("modifyOutputs")) {
                            case "add":
                                project.file("\$destinationDir/output-\${System.currentTimeMillis()}.txt").text = "output"
                                break
                            case "change":
                                project.file("\$destinationDir/output.txt").text = "changed output -- \${System.currentTimeMillis()}"
                                break
                            case "remove":
                                project.delete("\$destinationDir/output.txt")
                                break
                        }
                    }

                    if (project.hasProperty("expectIncremental")) {
                        def expectIncremental = Boolean.parseBoolean(project.property("expectIncremental"))
                        assert inputs.incremental == expectIncremental
                    }

                    if (project.hasProperty("fail")) {
                        throw new RuntimeException("Failure")
                    }
                }
            }

            task incrementalTask(type: IncrementalTask) {
                sourceDir = file("src")
                destinationDir = file("build")
            }
        """

        succeeds "incrementalTask", "-PexpectIncremental=false"

        file("src/input-change.txt") << "input"
        fails "incrementalTask", "-PexpectIncremental=true", "-PmodifyOutputs=$modifyOutputs", "-Pfail"

        expect:
        succeeds "incrementalTask", "-PexpectIncremental=$incremental"

        where:
        modifyOutputs | incremental | description
        "add"         | false       | "with additional outputs is fully rebuilt"
        "change"      | false       | "with changed outputs is fully rebuilt"
        "remove"      | false       | "with removed outputs is fully rebuilt"
        "none"        | true        | "with unmodified outputs is executed as incremental"
    }

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
                def inputFiles = project.files(${asFileList(inputs)}, '${normalizePath(renamedInput)}')
            
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
        inputs.collect { "'${normalizePath(it)}'" }.join(", ")
    }

    private static String normalizePath(TestFile it) {
        TextUtil.escapeString(it.absolutePath)
    }
}
