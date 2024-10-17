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

package org.gradle.api.file

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class FileCollectionPropertyIntegrationTest extends AbstractIntegrationSpec {
    def "task #annotation file property is implicitly finalized when task starts execution"() {
        buildFile << """
            abstract class SomeTask extends DefaultTask {
                ${annotation}
                ${propertyImpl}

                @TaskAction
                void go() {
                    println "value: " + prop.get()
                }
            }

            task show(type: SomeTask) {
                def layout = project.layout
                prop = [layout.projectDir.file("in.txt")]
                doFirst {
                    prop.set([layout.projectDir.file("other.txt")])
                }
            }
"""
        file("in.txt").createFile()

        when:
        fails("show")

        then:
        failure.assertHasDescription("Execution failed for task ':show'.")
        failure.assertHasCause("The value for task ':show' property 'prop' is final and cannot be changed any further.")

        where:
        [annotation, propertyImpl] << [
            [
                "@InputFiles",
                "@OutputFiles"
            ],
            [
                'final SetProperty<RegularFile> prop = project.objects.setProperty(RegularFile)',
                'abstract SetProperty<RegularFile> getProp()'
            ]
        ].combinations()
    }

    def "task @OutputDirectories directory property is implicitly finalized when task starts execution"() {
        buildFile """
            class SomeTask extends DefaultTask {
                @OutputDirectories
                final SetProperty<Directory> prop = project.objects.setProperty(Directory)

                @TaskAction
                void go() {
                    println "value: " + prop.get()
                }
            }

            task show(type: SomeTask) {
                def layout = project.layout
                prop = [layout.projectDir.dir("out.dir")]
                doFirst {
                    prop.set([layout.projectDir.dir("other.dir")])
                }
            }
        """

        when:
        fails("show")

        then:
        failure.assertHasDescription("Execution failed for task ':show'.")
        failure.assertHasCause("The value for task ':show' property 'prop' is final and cannot be changed any further.")
    }

    def "UPGRADED task #annotation file property is LENIENTLY implicitly finalized when task starts execution UNTIL NEXT MAJOR"() {
        executer.requireOwnGradleUserHomeDir("temp")
        buildFile << """
            import org.gradle.internal.instrumentation.api.annotations.ReplacesEagerProperty

            abstract class SomeTask extends DefaultTask {
                @ReplacesEagerProperty
                ${annotation}
                ${propertyImpl}

                @TaskAction
                void go() {
                    println "value: " + prop.get()
                }
            }

            task show(type: SomeTask) {
                def layout = project.layout
                prop = [layout.projectDir.$fileMethod("in")]
                doFirst {
                    prop.set([layout.projectDir.$fileMethod("other")])
                }
            }
"""

        expect:
        executer.expectDeprecationWarningWithPattern("Changing property value of task ':show' property 'prop' at execution time. This behavior has been deprecated.*")
        succeeds("show")
        outputContains("value: [${file('other')}]")

        where:
        annotation           | propertyImpl                                  | fileMethod
        "@InputFiles"        | "abstract SetProperty<RegularFile> getProp()" | 'file'
        "@OutputFiles"       | "abstract SetProperty<RegularFile> getProp()" | 'file'
        "@OutputDirectories" | "abstract SetProperty<Directory> getProp()"   | 'dir'
    }

    def "can wire the output file of multiple tasks as input to another task using property"() {
        buildFile """
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

    def "can wire the output files of a task as input to another task"() {
        buildFile """
            class FileOutputTask extends DefaultTask {
                @InputFile
                final RegularFileProperty inputFile = project.objects.fileProperty()
                @OutputFiles
                final SetProperty<RegularFile> outputFiles = project.objects.setProperty(RegularFile)

                @TaskAction
                void go() {
                    def content = inputFile.get().asFile.text
                    outputFiles.get().each { outputFile ->
                        outputFile.asFile.text = content
                    }
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

            task createFiles(type: FileOutputTask)
            task merge(type: MergeTask) {
                outputFile = layout.buildDirectory.file("merged.txt")
                inputFiles.set(createFiles.outputFiles)
            }

            // Set values lazily
            createFiles.inputFile = layout.projectDirectory.file("file-source.txt")
            createFiles.outputFiles.add(layout.buildDirectory.file("file1.txt"))
            createFiles.outputFiles.add(layout.buildDirectory.file("file2.txt"))

            buildDir = "output"
"""
        file("file-source.txt").text = "file1"

        when:
        run("merge")

        then:
        result.assertTasksExecuted(":createFiles", ":merge")
        file("output/merged.txt").text == 'file1,file1'

        when:
        run("merge")

        then:
        result.assertTasksNotSkipped()

        when:
        file("file-source.txt").text = "new-file1"
        run("merge")

        then:
        result.assertTasksNotSkipped(":createFiles", ":merge")
        file("output/merged.txt").text == 'new-file1,new-file1'
    }

    def "can wire the output directory of multiple tasks as input to another task using property"() {
        buildFile """
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
                final SetProperty<Directory> inputDirs = project.objects.setProperty(Directory)
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

    def "can wire the output directories of a task as input to another task"() {
        buildFile """
            class DirOutputTask extends DefaultTask {
                @InputFile
                final RegularFileProperty inputFile = project.objects.fileProperty()

                @OutputDirectories
                final SetProperty<Directory> outputDirs = project.objects.setProperty(Directory)

                @TaskAction
                void go() {
                    def content = inputFile.get().asFile.text
                    outputDirs.get().each { outputDir ->
                         new File(outputDir.asFile, "file.txt").text = content
                    }
                }
            }

            class MergeTask extends DefaultTask {
                @InputFiles
                final SetProperty<Directory> inputDirs = project.objects.setProperty(Directory)
                @OutputFile
                final RegularFileProperty outputFile = project.objects.fileProperty()

                @TaskAction
                void go() {
                    def file = outputFile.asFile.get()
                    file.text = inputDirs.get()*.asFile*.listFiles().text.flatten().join(',')
                }
            }

            task createDirs(type: DirOutputTask)
            task merge(type: MergeTask) {
                outputFile = layout.buildDirectory.file("merged.txt")
                inputDirs.set(createDirs.outputDirs)
            }

            // Set values lazily
            createDirs.inputFile = layout.projectDirectory.file("dir-source.txt")
            createDirs.outputDirs.add(layout.buildDirectory.dir("dir1"))
            createDirs.outputDirs.add(layout.buildDirectory.dir("dir2"))

            buildDir = "output"
"""
        file("dir-source.txt").text = "dir1"

        when:
        run("merge")

        then:
        result.assertTasksExecuted(":createDirs", ":merge")
        file("output/merged.txt").text == 'dir1,dir1'

        when:
        run("merge")

        then:
        result.assertTasksNotSkipped()

        when:
        file("dir-source.txt").text = "new-dir1"
        run("merge")

        then:
        result.assertTasksNotSkipped(":createDirs", ":merge")
        file("output/merged.txt").text == 'new-dir1,new-dir1'
    }

    def "can wire a set of output files modelled using a project level property as input to a task"() {
        buildFile """
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

            def generatedFiles = objects.setProperty(RegularFile)

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

    def "can wire a set of output directories modelled as a project level property as input to a task"() {
        buildFile """
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
                final SetProperty<Directory> inputDirs = project.objects.setProperty(Directory)
                @OutputFile
                final RegularFileProperty outputFile = project.objects.fileProperty()

                @TaskAction
                void go() {
                    def file = outputFile.asFile.get()
                    file.text = inputDirs.get()*.asFile*.listFiles().text.flatten().join(',')
                }
            }

            def generatedFiles = objects.setProperty(Directory)

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
}
