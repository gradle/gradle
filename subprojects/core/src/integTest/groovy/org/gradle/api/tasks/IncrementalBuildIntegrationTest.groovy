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

import org.gradle.integtests.fixtures.AbstractIntegrationTest
import org.gradle.test.fixtures.file.TestFile
import org.junit.Test

import static org.hamcrest.Matchers.equalTo
import static org.junit.Assert.assertThat

class IncrementalBuildIntegrationTest extends AbstractIntegrationTest {

    private TestFile writeDirTransformerTask() {
        testFile('buildSrc/src/main/groovy/DirTransformerTask.groovy') << '''
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
        testFile('buildSrc/src/main/groovy/TransformerTask.groovy') << '''
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

    @Test
    public void skipsTaskWhenOutputFileIsUpToDate() {
        writeTransformerTask()

        testFile('build.gradle') << '''
task a(type: TransformerTask) {
    inputFile = file('src.txt')
    outputFile = file('src.a.txt')
}
task b(type: TransformerTask, dependsOn: a) {
    inputFile = a.outputFile
    outputFile = file('src.b.txt')
}
'''
        TestFile inputFile = testFile('src.txt')
        TestFile outputFileA = testFile('src.a.txt')
        TestFile outputFileB = testFile('src.b.txt')

        inputFile.text = 'content'

        inTestDirectory().withTasks('b').run().assertTasksExecuted(':a', ':b').assertTasksSkipped()

        TestFile.Snapshot aSnapshot = outputFileA.snapshot()
        TestFile.Snapshot bSnapshot = outputFileB.snapshot()
        assertThat(outputFileA.text, equalTo('[content]'))
        assertThat(outputFileB.text, equalTo('[[content]]'))

        // No changes

        inTestDirectory().withTasks('b').run().assertTasksExecuted(':a', ':b').assertTasksSkipped(':a', ':b')

        outputFileA.assertHasNotChangedSince(aSnapshot)
        outputFileB.assertHasNotChangedSince(bSnapshot)

        // Update timestamp, no content changes

        inputFile.makeOlder()

        inTestDirectory().withTasks('b').run().assertTasksExecuted(':a', ':b').assertTasksSkipped(':a', ':b')

        outputFileA.assertHasNotChangedSince(aSnapshot)
        outputFileB.assertHasNotChangedSince(bSnapshot)

        // Change content

        inputFile.text = 'new content'

        inTestDirectory().withTasks('b').run().assertTasksExecuted(':a', ':b').assertTasksSkipped()

        outputFileA.assertHasChangedSince(aSnapshot)
        outputFileB.assertHasChangedSince(bSnapshot)
        assertThat(outputFileA.text, equalTo('[new content]'))
        assertThat(outputFileB.text, equalTo('[[new content]]'))

        // Delete intermediate output file

        outputFileA.delete()

        inTestDirectory().withTasks('b').run().assertTasksExecuted(':a', ':b').assertTasksSkipped(':b')

        assertThat(outputFileA.text, equalTo('[new content]'))
        assertThat(outputFileB.text, equalTo('[[new content]]'))

        // Delete final output file

        outputFileB.delete()

        inTestDirectory().withTasks('b').run().assertTasksExecuted(':a', ':b').assertTasksSkipped(':a')

        assertThat(outputFileA.text, equalTo('[new content]'))
        assertThat(outputFileB.text, equalTo('[[new content]]'))

        // Change build file in a way which does not affect the task

        testFile('build.gradle').text += '''
task c
'''

        inTestDirectory().withTasks('b').run().assertTasksExecuted(':a', ':b').assertTasksSkipped(':a', ':b')

        // Change an input property of the first task (the content format)
        testFile('build.gradle').text += '''
a.format = '     %s     '
'''
        inTestDirectory().withTasks('b').run().assertTasksExecuted(':a', ':b').assertTasksSkipped()

        assertThat(outputFileA.text, equalTo('     new content     '))
        assertThat(outputFileB.text, equalTo('[     new content     ]'))

        // Change final output file destination
        testFile('build.gradle').text += '''
b.outputFile = file('new-output.txt')
'''

        inTestDirectory().withTasks('b').run().assertTasksExecuted(':a', ':b').assertTasksSkipped(':a')
        outputFileB = testFile('new-output.txt')
        outputFileB.assertIsFile()

        // Run with --rerun-tasks command-line options
        inTestDirectory().withTasks('b').withArguments('--rerun-tasks').run().assertTasksExecuted(':a', ':b').assertTasksSkipped()

        // Output files already exist before using this version of Gradle
        // delete .gradle dir to simulate this
        testFile('.gradle').assertIsDir().deleteDir()
        outputFileA.makeOlder()
        outputFileB.makeOlder()

        inTestDirectory().withTasks('b').run().assertTasksExecuted(':a', ':b').assertTasksSkipped()

        outputFileB.delete()
        inTestDirectory().withTasks('b').run().assertTasksExecuted(':a', ':b').assertTasksSkipped(':a')

        inTestDirectory().withTasks('b').run().assertTasksExecuted(':a', ':b').assertTasksSkipped(':a', ':b')
    }

    @Test
    public void skipsTaskWhenOutputDirContentsAreUpToDate() {
        writeDirTransformerTask()

        testFile('build.gradle') << '''
task a(type: DirTransformerTask) {
    inputDir = file('src')
    outputDir = file('build/a')
}
task b(type: DirTransformerTask, dependsOn: a) {
    inputDir = a.outputDir
    outputDir = file('build/b')
}
'''

        testFile('src').createDir()
        testFile('src/file1.txt').write('content')

        inTestDirectory().withTasks('b').run().assertTasksExecuted(':a', ':b').assertTasksSkipped()

        TestFile outputAFile = testFile('build/a/file1.txt')
        TestFile outputBFile = testFile('build/b/file1.txt')
        TestFile.Snapshot aSnapshot = outputAFile.snapshot()
        TestFile.Snapshot bSnapshot = outputBFile.snapshot()

        outputAFile.assertContents(equalTo('[content]'))
        outputBFile.assertContents(equalTo('[[content]]'))

        // No changes

        inTestDirectory().withTasks('b').run().assertTasksExecuted(':a', ':b').assertTasksSkipped(':a', ':b')

        outputAFile.assertHasNotChangedSince(aSnapshot)
        outputBFile.assertHasNotChangedSince(bSnapshot)

        // Change content

        testFile('src/file1.txt').write('new content')

        inTestDirectory().withTasks('b').run().assertTasksExecuted(':a', ':b').assertTasksSkipped()

        outputAFile.assertHasChangedSince(aSnapshot)
        outputBFile.assertHasChangedSince(bSnapshot)
        outputAFile.assertContents(equalTo('[new content]'))
        outputBFile.assertContents(equalTo('[[new content]]'))

        // Add file

        testFile('src/file2.txt').write('content2')

        inTestDirectory().withTasks('b').run().assertTasksExecuted(':a', ':b').assertTasksSkipped()

        testFile('build/a/file2.txt').assertContents(equalTo('[content2]'))
        testFile('build/b/file2.txt').assertContents(equalTo('[[content2]]'))

        // Remove file

        testFile('src/file1.txt').delete()

        inTestDirectory().withTasks('b').run().assertTasksExecuted(':a', ':b').assertTasksSkipped(':b')

        // Output files already exist before using this version of Gradle
        // delete .gradle dir to simulate this
        testFile('.gradle').assertIsDir().deleteDir()
        outputAFile.makeOlder()
        outputBFile.makeOlder()

        inTestDirectory().withTasks('b').run().assertTasksExecuted(':a', ':b').assertTasksSkipped()

        testFile('build/b').deleteDir()
        inTestDirectory().withTasks('b').run().assertTasksExecuted(':a', ':b').assertTasksSkipped(':a')

        inTestDirectory().withTasks('b').run().assertTasksExecuted(':a', ':b').assertTasksSkipped(':a', ':b')
    }

    @Test
    public void skipsTaskWhenInputPropertiesHaveNotChanged() {
        testFile('build.gradle') << '''
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

        inTestDirectory().withTasks('a').withArguments('-Ptext=text').run().assertTasksExecuted(':a').assertTasksSkipped()

        inTestDirectory().withTasks('a').withArguments('-Ptext=text').run().assertTasksExecuted(':a').assertTasksSkipped(':a')

        inTestDirectory().withTasks('a').withArguments('-Ptext=newtext').run().assertTasksExecuted(':a').assertTasksSkipped()
    }

    @Test
    public void multipleTasksCanGenerateIntoOverlappingOutputDirectories() {
        writeDirTransformerTask()

        testFile('build.gradle') << '''
task a(type: DirTransformerTask) {
    inputDir = file('src/a')
    outputDir = file('build')
}
task b(type: DirTransformerTask) {
    inputDir = file('src/b')
    outputDir = file('build')
}
'''

        testFile('src/a/file1.txt') << 'content'
        testFile('src/b/file2.txt') << 'content'

        inTestDirectory().withTasks('a', 'b').run().assertTasksExecuted(':a', ':b').assertTasksSkipped()

        // No changes

        inTestDirectory().withTasks('a', 'b').run().assertTasksExecuted(':a', ':b').assertTasksSkipped(':a', ':b')

        // Delete an output file

        testFile('build/file1.txt').delete()

        inTestDirectory().withTasks('a', 'b').run().assertTasksExecuted(':a', ':b').assertTasksSkipped(':b')

        // Change an output file

        testFile('build/file2.txt').write('something else')

        inTestDirectory().withTasks('a', 'b').run().assertTasksExecuted(':a', ':b').assertTasksSkipped(':a')

        // Output files already exist before using this version of Gradle
        // Simulate this by removing the .gradle dir
        testFile('.gradle').assertIsDir().deleteDir()
        testFile('build/file1.txt').makeOlder()
        testFile('build/file2.txt').makeOlder()

        inTestDirectory().withTasks('a', 'b').run().assertTasksExecuted(':a', ':b').assertTasksSkipped()

        testFile('build').deleteDir()

        inTestDirectory().withTasks('a').run().assertTasksExecuted(':a').assertTasksSkipped()
        inTestDirectory().withTasks('b').run().assertTasksExecuted(':b').assertTasksSkipped()
    }

    @Test
    public void canUseUpToDatePredicateToForceTaskToExecute() {
        testFile('build.gradle') << '''
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
        TestFile srcFile = testFile('src.txt')
        srcFile.text = 'content'

        // Task with input files, output files and a predicate
        inTestDirectory().withTasks('inputsAndOutputs').run().assertTasksExecuted(':inputsAndOutputs').assertTasksSkipped()

        // Is up to date
        inTestDirectory().withArguments('-Puptodate').withTasks('inputsAndOutputs').run().assertTasksExecuted(':inputsAndOutputs').assertTasksSkipped(':inputsAndOutputs')

        // Changed input file
        srcFile.text = 'different'
        inTestDirectory().withArguments('-Puptodate').withTasks('inputsAndOutputs').run().assertTasksExecuted(':inputsAndOutputs').assertTasksSkipped()

        // Predicate is false
        inTestDirectory().withTasks('inputsAndOutputs').run().assertTasksExecuted(':inputsAndOutputs').assertTasksSkipped()

        // Task with input files and a predicate
        inTestDirectory().withTasks('noOutputs').run().assertTasksExecuted(':noOutputs').assertTasksSkipped()

        // Is up to date
        inTestDirectory().withArguments('-Puptodate').withTasks('noOutputs').run().assertTasksExecuted(':noOutputs').assertTasksSkipped(':noOutputs')

        // Changed input file
        srcFile.text = 'different again'
        inTestDirectory().withArguments('-Puptodate').withTasks('noOutputs').run().assertTasksExecuted(':noOutputs').assertTasksSkipped()

        // Predicate is false
        inTestDirectory().withTasks('noOutputs').run().assertTasksExecuted(':noOutputs').assertTasksSkipped()

        // Task a predicate only
        inTestDirectory().withTasks('nothing').run().assertTasksExecuted(':nothing').assertTasksSkipped()

        // Is up to date
        inTestDirectory().withArguments('-Puptodate').withTasks('nothing').run().assertTasksExecuted(':nothing').assertTasksSkipped(':nothing')

        // Predicate is false
        inTestDirectory().withTasks('nothing').run().assertTasksExecuted(':nothing').assertTasksSkipped()
    }

    @Test
    public void lifecycleTaskIsUpToDateWhenAllDependenciesAreSkipped() {
        writeTransformerTask()

        testFile('build.gradle') << '''
task a(type: TransformerTask) {
    inputFile = file('src.txt')
    outputFile = file('out.txt')
}
task b(dependsOn: a)
'''

        testFile('src.txt').text = 'content'

        inTestDirectory().withTasks('b').run().assertTasksExecuted(':a', ':b').assertTasksSkipped()
        inTestDirectory().withTasks('b').run().assertTasksExecuted(':a', ':b').assertTasksSkipped(':a', ':b')
    }

    @Test
    public void canShareArtifactsBetweenBuilds() {
        writeTransformerTask()

        def buildFile = testFile('build.gradle') << '''
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
        testFile('settings.gradle') << 'rootProject.name = "build"'
        testFile('src.txt').text = 'content'

        usingBuildFile(buildFile).withTasks('transform').run().assertTasksExecuted(':otherBuild', ':build:generate', ':transform').assertTasksSkipped()
        usingBuildFile(buildFile).withTasks('transform').run().assertTasksExecuted(':otherBuild', ':build:generate', ':transform').assertTasksSkipped(':transform', ':build:generate')
    }
}
