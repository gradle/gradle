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

import static org.hamcrest.Matchers.equalTo

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
        nonSkippedTasks as List ==  [":a", ":b"]

        when:
        TestFile.Snapshot aSnapshot = outputFileA.snapshot()
        TestFile.Snapshot bSnapshot = outputFileB.snapshot()

        then:
        outputFileA.text == '[content]'
        outputFileB.text == '[[content]]'

        when:
        // No changes
        succeeds "b"

        then:
        skippedTasks as List ==  [":a", ":b"]

        outputFileA.assertHasNotChangedSince(aSnapshot)
        outputFileB.assertHasNotChangedSince(bSnapshot)

        // Update timestamp, no content changes
        when:
        inputFile.makeOlder()
        succeeds "b"

        then:
        skippedTasks as List ==  [":a", ":b"]

        outputFileA.assertHasNotChangedSince(aSnapshot)
        outputFileB.assertHasNotChangedSince(bSnapshot)

        // Change content
        when:
        inputFile.text = 'new content'
        succeeds "b"

        then:
        nonSkippedTasks as List ==  [":a", ":b"]

        outputFileA.assertHasChangedSince(aSnapshot)
        outputFileB.assertHasChangedSince(bSnapshot)
        outputFileA.text == '[new content]'
        outputFileB.text == '[[new content]]'

        // Delete intermediate output file
        when:
        outputFileA.delete()
        succeeds "b"

        then:
        nonSkippedTasks as List ==  [":a"]
        skippedTasks as List ==  [":b"]

        outputFileA.text == '[new content]'
        outputFileB.text == '[[new content]]'

        // Delete final output file
        when:
        outputFileB.delete()
        succeeds "b"

        then:
        nonSkippedTasks as List ==  [":b"]
        skippedTasks as List ==  [":a"]

        outputFileA.text == '[new content]'
        outputFileB.text == '[[new content]]'

        // Change build file in a way which does not affect the task
        when:
        buildFile.text += '''
task c
'''
        succeeds "b"

        then:
        skippedTasks as List ==  [":a", ":b"]

        // Change an input property of the first task (the content format)
        when:
        buildFile.text += '''
a.format = '     %s     '
'''
        succeeds "b"

        then:
        nonSkippedTasks as List ==  [":a", ":b"]

        outputFileA.text == '     new content     '
        outputFileB.text == '[     new content     ]'

        // Change final output file destination
        when:
        buildFile.text += '''
b.outputFile = file('new-output.txt')
'''
        succeeds "b"

        then:
        skippedTasks as List ==  [":a"]
        nonSkippedTasks as List ==  [":b"]

        when:
        outputFileB = file('new-output.txt')
        then:
        outputFileB.assertIsFile()

        when:
        // Run with --rerun-tasks command-line options
        succeeds "b", "--rerun-tasks"

        then:
        nonSkippedTasks as List ==  [":a", ":b"]

        // Output files already exist before using this version of Gradle
        // delete .gradle dir to simulate this
        when:
        file('.gradle').assertIsDir().deleteDir()
        outputFileA.makeOlder()
        outputFileB.makeOlder()
        succeeds "b"

        then:
        nonSkippedTasks as List ==  [":a", ":b"]

        when:
        outputFileB.delete()
        succeeds "b"

        then:
        nonSkippedTasks as List ==  [":b"]
        skippedTasks as List ==  [":a"]

        when:
        succeeds "b"

        then:
        skippedTasks as List ==  [":a", ":b"]
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
        nonSkippedTasks as List ==  [":a", ":b"]

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
        skippedTasks as List ==  [":a", ":b"]
        outputAFile.assertHasNotChangedSince(aSnapshot)
        outputBFile.assertHasNotChangedSince(bSnapshot)

        // Change content
        when:
        file('src/file1.txt').text = 'new content'
        succeeds "b"

        then:
        nonSkippedTasks as List ==  [":a", ":b"]

        outputAFile.assertHasChangedSince(aSnapshot)
        outputBFile.assertHasChangedSince(bSnapshot)
        outputAFile.assertContents(equalTo('[new content]'))
        outputBFile.assertContents(equalTo('[[new content]]'))

        // Add file
        when:
        file('src/file2.txt').text = 'content2'
        succeeds "b"

        then:
        nonSkippedTasks as List ==  [":a", ":b"]

        file('build/a/file2.txt').text == '[content2]'
        file('build/b/file2.txt').text == '[[content2]]'

        // Remove file
        when:
        file('src/file1.txt').delete()
        succeeds "b"

        then:
        nonSkippedTasks as List ==  [":a"]
        skippedTasks as List ==  [":b"]

        // Output files already exist before using this version of Gradle
        // delete .gradle dir to simulate this
        when:
        file('.gradle').assertIsDir().deleteDir()
        outputAFile.makeOlder()
        outputBFile.makeOlder()
        succeeds "b"

        then:
        nonSkippedTasks as List ==  [":a", ":b"]

        when:
        file('build/b').deleteDir()
        succeeds "b"

        then:
        nonSkippedTasks as List ==  [":b"]
        skippedTasks as List ==  [":a"]

        when:
        succeeds "b"

        then:
        skippedTasks as List ==  [":a", ":b"]
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
        nonSkippedTasks as List ==  [":a"]

        when:
        succeeds "a", "-Ptext=text"

        then:
        skippedTasks as List ==  [":a"]

        when:
        succeeds "a", "-Ptext=newtext"

        then:
        nonSkippedTasks as List ==  [":a"]
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
        nonSkippedTasks as List ==  [":a", ":b"]

        // No changes
        when:
        succeeds "a", "b"

        then:
        skippedTasks as List ==  [":a", ":b"]

        // Delete an output file
        when:
        file('build/file1.txt').delete()
        succeeds "a", "b"

        then:
        nonSkippedTasks as List ==  [":a"]
        skippedTasks as List ==  [":b"]

        // Change an output file
        when:
        file('build/file2.txt').text = 'something else'
        succeeds "a", "b"

        then:
        nonSkippedTasks as List ==  [":b"]
        skippedTasks as List ==  [":a"]

        // Output files already exist before using this version of Gradle
        // Simulate this by removing the .gradle dir
        when:
        file('.gradle').assertIsDir().deleteDir()
        file('build/file1.txt').makeOlder()
        file('build/file2.txt').makeOlder()
        succeeds "a", "b"

        then:
        nonSkippedTasks as List ==  [":a", ":b"]

        when:
        file('build').deleteDir()
        succeeds "a"

        then:
        nonSkippedTasks as List ==  [":a"]

        when:
        succeeds "b"

        then:
        nonSkippedTasks as List ==  [":b"]
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
        nonSkippedTasks as List ==  [":inputsAndOutputs"]

        // Is up to date

        when:
        succeeds "inputsAndOutputs", '-Puptodate'

        then:
        skippedTasks as List ==  [":inputsAndOutputs"]

        // Changed input file
        when:
        srcFile.text = 'different'
        succeeds "inputsAndOutputs", '-Puptodate'

        then:
        nonSkippedTasks as List ==  [":inputsAndOutputs"]

        // Predicate is false
        when:
        succeeds "inputsAndOutputs"

        then:
        nonSkippedTasks as List ==  [":inputsAndOutputs"]

        // Task with input files and a predicate
        when:
        succeeds "noOutputs"

        then:
        nonSkippedTasks as List ==  [":noOutputs"]

        // Is up to date
        when:
        succeeds "noOutputs", "-Puptodate"

        then:
        skippedTasks as List ==  [":noOutputs"]

        // Changed input file
        when:
        srcFile.text = 'different again'
        succeeds "noOutputs", "-Puptodate"

        then:
        nonSkippedTasks as List ==  [":noOutputs"]

        // Predicate is false
        when:
        succeeds "noOutputs"

        then:
        nonSkippedTasks as List ==  [":noOutputs"]

        // Task a predicate only
        when:
        succeeds "nothing"

        then:
        nonSkippedTasks as List ==  [":nothing"]

        // Is up to date
        when:
        succeeds "nothing", "-Puptodate"

        then:
        skippedTasks as List ==  [":nothing"]

        // Predicate is false
        when:
        succeeds "nothing"

        then:
        nonSkippedTasks as List ==  [":nothing"]
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
        nonSkippedTasks as List ==  [":a", ":b"]

        when:
        succeeds "b"
        then:
        skippedTasks as List ==  [":a", ":b"]
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
        nonSkippedTasks as List ==  [":otherBuild", ":build:generate", ':transform']

        when:
        succeeds "transform"
        then:
        nonSkippedTasks as List ==  [":otherBuild"]
        skippedTasks as List ==  [":transform", ":build:generate"]
    }
}
