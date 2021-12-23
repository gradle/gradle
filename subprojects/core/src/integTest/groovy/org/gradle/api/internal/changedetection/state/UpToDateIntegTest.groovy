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

package org.gradle.api.internal.changedetection.state

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import spock.lang.Issue

import java.nio.file.Files

class UpToDateIntegTest extends AbstractIntegrationSpec {

    def "empty output directories created automatically are part of up-to-date checking"() {
        given:
        buildFile '''
apply plugin: 'base'

task checkCreated {
    dependsOn "createEmpty"
    def createdDir = file('build/createdDirectory')
    doLast {
        assert createdDir.exists()
        println "Directory 'build/createdDirectory' exists"
    }
}

task("createEmpty", type: CreateEmptyDirectory)

public abstract class CreateEmptyDirectory extends DefaultTask {

    @Inject
    abstract ProjectLayout getLayout()

    @TaskAction
    public void createDir() {
        println "did nothing: output dir is created automatically"
    }

    @OutputDirectory
    public File getDirectory() {
        return layout.buildDirectory.file('createdDirectory').get().asFile
    }
}
'''

        expect:
        succeeds("checkCreated")
        succeeds("checkCreated")
        result.assertTaskSkipped(":createEmpty")

        succeeds("clean", "checkCreated")
        result.assertTaskNotSkipped(":createEmpty")

        succeeds("checkCreated")
        result.assertTaskSkipped(":createEmpty")
    }

    @Issue("https://github.com/gradle/gradle/issues/13554")
    def "removing an empty output directory is detected even when it existed before the first task execution"() {
        buildFile """
            task createEmptyDir {
                outputs.dir("empty")
                doLast {
                    // do nothing, since Gradle does create the empty directory for us.
                }
            }
        """
        def emptyDir = file('empty').createDir()
        def emptyDirTask = ':createEmptyDir'

        when:
        run emptyDirTask
        then:
        executedAndNotSkipped emptyDirTask
        emptyDir.directory

        when:
        run emptyDirTask
        then:
        skipped emptyDirTask
        emptyDir.directory

        when:
        emptyDir.deleteDir()
        run emptyDirTask
        then:
        executedAndNotSkipped emptyDirTask
        emptyDir.directory
    }

    @Issue("https://issues.gradle.org/browse/GRADLE-834")
    def "task without actions is reported as up-to-date when it's up-to-date"() {
        file("src/main/java/Main.java") << "public class Main {}"
        buildFile << """
            apply plugin: "java"
        """

        expect:
        succeeds "jar"

        when:
        succeeds "jar"

        then:
        result.assertTaskSkipped(":classes")
        outputContains ":classes UP-TO-DATE"
    }

    def "reasons for task being not up-to-date are reported"() {
        buildFile << '''
            task customTask(type: CustomTask) {
                outputFile = file("$buildDir/outputFile")
                content = providers.gradleProperty('content').getOrElse(null)
            }

            class CustomTask extends DefaultTask {
                @OutputFile
                File outputFile

                @Input
                String content

                @TaskAction
                public void doStuff() {
                    outputFile.text = content
                }
            }
        '''

        def customTask = ':customTask'
        def notUpToDateBecause = /Task '${customTask}' is not up-to-date because:/
        when:
        run customTask, '-Pcontent=first', '--info'
        then:
        executedAndNotSkipped customTask
        result.output =~ notUpToDateBecause
        outputContains('No history is available')

        when:
        run customTask, '-Pcontent=second', '--info'
        then:
        executedAndNotSkipped(customTask)
        result.output =~ notUpToDateBecause
        outputContains("Value of input property 'content' has changed for task '${customTask}'")

        when:
        run customTask, '-Pcontent=second', '--info'
        then:
        skipped customTask
        result.output =~ /Skipping task '${customTask}' as it is up-to-date\./
    }

