/*
 * Copyright 2021 the original author or authors.
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

class SkipWhenEmptyIntegrationTest extends AbstractIntegrationSpec {

    def "SkipWhenEmpty reports empty sources for #description"() {
        def emptyDirectory = file("emptyDir").createDir()
        def emptyZip = file("emptyZip.zip")
        def emptyTar = file("emptyTar.tar")
        emptyDirectory.zipTo(emptyZip)
        emptyDirectory.tarTo(emptyTar)
        buildFile << sourceTask
        buildFile << """
            tasks.register("sourceTask", MySourceTask) {
                sources.setFrom(${inputDeclaration})
                outputFile.set(file("build/output.txt"))
            }
        """

        when:
        run "sourceTask"
        then:
        skipped(":sourceTask")

        where:
        description                           | inputDeclaration
        "empty file collection"               | "files()"
        "empty directory as file tree"        | "fileTree(file('emptyDir'))"
        "empty zip tree"                      | "zipTree(file('emptyZip.zip'))"
        "empty tar tree"                      | "tarTree(file('emptyTar.tar'))"
        "empty directory"                     | "files('emptyDir')"
    }

    def "SkipWhenEmpty does not skip for #description"() {
        def inputDirectory = file("inputDir").createDir()
        inputDirectory.file("inputFile").createFile()
        def inputZip = file("inputZip.zip")
        def inputTar = file("inputTar.tar")
        inputDirectory.zipTo(inputZip)
        inputDirectory.tarTo(inputTar)
        buildFile << sourceTask
        buildFile << """
            tasks.register("sourceTask", MySourceTask) {
                sources.setFrom(${inputDeclaration})
                outputFile.set(file("build/output.txt"))
            }
        """

        when:
        run "sourceTask"
        then:
        executedAndNotSkipped(":sourceTask")

        where:
        description                                     | inputDeclaration
        "directory containing files as file collection" | "files('inputDir')"
        "directory containing files as file tree"       | "fileTree(file('inputDir'))"
        "zip tree with files"                           | "zipTree(file('inputZip.zip'))"
        "tar tree with files"                           | "tarTree(file('inputTar.tar'))"
    }

    private String getSourceTask() {
        """
            import java.nio.file.Files

            abstract class MySourceTask extends DefaultTask {
                @SkipWhenEmpty @IgnoreEmptyDirectories
                @InputFiles
                abstract ConfigurableFileCollection getSources()

                @OutputFile
                abstract RegularFileProperty getOutputFile()

                @TaskAction
                void doStuff() {
                    println("Running...")
                    outputFile.get().asFile.text = "executed"
                }
            }
        """
    }
}
