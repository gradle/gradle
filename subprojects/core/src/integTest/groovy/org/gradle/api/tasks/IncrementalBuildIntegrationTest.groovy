/*
 * Copyright 2010 the original author or authors.
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
import org.gradle.test.fixtures.file.TestFile

class IncrementalBuildIntegrationTest extends AbstractIntegrationSpec {

    private TestFile writeDirTransformerTask() {
        file('buildSrc/src/main/groovy/DirTransformerTask.groovy') << '''
import org.gradle.api.*
import org.gradle.api.tasks.*

public class DirTransformerTask extends DefaultTask {
    private File inputDir
    private File outputDir

    @InputDirectory
    public File getInputDir() {
        return inputDir
    }

    public void setInputDir(File inputDir) {
        this.inputDir = inputDir
    }

    @OutputDirectory
    public File getOutputDir() {
        return outputDir
    }

    public void setOutputDir(File outputDir) {
        this.outputDir = outputDir
    }

    @TaskAction
    public void transform() {
        for (File inputFile : inputDir.listFiles()) {
            File outputFile = new File(outputDir, inputFile.name)
            outputFile.text = String.format("[%s]", inputFile.text)
        }
    }
}
'''
    }

    private TestFile writeTransformerTask() {
        file('buildSrc/src/main/groovy/TransformerTask.groovy') << '''
import org.gradle.api.*
import org.gradle.api.tasks.*

public class TransformerTask extends DefaultTask {
    private File inputFile
    private File outputFile
    private String format = "[%s]"

    @InputFile
    public File getInputFile() {
        return inputFile
    }

    public void setInputFile(File inputFile) {
        this.inputFile = inputFile
    }

    @OutputFile
    public File getOutputFile() {
        return outputFile
    }

    public void setOutputFile(File outputFile) {
        this.outputFile = outputFile
    }

    @Input
    public String getFormat() {
        return format
    }

    public void setFormat(String format) {
        this.format = format
    }

    @TaskAction
    public void transform() {
        outputFile.text = String.format(format, inputFile.text)
    }
}
'''
    }

    def "skips task when output file is up-to-date"() {
        writeTransformerTask()

        buildFile << '''
task a(type: TransformerTask) {
    inputFile = file('src.txt')
    outputFile = file('src.a.txt')
}
task b(type: TransformerTask, dependsOn: a) {
    inputFile = a.outputFile
    outputFile = file('src.b.txt')
}
'''
        TestFile inputFile = file('src.txt')
        TestFile outputFileA = file('src.a.txt')
        TestFile outputFileB = file('src.b.txt')

        when:
        inputFile.text = 'content'
        succeeds "b"

        then:
        nonSkippedTasks.sort() ==  [":a", ":b"]

        when:
        TestFile.Snapshot aSnapshot = outputFileA.snapshot()
        TestFile.Snapshot bSnapshot = outputFileB.snapshot()

        then:
        outputFileA.text == '[content]'
        outputFileB.text == '[[content]]'

        // No changes
        when:
        succeeds "b"

        then:
        skippedTasks.sort() ==  [":a", ":b"]

        outputFileA.assertHasNotChangedSince(aSnapshot)
        outputFileB.assertHasNotChangedSince(bSnapshot)

        // Update input timestamp, no content changes
        when:
        inputFile.makeOlder()
        succeeds "b"

        then:
        skippedTasks.sort() ==  [":a", ":b"]

        outputFileA.assertHasNotChangedSince(aSnapshot)
        outputFileB.assertHasNotChangedSince(bSnapshot)

        // Change input content
        when:
        inputFile.text = 'new content'
        succeeds "b"

        then:
        nonSkippedTasks.sort() ==  [":a", ":b"]

        outputFileA.assertHasChangedSince(aSnapshot)
        outputFileB.assertHasChangedSince(bSnapshot)
        outputFileA.text == '[new content]'
        outputFileB.text == '[[new content]]'

        when:
        succeeds "b"

        then:
        skippedTasks.sort() == [":a", ":b"]

        // Delete intermediate output file
        when:
        outputFileA.delete()
        succeeds "b"

        then:
        nonSkippedTasks.sort() ==  [":a"]
        skippedTasks.sort() ==  [":b"]

        outputFileA.text == '[new content]'
        outputFileB.text == '[[new content]]'

        // Delete final output file
        when:
        outputFileB.delete()
        succeeds "b"

        then:
        nonSkippedTasks.sort() ==  [":b"]
        skippedTasks.sort() ==  [":a"]

        outputFileA.text == '[new content]'
        outputFileB.text == '[[new content]]'

        when:
        succeeds "b"

        then:
        skippedTasks.sort() == [":a", ":b"]

        // Change intermediate output file
        when:
        outputFileA.text = 'changed'
        succeeds "b"

        then:
        nonSkippedTasks.sort() ==  [":a"]
        skippedTasks.sort() ==  [":b"]

        outputFileA.text == '[new content]'
        outputFileB.text == '[[new content]]'

        // Change intermediate output file timestamp
        when:
        outputFileA.makeOlder()
        succeeds "b"

        then:
        skippedTasks.sort() ==  [":a", ":b"]

        // Change input file location
        when:
        buildFile << '''
a.inputFile = file('new-a-input.txt')
'''
        file('new-a-input.txt').text = 'new content'
        succeeds "b"

        then:
        nonSkippedTasks.sort() ==  [":a"]
        skippedTasks.sort() ==  [":b"]
        outputFileA.text == '[new content]'

        when:
        succeeds "b"

        then:
        skippedTasks.sort() == [":a", ":b"]

        // Change final output file destination
        when:
        buildFile.text += '''
b.outputFile = file('new-output.txt')
'''
        succeeds "b"
        outputFileB = file('new-output.txt')

        then:
        skippedTasks.sort() ==  [":a"]
        nonSkippedTasks.sort() ==  [":b"]

        outputFileB.text == '[[new content]]'

        when:
        succeeds "b"

        then:
        skippedTasks.sort() == [":a", ":b"]

        // Change intermediate output file destination
        when:
        buildFile << '''
a.outputFile = file('new-a-output.txt')
b.inputFile = a.outputFile
'''
        succeeds "b"
        outputFileA = file('new-a-output.txt')

        then:
        nonSkippedTasks.sort() ==  [":a", ":b"]
        outputFileA.text == '[new content]'

        when:
        succeeds "b"

        then:
        skippedTasks.sort() == [":a", ":b"]

        // Change build file in a way which does not affect the task
        when:
        buildFile.text += '''
task c
'''
        succeeds "b"

        then:
        skippedTasks.sort() ==  [":a", ":b"]

        // Change an input property of the first task (the content format)
        when:
        buildFile.text += '''
a.format = '- %s -'
'''
        succeeds "b"

        then:
        nonSkippedTasks.sort() ==  [":a", ":b"]

        outputFileA.text == '- new content -'
        outputFileB.text == '[- new content -]'

        when:
        succeeds "b"

        then:
        skippedTasks.sort() == [":a", ":b"]

        // Run with --rerun-tasks command-line options
        when:
        succeeds "b", "--rerun-tasks"

        then:
        nonSkippedTasks.sort() ==  [":a", ":b"]

        // Output files already exist before using this version of Gradle
        // delete .gradle dir to simulate this
        when:
        executer.withArguments("--stop").run()
        file('.gradle').assertIsDir().deleteDir()
        outputFileA.makeOlder()
        outputFileB.makeOlder()
        succeeds "b"

        then:
        nonSkippedTasks.sort() ==  [":a", ":b"]

        when:
        outputFileB.delete()
        succeeds "b"

        then:
        nonSkippedTasks.sort() ==  [":b"]
        skippedTasks.sort() ==  [":a"]

        when:
        succeeds "b"

        then:
        skippedTasks.sort() ==  [":a", ":b"]
    }

    def "skips task when output dir contents are up-to-date"() {
        writeDirTransformerTask()

        buildFile << '''
task a(type: DirTransformerTask) {
    inputDir = file('src')
    outputDir = file('build/a')
}
task b(type: DirTransformerTask, dependsOn: a) {
    inputDir = a.outputDir
    outputDir = file('build/b')
}
'''

        file('src/file1.txt') << 'content'

        when:
        succeeds "b"

        then:
        nonSkippedTasks.sort() ==  [":a", ":b"]

        when:
        TestFile outputAFile = file('build/a/file1.txt')
        TestFile outputBFile = file('build/b/file1.txt')
        TestFile.Snapshot aSnapshot = outputAFile.snapshot()
        TestFile.Snapshot bSnapshot = outputBFile.snapshot()

        then:
        outputAFile.text == '[content]'
        outputBFile.text == '[[content]]'

        // No changes
        when:
        succeeds "b"

        then:
        skippedTasks.sort() ==  [":a", ":b"]
        outputAFile.assertHasNotChangedSince(aSnapshot)
        outputBFile.assertHasNotChangedSince(bSnapshot)

        // Change input content
        when:
        file('src/file1.txt').text = 'new content'
        succeeds "b"

        then:
        nonSkippedTasks.sort() ==  [":a", ":b"]

        outputAFile.assertHasChangedSince(aSnapshot)
        outputBFile.assertHasChangedSince(bSnapshot)
        outputAFile.text == '[new content]'
        outputBFile.text == '[[new content]]'

        when:
        succeeds "b"

        then:
        skippedTasks.sort() ==  [":a", ":b"]

        // Add input file
        when:
        file('src/file2.txt').text = 'content2'
        succeeds "b"

        then:
        nonSkippedTasks.sort() ==  [":a", ":b"]

        file('build/a/file2.txt').text == '[content2]'
        file('build/b/file2.txt').text == '[[content2]]'

        when:
        succeeds "b"

        then:
        skippedTasks.sort() ==  [":a", ":b"]

        // Remove input file
        when:
        file('src/file2.txt').delete()
        succeeds "b"

        then:
        nonSkippedTasks.sort() ==  [":a"]
        skippedTasks.sort() ==  [":b"]

        when:
        succeeds "b"

        then:
        skippedTasks.sort() ==  [":a", ":b"]

        // Change intermediate output file
        when:
        outputAFile.text = 'changed'
        succeeds "b"

        then:
        nonSkippedTasks.sort() ==  [":a"]
        skippedTasks.sort() ==  [":b"]
        outputAFile.text == '[new content]'

        when:
        succeeds "b"

        then:
        skippedTasks.sort() ==  [":a", ":b"]

        // Remove intermediate output file
        when:
        outputAFile.delete()
        succeeds "b"

        then:
        nonSkippedTasks.sort() ==  [":a"]
        skippedTasks.sort() ==  [":b"]
        outputAFile.text == '[new content]'

        when:
        succeeds "b"

        then:
        skippedTasks.sort() ==  [":a", ":b"]

        // Output files already exist before using this version of Gradle
        // delete .gradle dir to simulate this
        when:
        executer.withArguments("--stop").run()
        file('.gradle').assertIsDir().deleteDir()
        outputAFile.makeOlder()
        outputBFile.makeOlder()
        succeeds "b"

        then:
        nonSkippedTasks.sort() ==  [":a", ":b"]

        when:
        file('build/b').deleteDir()
        succeeds "b"

        then:
        nonSkippedTasks.sort() ==  [":b"]
        skippedTasks.sort() ==  [":a"]

        when:
        succeeds "b"

        then:
        skippedTasks.sort() ==  [":a", ":b"]
    }

    def "skips tasks when input properties have not changed"() {
        buildFile << '''
public class GeneratorTask extends DefaultTask {
    @Input
    private String text
    @OutputFile
    private File outputFile

    public String getText() {
        return text
    }

    public void setText(String text) {
        this.text = text
    }

    public File getOutputFile() {
        return outputFile
    }

    public void setOutputFile(File outputFile) {
        this.outputFile = outputFile
    }

    @TaskAction
    public void generate() {
        outputFile.text = text
    }
}

task a(type: GeneratorTask) {
    text = project.text
    outputFile = file('dest.txt')
}
'''

        when:
        succeeds "a", "-Ptext=text"

        then:
        nonSkippedTasks.sort() ==  [":a"]

        when:
        succeeds "a", "-Ptext=text"

        then:
        skippedTasks.sort() ==  [":a"]

        when:
        succeeds "a", "-Ptext=newtext"

        then:
        nonSkippedTasks.sort() ==  [":a"]
    }

    def "multiple tasks can generate into overlapping output directories"() {
        writeDirTransformerTask()

        buildFile << '''
task a(type: DirTransformerTask) {
    inputDir = file('src/a')
    outputDir = file('build')
}
task b(type: DirTransformerTask) {
    inputDir = file('src/b')
    outputDir = file('build')
}
'''

        file('src/a/file1.txt') << 'content'
        file('src/b/file2.txt') << 'content'

        when:
        succeeds "a", "b"

        then:
        nonSkippedTasks.sort() ==  [":a", ":b"]

        // No changes
        when:
        succeeds "a", "b"

        then:
        skippedTasks.sort() ==  [":a", ":b"]

        // Delete an output file
        when:
        file('build/file1.txt').delete()
        succeeds "a", "b"

        then:
        nonSkippedTasks.sort() ==  [":a"]
        skippedTasks.sort() ==  [":b"]

        // Change an output file
        when:
        file('build/file2.txt').text = 'something else'
        succeeds "a", "b"

        then:
        nonSkippedTasks.sort() ==  [":b"]
        skippedTasks.sort() ==  [":a"]

        // Output files already exist before using this version of Gradle
        // Simulate this by removing the .gradle dir
        when:
        executer.withArguments("--stop").run()
        file('.gradle').assertIsDir().deleteDir()
        file('build/file1.txt').makeOlder()
        file('build/file2.txt').makeOlder()
        succeeds "a", "b"

        then:
        nonSkippedTasks.sort() ==  [":a", ":b"]

        when:
        file('build').deleteDir()
        succeeds "a"

        then:
        nonSkippedTasks.sort() ==  [":a"]

        when:
        succeeds "b"

        then:
        nonSkippedTasks.sort() ==  [":b"]
    }

    def "can use up-to-date predicate to force task to execute"() {
        buildFile << '''
task inputsAndOutputs {
    inputs.files 'src.txt'
    outputs.file 'src.a.txt'
    outputs.upToDateWhen { project.hasProperty('uptodate') }
    doFirst {
        outputs.files.singleFile.text = "[${inputs.files.singleFile.text}]"
    }
}
task noOutputs {
    inputs.file 'src.txt'
    outputs.upToDateWhen { project.hasProperty('uptodate') }
    doFirst { }
}
task nothing {
    outputs.upToDateWhen { project.hasProperty('uptodate') }
    doFirst { }
}
'''
        TestFile srcFile = file('src.txt')
        srcFile.text = 'content'

        when:
        succeeds "inputsAndOutputs"

        then:
        nonSkippedTasks.sort() ==  [":inputsAndOutputs"]

        // Is up to date

        when:
        succeeds "inputsAndOutputs", '-Puptodate'

        then:
        skippedTasks.sort() ==  [":inputsAndOutputs"]

        // Changed input file
        when:
        srcFile.text = 'different'
        succeeds "inputsAndOutputs", '-Puptodate'

        then:
        nonSkippedTasks.sort() ==  [":inputsAndOutputs"]

        // Predicate is false
        when:
        succeeds "inputsAndOutputs"

        then:
        nonSkippedTasks.sort() ==  [":inputsAndOutputs"]

        // Task with input files and a predicate
        when:
        succeeds "noOutputs"

        then:
        nonSkippedTasks.sort() ==  [":noOutputs"]

        // Is up to date
        when:
        succeeds "noOutputs", "-Puptodate"

        then:
        skippedTasks.sort() ==  [":noOutputs"]

        // Changed input file
        when:
        srcFile.text = 'different again'
        succeeds "noOutputs", "-Puptodate"

        then:
        nonSkippedTasks.sort() ==  [":noOutputs"]

        // Predicate is false
        when:
        succeeds "noOutputs"

        then:
        nonSkippedTasks.sort() ==  [":noOutputs"]

        // Task a predicate only
        when:
        succeeds "nothing"

        then:
        nonSkippedTasks.sort() ==  [":nothing"]

        // Is up to date
        when:
        succeeds "nothing", "-Puptodate"

        then:
        skippedTasks.sort() ==  [":nothing"]

        // Predicate is false
        when:
        succeeds "nothing"

        then:
        nonSkippedTasks.sort() ==  [":nothing"]
    }

    def "lifecycle task is up-to-date when all dependencies are skipped"() {
        writeTransformerTask()

        buildFile << '''
task a(type: TransformerTask) {
    inputFile = file('src.txt')
    outputFile = file('out.txt')
}
task b(dependsOn: a)
'''

        file('src.txt').text = 'content'

        when:
        succeeds "b"
        then:
        nonSkippedTasks.sort() ==  [":a", ":b"]

        when:
        succeeds "b"
        then:
        skippedTasks.sort() ==  [":a", ":b"]
    }

    def "can share artifacts between builds"() {
        writeTransformerTask()

        buildFile << '''
task otherBuild(type: GradleBuild) {
    buildFile = 'build.gradle'
    tasks = ['generate']
    startParameter.searchUpwards = false
}
task transform(type: TransformerTask) {
    dependsOn otherBuild
    inputFile = file('generated.txt')
    outputFile = file('out.txt')
}
task generate(type: TransformerTask) {
    inputFile = file('src.txt')
    outputFile = file('generated.txt')
}
'''
        file('settings.gradle') << 'rootProject.name = "build"'
        file('src.txt').text = 'content'

        when:
        succeeds "transform"
        then:
        nonSkippedTasks.sort() ==  [":build:generate", ":otherBuild", ':transform']

        when:
        succeeds "transform"
        then:
        nonSkippedTasks.sort() ==  [":otherBuild"]
        skippedTasks.sort() ==  [":build:generate", ":transform"]
    }

    def "task can have outputs and no inputs"() {
        buildFile << """
            class TaskA extends DefaultTask {
                @OutputFile
                File outputFile

                @TaskAction void exec() {
                    outputFile.text = "output-file"
                }
            }

            task a(type: TaskA) {
                outputFile = file("output.txt")
            }
        """

        when:
        succeeds "a"

        then:
        nonSkippedTasks.sort() == [':a']
        def outputFile = file('output.txt')
        outputFile.text == 'output-file'

        // No changes
        when:
        succeeds "a"

        then:
        skippedTasks.sort() == [':a']

        // Remove output file
        when:
        outputFile.delete()
        succeeds "a"

        then:
        nonSkippedTasks.sort() == [':a']
        outputFile.text == 'output-file'

        when:
        succeeds "a"

        then:
        skippedTasks.sort() == [':a']

        // Change output file
        when:
        outputFile.text = 'changed'
        succeeds "a"

        then:
        nonSkippedTasks.sort() == [':a']
        outputFile.text == 'output-file'

        when:
        succeeds "a"

        then:
        skippedTasks.sort() == [':a']
    }

    def "task can have inputs and no outputs"() {
        buildFile << """
            class TaskA extends DefaultTask {
                @InputFile
                File inputFile

                @TaskAction void exec() {
                    println "file name: \${inputFile.name} content: '\${inputFile.text}'"
                }
            }

            task a(type: TaskA) {
                inputFile = file("input.txt")
            }
        """
        file("input.txt").text = 'input-file'

        when:
        succeeds "a"

        then:
        nonSkippedTasks.sort() == [':a']
        outputContains("file name: input.txt content: 'input-file'")

        // No changes
        when:
        succeeds "a"

        then:
        nonSkippedTasks.sort() == [':a']
        outputContains("file name: input.txt content: 'input-file'")
    }

    def "can use outputs and inputs from other task"() {
        buildFile << """
            class TaskA extends DefaultTask {
                @OutputFile
                File outputFile

                @TaskAction void exec() {
                    outputFile.text = "output-file"
                }
            }

            class TaskB extends DefaultTask {
                @InputFiles
                FileCollection inputFiles

                @TaskAction void exec() {
                    inputFiles.each { file ->
                        println "Task '\$name' file '\${file.name}' with '\${file.text}'"
                    }
                }
            }

            task a(type: TaskA) {
                outputFile = file("output.txt")
            }

            task b(type: TaskB) {
                inputFiles = tasks.a.outputs.files
            }

            task b2(type: TaskB) {
                inputFiles = tasks.b.inputs.files
            }
        """

        when:
        succeeds "b", "b2"

        then:
        nonSkippedTasks.sort() == [':a', ':b', ':b2']
        output.contains "Task 'b' file 'output.txt' with 'output-file'"
        output.contains "Task 'b2' file 'output.txt' with 'output-file'"

        when:
        succeeds "b", "b2"

        then:
        skippedTasks.sort() == [':a']
        nonSkippedTasks.sort() == [':b', ':b2']
        output.contains "Task 'b' file 'output.txt' with 'output-file'"
        output.contains "Task 'b2' file 'output.txt' with 'output-file'"
    }
}
