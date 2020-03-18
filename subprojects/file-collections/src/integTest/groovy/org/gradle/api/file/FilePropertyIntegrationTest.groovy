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

import org.gradle.api.tasks.TasksWithInputsAndOutputs
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Unroll

class FilePropertyIntegrationTest extends AbstractIntegrationSpec implements TasksWithInputsAndOutputs {
    def "can attach a calculated directory to task property"() {
        buildFile << """
            class SomeTask extends DefaultTask {
                @OutputDirectory
                final DirectoryProperty outputDir = project.objects.directoryProperty()

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

    def "can attach a calculated file to task property"() {
        buildFile << """
            class SomeTask extends DefaultTask {
                @OutputFile
                final RegularFileProperty outputFile = project.objects.fileProperty()

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
    SomeExtension(ObjectFactory objects) {
        prop = objects.directoryProperty()
    }
}

extensions.create('custom', SomeExtension)
custom.prop = layout.projectDir.dir("dir1")
assert custom.prop.get().asFile == file("dir1")

custom.prop = providers.provider { layout.projectDir.dir("dir2") }
assert custom.prop.get().asFile == file("dir2")

custom.prop = layout.buildDir.dir("dir3")
assert custom.prop.get().asFile == file("build/dir3")

custom.prop = file("dir4")
assert custom.prop.get().asFile == file("dir4")

custom.prop.set((Directory)null)
assert custom.prop.getOrNull() == null

custom.prop = file("foo")
custom.prop.set(null)
assert custom.prop.getOrNull() == null

custom.prop = file("foo")
custom.prop.set((File)null)
assert custom.prop.getOrNull() == null

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
    SomeExtension(ObjectFactory objects) {
        prop = objects.fileProperty()
    }
}

extensions.create('custom', SomeExtension)
custom.prop = layout.projectDir.file("file1")
assert custom.prop.get().asFile == file("file1")

custom.prop = providers.provider { layout.projectDir.file("file2") }
assert custom.prop.get().asFile == file("file2")

custom.prop = layout.buildDir.file("file3")
assert custom.prop.get().asFile == file("build/file3")

custom.prop = file("file4")
assert custom.prop.get().asFile == file("file4")

custom.prop.set((RegularFile)null)
assert custom.prop.getOrNull() == null

custom.prop = file("foo")
custom.prop.set(null)
assert custom.prop.getOrNull() == null

custom.prop = file("foo")
custom.prop.set((File)null)
assert custom.prop.getOrNull() == null
"""

        expect:
        succeeds()
    }

    def "reports failure to set directory property value using incompatible type"() {
        given:
        buildFile << """
class SomeExtension {
    final Property<Directory> prop

    @javax.inject.Inject
    SomeExtension(ObjectFactory objects) {
        prop = objects.directoryProperty()
    }
}

extensions.create('custom', SomeExtension)

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
        failure.assertHasCause("Cannot set the value of extension 'custom' property 'prop' of type org.gradle.api.file.Directory using an instance of type java.lang.Integer.")

        when:
        fails("useIntTypeApi")

        then:
        failure.assertHasDescription("Execution failed for task ':useIntTypeApi'.")
        failure.assertHasCause("Cannot set the value of extension 'custom' property 'prop' of type org.gradle.api.file.Directory using an instance of type java.lang.Integer.")

        when:
        fails("useFileTypeDsl")

        then:
        failure.assertHasDescription("Execution failed for task ':useFileTypeDsl'.")
        failure.assertHasCause("Cannot set the value of extension 'custom' property 'prop' of type org.gradle.api.file.Directory using an instance of type org.gradle.api.internal.file.DefaultFilePropertyFactory\$FixedFile.")

        when:
        fails("useFileProviderDsl")

        then:
        failure.assertHasDescription("Execution failed for task ':useFileProviderDsl'.")
        failure.assertHasCause("Cannot set the value of extension 'custom' property 'prop' of type org.gradle.api.file.Directory using a provider of type org.gradle.api.file.RegularFile.")

        when:
        fails("useFileProviderApi")

