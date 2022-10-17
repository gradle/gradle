/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.file

import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.CompileClasspath
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.internal.reflect.problems.ValidationProblemId
import org.gradle.internal.reflect.validation.ValidationMessageChecker
import org.gradle.internal.reflect.validation.ValidationTestFor
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import spock.lang.Issue

import static org.gradle.util.internal.TextUtil.escapeString
import static org.gradle.work.ChangeType.ADDED
import static org.gradle.work.ChangeType.REMOVED

@Requires(TestPrecondition.SYMLINKS)
class FileCollectionSymlinkIntegrationTest extends AbstractIntegrationSpec implements ValidationMessageChecker {
    def setup() {
        expectReindentedValidationMessage()
    }

    def "#desc can handle symlinks"() {
        def buildScript = file("build.gradle")
        def baseDir = file('build')
        baseDir.file('file').text = 'some contents'
        def symlinked = baseDir.file('symlinked')
        symlinked.text = 'target of symlink'
        baseDir.file('symlink').createLink(symlinked)

        buildScript << """
            def baseDir = new File("${escapeString(baseDir)}")
            def file = new File(baseDir, "file")
            def symlink = new File(baseDir, "symlink")
            def symlinked = new File(baseDir, "symlinked")
            def fileCollection = $code

            assert fileCollection.contains(file)
            assert fileCollection.contains(symlink)
            assert fileCollection.contains(symlinked)
            assert fileCollection.files == [file, symlink, symlinked] as Set
            assert (fileCollection - project.layout.files(symlink)).files == [file, symlinked] as Set
        """

        when:
        run()

        then:
        noExceptionThrown()

        where:
        desc                                 | code
        "project.files()"                    | "project.files(file, symlink, symlinked)"
        "project.fileTree()"                 | "project.fileTree(baseDir)"
        "project.layout.files()"             | "project.layout.files(file, symlink, symlinked)"
        "project.objects.fileCollection()"   | "project.objects.fileCollection().from(file, symlink, symlinked)"
    }

    @Issue("https://github.com/gradle/gradle/issues/1365")
    def "detect changes to broken symlink outputs in OutputDirectory"() {
        def root = file("root").createDir()
        def target = file("target")
        def link = root.file("link")
        buildFile << """
            import java.nio.file.*
            class ProducesLink extends DefaultTask {
                @OutputDirectory Path outputDirectory

                @TaskAction execute() {
                    def link = Paths.get('${link}')
                    Files.deleteIfExists(link);
                    Files.createSymbolicLink(link, Paths.get('${target}'));
                }
            }

            task producesLink(type: ProducesLink) {
                outputDirectory =  Paths.get('${root}')
            }
        """

        when:
        target.createFile()
        run 'producesLink'
        then:
        executedAndNotSkipped ':producesLink'

        when:
        run 'producesLink'
        then:
        skipped ':producesLink'

        when:
        target.delete()
        run 'producesLink'
        then:
        executedAndNotSkipped ':producesLink'

        when:
        run 'producesLink'
        then:
        skipped ':producesLink'

        when:
        target.createFile()
        run 'producesLink'
        then:
        executedAndNotSkipped ':producesLink'

        when:
        target.delete()
        target.createDir()
        run 'producesLink'
        then:
        executedAndNotSkipped ':producesLink'
    }

    @Issue("https://github.com/gradle/gradle/issues/1365")
    def "detect changes to broken symlink outputs in OutputFile"() {
        def root = file("root").createDir()
        def target = file("target")
        def link = root.file("link")
        buildFile << """
            import java.nio.file.*
            class ProducesLink extends DefaultTask {
                @OutputFile Path outputFileLink

                @TaskAction execute() {
                    Files.deleteIfExists(outputFileLink);
                    Files.createSymbolicLink(outputFileLink, Paths.get('${target}'));
                }
            }

            task producesLink(type: ProducesLink) {
                outputFileLink = Paths.get('${link}')
            }
        """

        when:
        target.createFile()
        run 'producesLink'
        then:
        executedAndNotSkipped ':producesLink'

        when:
        run 'producesLink'
        then:
        skipped ':producesLink'

        when:
        target.delete()
        run 'producesLink'
        then:
        executedAndNotSkipped ':producesLink'

        when:
        run 'producesLink'
        then:
        skipped ':producesLink'

        when:
        target.createFile()
        run 'producesLink'
        then:
        executedAndNotSkipped ':producesLink'
    }

