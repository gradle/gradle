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

import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFiles
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Issue
import spock.lang.Unroll

class TaskUpToDateIntegrationTest extends AbstractIntegrationSpec {
    @Unroll
    @Issue("https://issues.gradle.org/browse/GRADLE-3540")
    def "order of #annotation marks task not up-to-date"() {
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
        noneSkipped()

        when:
        run "myTask"
        then:
        skipped ":myTask"

        when:
        run "myTask", "-Preverse"
        then:
        executedAndNotSkipped ":myTask"

        where:
        annotation           | files
        '@OutputFiles'       | true
        '@OutputDirectories' | false
    }

    @Issue("https://issues.gradle.org/browse/GRADLE-3540")
    def "hash set output files marks task up-to-date"() {
        buildFile << """
            class MyTask extends DefaultTask {
                @OutputFiles Set<File> out = new HashSet<File>()

                @TaskAction def exec() {
                    out.each { it.text = 'data' }
                }
            }

            task myTask(type: MyTask) {
                out.addAll([file("out1"), file("out2")])
            }
        """

        when:
        run ':myTask'

        then:
        executedAndNotSkipped ':myTask'

        when:
        run ':myTask'

        then:
        skipped ':myTask'
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
    def "output files changed from #before to #after marks task #upToDateString"() {
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
                int numOutputs = Integer.valueOf(project.findProperty('numOutputs'))
                outputFiles = (0..(numOutputs-1)).collect { lazyProperty("output\$it") }
            }
        """

        when:
        succeeds customTaskWithOutputs(before)
        then:
        executedAndNotSkipped ":customTask"

        when:
        succeeds customTaskWithOutputs(after)
        then:
        if (upToDate) {
            skipped ":customTask"
        } else {
            executedAndNotSkipped ":customTask"
        }

        where:
        before                                        | after                          | upToDate
        [null, 'outputFile']                          | ['outputFile', null]           | true
        ['outputFile1', null]                         | ['outputFile1', 'outputFile2'] | false
        ['outputFile1', 'outputFile2', 'outputFile3'] | ['outputFile1', 'outputFile2'] | false
        ['outputFile1', 'outputFile2']                | ['outputFile2', 'outputFile3'] | false
        ['outputFile1', 'outputFile2']                | []                             | false
        ['outputFile1', 'outputFile2']                | ['outputFile1']                | false
        ['outputFile1']                               | ['outputFile1', 'outputFile2'] | false
        [null, 'outputFile2']                         | ['outputFile1', 'outputFile2'] | false
        [null, 'outputFile1']                         | ['outputFile1', 'outputFile2'] | false
        upToDateString = upToDate ? 'up-to-date' : 'not up-to-date'
    }


    private static String[] customTaskWithOutputs(List<String> outputs) {
        (["customTask", "-PnumOutputs=${outputs.size()}"] + outputs.withIndex().collect { value, idx -> "-Poutput${idx}" + (value ? "=${value}" : '') }) as String[]
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

    def "task stays up-to-date when filtered-out output is changed"() {
        file("build").mkdirs()
        buildFile << """
            class CustomTask extends DefaultTask {
                @OutputFiles
                FileTree outputFiles
            
                @TaskAction
                void doAction() {
                    project.file("build/output.txt").text = "Hello"
                    project.file("build/build.log") << "Produced at \${new Date()}\\n"
                }
            }
            
            task customTask(type: CustomTask) {
                outputFiles = fileTree(file("build")) {
                    exclude "*.log"
                }
            }
        """

        when:
        succeeds "customTask"
        then:
        executedAndNotSkipped ":customTask"

        when:
        succeeds "customTask"
        then:
        skipped ":customTask"

        when:
        file("build/build.log").delete()
        file("build/external.log").text = "External log"
        succeeds "customTask"
        then:
        skipped ":customTask"

        when:
        file("build/output.txt").delete()
        succeeds "customTask"
        then:
        executedAndNotSkipped ":customTask"
    }

    def "changes to inputs that are excluded by default leave task up-to-date"() {
        def inputDir = file("inputDir").createDir()
        inputDir.file('inputFile.txt').text = "input file"
        inputDir.createDir('something')

        buildFile << """
            task myTask {
                inputs.dir('inputDir')
                outputs.file('build/output.txt')
                doLast {
                    file('build/output.txt').text = "Hello world"
                }
            }
        """

        when:
        run 'myTask'
        then:
        executedAndNotSkipped(':myTask')

        when:
        inputDir.file('.gitignore').text = "some ignored file"
        inputDir.file('#ignored#').text = "some ignored file"
        inputDir.file('.git/any-name.txt').text = "some ignored file"
        inputDir.file('something/.git/deeper/dir/structure/any-name.txt').text = "some ignored file"
        inputDir.file('._ignored').text = "some ignored file"
        inputDir.file('some-file.txt~').text = "some ignored file"

        run 'myTask', "--info"
        then:
        skipped(':myTask')
    }

    @Unroll
    @Issue("https://github.com/gradle/gradle/issues/4204")
    def "changing path of empty root directory makes task out of date for #inputAnnotation"() {
        buildFile << """
            class MyTask extends DefaultTask {
                @${inputAnnotation}
                File input
                @OutputFile
                File output
                
                @TaskAction
                void doStuff() {
                    output.text = input.list().join('\\n')
                }
            }           
            
            task myTask(type: MyTask) {
                input = file(inputDir)
                output = project.file("build/output.txt")
            }          

            myTask.input.mkdirs()
        """
        String myTask = ':myTask'

        when:
        run myTask, '-PinputDir=inputDir1'
        then:
        executedAndNotSkipped(myTask)

        when:
        run myTask, '-PinputDir=inputDir2'
        then:
        executedAndNotSkipped(myTask)

        where:
        inputAnnotation << [InputFiles.name, InputDirectory.name]
    }

    @Issue("https://github.com/gradle/gradle/issues/6592")
    def "missing directory is ignored"() {
        buildFile << """
            class TaskWithInputDir extends DefaultTask {
            
                @InputFiles
                FileTree inputDir
                
                @OutputFile
                File outputFile
            
                @TaskAction
                void doStuff() { 
                    outputFile.text = inputDir.files.collect { it.name }.join("\\n") 
                }
            }                             

            task myTask1(type: TaskWithInputDir) {
                inputDir = fileTree(file('input'))
                outputFile = file('build/output.txt')
            }
            task myTask2(type: TaskWithInputDir) {
                inputDir = fileTree(file('input'))
                outputFile = file('build/output.txt')
                dependsOn("myTask1")
            }
        """

        def tasks = [":myTask1", ":myTask2"]

        when:
        run(*tasks)
        then:
        executedAndNotSkipped(*tasks)

        when:
        run(*tasks)
        then:
        skipped(*tasks)
    }
}
