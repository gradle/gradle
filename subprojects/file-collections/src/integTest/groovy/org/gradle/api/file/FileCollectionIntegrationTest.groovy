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

import org.gradle.api.tasks.TasksWithInputsAndOutputs
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import spock.lang.Issue

import static org.gradle.util.internal.TextUtil.escapeString

class FileCollectionIntegrationTest extends AbstractIntegrationSpec implements TasksWithInputsAndOutputs {
    def "can use 'as' operator with #type"() {
        buildFile << """
            def fileCollection = files("input.txt")
            def castValue = fileCollection as $type
            println "Cast value: \$castValue (\${castValue.getClass().name})"
            assert castValue instanceof $type
        """

        expect:
        succeeds "help"

        where:
        type << ["Object", "Object[]", "Set", "LinkedHashSet", "List", "LinkedList", "Collection", "FileCollection"]
    }

    @Issue("https://github.com/gradle/gradle/issues/10322")
    def "can construct file collection from the elements of a source directory set"() {
        buildFile """
            def fileCollection = objects.fileCollection()
            def sourceDirs = objects.sourceDirectorySet('main', 'main files')
            sourceDirs.srcDirs("dir1", "dir2")
            fileCollection.from(sourceDirs.srcDirTrees)
            println("files = \${fileCollection.files.name.sort()}")
        """

        given:
        file("dir1/file1").createFile()
        file("dir1/file2").createFile()
        file("dir2/sub/file3").createFile()

        expect:
        succeeds()
        outputContains("files = [file1, file2, file3]")
    }

    def "can view the elements of file collection as a Provider"() {
        buildFile """
            def files = objects.fileCollection()
            def elements = files.elements

            def name = 'a'
            files.from { name }

            assert elements.get().asFile == [file('a')]
        """

        expect:
        succeeds()
    }

    def "task @InputFiles file collection closure is called once only when task executes"() {
        taskTypeWithInputFileCollection()
        buildFile """
            task merge(type: InputFilesTask) {
                outFile = file("out.txt")
                inFiles.from {
                    println("calculating value")
                    return 'in.txt'
                }
            }
"""
        file("in.txt").text = "in"

        when:
        run("merge")

        then:
        output.count("calculating value") == 2 // once for task dependency calculation, once for task execution
    }

    def "task @InputFiles file collection provider is called once only when task executes"() {
        taskTypeWithInputFileCollection()
        buildFile """
            task merge(type: InputFilesTask) {
                outFile = file("out.txt")
                inFiles.from providers.provider {
                    println("calculating value")
                    return 'in.txt'
                }
            }
"""
        file("in.txt").text = "in"

        when:
        run("merge")

        then:
        output.count("calculating value") == 2 // once for task dependency calculation, once for task execution
    }

    def "can connect the elements of a file collection to task input ListProperty"() {
        taskTypeWithOutputFileProperty()
        taskTypeWithInputFileListProperty()
        buildFile """
            task produce1(type: FileProducer) {
                output = file("out1.txt")
                content = "one"
            }
            task produce2(type: FileProducer) {
                output = file("out2.txt")
                content = "two"
            }
            def files = project.files(produce1, produce2)
            task merge(type: InputFilesTask) {
                inFiles.addAll(files.elements)
                outFile = file("merge.txt")
            }
        """

        when:
        run("merge")

        then:
        result.assertTasksExecuted(":produce1", ":produce2", ":merge")
        file("merge.txt").text == "one,two"
    }

    @Issue("https://github.com/gradle/gradle/issues/12832")
    def "can use += convenience in Groovy DSL to add elements to file collection when property has legacy setter"() {
        taskTypeLogsInputFileCollectionContent()
        buildFile << """
            class LegacyTask extends ShowFilesTask {
                void setInFiles(def c) {
                    inFiles.from = c
                }
            }

            def files1 = files('a.txt')
            def files2 = files('b.txt')
            task merge(type: LegacyTask) {
                inFiles += files1
                inFiles += files2
            }
        """
        file('a.txt').text = 'a'
        file('b.txt').text = 'b'

        when:
        run("merge")

        then:
        outputContains("result = [a.txt, b.txt]")
    }