    @Issue('https://github.com/gradle/gradle/issues/1365')
    def "broken symlink not produced by task is ignored"() {
        given:
        def input = file("input.txt").createFile()
        def outputDirectory = file("output")

        def brokenLink = outputDirectory.file('link').createLink("broken")
        assert !brokenLink.exists()

        buildFile << """
            task copy(type: Copy) {
                from '${input.name}'
                into '${outputDirectory.name}'
            }
        """

        when:
        run 'copy'
        then:
        outputDirectory.list().sort() == [input.name, brokenLink.name].sort()
        executedAndNotSkipped ':copy'

        when:
        run 'copy'
        then:
        outputDirectory.list().sort() == [input.name, brokenLink.name].sort()
        skipped ':copy'

        when:
        brokenLink.delete()
        run 'copy'
        then:
        skipped ':copy'
        outputDirectory.list() == [input.name]
    }

    @Issue('https://github.com/gradle/gradle/issues/9904')
    def "task with broken symlink in InputDirectory is valid"() {
        def inputFileTarget = file("brokenInputFileTarget")
        def inputDirectoryWithBrokenLink = file('inputDirectoryWithBrokenLink')
        inputDirectoryWithBrokenLink.file('brokenInputFile').createLink(inputFileTarget)
        def output = file("output.txt")

        buildFile << """
            class CustomTask extends DefaultTask {
                @InputDirectory File inputDirectoryWithBrokenLink

                @OutputFile File output

                @TaskAction execute() {
                    output.text = inputDirectoryWithBrokenLink.list()
                }
            }
            task inputBrokenLinkNameCollector(type: CustomTask) {
                inputDirectoryWithBrokenLink = file '${inputDirectoryWithBrokenLink}'
                output = file '${output}'
            }
        """

        when:
        run 'inputBrokenLinkNameCollector'
        then:
        executedAndNotSkipped ':inputBrokenLinkNameCollector'
        output.text == "[brokenInputFile]"

        when:
        run 'inputBrokenLinkNameCollector'
        then:
        skipped ':inputBrokenLinkNameCollector'

        when:
        inputFileTarget.createFile()
        run 'inputBrokenLinkNameCollector'
        then:
        executedAndNotSkipped ':inputBrokenLinkNameCollector'

        when:
        run 'inputBrokenLinkNameCollector'
        then:
        skipped ':inputBrokenLinkNameCollector'
    }

    @Issue('https://github.com/gradle/gradle/issues/9904')
    def "task with broken symlink in InputFiles is valid"() {
        def inputFileTarget = file("brokenInputFileTarget")
        def brokenInputFile = file('brokenInputFile').createLink(inputFileTarget)
        def output = file("output.txt")

        buildFile << """
            class CustomTask extends DefaultTask {

                @InputFiles FileCollection brokenInputFiles

                @OutputFile File output

                @TaskAction execute() {
                    output.text = brokenInputFiles.files*.name
                }
            }
            task inputBrokenLinkNameCollector(type: CustomTask) {
                brokenInputFiles = files '${brokenInputFile}'
                output = file '${output}'
            }
        """

        when:
        run 'inputBrokenLinkNameCollector'
        then:
        executedAndNotSkipped ':inputBrokenLinkNameCollector'
        output.text == "[brokenInputFile]"

        when:
        run 'inputBrokenLinkNameCollector'
        then:
        skipped ':inputBrokenLinkNameCollector'

        when:
        inputFileTarget.createFile()
        run 'inputBrokenLinkNameCollector'
        then:
        executedAndNotSkipped ':inputBrokenLinkNameCollector'

        when:
        run 'inputBrokenLinkNameCollector'
        then:
        skipped ':inputBrokenLinkNameCollector'
    }

