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

class TaskUpToDateIntegrationTest extends AbstractIntegrationSpec {
    @Issue("https://issues.gradle.org/browse/GRADLE-3540")
    def "order of #annotation marks task not up-to-date"() {
        buildFile << """
            class MyTask extends DefaultTask {
                @Output${files ? "Files" : "Directories"} FileCollection out

                @TaskAction def exec() {
                    out.each { it${files ? ".text = 'data' + it.name" : ".mkdirs(); new File(it, 'contents').text = 'data' + it.name"} }
                }
            }

            task myTask(type: MyTask) {
                if (providers.gradleProperty("reverse").isPresent()) {
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
                outputFile = providers.gradleProperty('outputFile').map { file(it) }.orNull
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
                def outputFile =
                    providers.gradleProperty(name).map { value ->
                        value ? file(value) : null
                    }.orNull
                return { -> outputFile }
            }

            task customTask(type: CustomTask) {
                int numOutputs = Integer.parseInt(
                    providers.gradleProperty('numOutputs').get()
                )
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

    def "output files moved from one location to another marks task up-to-date"() {
        buildFile << """
            abstract class CustomTask extends DefaultTask {
                @OutputDirectory
                abstract DirectoryProperty getOutputDirectory()

                @TaskAction
                void doAction() {
                    getOutputDirectory().file("output.txt").get().asFile.text = "output"
                }
            }

            task customTask(type: CustomTask) {
                outputDirectory = project.file(project.providers.gradleProperty('outputDir'))
            }
        """

        when:
        succeeds ":customTask", "-PoutputDir=build/output1"
        then:
        executedAndNotSkipped ":customTask"

        when:
        succeeds ":customTask", "-PoutputDir=build/output2"
        then:
        executedAndNotSkipped ":customTask"

        when:
        succeeds ":customTask", "-PoutputDir=build/output1"
        then:
        skipped ":customTask"

        when:
        succeeds ":customTask", "-PoutputDir=build/output2"
        then:
        skipped ":customTask"
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
                inputFile = providers.gradleProperty('inputFile').map { file(it) }.orNull
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
            abstract class CustomTask extends DefaultTask {
                @OutputFiles
                FileTree outputFiles

                @Inject
                abstract ProjectLayout getLayout()

                @TaskAction
                void doAction() {
                    layout.buildDirectory.file("output.txt").get().asFile.text = "Hello"
                    layout.buildDirectory.file("build.log").get().asFile << "Produced at \${new Date()}\\n"
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
                def outputFile = file('build/output.txt')
                doLast {
                    outputFile.text = "Hello world"
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

    def "can register multiple file trees within a single output property"() {
        buildFile << """
            abstract class MyTask extends DefaultTask {
                @OutputFiles
                abstract ConfigurableFileCollection getOutputFiles()

                @Inject
                abstract ProjectLayout getLayout()

                @TaskAction
                void doStuff() {
                    layout.buildDirectory.file("dir1/output1.txt").get().asFile.text = "first"
                    layout.buildDirectory.file("dir2/output2.txt").get().asFile.text = "second"
                }
            }

            tasks.register("myTask", MyTask) {
                outputFiles.from(fileTree('build/dir1'))
                outputFiles.from(fileTree('build/dir2'))
            }
        """

        when:
        run("myTask")
        then:
        executedAndNotSkipped(":myTask")

        when:
        file("build/dir1").deleteDir()
        run("myTask")
        then:
        executedAndNotSkipped(":myTask")
    }

    @Issue("https://github.com/gradle/gradle/issues/4204")
    def "changing path of empty root directory #outOfDateDescription for #inputAnnotation"() {
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
                input = providers.gradleProperty('inputDir').map { file(it) }.get()
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
        if (outOfDate) {
            executedAndNotSkipped(myTask)
        } else {
            skipped(myTask)
        }

        where:
        inputAnnotation     | outOfDate
        InputFiles.name     | true
        InputDirectory.name | false

        outOfDateDescription = outOfDate ? "makes task out of date" : "leaves task up to date"
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

    def "cannot register #invalidOutput as an output"() {
        buildFile << """
            abstract class TaskWithInvalidOutput extends DefaultTask {
                @TaskAction
                void doStuff() {}

                @OutputFiles
                abstract ConfigurableFileCollection getInvalidOutput()
            }

            tasks.register("taskWithInvalidOutput", TaskWithInvalidOutput) {
                invalidOutput.from(${invalidOutput}(file('${fileName}')))
            }
        """

        expect:
        fails("taskWithInvalidOutput")
        failure.assertHasCause("Only files and directories can be registered as outputs (was: ${String.format(message, file(fileName).getAbsolutePath())})")

        where:
        invalidOutput | fileName   | message
        'zipTree'     | 'some.jar' | "ZIP '%s'"
        'tarTree'     | 'some.tar' | "TAR '%s'"
    }

    def "task with base Java type input property can be up-to-date"() {
        buildFile << """
            class MyTask extends DefaultTask {
                @Input Duration duration
                @OutputFile File output

                @TaskAction def exec() {
                    output.text = duration.toString()
                }
            }

            task myTask(type: MyTask) {
                duration = Duration.ofMinutes(1)
                output = project.file("build/output.txt")
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

}