    @Issue("https://github.com/gradle/gradle/issues/12832")
    def "can compose and filter a file collection that includes the collection it replaces"() {
        taskTypeLogsInputFileCollectionContent()
        buildFile """
            class LegacyTask extends ShowFilesTask {
                void setInFiles(def c) {
                    inFiles.from = c
                }
            }

            def files1 = files('a.txt', 'a.bin')
            def files2 = files('b.txt', 'b.bin')
            task merge(type: LegacyTask) {
                inFiles = files1
                inFiles = files(inFiles, files2).filter { f -> f.name.endsWith('.txt') }
            }
        """
        file('a.txt').text = 'a'
        file('a.bin').text = 'ignore-me'
        file('b.txt').text = 'b'
        file('b.bin').text = 'ignore-me'

        when:
        run("merge")

        then:
        outputContains("result = [a.txt, b.txt]")
    }

    @Issue("https://github.com/gradle/gradle/issues/13745")
    def "can compose and filter a file collection to rearrange its elements"() {
        taskTypeLogsInputFileCollectionContent()
        buildFile """
            class LegacyTask extends ShowFilesTask {
                void setInFiles(def c) {
                    inFiles.from = c
                }
            }

            def files1 = files('a.txt', 'a.bin')
            def files2 = files('b.txt', 'b.bin')
            task merge(type: LegacyTask) {
                inFiles = files1
                def sum = inFiles.plus(files2)
                inFiles = sum.filter { f -> f.name.endsWith('.txt') } + sum.filter { f -> f.name.endsWith('.bin') }
            }
        """
        file('a.txt').text = 'a1'
        file('a.bin').text = 'a2'
        file('b.txt').text = 'b1'
        file('b.bin').text = 'b2'

        when:
        run("merge")

        then:
        outputContains("result = [a.txt, b.txt, a.bin, b.bin]")
    }

    def "can subtract the elements of another file collection"() {
        given:
        file('files/a/one.txt').createFile()
        file('files/b/two.txt').createFile()
        buildFile """
            def files = files('files/a', 'files/b').minus(files('files/b'))
            task copy(type: Copy) {
                from files
                into 'dest'
            }
        """

        when:
        run 'copy'

        then:
        file('dest').assertHasDescendants(
            'one.txt'
        )

        when:
        file('files/b/ignore.txt').createFile()
        run 'copy'

        then:
        result.assertTaskSkipped(':copy')
        file('dest').assertHasDescendants(
            'one.txt'
        )

        when:
        file('files/a/three.txt').createFile()
        run 'copy'

        then:
        result.assertTaskNotSkipped(':copy')
        file('dest').assertHasDescendants(
            'one.txt',
            'three.txt'
        )
    }

    def "can filter the elements of a file collection using a closure"() {
        given:
        file('files/a/one.txt').createFile()
        file('files/b/two.txt').createFile()
        buildFile """
            def files = files('files/a', 'files/b').filter { it.name != 'b' }
            task copy(type: Copy) {
                from files
                into 'dest'
            }
        """

        when:
        run 'copy'

        then:
        file('dest').assertHasDescendants(
            'one.txt'
        )

        when:
        file('files/b/ignore.txt').createFile()
        run 'copy'

        then:
        result.assertTaskSkipped(':copy')
        file('dest').assertHasDescendants(
            'one.txt'
        )

        when:
        file('files/a/three.txt').createFile()
        run 'copy'

        then:
        result.assertTaskNotSkipped(':copy')
        file('dest').assertHasDescendants(
            'one.txt',
            'three.txt'
        )
    }

    def "can filter a file collection using a closure hitting the filesystem"() {
        given:
        file("files/file0.txt") << ""
        file("files/dir1/file1.txt") << ""
        file("files/dir2/file2.txt") << ""

        and:
        buildFile """
            def files = files("files/file0.txt", "files/dir1", "files/dir2", "files/dir3").filter { file ->
                file.isDirectory()
            }
            tasks.register("sync", Sync) {
                from files
                into "output"
            }
        """

        when:
        run "sync"

        then:
        file("output").assertHasDescendants("file1.txt", "file2.txt")

        when:
        run "sync"

        then:
        result.assertTaskSkipped(':sync')

        when:
        file("files/dir2").deleteDir()
        file("files/dir3/file3.txt") << ""
        run "sync"

        then:
        result.assertTaskNotSkipped(":sync")
        file("output").assertHasDescendants("file1.txt", "file3.txt")
    }