    @Issue('https://github.com/gradle/gradle/issues/9904')
    def "unbreaking a symlink in InputFiles is detected incrementally"() {
        def inputFileTarget = file("brokenInputFileTarget")
        def brokenInputFile = file('brokenInputFile')
        def output = file("output.txt")

        buildFile << """
            class CustomTask extends DefaultTask {
                @Incremental @InputFiles FileCollection brokenInputFiles

                @OutputFile File output

                @TaskAction execute(InputChanges changes) {
                    output.text = changes.getFileChanges(brokenInputFiles)*.changeType
                }
            }
            task inputBrokenLinkNameCollector(type: CustomTask) {
                brokenInputFiles = files '${brokenInputFile}'
                output = file '${output}'
            }
        """

        when:
        run 'inputBrokenLinkNameCollector'
        then:
        executedAndNotSkipped ':inputBrokenLinkNameCollector'
        output.text == "[]"

        when:
        brokenInputFile.createLink(inputFileTarget)
        run 'inputBrokenLinkNameCollector'
        then:
        skipped ':inputBrokenLinkNameCollector'

        when:
        inputFileTarget.createFile()
        run 'inputBrokenLinkNameCollector'
        then:
        executedAndNotSkipped ':inputBrokenLinkNameCollector'
        output.text == "${[ADDED]}"

        when:
        run 'inputBrokenLinkNameCollector'
        then:
        skipped ':inputBrokenLinkNameCollector'

        when:
        inputFileTarget.delete()
        run 'inputBrokenLinkNameCollector'
        then:
        executedAndNotSkipped ':inputBrokenLinkNameCollector'
        output.text == "${[REMOVED]}"
    }

    @ValidationTestFor(
        ValidationProblemId.INPUT_FILE_DOES_NOT_EXIST
    )
    def "broken symlink in #inputType.simpleName fails validation"() {
        def brokenInputFile = file('brokenInput').createLink("brokenInputFileTarget")
        buildFile << """
            class CustomTask extends DefaultTask {
                @${inputType.simpleName} File brokenInputFile

                @TaskAction execute() {}
            }
            task brokenInput(type: CustomTask) {
                brokenInputFile = file '${brokenInputFile}'
            }
        """

        when:
        fails 'brokenInput'

        then:
        failureDescriptionContains(inputDoesNotExist {
            type('CustomTask')
            property('brokenInputFile')
            kind(inputName)
            missing(brokenInputFile)
            includeLink()
        })

        where:
        inputName   | inputType
        "File"      | InputFile
        "Directory" | InputDirectory
    }

    @Issue('https://github.com/gradle/gradle/issues/9904')
    def "task with a broken #classpathType.simpleName root input is accepted"() {
        def brokenClasspathEntry = file('broken.jar').createLink("broken.jar.target")
        def output = file("output.txt")

        buildFile << """
            class CustomTask extends DefaultTask {
                @${classpathType.name} File classpath

                @OutputFile File output

                @TaskAction execute() {}
            }
            task brokenClasspathInput(type: CustomTask) {
                classpath = file '${brokenClasspathEntry}'
                output = file '${output}'
            }
        """
        assert !brokenClasspathEntry.exists()

        when:
        run 'brokenClasspathInput'
        then:
        executedAndNotSkipped ':brokenClasspathInput'

        where:
        classpathType << [Classpath, CompileClasspath]
    }

    @Issue('https://github.com/gradle/gradle/issues/9904')
    def "task with a broken #classpathType.simpleName directory input is accepted"() {
        def classes = file('classes').createDir()
        def brokenInputFile = classes.file('BrokenInputFile.class').createLink("BrokenInputFile.target")
        def output = file("output.txt")

        buildFile << """
            class CustomTask extends DefaultTask {
                @${classpathType.name} File classpath

                @OutputFile File output

                @TaskAction execute() {}
            }
            task brokenClasspathInput(type: CustomTask) {
                classpath = file '${classes}'
                output = file '${output}'
            }
        """
        assert !brokenInputFile.exists()

        when:
        fails 'brokenClasspathInput'
        then:
        failure.assertHasCause("Couldn't read file content: '${brokenInputFile}'.")

        where:
        classpathType << [Classpath, CompileClasspath]
    }

    @Issue('https://github.com/gradle/gradle/issues/9904')
    def "directory with broken symlink and @SkipWhenEmpty executes the task action"() {
        def root = file('root').createDir()
        def brokenInputFile = root.file('BrokenInputFile').createLink("BrokenInputFileTarget")

        buildFile << """
            class CustomTask extends DefaultTask {
                @InputDirectory @SkipWhenEmpty File directoryWithBrokenLink

                @TaskAction execute() {}
            }
            task brokenDirectoryWithSkipWhenEmpty(type: CustomTask) {
                directoryWithBrokenLink = file '${root}'
            }
        """
        assert !brokenInputFile.exists()

        when:
        run 'brokenDirectoryWithSkipWhenEmpty'
        then:
        executedAndNotSkipped(':brokenDirectoryWithSkipWhenEmpty')
    }
}