        then:
        failure.assertHasDescription("Execution failed for task ':useFileProviderApi'.")
        failure.assertHasCause("Cannot set the value of extension 'custom' property 'prop' of type org.gradle.api.file.Directory using a provider of type org.gradle.api.file.RegularFile.")
    }

    def "reports failure to set regular file property value using incompatible type"() {
        given:
        buildFile << """
class SomeExtension {
    final Property<RegularFile> prop

    @javax.inject.Inject
    SomeExtension(ObjectFactory objects) {
        prop = objects.fileProperty()
    }
}

extensions.create('custom', SomeExtension)

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
        failure.assertHasCause("Cannot set the value of extension 'custom' property 'prop' of type org.gradle.api.file.RegularFile using an instance of type java.lang.Integer.")

        when:
        fails("useIntTypeApi")

        then:
        failure.assertHasDescription("Execution failed for task ':useIntTypeApi'.")
        failure.assertHasCause("Cannot set the value of extension 'custom' property 'prop' of type org.gradle.api.file.RegularFile using an instance of type java.lang.Integer.")

        when:
        fails("useDirTypeDsl")

        then:
        failure.assertHasDescription("Execution failed for task ':useDirTypeDsl'.")
        failure.assertHasCause("Cannot set the value of extension 'custom' property 'prop' of type org.gradle.api.file.RegularFile using an instance of type org.gradle.api.internal.file.DefaultFilePropertyFactory\$FixedDirectory.")

        when:
        fails("useDirProviderDsl")

        then:
        failure.assertHasDescription("Execution failed for task ':useDirProviderDsl'.")
        failure.assertHasCause("Cannot set the value of extension 'custom' property 'prop' of type org.gradle.api.file.RegularFile using a provider of type org.gradle.api.file.Directory.")

        when:
        fails("useDirProviderApi")

        then:
        failure.assertHasDescription("Execution failed for task ':useDirProviderApi'.")
        failure.assertHasCause("Cannot set the value of extension 'custom' property 'prop' of type org.gradle.api.file.RegularFile using a provider of type org.gradle.api.file.Directory.")
    }

    def "can wire the output file of a task as input to another task using property"() {
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

            task createFile(type: FileOutputTask)
            task merge(type: FileOutputTask) {
                outputFile = layout.buildDirectory.file("merged.txt")
                inputFile = createFile.outputFile
            }

            // Set values lazily
            createFile.inputFile = layout.projectDirectory.file("file-source.txt")
            createFile.outputFile = layout.buildDirectory.file("intermediate.txt")

            buildDir = "output"
"""
        file("file-source.txt").text = "file1"

        when:
        run("merge")

        then:
        result.assertTasksExecuted(":createFile", ":merge")
        file("output/merged.txt").text == 'file1'

        when:
        run("merge")

        then:
        result.assertTasksNotSkipped()

        when:
        file("file-source.txt").text = "new-file1"
        run("merge")

        then:
        result.assertTasksExecuted(":createFile", ":merge")
        file("output/merged.txt").text == 'new-file1'
    }

    def "can wire an output file from unmanaged nested property of a task as input to another task using property"() {
        buildFile << """
            interface Params {
                @OutputFile
                RegularFileProperty getOutputFile()
            }

            class FileOutputTask extends DefaultTask {
                private params = project.objects.newInstance(Params)

                @InputFile
                final RegularFileProperty inputFile = project.objects.fileProperty()
                @Nested
                Params getParams() { return params }

                @TaskAction
                void go() {
                    def file = params.outputFile.asFile.get()
                    file.text = inputFile.asFile.get().text
                }
            }

            task createFile(type: FileOutputTask) {
                inputFile = layout.projectDirectory.file("file-source.txt")
                params.outputFile = layout.buildDirectory.file("intermediate.txt")
            }
            task merge(type: FileOutputTask) {
                params.outputFile = layout.buildDirectory.file("merged.txt")
                inputFile = createFile.params.outputFile
            }

            buildDir = "output"
"""
        file("file-source.txt").text = "file1"

        when:
        run("merge")

        then:
        result.assertTasksExecuted(":createFile", ":merge")
        file("output/merged.txt").text == 'file1'

        when:
        run("merge")

        then:
        result.assertTasksNotSkipped()

        when:
        file("file-source.txt").text = "new-file1"
        run("merge")

        then:
        result.assertTasksExecuted(":createFile", ":merge")
        file("output/merged.txt").text == 'new-file1'
    }

    def "can wire an output file from managed nested property of a task as input to another task using property"() {
        buildFile << """
            interface Params {
                @OutputFile
                RegularFileProperty getOutputFile()
            }

            abstract class FileOutputTask extends DefaultTask {
                @InputFile
                final RegularFileProperty inputFile = project.objects.fileProperty()
                @Nested
                abstract Params getParams()

                @TaskAction
                void go() {
                    def file = params.outputFile.asFile.get()
                    file.text = inputFile.asFile.get().text
                }
            }

            task createFile(type: FileOutputTask) {
                inputFile = layout.projectDirectory.file("file-source.txt")
                params.outputFile = layout.buildDirectory.file("intermediate.txt")
            }
            task merge(type: FileOutputTask) {
                params.outputFile = layout.buildDirectory.file("merged.txt")
                inputFile = createFile.params.outputFile
            }

            buildDir = "output"
"""
        file("file-source.txt").text = "file1"

        when:
        run("merge")

        then:
        result.assertTasksExecuted(":createFile", ":merge")
        file("output/merged.txt").text == 'file1'

        when:
        run("merge")

        then:
        result.assertTasksNotSkipped()

        when:
        file("file-source.txt").text = "new-file1"
        run("merge")

        then:
        result.assertTasksExecuted(":createFile", ":merge")
        file("output/merged.txt").text == 'new-file1'
    }

    @Unroll
    def "task #annotation file property is implicitly finalized when task starts execution"() {
        buildFile << """
            class SomeTask extends DefaultTask {
                ${annotation}
                final RegularFileProperty prop = project.objects.fileProperty()

                @TaskAction
                void go() {
                    println "value: " + prop.get()
                }
            }

            task show(type: SomeTask) {
                prop = file("in.txt")
                doFirst {
                    prop = file("other.txt")
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
        annotation    | _
        "@InputFile"  | _
        "@OutputFile" | _
    }

    @Unroll
    def "task #annotation directory property is implicitly finalized when task starts execution"() {
        buildFile << """
            class SomeTask extends DefaultTask {
                ${annotation}
                final DirectoryProperty prop = project.objects.directoryProperty()

                @TaskAction
                void go() {
                    println "value: " + prop.get()
                }
            }

            task show(type: SomeTask) {
                prop = file("in.dir")
                doFirst {
                    prop = file("other.dir")
                }
            }
"""
        file("in.dir").createDir()

        when:
        fails("show")

        then:
        failure.assertHasDescription("Execution failed for task ':show'.")
        failure.assertHasCause("The value for task ':show' property 'prop' is final and cannot be changed any further.")

        where:
        annotation         | _
        "@InputDirectory"  | _
        "@OutputDirectory" | _
    }

    @Unroll
    def "task ad hoc file property registered using #registrationMethod is implicitly finalized when task starts execution"() {
        given:
        buildFile << """

def prop = project.objects.fileProperty()

task thing {
    ${registrationMethod}(prop)
    prop.set(file("file-1"))
    doLast {
        prop.set(file("ignored"))
        println "prop = " + prop.get()
    }
}
"""
        file("file-1").createFile()

        when:
        fails("thing")

        then:
        failure.assertHasDescription("Execution failed for task ':thing'.")
        failure.assertHasCause("The value for this property is final and cannot be changed any further.")

        where:
        registrationMethod | _
        "inputs.file"      | _
        "outputs.file"     | _
    }

    @Unroll
    def "task ad hoc directory property registered using #registrationMethod is implicitly finalized when task starts execution"() {
        given:
        buildFile << """

def prop = project.objects.directoryProperty()

task thing {
    ${registrationMethod}(prop)
    prop.set(file("file-1"))
    doLast {
        prop.set(file("ignored"))
        println "prop = " + prop.get()
    }
}
"""
        file("file-1").createDir()

        when:
        fails("thing")

        then:
        failure.assertHasDescription("Execution failed for task ':thing'.")
        failure.assertHasCause("The value for this property is final and cannot be changed any further.")

        where:
        registrationMethod | _
        "inputs.dir"       | _
        "outputs.dir"      | _
    }

    def "can wire the output file of an ad hoc task as input to another task using property"() {
        buildFile << """
            class MergeTask extends DefaultTask {
                @InputFile
                final RegularFileProperty inputFile = project.objects.fileProperty()
                @OutputFile
                final RegularFileProperty outputFile = project.objects.fileProperty()

                @TaskAction
                void go() {
                    def file = outputFile.asFile.get()
                    file.text = ""
                    file << inputFile.asFile.get().text
                }
            }

            task createFile {
                ext.outputFile = project.objects.fileProperty()
                outputs.file(outputFile)
                doLast {
                    outputFile.get().asFile.text = 'file1'
                }
            }
            task merge(type: MergeTask) {
                outputFile = layout.buildDirectory.file("merged.txt")
                inputFile = createFile.outputFile
            }

            // Set values lazily
            createFile.outputFile.set(layout.buildDirectory.file("file.txt"))

            buildDir = "output"
"""

        when:
        run("merge")

        then:
        result.assertTasksExecuted(":createFile", ":merge")
        file("output/merged.txt").text == 'file1'

        when:
        run("merge")

        then:
        result.assertTasksNotSkipped()
    }

    def "can wire the output directory of a task as input to another task using property"() {
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
                @InputDirectory
                final DirectoryProperty inputDir = project.objects.directoryProperty()
                @OutputFile
                final RegularFileProperty outputFile = project.objects.fileProperty()

                @TaskAction
                void go() {
                    def file = outputFile.asFile.get()
                    file.text = inputDir.asFile.get().listFiles()*.text.join(',')
                }
            }

            task createDir(type: DirOutputTask)
            task merge(type: MergeTask) {
                outputFile = layout.buildDirectory.file("merged.txt")
                inputDir = createDir.outputDir
            }

            // Set values lazily
            createDir.inputFile = layout.projectDirectory.file("dir-source.txt")
            createDir.outputDir = layout.buildDirectory.dir("dir")

            buildDir = "output"
"""
        file("dir-source.txt").text = "dir1"

        when:
        run("merge")

        then:
        result.assertTasksExecuted(":createDir", ":merge")
        file("output/merged.txt").text == 'dir1'

        when:
        run("merge")

        then:
        result.assertTasksNotSkipped()

        when:
        file("dir-source.txt").text = "new-dir1"
        run("merge")

        then:
        result.assertTasksNotSkipped(":createDir", ":merge")
        file("output/merged.txt").text == 'new-dir1'
    }

    def "can wire the output directory of an ad hoc task as input to another task using property"() {
        buildFile << """
            class MergeTask extends DefaultTask {
                @InputDirectory
                final DirectoryProperty inputDir = project.objects.directoryProperty()
                @OutputFile
                final RegularFileProperty outputFile = project.objects.fileProperty()

                @TaskAction
                void go() {
                    def file = outputFile.asFile.get()
                    file.text = inputDir.asFile.get().listFiles()*.text.join(',')
                }
            }

            task createDir {
                ext.outputDir = project.objects.directoryProperty()
                outputs.dir(outputDir)
                doLast {
                    new File(outputDir.get().asFile, "file.txt").text = "dir1"
                }
            }
            task merge(type: MergeTask) {
                outputFile = layout.buildDirectory.file("merged.txt")
                inputDir = createDir.outputDir
            }

            // Set values lazily
            createDir.outputDir.set(layout.buildDirectory.dir("dir1"))

            buildDir = "output"
"""

        when:
        run("merge")

        then:
        result.assertTasksExecuted(":createDir", ":merge")
        file("output/merged.txt").text == 'dir1'

        when:
        run("merge")

        then:
        result.assertTasksNotSkipped()
    }

    @Unroll
    def "can wire the output of a task as a dependency of another task via #fileMethod"() {
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
    final Property<RegularFile> inFile = project.objects.fileProperty()

    @Optional @InputDirectory
    final Property<Directory> inDir = project.objects.directoryProperty()

    @Optional @OutputFile
    final Property<RegularFile> outFile = project.objects.fileProperty()

    @Optional @OutputDirectory
    final Property<Directory> outDir = project.objects.directoryProperty()

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
                final Property<RegularFile> outFile = project.objects.fileProperty()

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
                NestedBean bean = new NestedBean(project.objects.fileProperty())

                @Optional
                @OutputFile
                final Property<RegularFile> outputFile = project.objects.directoryProperty()

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
        failure.assertHasDescription("A problem was found with the configuration of task ':consumer' (type 'ConsumerTask').")
        failure.assertHasCause("No value has been specified for property 'bean.inputFile'.")
        failure.assertTasksExecuted(':consumer')
    }

    def "can query task output file property at any time"() {
        taskTypeWithOutputFileProperty()
        buildFile << """
            task producer(type: FileProducer) {
                output = layout.buildDir.file("text.out")
            }
            println("prop = " + producer.output.get())
            task after {
                dependsOn(producer)
                doLast {
                    println("prop = " + producer.output.get())
                }
            }
            task before {
                doLast {
                    println("prop = " + producer.output.get())
                }
            }
            producer.dependsOn(before)
        """

        expect:
        succeeds("after")
    }

    def "can query task output directory property at any time"() {
        taskTypeWithOutputDirectoryProperty()
        buildFile << """
            task producer(type: DirProducer) {
                output = layout.buildDir.dir("dir.out")
                names = ["a", "b"]
            }
            println("prop = " + producer.output.get())
            task after {
                dependsOn(producer)
                doLast {
                    println("prop = " + producer.output.get())
                }
            }
            task before {
                doLast {
                    println("prop = " + producer.output.get())
                }
            }
            producer.dependsOn(before)
        """

        expect:
        succeeds("after")
    }

    def "can query mapped task output file location property at any time"() {
        taskTypeWithOutputFileProperty()
        buildFile << """
            task producer(type: FileProducer) {
                output = layout.buildDir.file("text.out")
            }
            def prop = producer.output.locationOnly.map { it.asFile.name }
            println("prop = " + prop.get())
            task after {
                dependsOn(producer)
                doLast {
                    println("prop = " + prop.get())
                }
            }
            task before {
                doLast {
                    println("prop = " + prop.get())
                }
            }
            producer.dependsOn(before)
        """

        expect:
        succeeds("after")
    }

    def "can query mapped task output directory location property at any time"() {
        taskTypeWithOutputDirectoryProperty()
        buildFile << """
            task producer(type: DirProducer) {
                output = layout.buildDir.dir("dir.out")
                names = ["a", "b"]
            }
            def prop = producer.output.locationOnly.map { it.asFile.name }
            println("prop = " + prop.get())
            task after {
                dependsOn(producer)
                doLast {
                    println("prop = " + prop.get())
                }
            }
            task before {
                doLast {
                    println("prop = " + prop.get())
                }
            }
            producer.dependsOn(before)
        """

        expect:
        succeeds("after")
    }

    def "querying the value of a mapped task output file property before the task has started is deprecated"() {
        taskTypeWithOutputFileProperty()
        buildFile << """
            task producer(type: FileProducer) {
                output = layout.buildDir.file("text.out")
            }
            def prop = producer.output.map { it.asFile.file ? it.asFile.text : "(null)" }
            println("prop = " + prop.get())
        """

        when:
        executer.expectDocumentedDeprecationWarning("Querying the mapped value of task ':producer' property 'output' before task ':producer' has completed has been deprecated. " +
            "This will fail with an error in Gradle 7.0. " +
            "Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_6.html#querying_a_mapped_output_property_of_a_task_before_the_task_has_completed")
        succeeds("producer")

        then:
        outputContains("prop = (null)")
    }

    def "querying the value of a mapped task output file property before the task has completed is deprecated"() {
        taskTypeWithOutputFileProperty()
        buildFile << """
            task producer(type: FileProducer) {
                output = layout.buildDir.file("text.out")
            }
            def prop = producer.output.map { it.asFile.file ? it.asFile.text : "(null)" }
            producer.doFirst {
                println("prop = " + prop.get())
            }
        """

        when:
        executer.expectDocumentedDeprecationWarning("Querying the mapped value of task ':producer' property 'output' before task ':producer' has completed has been deprecated. " +
            "This will fail with an error in Gradle 7.0. " +
            "Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_6.html#querying_a_mapped_output_property_of_a_task_before_the_task_has_completed")
        succeeds("producer")

        then:
        outputContains("prop = (null)")
    }

    def "querying the value of a mapped task output directory property before the task has started is deprecated"() {
        taskTypeWithOutputDirectoryProperty()
        buildFile << """
            task producer(type: DirProducer) {
                output = layout.buildDir.dir("dir.out")
                names = ["a", "b"]
            }
            def prop = producer.output.map { it.asFile.directory ? it.asFile.list().length : -1 }
            println("prop = " + prop.get())
        """

        when:
        executer.expectDocumentedDeprecationWarning("Querying the mapped value of task ':producer' property 'output' before task ':producer' has completed has been deprecated. " +
            "This will fail with an error in Gradle 7.0. " +
            "Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_6.html#querying_a_mapped_output_property_of_a_task_before_the_task_has_completed")
        succeeds("producer")

        then:
        outputContains("prop = -1")
    }

    def "querying the value of a mapped task output directory property before the task has completed is deprecated"() {
        taskTypeWithOutputDirectoryProperty()
        buildFile << """
            task producer(type: DirProducer) {
                output = layout.buildDir.dir("dir.out")
                names = ["a", "b"]
            }
            def prop = producer.output.map { it.asFile.directory ? it.asFile.list().length : -1 }
            producer.doFirst {
                println("prop = " + prop.get())
            }
        """

        when:
        executer.expectDocumentedDeprecationWarning("Querying the mapped value of task ':producer' property 'output' before task ':producer' has completed has been deprecated. " +
            "This will fail with an error in Gradle 7.0. " +
            "Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_6.html#querying_a_mapped_output_property_of_a_task_before_the_task_has_completed")
        succeeds("producer")

        then:
        outputContains("prop = 0")
    }
}