    @ToBeFixedForConfigurationCache(because = "provider assumed to be of fixed value but hits the filesystem")
    def "can filter the elements of a file collection using a closure hitting the filesystem"() {
        given:
        file("files/file0.txt") << ""
        file("files/dir1/file1.txt") << ""
        file("files/dir2/file2.txt") << ""

        and:
        buildFile """
            def files = files("files/file0.txt", "files/dir1", "files/dir2", "files/dir3").elements.map {
                it.findAll { file ->
                    file.asFile.isDirectory()
                }
            }
            tasks.register("sync", Sync) {
                from files
                into "output"
            }
        """

        when:
        run "sync"

        then:
        file("output").assertHasDescendants("file1.txt", "file2.txt")

        when:
        run "sync"

        then:
        result.assertTaskSkipped(':sync')

        when:
        file("files/dir2").deleteDir()
        file("files/dir3/file3.txt") << ""
        run "sync"

        then:
        result.assertTaskNotSkipped(":sync")
        file("output").assertHasDescendants("file1.txt", "file3.txt")
    }

    @Issue("https://github.com/gradle/gradle/issues/17542")
    def "can map task generated FC to a filtered List of Directory (#useCase)"() {
        given:
        buildFile """
            import static org.gradle.util.internal.TextUtil.normaliseFileSeparators

            abstract class Producer extends DefaultTask {
                @OutputDirectory abstract DirectoryProperty getOutputClasses()

                Producer() { outputs.upToDateWhen { false } } // TODO doesn't matter, remove this

                @TaskAction void doStuff() {
                    File f = outputClasses.get().asFile
                    f.mkdirs()
                    new File(f, "some.txt") << "some text"
                }
            }

            TaskProvider<Producer> prod = tasks.register("prod", Producer) {
                outputClasses = layout.buildDirectory.dir("producerOutput")
            }

            abstract class MyTask extends DefaultTask {
                @Classpath abstract ConfigurableFileCollection getClasses()
                @Inject abstract ProjectLayout getLayout()
                @TaskAction void doStuff() {
                    def root = layout.projectDirectory.asFile
                    classes.files.each { file ->
                        println("CONSUMING ${'$'}{normaliseFileSeparators(root.relativePath(file))}")
                    }
                }
            }

            Provider<List<Directory>> dirsFromFilteredFcMappedElements(FileCollection fc, Directory projectDir) {
                def root = layout.projectDirectory.asFile
                fc.filter { File f ->
                    f.isDirectory()
                }.elements.map {
                    it.collect { fileSystemLocation ->
                        projectDir.dir(fileSystemLocation.asFile.absolutePath)
                    }
                }
            }

            Provider<List<Directory>> dirsFromFcElementsSetFiltered(FileCollection fc, Directory projectDir) {
                fc.elements.map {
                    it.findAll { FileSystemLocation fsl ->
                        fsl.asFile.isDirectory()
                    }.collect { fileSystemLocation ->
                        projectDir.dir(fileSystemLocation.asFile.absolutePath)
                    }
                }
            }

            FileCollection myFc = project.files(prod.map { it.outputClasses })

            tasks.register("myTask", MyTask.class) {
                classes.from(
                    files(${methodName}(myFc, project.layout.buildDirectory.get()))
                )
            }
        """

        when:
        run "myTask"

        then:
        outputContains("CONSUMING build/producerOutput")

        when:
        run "myTask"

        then:
        outputContains("CONSUMING build/producerOutput")

        where:
        methodName                         | useCase
        "dirsFromFilteredFcMappedElements" | "dirs from filtered FC elements mapped"
        "dirsFromFcElementsSetFiltered"    | "dirs from FC elements set filtered"
    }

    @Issue("https://github.com/gradle/gradle/issues/19817")
    def "fail when concatenation of files is used for path instead of single files"() {
        def path = file("files/file0.txt${File.pathSeparator}files/dir1").path
        buildFile """
            def files = files('${escapeString(path)}')
            tasks.register("getAsPath") {
                doLast {
                    println files.asPath
                }
            }
        """

        when:
        runAndFail "getAsPath"
        then:
        failure.assertHasDocumentedCause("Converting files to a classpath string when their paths contain the path separator '${File.pathSeparator}' is not supported." +
            " The path separator is not a valid element of a file path." +
            " Problematic paths in 'file collection' are: '$path'." +
            " Add the individual files to the file collection instead." +
            " Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_7.html#file_collection_to_classpath")
    }
}
