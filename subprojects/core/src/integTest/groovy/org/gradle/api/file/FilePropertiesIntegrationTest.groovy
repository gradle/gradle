/*
 * Copyright 2017 the original author or authors.
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
import org.gradle.util.ToBeImplemented
import spock.lang.Unroll

class FilePropertiesIntegrationTest extends AbstractIntegrationSpec {
    def "can attach a calculated directory to task property"() {
        buildFile << """
            class SomeTask extends DefaultTask {
                final DirectoryProperty outputDir = project.layout.directoryProperty()
                
                @TaskAction
                void go() {
                    println "task output dir: " + outputDir.get() 
                }
            }
            
            ext.childDirName = "child"
            def t = tasks.create("show", SomeTask)
            t.outputDir = layout.buildDirectory.dir(providers.provider { childDirName })
            println "output dir before: " + t.outputDir.getOrNull()
            buildDir = "output/some-dir"
            childDirName = "other-child"
"""

        when:
        run("show")

        then:
        outputContains("output dir before: " + testDirectory.file("build/child"))
        outputContains("task output dir: " + testDirectory.file("output/some-dir/other-child"))
    }

    def "reports failure to set directory property value using incompatible type"() {
        given:
        buildFile << """
class SomeExtension {
    final Property<Directory> prop
    
    @javax.inject.Inject
    SomeExtension(ProjectLayout layout) {
        prop = layout.directoryProperty()
    }
}

extensions.create('custom', SomeExtension, layout)

task useIntTypeDsl {
    doLast {
        custom.prop = 123
    }
}

task useIntTypeApi {
    doLast {
        custom.prop.set(123)
    }
}

task useFileTypeDsl {
    doLast {
        custom.prop = layout.projectDirectory.file("build.gradle")
    }
}

task useFileProviderDsl {
    doLast {
        custom.prop = layout.buildDirectory.file("build.gradle")
    }
}

task useFileProviderApi {
    doLast {
        custom.prop.set(layout.buildDirectory.file("build.gradle"))
    }
}
"""

        when:
        fails("useIntTypeDsl")

        then:
        failure.assertHasDescription("Execution failed for task ':useIntTypeDsl'.")
        failure.assertHasCause("Cannot set the value of a property of type org.gradle.api.file.Directory using an instance of type java.lang.Integer.")

        when:
        fails("useIntTypeApi")

        then:
        failure.assertHasDescription("Execution failed for task ':useIntTypeApi'.")
        failure.assertHasCause("Cannot set the value of a property of type org.gradle.api.file.Directory using an instance of type java.lang.Integer.")

        when:
        fails("useFileTypeDsl")

        then:
        failure.assertHasDescription("Execution failed for task ':useFileTypeDsl'.")
        failure.assertHasCause("Cannot set the value of a property of type org.gradle.api.file.Directory using an instance of type org.gradle.api.internal.file.DefaultProjectLayout\$FixedFile.")

        when:
        fails("useFileProviderDsl")

        then:
        failure.assertHasDescription("Execution failed for task ':useFileProviderDsl'.")
        failure.assertHasCause("Cannot set the value of a property of type org.gradle.api.file.Directory using a provider of type org.gradle.api.file.RegularFile.")

        when:
        fails("useFileProviderApi")

        then:
        failure.assertHasDescription("Execution failed for task ':useFileProviderApi'.")
        failure.assertHasCause("Cannot set the value of a property of type org.gradle.api.file.Directory using a provider of type org.gradle.api.file.RegularFile.")
    }

    def "can attach a calculated file to task property"() {
        buildFile << """
            class SomeTask extends DefaultTask {
                final RegularFileProperty outputFile = project.layout.fileProperty()
                
                @TaskAction
                void go() {
                    println "task output file: " + outputFile.get() 
                }
            }
            
            ext.childDirName = "child"
            def t = tasks.create("show", SomeTask)
            t.outputFile = layout.buildDirectory.file(providers.provider { childDirName })
            println "output file before: " + t.outputFile.getOrNull()
            buildDir = "output/some-dir"
            childDirName = "other-child"
"""

        when:
        run("show")

        then:
        outputContains("output file before: " + testDirectory.file("build/child"))
        outputContains("task output file: " + testDirectory.file("output/some-dir/other-child"))
    }

    def "can set directory property value from DSL using a value or a provider"() {
        given:
        buildFile << """
class SomeExtension {
    final DirectoryProperty prop
    
    @javax.inject.Inject
    SomeExtension(ProjectLayout layout) {
        prop = layout.directoryProperty()
    }
}

extensions.create('custom', SomeExtension, layout)
custom.prop = layout.projectDir.dir("dir1")
assert custom.prop.get().asFile == file("dir1")

custom.prop = providers.provider { layout.projectDir.dir("dir2") }
assert custom.prop.get().asFile == file("dir2")

custom.prop = layout.buildDir.dir("dir3")
assert custom.prop.get().asFile == file("build/dir3")

custom.prop = file("dir4")
assert custom.prop.get().asFile == file("dir4")

"""

        expect:
        succeeds()
    }

    def "can set regular file property value from DSL using a value or a provider"() {
        given:
        buildFile << """
class SomeExtension {
    final RegularFileProperty prop
    
    @javax.inject.Inject
    SomeExtension(ProjectLayout layout) {
        prop = layout.fileProperty()
    }
}

extensions.create('custom', SomeExtension, layout)
custom.prop = layout.projectDir.file("file1")
assert custom.prop.get().asFile == file("file1")

custom.prop = providers.provider { layout.projectDir.file("file2") }
assert custom.prop.get().asFile == file("file2")

custom.prop = layout.buildDir.file("file3")
assert custom.prop.get().asFile == file("build/file3")

custom.prop = file("file4")
assert custom.prop.get().asFile == file("file4")

"""

        expect:
        succeeds()
    }

    def "reports failure to set regular file property value using incompatible type"() {
        given:
        buildFile << """
class SomeExtension {
    final Property<RegularFile> prop
    
    @javax.inject.Inject
    SomeExtension(ProjectLayout layout) {
        prop = layout.fileProperty()
    }
}

extensions.create('custom', SomeExtension, layout)

task useIntTypeDsl {
    doLast {
        custom.prop = 123
    }
}

task useIntTypeApi {
    doLast {
        custom.prop.set(123)
    }
}

task useDirTypeDsl {
    doLast {
        custom.prop = layout.projectDirectory.dir("src")
    }
}

task useDirProviderDsl {
    doLast {
        custom.prop = layout.buildDirectory
    }
}

task useDirProviderApi {
    doLast {
        custom.prop.set(layout.buildDirectory)
    }
}
"""

        when:
        fails("useIntTypeDsl")

        then:
        failure.assertHasDescription("Execution failed for task ':useIntTypeDsl'.")
        failure.assertHasCause("Cannot set the value of a property of type org.gradle.api.file.RegularFile using an instance of type java.lang.Integer.")

        when:
        fails("useIntTypeApi")

        then:
        failure.assertHasDescription("Execution failed for task ':useIntTypeApi'.")
        failure.assertHasCause("Cannot set the value of a property of type org.gradle.api.file.RegularFile using an instance of type java.lang.Integer.")

        when:
        fails("useDirTypeDsl")

        then:
        failure.assertHasDescription("Execution failed for task ':useDirTypeDsl'.")
        failure.assertHasCause("Cannot set the value of a property of type org.gradle.api.file.RegularFile using an instance of type org.gradle.api.internal.file.DefaultProjectLayout\$FixedDirectory.")

        when:
        fails("useDirProviderDsl")

        then:
        failure.assertHasDescription("Execution failed for task ':useDirProviderDsl'.")
        failure.assertHasCause("Cannot set the value of a property of type org.gradle.api.file.RegularFile using a provider of type org.gradle.api.file.Directory.")

        when:
        fails("useDirProviderApi")

        then:
        failure.assertHasDescription("Execution failed for task ':useDirProviderApi'.")
        failure.assertHasCause("Cannot set the value of a property of type org.gradle.api.file.RegularFile using a provider of type org.gradle.api.file.Directory.")
    }

    @Unroll
    def "can wire the output file of a task as input to another task using property created by #outputFileMethod"() {
        buildFile << """
            class FileOutputTask extends DefaultTask {
                @InputFile
                final RegularFileProperty inputFile = newInputFile()
                @OutputFile
                final RegularFileProperty outputFile = ${outputFileMethod}
                
                @TaskAction
                void go() {
                    def file = outputFile.asFile.get()
                    file.text = inputFile.asFile.get().text
                }
            }

            class MergeTask extends DefaultTask {
                @InputFile
                final RegularFileProperty inputFile = ${inputFileMethod}
                @InputFiles
                final ConfigurableFileCollection inputFiles = project.layout.configurableFiles()
                @OutputFile
                final RegularFileProperty outputFile = newOutputFile()
                
                @TaskAction
                void go() {
                    def file = outputFile.asFile.get()
                    file.text = ""
                    file << inputFile.asFile.get().text
                    inputFiles.each { file << ',' + it.text }
                }
            }
            
            task createFile1(type: FileOutputTask)
            task createFile2(type: FileOutputTask)
            task merge(type: MergeTask) {
                outputFile = layout.buildDirectory.file("merged.txt")
                inputFile = createFile1.outputFile
                inputFiles.from(createFile2.outputFile)
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

        where:
        outputFileMethod                | inputFileMethod
        "newOutputFile()"               | "newInputFile()"
        "project.layout.fileProperty()" | "project.layout.fileProperty()"
    }

    @Unroll
    def "can wire the output directory of a task as input to another task using property created by #outputDirMethod"() {
        buildFile << """
            class DirOutputTask extends DefaultTask {
                @InputFile
                final RegularFileProperty inputFile = newInputFile()

                @OutputDirectory
                final DirectoryProperty outputDir = ${outputDirMethod}

                @TaskAction
                void go() {
                    def dir = outputDir.asFile.get()
                    new File(dir, "file.txt").text = inputFile.asFile.get().text
                }
            }

            class MergeTask extends DefaultTask {
                @InputDirectory
                final DirectoryProperty inputDir = ${inputDirMethod}
                @InputFiles
                final ConfigurableFileCollection inputFiles = project.files()
                @OutputFile
                final RegularFileProperty outputFile = newOutputFile()

                @TaskAction
                void go() {
                    def file = outputFile.asFile.get()
                    file.text = (inputDir.asFile.get().listFiles() + inputFiles.files)*.text.join(',')
                }
            }

            task createDir1(type: DirOutputTask)
            task createDir2(type: DirOutputTask)
            task merge(type: MergeTask) {
                outputFile = layout.buildDirectory.file("merged.txt")
                inputDir = createDir1.outputDir
                inputFiles.from(createDir2.outputDir.asFileTree)
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

        where:
        outputDirMethod                      | inputDirMethod
        "newOutputDirectory()"               | "newInputDirectory()"
        "project.layout.directoryProperty()" | "project.layout.directoryProperty()"
    }

    @Unroll
    def "can wire the output of a task as a dependency of another task via #fileMethod"() {
        buildFile << """
            class DirOutputTask extends DefaultTask {
                @InputFile
                final RegularFileProperty inputFile = newInputFile()
                @OutputDirectory
                final DirectoryProperty outputDir = newOutputDirectory()
                
                @TaskAction
                void go() {
                    def dir = outputDir.asFile.get()
                    new File(dir, "file.txt").text = inputFile.asFile.get().text
                }
            }
            
            class FileOutputTask extends DefaultTask {
                @InputFile
                final RegularFileProperty inputFile = newInputFile()
                @OutputFile
                final RegularFileProperty outputFile = newOutputFile()
                
                @TaskAction
                void go() {
                    def file = outputFile.asFile.get()
                    file.text = inputFile.asFile.get().text
                }
            }
            
            task createDir(type: DirOutputTask)
            task createFile1(type: FileOutputTask)
            task otherTask {
                ${fileMethod}(createFile1.outputFile)
                ${dirMethod}(createDir.outputDir.asFileTree)
            }
            
            // Set values lazily
            createDir.inputFile = layout.projectDirectory.file("dir1-source.txt")
            createDir.outputDir = layout.buildDirectory.dir("dir1")
            createFile1.inputFile = layout.projectDirectory.file("file1-source.txt")
            createFile1.outputFile = layout.buildDirectory.file("file1.txt")
            
            buildDir = "output"
"""
        file("dir1-source.txt").text = "dir1"
        file("file1-source.txt").text = "file1"

        when:
        run("otherTask")

        then:
        result.assertTasksExecuted(":createDir", ":createFile1", ":otherTask")

        where:
        fileMethod    | dirMethod
        'dependsOn'   | 'dependsOn'
        'inputs.file' | 'inputs.dir'
    }

    def "can use @Optional on properties with type Property"() {
        given:
        buildFile << """
class SomeTask extends DefaultTask {
    @Optional @InputFile
    Property<RegularFile> inFile = newInputFile()
    
    @Optional @InputDirectory
    Property<Directory> inDir = newInputDirectory()
    
    @Optional @OutputFile
    Property<RegularFile> outFile = newOutputFile()
    
    @Optional @OutputDirectory
    Property<Directory> outDir = newOutputDirectory()
    
    @TaskAction
    def go() { }
}

    task doNothing(type: SomeTask)
"""

        when:
        run("doNothing")

        then:
        result.assertTasksNotSkipped(":doNothing")

        when:
        run("doNothing")

        then:
        result.assertTasksSkipped(":doNothing")
    }

    def "optional output consumed as non-optional input yields a reasonable error message"() {
        given:
        buildFile << """
            class ProducerTask extends DefaultTask {
                @Optional @OutputFile
                Property<RegularFile> outFile = newOutputFile()
                
                @TaskAction
                def go() { }
            }
            
            class NestedBean {
                NestedBean(RegularFileProperty inputFile) {
                    this.inputFile = inputFile
                }
            
                @InputFile
                final RegularFileProperty inputFile
            }
            
            class ConsumerTask extends DefaultTask {
            
                @Nested
                NestedBean bean = new NestedBean(project.layout.fileProperty())
                
                @Optional
                @OutputFile
                Property<RegularFile> outputFile = newOutputFile() 
                
                @TaskAction
                def go() { }
            }
            
            task producer(type: ProducerTask)
            task consumer(type: ConsumerTask) {
                bean.inputFile.set(producer.outFile)
            }
        """

        when:
        fails("consumer")

        then:
        failure.assertHasDescription("A problem was found with the configuration of task ':consumer'.")
        failure.assertHasCause("No value has been specified for property 'bean.inputFile'.")
        executedTasks == [':consumer']
    }

    @ToBeImplemented("Absent Provider in FileCollection throws exception")
    def "depending on an optional output from another task as part of a FileCollection works"() {
        given:
        buildFile << """
            class ProducerTask extends DefaultTask {
                @Optional @OutputFile
                Property<RegularFile> outFile = newOutputFile()
                
                @TaskAction
                def go() { }
            }
            
            class ConsumerTask extends DefaultTask {
            
                @InputFiles
                ConfigurableFileCollection inputFiles = project.layout.configurableFiles()
                
                @Optional
                @OutputFile
                Property<RegularFile> outputFile = newOutputFile() 
                
                @TaskAction
                def go() { }
            }
            
            task producer(type: ProducerTask)
            task consumer(type: ConsumerTask) {
                inputFiles.from(producer.outFile)
            }
        """

        when:
        // FIXME: The task should succeed
        fails("consumer")

        then:
        failure.assertHasDescription("Failed to capture fingerprint of input files for task ':consumer' property 'inputFiles' during up-to-date check.")
        failure.assertHasCause("No value has been specified for this provider.")
        executedAndNotSkipped(':producer', ':consumer')
    }
}
