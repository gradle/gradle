/*
 * Copyright 2018 the original author or authors.
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


class FileSetPropertiesIntegrationTest extends AbstractIntegrationSpec {
    @Unroll
    def "can wire the output file of multiple tasks as input to another task using property created by #outputFileMethod"() {
        buildFile << """
            class FileOutputTask extends DefaultTask {
                @InputFile
                final RegularFileProperty inputFile = project.objects.fileProperty()
                @OutputFile
                final RegularFileProperty outputFile = ${outputFileMethod}
                
                @TaskAction
                void go() {
                    def file = outputFile.asFile.get()
                    file.text = inputFile.asFile.get().text
                }
            }

            class MergeTask extends DefaultTask {
                @InputFiles
                final SetProperty<RegularFile> inputFiles = project.objects.setProperty(RegularFile).empty()
                @OutputFile
                final RegularFileProperty outputFile = project.objects.fileProperty()
                
                @TaskAction
                void go() {
                    def file = outputFile.asFile.get()
                    file.text = inputFiles.get()*.asFile.text.join(',')
                }
            }
            
            task createFile1(type: FileOutputTask)
            task createFile2(type: FileOutputTask)
            task merge(type: MergeTask) {
                outputFile = layout.buildDirectory.file("merged.txt")
                inputFiles.add(createFile1.outputFile)
                inputFiles.add(createFile2.outputFile)
            }

            // Set values lazily
            createFile1.inputFile = layout.projectDirectory.file("file1-source.txt")
            createFile1.outputFile = layout.buildDirectory.file("file1.txt")
            createFile2.inputFile = layout.projectDirectory.file("file2-source.txt")
            createFile2.outputFile = layout.buildDirectory.file("file2.txt")
            
            buildDir = "output"
"""
        file("file1-source.txt").text = "file1"
        file("file2-source.txt").text = "file2"
        expectDeprecated(deprecated)

        when:
        run("merge")

        then:
        result.assertTasksExecuted(":createFile1", ":createFile2", ":merge")
        file("output/merged.txt").text == 'file1,file2'

        when:
        run("merge")

        then:
        result.assertTasksNotSkipped()

        when:
        file("file1-source.txt").text = "new-file1"
        run("merge")

        then:
        result.assertTasksNotSkipped(":createFile1", ":merge")
        file("output/merged.txt").text == 'new-file1,file2'

        where:
        outputFileMethod                 | deprecated
        "newOutputFile()"                | 1
        "project.layout.fileProperty()"  | 1
        "project.objects.fileProperty()" | 0
    }

    @Unroll
    def "can wire the output directory of multiple tasks as input to another task using property created by #outputDirMethod"() {
        buildFile << """
            class DirOutputTask extends DefaultTask {
                @InputFile
                final RegularFileProperty inputFile = project.objects.fileProperty()

                @OutputDirectory
                final DirectoryProperty outputDir = ${outputDirMethod}

                @TaskAction
                void go() {
                    def dir = outputDir.asFile.get()
                    new File(dir, "file.txt").text = inputFile.asFile.get().text
                }
            }

            class MergeTask extends DefaultTask {
                @InputFiles
                final SetProperty<Directory> inputDirs = project.objects.setProperty(Directory).empty()
                @OutputFile
                final RegularFileProperty outputFile = project.objects.fileProperty()

                @TaskAction
                void go() {
                    def file = outputFile.asFile.get()
                    file.text = inputDirs.get()*.asFile*.listFiles().text.flatten().join(',')
                }
            }

            task createDir1(type: DirOutputTask)
            task createDir2(type: DirOutputTask)
            task merge(type: MergeTask) {
                outputFile = layout.buildDirectory.file("merged.txt")
                inputDirs.add(createDir1.outputDir)
                inputDirs.add(createDir2.outputDir)
            }

            // Set values lazily
            createDir1.inputFile = layout.projectDirectory.file("dir1-source.txt")
            createDir1.outputDir = layout.buildDirectory.dir("dir1")
            createDir2.inputFile = layout.projectDirectory.file("dir2-source.txt")
            createDir2.outputDir = layout.buildDirectory.dir("dir2")

            buildDir = "output"
"""
        file("dir1-source.txt").text = "dir1"
        file("dir2-source.txt").text = "dir2"

        when:
        expectDeprecated(deprecated)
        run("merge")

        then:
        result.assertTasksExecuted(":createDir1", ":createDir2", ":merge")
        file("output/merged.txt").text == 'dir1,dir2'

        when:
        run("merge")

        then:
        result.assertTasksNotSkipped()

        when:
        file("dir1-source.txt").text = "new-dir1"
        run("merge")

        then:
        result.assertTasksNotSkipped(":createDir1", ":merge")
        file("output/merged.txt").text == 'new-dir1,dir2'

        where:
        outputDirMethod                       | deprecated
        "newOutputDirectory()"                | 1
        "project.layout.directoryProperty()"  | 1
        "project.objects.directoryProperty()" | 0
    }

    def "can wire a set of output files as input to a task"() {
        buildFile << """
            class FileOutputTask extends DefaultTask {
                @InputFile
                final RegularFileProperty inputFile = project.objects.fileProperty()
                @OutputFile
                final RegularFileProperty outputFile = project.objects.fileProperty()
                
                @TaskAction
                void go() {
                    def file = outputFile.asFile.get()
                    file.text = inputFile.asFile.get().text
                }
            }

            class MergeTask extends DefaultTask {
                @InputFiles
                final SetProperty<RegularFile> inputFiles = project.objects.setProperty(RegularFile)
                @OutputFile
                final RegularFileProperty outputFile = project.objects.fileProperty()
                
                @TaskAction
                void go() {
                    def file = outputFile.asFile.get()
                    file.text = inputFiles.get()*.asFile.text.join(',')
                }
            }
            
            def generatedFiles = objects.setProperty(RegularFile).empty() 
            
            task createFile1(type: FileOutputTask)
            generatedFiles.add(createFile1.outputFile)
            
            task createFile2(type: FileOutputTask)
            generatedFiles.add(createFile2.outputFile)
            
            task merge(type: MergeTask) {
                outputFile = layout.buildDirectory.file("merged.txt")
                inputFiles = generatedFiles
            }

            // Set values lazily
            createFile1.inputFile = layout.projectDirectory.file("file1-source.txt")
            createFile1.outputFile = layout.buildDirectory.file("file1.txt")
            createFile2.inputFile = layout.projectDirectory.file("file2-source.txt")
            createFile2.outputFile = layout.buildDirectory.file("file2.txt")
            
            buildDir = "output"
"""
        file("file1-source.txt").text = "file1"
        file("file2-source.txt").text = "file2"

        when:
        run("merge")

        then:
        result.assertTasksExecuted(":createFile1", ":createFile2", ":merge")
        file("output/merged.txt").text == 'file1,file2'

        when:
        run("merge")

        then:
        result.assertTasksNotSkipped()

        when:
        file("file1-source.txt").text = "new-file1"
        run("merge")

        then:
        result.assertTasksNotSkipped(":createFile1", ":merge")
        file("output/merged.txt").text == 'new-file1,file2'
    }

    def "can wire a set of output directories as input to a task"() {
        buildFile << """
            class DirOutputTask extends DefaultTask {
                @InputFile
                final RegularFileProperty inputFile = project.objects.fileProperty()

                @OutputDirectory
                final DirectoryProperty outputDir = project.objects.directoryProperty()

                @TaskAction
                void go() {
                    def dir = outputDir.asFile.get()
                    new File(dir, "file.txt").text = inputFile.asFile.get().text
                }
            }

            class MergeTask extends DefaultTask {
                @InputFiles
                final SetProperty<Directory> inputDirs = project.objects.setProperty(Directory).empty()
                @OutputFile
                final RegularFileProperty outputFile = project.objects.fileProperty()

                @TaskAction
                void go() {
                    def file = outputFile.asFile.get()
                    file.text = inputDirs.get()*.asFile*.listFiles().text.flatten().join(',')
                }
            }

            def generatedFiles = objects.setProperty(Directory).empty() 

            task createDir1(type: DirOutputTask)
            generatedFiles.add(createDir1.outputDir)
            
            task createDir2(type: DirOutputTask)
            generatedFiles.add(createDir2.outputDir)
            
            task merge(type: MergeTask) {
                outputFile = layout.buildDirectory.file("merged.txt")
                inputDirs = generatedFiles
            }

            // Set values lazily
            createDir1.inputFile = layout.projectDirectory.file("dir1-source.txt")
            createDir1.outputDir = layout.buildDirectory.dir("dir1")
            createDir2.inputFile = layout.projectDirectory.file("dir2-source.txt")
            createDir2.outputDir = layout.buildDirectory.dir("dir2")

            buildDir = "output"
"""
        file("dir1-source.txt").text = "dir1"
        file("dir2-source.txt").text = "dir2"

        when:
        run("merge")

        then:
        result.assertTasksExecuted(":createDir1", ":createDir2", ":merge")
        file("output/merged.txt").text == 'dir1,dir2'

        when:
        run("merge")

        then:
        result.assertTasksNotSkipped()

        when:
        file("dir1-source.txt").text = "new-dir1"
        run("merge")

        then:
        result.assertTasksNotSkipped(":createDir1", ":merge")
        file("output/merged.txt").text == 'new-dir1,dir2'
    }

    def expectDeprecated(int count) {
        if (count > 0) {
            executer.beforeExecute {
                expectDeprecationWarnings(count)
            }
        }
    }
}
