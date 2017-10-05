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


package org.gradle.integtests

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Issue
import spock.lang.Unroll

class TaskUpToDateIntegrationTest extends AbstractIntegrationSpec {
    @Unroll
    @Issue("https://issues.gradle.org/browse/GRADLE-3540")
    def "order of #annotation doesn't mark task out-of-date"() {
        buildFile << """
            class MyTask extends DefaultTask {
                @Output${files ? "Files" : "Directories"} FileCollection out

                @TaskAction def exec() {
                    out.each { it${files ? ".text = 'data'" : ".mkdirs()"} }
                }
            }

            task myTask(type: MyTask) {
                if (project.hasProperty("reverse")) {
                    out = files("out2", "out1")
                } else {
                    out = files("out1", "out2")
                }
            }
        """

        run "myTask"
        skippedTasks.empty

        when:
        run "myTask"
        then:
        skippedTasks.contains ":myTask"

        when:
        run "myTask", "-Preverse"
        then:
        skippedTasks.contains ":myTask"

        where:
        annotation           | files
        '@OutputFiles'       | true
        '@OutputDirectories' | false
    }


    @Issue("https://github.com/gradle/gradle/issues/3073")
    def "optional output changed from null to non-null marks task not up-to-date"() {
        buildFile << """
            class CustomTask extends DefaultTask {
                @OutputFile
                @Optional
                File outputFile
            
                @TaskAction
                void doAction() {
                    if (outputFile != null) {
                        outputFile.write "Task is running"
                    }
                }
            }
            
            task customTask(type: CustomTask) {
                outputFile = project.hasProperty('outputFile') ? file(project.property("outputFile")) : null
            }
        """

        when:
        succeeds "customTask"
        then:
        executedAndNotSkipped ":customTask"

        when:
        succeeds "customTask", "-PoutputFile=log.txt"
        then:
        executedAndNotSkipped ":customTask"
    }

    @Unroll
    @Issue("https://github.com/gradle/gradle/issues/3073")
    def "output files changed from #before to #after marks task up-to-date"() {
        buildFile << """
            class CustomTask extends DefaultTask {
                @OutputFiles
                List<Object> outputFiles
            
                @TaskAction
                void doAction() {
                    outputFiles.each { output ->
                         def outputFile = output.call()
                         if (outputFile != null) {
                            outputFile.text = "task executed"
                         }
                    }
                }
            }
            
            def lazyProperty(String name) {
                def value = project.findProperty(name)
                def outputFile = value ? file(value) : null
                return { -> outputFile }
            }
            
            task customTask(type: CustomTask) {
                outputFiles = [lazyProperty('output0'), lazyProperty('output1')]
            }
        """

        when:
        succeeds customTaskWithOutputs(before)
        then:
        executedAndNotSkipped ":customTask"

        when:
        succeeds customTaskWithOutputs(after)
        then:
        skipped ":customTask"

        where:
        before                | after
        [null, 'outputFile']  | ['outputFile', null]
        ['outputFile1', null] | ['outputFile1', 'outputFile2']
    }


    private static String[] customTaskWithOutputs(List<String> outputs) {
        (["customTask"] + outputs.withIndex().collect { value, idx -> "-Poutput${idx}" + (value ? "=${value}" : '') }) as String[]
    }

    @Issue("https://github.com/gradle/gradle/issues/3073")
    def "optional input changed from null to non-null marks task not up-to-date"() {
        file("input.txt") << "Input data"
        buildFile << """
            class CustomTask extends DefaultTask {
                @Optional
                @InputFile
                File inputFile
                
                @OutputFile
                File outputFile
            
                @TaskAction
                void doAction() {
                    if (inputFile != null) {
                        outputFile.text = inputFile.text
                    }
                }
            }
            
            task customTask(type: CustomTask) {
                inputFile = project.hasProperty('inputFile') ? file(project.property("inputFile")) : null
                outputFile = file("output.txt")
            }
        """

        when:
        succeeds "customTask"
        then:
        executedAndNotSkipped ":customTask"

        when:
        succeeds "customTask", "-PinputFile=input.txt"
        then:
        executedAndNotSkipped ":customTask"
    }
}
