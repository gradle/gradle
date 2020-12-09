/*
 * Copyright 2020 the original author or authors.
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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.IgnoreRest
import spock.lang.Unroll


class ArchiveOperationsIntegrationTest extends AbstractIntegrationSpec {

    @IgnoreRest
    @Unroll
    def "can read #archiveType resources in task action"() {

        given:
        file("inputs/file.txt") << "some text"
        buildFile << """

            ${compressTaskDeclaration(archiveType)}

            abstract class MyTask extends DefaultTask {
                @InputFile abstract RegularFileProperty getInputFile()
                @OutputFile abstract RegularFileProperty getOutputFile()
                @Inject abstract ArchiveOperations getArchives()
                @TaskAction def action() {
                    def outFile = outputFile.get().asFile
                    outFile.parentFile.mkdirs()
                    outFile.text = archives.${archiveType}(inputFile.get().asFile).read().text
                }
            }

            tasks.register("myTask", MyTask) {
                inputFile.set(createArchive.flatMap { it.outputFile })
                outputFile.set(layout.buildDirectory.file("unpacked/file.txt"))
            }
        """

        when:
        run "myTask"

        then:
        file("build/unpacked/file.txt").isFile()

        where:
        archiveType << ['gzip', 'bzip2']
    }

    @IgnoreRest
    @Unroll
    def "can read #archiveType resources in #isolation isolation worker action"() {

        given:
        file("inputs/file.txt") << "some text"
        buildFile << """

            ${compressTaskDeclaration(archiveType)}

            interface MyParameters extends WorkParameters {
                RegularFileProperty getCompressedFile()
                RegularFileProperty getTextFile()
            }

            abstract class MyWork implements WorkAction<MyParameters> {
                @Inject abstract ArchiveOperations getArchives()
                @Override void execute() {
                    def outFile = parameters.textFile.get().asFile
                    outFile.parentFile.mkdirs()
                    outFile.text = archives.${archiveType}(parameters.compressedFile.get().asFile).read().text
                }
            }

            abstract class MyTask extends DefaultTask {
                @InputFile abstract RegularFileProperty getInputFile()
                @OutputFile abstract RegularFileProperty getOutputFile()
                @Inject abstract WorkerExecutor getWorkerExecutor()
                @TaskAction void action() {
                    workerExecutor.${isolation}Isolation().submit(MyWork) { parameters ->
                        parameters.compressedFile.set(inputFile)
                        parameters.textFile.set(outputFile)
                    }
                }
            }

            tasks.register("myTask", MyTask) {
                inputFile.set(createArchive.flatMap { it.outputFile })
                outputFile.set(layout.buildDirectory.file("unpacked/file.txt"))
            }
        """

        when:
        run "myTask"

        then:
        file("build/unpacked/file.txt").isFile()

        where:
        archiveType | isolation
        'gzip'      | 'no'
        'bzip2'     | 'no'
        'gzip'      | 'classLoader'
        'bzip2'     | 'classLoader'
    }

    private static String compressTaskDeclaration(String archiveType) {
        return """
            abstract class ${archiveType.capitalize()} extends DefaultTask {
                @InputFile abstract RegularFileProperty getInputFile()
                @OutputFile abstract RegularFileProperty getOutputFile()
                @TaskAction def action() {
                    ant.${archiveType}(src: inputFile.get().asFile.absolutePath, destfile: outputFile.get().asFile.absolutePath)
                }
            }

            def createArchive = tasks.register("createArchive", ${archiveType.capitalize()}) {
                inputFile.set(layout.projectDirectory.file("inputs/file.txt"))
                outputFile.set(layout.buildDirectory.file("archive.${archiveType}"))
            }
        """
    }

    @Unroll
    def "can create readonly FileTree for #archiveType archive in task action"() {

        given:
        file("inputs/file.txt") << "some text"
        buildFile << """
            def createArchive = tasks.register("createArchive", ${archiveType.capitalize()}) {
                destinationDirectory.set(layout.buildDirectory.dir("archives"))
                archiveFileName.set("archive.$archiveType")
                from("inputs")
            }

            abstract class MyTask extends DefaultTask {
                @InputFile abstract RegularFileProperty getArchiveFile()
                @OutputDirectory abstract DirectoryProperty getUnpackDir()
                @Inject abstract FileSystemOperations getFs()
                @Inject abstract ArchiveOperations getArchives()
                @TaskAction void action() {
                    fs.copy {
                        from(archives.${archiveType}Tree(archiveFile))
                        into(unpackDir)
                    }
                }
            }

            tasks.register("myTask", MyTask) {
                archiveFile = createArchive.flatMap { it.archiveFile }
                unpackDir = layout.buildDirectory.dir("unpacked")
            }
        """

        when:
        run "myTask"

        then:
        file("build/unpacked/file.txt").isFile()

        where:
        archiveType << ['zip', 'tar']
    }

    @Unroll
    def "can create readonly FileTree for #archiveType archive in #isolation isolation worker action"() {

        given:
        file("inputs/file.txt") << "some text"
        buildFile << """
            def createArchive = tasks.register("createArchive", ${archiveType.capitalize()}) {
                destinationDirectory.set(layout.buildDirectory.dir("archives"))
                archiveFileName.set("archive.$archiveType")
                from("inputs")
            }

            interface MyParameters extends WorkParameters {
                RegularFileProperty getArchive()
                DirectoryProperty getUnpackDir()
            }

            abstract class MyWork implements WorkAction<MyParameters> {
                @Inject abstract FileSystemOperations getFs()
                @Inject abstract ArchiveOperations getArchives()
                @Override void execute() {
                    fs.copy {
                        from(archives.${archiveType}Tree(parameters.archive))
                        into(parameters.unpackDir)
                    }
                }
            }

            abstract class MyTask extends DefaultTask {
                @InputFile abstract RegularFileProperty getArchiveFile()
                @OutputDirectory abstract DirectoryProperty getUnpackDirectory()
                @Inject abstract WorkerExecutor getWorkerExecutor()
                @TaskAction void action() {
                    workerExecutor.${isolation}Isolation().submit(MyWork) { parameters ->
                        parameters.archive.set(archiveFile)
                        parameters.unpackDir.set(unpackDirectory)
                    }
                }
            }

            tasks.register("myTask", MyTask) {
                archiveFile = createArchive.flatMap { it.archiveFile }
                unpackDirectory = layout.buildDirectory.dir("unpacked")
            }
        """

        when:
        run "myTask"

        then:
        file("build/unpacked/file.txt").isFile()

        where:
        archiveType | isolation
        'zip'       | 'no'
        'tar'       | 'no'
        'zip'       | 'classLoader'
        'tar'       | 'classLoader'
    }
}
