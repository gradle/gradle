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
import spock.lang.Unroll


class ArchiveOperationsIntegrationTest extends AbstractIntegrationSpec {

    @Unroll
    def "can create readonly FileTree for #archiveType archive in task action"() {

        given:
        file("inputs/file.txt") << "some text"
        buildFile << """
            import javax.inject.Inject

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
            import javax.inject.Inject

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
