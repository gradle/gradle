/*
 * Copyright 2019 the original author or authors.
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
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import org.gradle.util.internal.TextUtil

@Requires(UnitTestPreconditions.Windows)
class SubstIntegrationTest extends AbstractIntegrationSpec {
    def "up to date check works from filesystem's root - input folder to output file"() {
        def drive = 'X:'
        def root = substRoot(drive)

        def inputFileName = "input.txt"
        root.file(inputFileName) << 'content'

        def outputFile = file("output.txt")

        def taskName = 'inputFromFilesystemRoot'
        def script = """
            class InputDirectoryContentToOutputFileAction extends DefaultTask {
                @InputDirectory File inputDirectory
                @OutputFile File output

                @TaskAction def execute() {
                    output.text = inputDirectory.list().join()
                }
            }

            task ${taskName}(type: InputDirectoryContentToOutputFileAction) {
                inputDirectory = new File("${drive}\\\\")
                output = file("${TextUtil.escapeString(outputFile.absolutePath)}")
            }
        """
        when:
        buildScript script

        then:
        succeeds taskName
        outputFile.text.contains inputFileName

        cleanup:
        cleanupSubst(drive)
    }

    def "up to date check works from filesystem's root - input file to output folder copy"() {
        def drive = 'Y:'
        substRoot(drive)

        def inputFileName = "input.txt"
        def inputFile = file(inputFileName)
        inputFile << 'content'

        def outputDirectory = new File(drive)

        def taskName = 'outputFromFilesystemRoot'
        def script = """
            class InputFileToOutputDirectoryCopyAction extends DefaultTask {
                @InputFile File input
                @OutputDirectory File outputDirectory

                @TaskAction def execute() {
                    new File(outputDirectory, "${inputFileName}") << input.text
                }
            }

            task ${taskName}(type: InputFileToOutputDirectoryCopyAction) {
                input = file("${TextUtil.escapeString(inputFile.absolutePath)}")
                outputDirectory = new File("${drive}\\\\")
            }
        """
        when:
        buildScript script

        then:
        succeeds taskName
        outputDirectory.list().contains inputFileName

        cleanup:
        cleanupSubst(drive)
    }

    private substRoot(String drive) {
        assert !isPresent(drive): "Drive ${drive} already in use!"
        def substRoot = temporaryFolder.createDir("root").createDir()
        ['subst', drive, substRoot].execute().waitForProcessOutput()
        assert isPresent(drive): "Can't subst ${drive}!"
        substRoot
    }

    static isPresent(String drive) {
        File.listRoots().any { "${it}".toUpperCase().startsWith(drive.toUpperCase()) }
    }

    def cleanupSubst(String drive) {
        ['subst', '/d', drive].execute()
    }
}