    def "registering an optional #type output property with a null value keeps task up-to-date"() {
        buildFile << """
            task customTask(type: CustomTask) {
                outputFile = file("\$buildDir/outputFile")
                if (project.hasProperty("addNullOutput")) {
                    outputs.$type(null).optional()
                }
            }

            class CustomTask extends DefaultTask {
                @OutputFile
                File outputFile

                @TaskAction
                public void doStuff() {
                    outputFile.text = "output"
                }
            }
        """

        run "customTask"
        when:
        run "customTask", "-PaddNullOutput"
        then:
        skipped ":customTask"

        where:
        type << ["dir", "file"]
    }

    @ToBeFixedForConfigurationCache(because = "The cache fix plugin hackery doesn't work with configuration caching")
    @Issue("https://github.com/gradle/gradle/issues/15397")
    def "can add a file input in a task execution listener"() {
        buildFile << """
            abstract class TaskMissingPathSensitivity extends DefaultTask {
                @InputFiles
                FileCollection inputFiles

                @OutputFile
                abstract RegularFileProperty getOutputFile()

                @TaskAction
                void doWork() {
                    outputFile.get().asFile.text = "output"
                }
            }

            tasks.register("customTask", TaskMissingPathSensitivity) {
                inputFiles = files("input1", "input2")
                outputFile = layout.buildDirectory.file("output.txt")
            }

            // This is what the Android cache fix plugin is doing:
            tasks.withType(TaskMissingPathSensitivity).configureEach { TaskMissingPathSensitivity task ->
                ConfigurableFileCollection newInputs = files()
                FileCollection originalPropertyValue
                task.inputs.files(newInputs)
                    .withPathSensitivity(PathSensitivity.RELATIVE)
                    .withPropertyName("inputFiles.workaround")
                    .optional()
                // Create a synthetic input with the original property value and RELATIVE path sensitivity
                project.gradle.taskGraph.beforeTask {
                    if (it == task) {
                        originalPropertyValue = task.inputFiles
                        task.inputFiles = project.files()
                        newInputs.from(originalPropertyValue)
                    }
                }
                // Set the task property back to its original value
                task.doFirst {
                    task.inputFiles = originalPropertyValue
                }
            }
        """
        def inputDir1 = file("input1").createDir()
        def inputDir2 = file("input2").createDir()
        def inputFileName = "inputFile.txt"
        def inputFile = inputDir1.file(inputFileName)
        inputFile.text = "input"

        when:
        run "customTask"
        then:
        executedAndNotSkipped(":customTask")

        when:
        Files.move(inputFile.toPath(), inputDir2.file(inputFileName).toPath())
        run "customTask"
        then:
        skipped(":customTask")

        when:
        inputDir2.file(inputFileName).text = "changed"
        run "customTask"
        then:
        executedAndNotSkipped(":customTask")
    }

    @Issue("https://github.com/gradle/gradle/issues/19353")
    def "file changes are recognized when file length does not change with #inputAnnotation"() {
        buildFile << """
            abstract class FilePrinter extends DefaultTask {
                $inputAnnotation
                abstract $inputType getInput()

                @OutputFile
                abstract RegularFileProperty getOutputFile()

                @TaskAction
                void doWork() {
                    println $inputToPrint
                }
            }
            tasks.register("printFile", FilePrinter) {
                input = file("$inputPath")
                outputFile = layout.buildDirectory.file("output/output.txt")
            }
        """
        def inputFile = file("input/input.txt")
        inputFile.text = "[0] Hello World!"
        def originalInputFileLength = inputFile.length()

        expect:
        (1..4).each {
            def givenText = "[$it] Hello World!"
            inputFile.text = givenText
            run "printFile"
            executedAndNotSkipped(":printFile")
            outputContains(givenText)
            assert originalInputFileLength == inputFile.length()
        }

        where:
        inputAnnotation   | inputType             | inputPath         | inputToPrint
        "@InputDirectory" | "DirectoryProperty"   | "input"           | "getInput().file('input.txt').get().asFile.text"
        "@InputFile"      | "RegularFileProperty" | "input/input.txt" | "getInput().get().asFile.text"
    }
}
