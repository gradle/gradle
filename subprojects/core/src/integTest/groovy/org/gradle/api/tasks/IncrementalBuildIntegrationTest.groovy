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
import org.gradle.integtests.fixtures.DirectoryBuildCacheFixture
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.internal.reflect.validation.ValidationMessageChecker
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.internal.ToBeImplemented
import spock.lang.Issue

class IncrementalBuildIntegrationTest extends AbstractIntegrationSpec implements ValidationMessageChecker, DirectoryBuildCacheFixture {

    def setup() {
        expectReindentedValidationMessage()
    }

    private TestFile writeDirTransformerTask() {
        buildFile << '''
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
        buildFile << '''
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

    @ToBeFixedForConfigurationCache(because = "task wrongly up-to-date")
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
// Use a separate build script to avoid invalidating task implementations
apply from: 'changes.gradle'
'''
        def changesFile = file('changes.gradle').createFile()

        TestFile inputFile = file('src.txt')
        TestFile outputFileA = file('src.a.txt')
        TestFile outputFileB = file('src.b.txt')

        when:
        inputFile.text = 'content'
        succeeds "b"

        then:
        result.assertTasksNotSkipped(":a", ":b")

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
        result.assertTasksSkipped(":a", ":b")

        outputFileA.assertHasNotChangedSince(aSnapshot)
        outputFileB.assertHasNotChangedSince(bSnapshot)

        // Update input timestamp, no content changes
        when:
        inputFile.makeOlder()
        succeeds "b"

        then:
        result.assertTasksSkipped(":a", ":b")

        outputFileA.assertHasNotChangedSince(aSnapshot)
        outputFileB.assertHasNotChangedSince(bSnapshot)

        // Change input content, same length
        when:
        inputFile.text = 'CONTENT'
        succeeds "b"

        then:
        result.assertTasksNotSkipped(":a", ":b")

        outputFileA.assertHasChangedSince(aSnapshot)
        outputFileB.assertHasChangedSince(bSnapshot)
        outputFileA.text == '[CONTENT]'
        outputFileB.text == '[[CONTENT]]'

        when:
        succeeds "b"

        then:
        result.assertTasksSkipped(":a", ":b")

        // Change input content, different length
        when:
        inputFile.text = 'new content'
        succeeds "b"

        then:
        result.assertTasksNotSkipped(":a", ":b")

        outputFileA.assertHasChangedSince(aSnapshot)
        outputFileB.assertHasChangedSince(bSnapshot)
        outputFileA.text == '[new content]'
        outputFileB.text == '[[new content]]'

        when:
        succeeds "b"

        then:
        result.assertTasksSkipped(":a", ":b")

        // Delete intermediate output file
        when:
        outputFileA.delete()
        succeeds "b"

        then:
        result.assertTasksNotSkipped(":a")
        result.assertTasksSkipped(":b")

        outputFileA.text == '[new content]'
        outputFileB.text == '[[new content]]'

        // Delete final output file
        when:
        outputFileB.delete()
        succeeds "b"

        then:
        result.assertTasksNotSkipped(":b")
        result.assertTasksSkipped(":a")

        outputFileA.text == '[new content]'
        outputFileB.text == '[[new content]]'

        when:
        succeeds "b"

        then:
        result.assertTasksSkipped(":a", ":b")

        // Change intermediate output file, different length
        when:
        outputFileA.text = 'changed'
        succeeds "b"

        then:
        result.assertTasksNotSkipped(":a")
        result.assertTasksSkipped(":b")

        outputFileA.text == '[new content]'
        outputFileB.text == '[[new content]]'

        when:
        succeeds "b"

        then:
        result.assertTasksSkipped(":a", ":b")

        // Change intermediate output file timestamp, same content
        when:
        outputFileA.makeOlder()
        succeeds "b"

        then:
        result.assertTasksSkipped(":a", ":b")

        // Change input file location
        when:
        changesFile << '''
a.inputFile = file('new-a-input.txt')
'''
        file('new-a-input.txt').text = 'new content'
        succeeds "b"

        then:
        result.assertTasksNotSkipped(":a")
        result.assertTasksSkipped(":b")
        outputFileA.text == '[new content]'

        when:
        succeeds "b"

        then:
        result.assertTasksSkipped(":a", ":b")

        // Change final output file destination
        when:
        changesFile << '''
b.outputFile = file('new-output.txt')
'''
        succeeds "b"
        outputFileB = file('new-output.txt')

        then:
        result.assertTasksSkipped(":a")
        result.assertTasksNotSkipped(":b")

        outputFileB.text == '[[new content]]'

        when:
        succeeds "b"

        then:
        result.assertTasksSkipped(":a", ":b")

        // Change intermediate output file destination
        when:
        changesFile << '''
a.outputFile = file('new-a-output.txt')
b.inputFile = a.outputFile
'''
        succeeds "b"
        outputFileA = file('new-a-output.txt')

        then:
        result.assertTasksNotSkipped(":a", ":b")
        outputFileA.text == '[new content]'

        when:
        succeeds "b"

        then:
        result.assertTasksSkipped(":a", ":b")

        // Change an input property of the first task (the content format)
        when:
        changesFile << '''
a.format = '- %s -'
'''
        succeeds "b"

        then:
        result.assertTasksNotSkipped(":a", ":b")

        outputFileA.text == '- new content -'
        outputFileB.text == '[- new content -]'

        when:
        succeeds "b"

        then:
        result.assertTasksSkipped(":a", ":b")

        // Run with --rerun-tasks command-line options
        when:
        succeeds "b", "--rerun-tasks"

        then:
        result.assertTasksNotSkipped(":a", ":b")

        // Output files already exist before using this version of Gradle
        // delete .gradle dir to simulate this
        when:
        file('.gradle').assertIsDir().deleteDir()
        outputFileA.makeOlder()
        outputFileB.makeOlder()
        succeeds "b"

        then:
        result.assertTasksNotSkipped(":a", ":b")

        when:
        outputFileB.delete()
        succeeds "b"

        then:
        result.assertTasksNotSkipped(":b")
        result.assertTasksSkipped(":a")

        when:
        succeeds "b"

        then:
        result.assertTasksSkipped(":a", ":b")
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
        result.assertTasksNotSkipped(":a", ":b")

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
        result.assertTasksSkipped(":a", ":b")
        outputAFile.assertHasNotChangedSince(aSnapshot)
        outputBFile.assertHasNotChangedSince(bSnapshot)

        // Change input content, same length
        when:
        file('src/file1.txt').text = 'CONTENT'
        succeeds "b"

        then:
        result.assertTasksNotSkipped(":a", ":b")

        outputAFile.assertHasChangedSince(aSnapshot)
        outputBFile.assertHasChangedSince(bSnapshot)
        outputAFile.text == '[CONTENT]'
        outputBFile.text == '[[CONTENT]]'

        when:
        succeeds "b"

        then:
        result.assertTasksSkipped(":a", ":b")

        // Change input content, different length
        when:
        file('src/file1.txt').text = 'new content'
        succeeds "b"

        then:
        result.assertTasksNotSkipped(":a", ":b")

        outputAFile.assertHasChangedSince(aSnapshot)
        outputBFile.assertHasChangedSince(bSnapshot)
        outputAFile.text == '[new content]'
        outputBFile.text == '[[new content]]'

        when:
        succeeds "b"

        then:
        result.assertTasksSkipped(":a", ":b")

        // Add input file
        when:
        file('src/file2.txt').text = 'content2'
        succeeds "b"

        then:
        result.assertTasksNotSkipped(":a", ":b")

        file('build/a/file2.txt').text == '[content2]'
        file('build/b/file2.txt').text == '[[content2]]'

        when:
        succeeds "b"

        then:
        result.assertTasksSkipped(":a", ":b")

        // Remove input file
        when:
        file('src/file2.txt').delete()
        succeeds "b"

        then:
        result.assertTasksNotSkipped(":a")
        result.assertTasksSkipped(":b")

        when:
        succeeds "b"

        then:
        result.assertTasksSkipped(":a", ":b")

        // Change intermediate output file, different length
        when:
        outputAFile.text = 'changed'
        succeeds "b"

        then:
        result.assertTasksNotSkipped(":a")
        result.assertTasksSkipped(":b")
        outputAFile.text == '[new content]'

        when:
        succeeds "b"

        then:
        result.assertTasksSkipped(":a", ":b")

        // Remove intermediate output file
        when:
        outputAFile.delete()
        succeeds "b"

        then:
        result.assertTasksNotSkipped(":a")
        result.assertTasksSkipped(":b")
        outputAFile.text == '[new content]'

        when:
        succeeds "b"

        then:
        result.assertTasksSkipped(":a", ":b")

        // Output files already exist before using this version of Gradle
        // delete .gradle dir to simulate this
        when:
        file('.gradle').assertIsDir().deleteDir()
        outputAFile.makeOlder()
        outputBFile.makeOlder()
        succeeds "b"

        then:
        result.assertTasksNotSkipped(":a", ":b")

        when:
        file('build/b').deleteDir()
        succeeds "b"

        then:
        result.assertTasksNotSkipped(":b")
        result.assertTasksSkipped(":a")

        when:
        succeeds "b"

        then:
        result.assertTasksSkipped(":a", ":b")
    }

    def "notices changes to input files where the file length does not change"() {
        writeTransformerTask()
        writeDirTransformerTask()

        buildFile << '''
task a(type: TransformerTask) {
    inputFile = file('src.txt')
    outputFile = file('build/a/src.txt')
}
task b(type: DirTransformerTask, dependsOn: a) {
    inputDir = file('build/a')
    outputDir = file('build/b')
}
'''

        given:
        def inputFile = file('src.txt')
        inputFile.text = "__"
        long before = inputFile.length()

        expect:
        (10..40).each {
            inputFile.text = it as String
            assert inputFile.length() == before

            succeeds("b")
            result.assertTasksNotSkipped(":a", ":b")
        }
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
        result.assertTasksNotSkipped(":a")

        when:
        succeeds "a", "-Ptext=text"

        then:
        result.assertTasksSkipped(":a")

        when:
        succeeds "a", "-Ptext=newtext"

        then:
        result.assertTasksNotSkipped(":a")
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
        result.assertTasksNotSkipped(":a", ":b")

        // No changes
        when:
        succeeds "a", "b"

        then:
        result.assertTasksSkipped(":a", ":b")

        // Delete an output file
        when:
        file('build/file1.txt').delete()
        succeeds "a", "b"

        then:
        result.assertTasksNotSkipped(":a")
        result.assertTasksSkipped(":b")

        // Change an output file
        when:
        file('build/file2.txt').text = 'something else'
        succeeds "a", "b"

        then:
        result.assertTasksNotSkipped(":b")
        result.assertTasksSkipped(":a")

        // Output files already exist before using this version of Gradle
        // Simulate this by removing the .gradle dir
        when:
        file('.gradle').assertIsDir().deleteDir()
        file('build/file1.txt').makeOlder()
        file('build/file2.txt').makeOlder()
        succeeds "a", "b"

        then:
        result.assertTasksNotSkipped(":a", ":b")

        when:
        file('build').deleteDir()
        succeeds "a"

        then:
        result.assertTasksNotSkipped(":a")

        when:
        succeeds "b"

        then:
        result.assertTasksNotSkipped(":b")
    }

    def "can use up-to-date predicate to force task to execute"() {
        def inputFileName = 'src.txt'

        buildFile << """
def isUpToDate = providers.gradleProperty('uptodate').map { true }.orElse(false)

task inputsAndOutputs {
    def inputFile = file('${inputFileName}')
    def outputFile = file('src.a.txt')
    inputs.files inputFile
    outputs.file outputFile
    outputs.upToDateWhen { isUpToDate.get() }
    doFirst {
        outputFile.text = "[\${inputFile.text}]"
    }
}
task noOutputs {
    inputs.file 'src.txt'
    outputs.upToDateWhen { isUpToDate.get() }
    doFirst { }
}
task nothing {
    outputs.upToDateWhen { isUpToDate.get() }
    doFirst { }
}
"""
        TestFile srcFile = file(inputFileName)
        srcFile.text = 'content'

        when:
        succeeds "inputsAndOutputs"

        then:
        result.assertTasksNotSkipped(":inputsAndOutputs")

        // Is up to date

        when:
        succeeds "inputsAndOutputs", '-Puptodate'

        then:
        result.assertTasksSkipped(":inputsAndOutputs")

        // Changed input file
        when:
        srcFile.text = 'different'
        succeeds "inputsAndOutputs", '-Puptodate'

        then:
        result.assertTasksNotSkipped(":inputsAndOutputs")

        // Predicate is false
        when:
        succeeds "inputsAndOutputs"

        then:
        result.assertTasksNotSkipped(":inputsAndOutputs")

        // Task with input files and a predicate
        when:
        succeeds "noOutputs"

        then:
        result.assertTasksNotSkipped(":noOutputs")

        // Is up to date
        when:
        succeeds "noOutputs", "-Puptodate"

        then:
        result.assertTasksSkipped(":noOutputs")

        // Changed input file
        when:
        srcFile.text = 'different again'
        succeeds "noOutputs", "-Puptodate"

        then:
        result.assertTasksNotSkipped(":noOutputs")

        // Predicate is false
        when:
        succeeds "noOutputs"

        then:
        result.assertTasksNotSkipped(":noOutputs")

        // Task a predicate only
        when:
        succeeds "nothing"

        then:
        result.assertTasksNotSkipped(":nothing")

        // Is up to date
        when:
        succeeds "nothing", "-Puptodate"

        then:
        result.assertTasksSkipped(":nothing")

        // Predicate is false
        when:
        succeeds "nothing"

        then:
        result.assertTasksNotSkipped(":nothing")
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
        result.assertTasksNotSkipped(":a", ":b")

        when:
        succeeds "b"
        then:
        result.assertTasksSkipped(":a", ":b")
    }

    def "can share artifacts between builds"() {
        writeTransformerTask()

        buildFile << '''
            task otherBuild(type: GradleBuild) {
                tasks = ['generate']
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
        result.assertTasksNotSkipped(":${testDirectory.name}:generate", ":otherBuild", ':transform')

        when:
        succeeds "transform"
        then:
        result.assertTasksNotSkipped(":otherBuild")
        result.assertTasksSkipped(":${testDirectory.name}:generate", ":transform")
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
        result.assertTasksNotSkipped(':a')
        def outputFile = file('output.txt')
        outputFile.text == 'output-file'

        // No changes
        when:
        succeeds "a"

        then:
        result.assertTasksSkipped(':a')

        // Remove output file
        when:
        outputFile.delete()
        succeeds "a"

        then:
        result.assertTasksNotSkipped(':a')
        outputFile.text == 'output-file'

        when:
        succeeds "a"

        then:
        result.assertTasksSkipped(':a')

        // Change output file
        when:
        outputFile.text = 'changed'
        succeeds "a"

        then:
        result.assertTasksNotSkipped(':a')
        outputFile.text == 'output-file'

        when:
        succeeds "a"

        then:
        result.assertTasksSkipped(':a')
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
        result.assertTasksNotSkipped(':a')
        outputContains("file name: input.txt content: 'input-file'")

        // No changes
        when:
        succeeds "a"

        then:
        result.assertTasksNotSkipped(':a')
        outputContains("file name: input.txt content: 'input-file'")
    }

    def "directory can be changed by another task between execution of two tasks that use the directory as input"() {
        writeDirTransformerTask()
        buildFile << """
            def srcDir = file('src')
            // Note: task mutates inputs of transform1 just before transform1 executes
            task src1 {
                outputs.dir(srcDir)
                outputs.upToDateWhen { false }
                doLast {
                    srcDir.mkdirs()
                    new File(srcDir, "src.txt").text = "123"
                }
            }
            task transform1(type: DirTransformerTask) {
                mustRunAfter src1
                inputDir = srcDir
                outputDir = file("out-1")
            }
            // Note: task mutates inputs of transform2 just before transform2 executes
            task src2 {
                mustRunAfter transform1
                outputs.dir(srcDir)
                outputs.upToDateWhen { false }
                doLast {
                    srcDir.mkdirs()
                    new File(srcDir, "src.txt").text = "abcd"
                    new File(srcDir, "src2.txt").text = "123"
                }
            }
            task transform2(type: DirTransformerTask) {
                mustRunAfter src1, src2
                inputDir = srcDir
                outputDir = file("out-2")
            }
"""

        when:
        run "src1", "transform1", "src2", "transform2"

        then:
        result.assertTasksExecuted(":src1", ":transform1", ":src2", ":transform2")
        result.assertTasksNotSkipped(":src1", ":transform1", ":src2", ":transform2")

        when:
        run "transform2"

        then:
        result.assertTasksExecuted(":transform2")
        result.assertTasksSkipped(":transform2")

        when:
        run "transform1"

        then:
        result.assertTasksExecuted(":transform1")
        result.assertTasksNotSkipped(":transform1")

        when:
        run "transform1", "transform2"

        then:
        result.assertTasksExecuted(":transform1", ":transform2")
        result.assertTasksSkipped(":transform1", ":transform2")
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
        result.assertTasksNotSkipped(':a', ':b', ':b2')
        output.contains "Task 'b' file 'output.txt' with 'output-file'"
        output.contains "Task 'b2' file 'output.txt' with 'output-file'"

        when:
        succeeds "b", "b2"

        then:
        result.assertTasksSkipped(':a')
        result.assertTasksNotSkipped(':b', ':b2')
        output.contains "Task 'b' file 'output.txt' with 'output-file'"
        output.contains "Task 'b2' file 'output.txt' with 'output-file'"
    }

    def "task loaded with custom classloader fails the build"() {
        file("input.txt").text = "data"
        buildFile << """
            def CustomTask = new GroovyClassLoader(getClass().getClassLoader()).parseClass '''
                import org.gradle.api.*
                import org.gradle.api.tasks.*

                class CustomTask extends DefaultTask {
                    @InputFile File input
                    @OutputFile File output
                    @TaskAction action() {
                        output.parentFile.mkdirs()
                        output.text = input.text
                    }
                }
            '''

            task customTask(type: CustomTask) {
                input = file("input.txt")
                output = file("build/output.txt")
            }
        """
        when:
        fails "customTask"
        then:
        failureDescriptionStartsWith("Some problems were found with the configuration of task ':customTask' (type 'CustomTask').")
        failureDescriptionContains(implementationUnknown {
            implementationOfTask(':customTask')
            unknownClassloader('CustomTask_Decorated')
        })
        failureDescriptionContains(implementationUnknown {
            additionalTaskAction(':customTask')
            unknownClassloader('CustomTask_Decorated')
        })
    }

    def "task with custom action loaded with custom classloader fails the build"() {
        file("input.txt").text = "data"
        buildFile << """
            import org.gradle.api.*
            import org.gradle.api.tasks.*

            class CustomTask extends DefaultTask {
                @InputFile File input
                @OutputFile File output
                @TaskAction action() {
                    output.parentFile.mkdirs()
                    output.text = input.text
                }
            }

            def CustomTaskAction = new GroovyClassLoader(getClass().getClassLoader()).parseClass '''
                import org.gradle.api.*

                class CustomTaskAction implements Action<Task> {
                    static Action<Task> create() {
                        return new CustomTaskAction()
                    }

                    @Override
                    void execute(Task task) {
                    }
                }
            '''

            task customTask(type: CustomTask) {
                input = file("input.txt")
                output = file("build/output.txt")
                doFirst(CustomTaskAction.create())
            }
        """
        when:
        fails "customTask"
        then:
        failureDescriptionStartsWith("A problem was found with the configuration of task ':customTask' (type 'CustomTask').")
        failureDescriptionContains(implementationUnknown {
            additionalTaskAction(':customTask')
            unknownClassloader('CustomTaskAction')
        })
    }

    @Issue("gradle/gradle#1168")
    def "task is not up-to-date when it has overlapping outputs"() {
        buildFile << """
            apply plugin: 'base'

            class CustomTask extends DefaultTask {
                @OutputDirectory File outputDir = new File(project.buildDir, "output")

                @TaskAction
                public void generate() {
                    File outputFile = new File(outputDir, "file.txt")
                    outputFile.text = "generated"
                    outputFile.lastModified = 0
                }
            }

            task customTask(type: CustomTask)
        """
        when:
        succeeds("customTask")
        then:
        result.assertTasksExecuted(":customTask")
        file("build/output/file.txt").assertExists()

        when:
        file(".gradle").deleteDir()
        succeeds("customTask")
        then:
        result.assertTasksExecuted(":customTask")
        file("build/output/file.txt").assertExists()

        when:
        succeeds("clean")
        then:
        result.assertTasksExecuted(":clean")
        file("build/output/file.txt").assertDoesNotExist()

        when:
        succeeds("customTask")
        then:
        result.assertTasksExecuted(":customTask")
        file("build/output/file.txt").assertExists()
    }

    @Issue("https://github.com/gradle/gradle/issues/2180")
    def "fileTrees can be used as output files"() {
        given:
        buildScript """
            task myTask {
                inputs.file file('input.txt')
                outputs.files fileTree(dir: 'build', include: 'output.txt')
                doLast {
                    file('build').mkdirs()
                    file('build/output.txt').text = new File('input.txt').text
                }
            }
        """.stripIndent()

        file('input.txt').text = 'input file'

        when:
        succeeds 'myTask'

        then:
        executedAndNotSkipped(':myTask')

        when:
        succeeds('myTask')

        then:
        skipped(':myTask')
    }

    def "task with no actions is skipped even if it has inputs"() {
        buildFile << """
            task taskWithInputs(type: TaskWithInputs) {
                input = 'some-name'
            }

            class TaskWithInputs extends DefaultTask {
                @Input
                String input
            }
        """

        when:
        succeeds 'taskWithInputs'

        then:
        skipped(':taskWithInputs')
    }

    @ToBeImplemented("Private getters should be ignored")
    def "private inputs can be overridden in subclass"() {
        given:
        buildFile << '''
            abstract class MyBaseTask extends DefaultTask {

                @Inject
                abstract ProviderFactory getProviders()

                @Input
                private String getMyPrivateInput() {
                    return 'overridden private'
                }

                @OutputFile
                File getOutput() {
                    new File('build/output.txt')
                }

                @TaskAction
                void doStuff() {
                    output.text = getMyPrivateInput()
                }
            }

            abstract class MyTask extends MyBaseTask {
                @Input
                private String getMyPrivateInput() { 'only private' }
            }

            task myTask(type: MyTask)
        '''

        when:
        fails 'myTask'

        then:
        failureDescriptionContains(
            privateGetterAnnotatedMessage { type('MyTask').property('myPrivateInput').annotation('Input') }
        )
    }

    @ToBeImplemented("Private getters should be ignored")
    def "private inputs in superclass are respected"() {
        given:
        buildFile << '''
            abstract class MyBaseTask extends DefaultTask {

                @Inject
                abstract ProviderFactory getProviders()

                @Input
                private String getMyPrivateInput() {
                    return providers.gradleProperty('private').get()
                }

                @OutputFile
                File getOutput() {
                    new File('build/output.txt')
                }

                @TaskAction
                void doStuff() {
                    output.text = getMyPrivateInput()
                }
            }

            abstract class MyTask extends MyBaseTask {
            }

            task myTask(type: MyTask)
        '''

        when:
        run 'myTask', '-Pprivate=first'

        then:
        def outputFile = file('build/output.txt')
        outputFile.text == 'first'

        when:
        run 'myTask', '-Pprivate=second'

        then:
        executedAndNotSkipped ':myTask'
        outputFile.text == 'second'

        when:
        run 'myTask', '-Pprivate=second'

        then:
        skipped ':myTask'
        outputFile.text == 'second'
    }

    @Issue("https://github.com/gradle/gradle/issues/11805")
    def "Groovy property annotated as @Internal with differently annotated getter is not allowed"() {
        def inputFile = file("input.txt")
        inputFile.text = "original"

        buildFile << """
            class CustomTask extends DefaultTask {
                    @Internal
                    FileCollection classpath

                    @InputFiles
                    @Classpath
                    FileCollection getClasspath() {
                        return classpath
                    }

                    @OutputFile
                    File outputFile

                    @TaskAction
                    void execute() {
                        outputFile << classpath*.name.join("\\n")
                    }
            }

            task custom(type: CustomTask) {
                classpath = files("input.txt")
                outputFile = file("build/output.txt")
            }
        """

        when:
        fails "custom"

        then:
        failureDescriptionContains(
            ignoredAnnotatedPropertyMessage {
                type('CustomTask').property('classpath')
                ignoring('Internal')
                alsoAnnotatedWith('Classpath', 'InputFiles')
            }
        )
    }

    @Issue("https://github.com/gradle/gradle/issues/7923")
    def "task is not up-to-date when the implementation of a named #actionMethodName action changes"() {
        buildScript """
            tasks.register('myTask') {
                outputs.dir(layout.buildDirectory.dir('myDir'))
                ${actionMethodName}('myAction') { println("printing from action") }
            }
        """

        when:
        run ':myTask'
        then:
        executedAndNotSkipped(':myTask')
        outputContains("printing from action")

        when:
        buildFile << """
            tasks.register('other')
        """
        run ':myTask', '--info'
        then:
        executedAndNotSkipped(':myTask')
        outputContains("printing from action")
        outputContains("One or more additional actions for task ':myTask' have changed.")

        when:
        run ':myTask'
        then:
        skipped(':myTask')

        where:
        actionMethodName << [
            "doFirst",
            "doLast",
        ]
    }
}
