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

package org.gradle.api.tasks

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.ToBeFixedForIsolatedProjects
import org.gradle.internal.fingerprint.DirectorySensitivity

abstract class AbstractDirectorySensitivityIntegrationSpec extends AbstractIntegrationSpec {

    public static final String TRANSFORM_EXECUTED = 'Transform bar.zip (project :bar) with AugmentTransform'

    abstract void execute(String... tasks)

    abstract void cleanWorkspace()

    abstract String getStatusForReusedOutput()

    def "task is sensitive to empty directories by default (#api, #pathSensitivity)"() {
        createTaskWithSensitivity(DirectorySensitivity.DEFAULT, api, pathSensitivity)
        buildFile << """
            taskWithInputs {
                sources.from(project.files("foo", "bar"))
                outputFile = project.file("\${buildDir}/output.txt")
            }
        """
        file('foo').mkdir()
        file('foo/a').createFile()
        file('foo/b').createFile()
        file('bar').mkdir()
        file('bar/a').createFile()

        when:
        execute("taskWithInputs")

        then:
        executedAndNotSkipped(":taskWithInputs")

        when:
        cleanWorkspace()
        execute("taskWithInputs")

        then:
        reused(":taskWithInputs")

        when:
        cleanWorkspace()
        file('foo/c').mkdir()
        file('foo/c/1').mkdir()
        file('foo/c/2').mkdir()
        execute("taskWithInputs")

        then:
        executedAndNotSkipped(":taskWithInputs")

        where:
        [api, pathSensitivity] << [Api.values(), [PathSensitivity.RELATIVE, PathSensitivity.ABSOLUTE, PathSensitivity.NAME_ONLY]].combinations()
    }

    def "input directories ignore empty directories by default (#api)"() {
        buildFile """
            @CacheableTask
            abstract class TaskWithInputs extends DefaultTask {
                ${ api == Api.ANNOTATION_API ? "@InputDirectory @PathSensitive(PathSensitivity.RELATIVE)" : "@Internal" }
                abstract DirectoryProperty getSources()

                @OutputFile
                abstract RegularFileProperty getOutputFile()

                @TaskAction
                void doSomething() {
                    outputFile.get().asFile.text = "executed"
                }
            }

            tasks.register("taskWithInputs", TaskWithInputs) {
                sources.set(file("inputDir"))
                outputFile.set(file("build/outputFile.txt"))
                ${ api == Api.RUNTIME_API
                    ? "inputs.dir(sources).withPathSensitivity(PathSensitivity.RELATIVE).withPropertyName('sources')"
                    : "" }
            }
        """
        def inputDir = file("inputDir").createDir()
        inputDir.createDir("some/empty/sub-directory")
        inputDir.createFile("some/file.txt")

        when:
        execute("taskWithInputs")
        then:
        executedAndNotSkipped(":taskWithInputs")

        when:
        cleanWorkspace()
        inputDir.createDir("some/other/empty/dir")
        execute("taskWithInputs")

        then:
        reused(":taskWithInputs")

        where:
        api << Api.values()
    }

    def "empty directories are ignored when specified (#api, #pathSensitivity)"() {
        createTaskWithSensitivity(DirectorySensitivity.IGNORE_DIRECTORIES, api, pathSensitivity)
        buildFile << """
            taskWithInputs {
                sources.from(project.files("foo", "bar"))
                outputFile = project.file("\${buildDir}/output.txt")
            }
        """
        file('foo').mkdir()
        file('foo/a').createFile()
        file('foo/b').createFile()
        file('bar').mkdir()
        file('bar/a').createFile()

        when:
        execute("taskWithInputs")

        then:
        executedAndNotSkipped(":taskWithInputs")

        when:
        cleanWorkspace()
        execute("taskWithInputs")

        then:
        reused(":taskWithInputs")

        when:
        cleanWorkspace()
        file('foo/c').mkdir()
        file('foo/c/1').mkdir()
        file('foo/c/2').mkdir()
        execute("taskWithInputs")

        then:
        reused(":taskWithInputs")

        where:
        [api, pathSensitivity] << [Api.values(), [PathSensitivity.RELATIVE, PathSensitivity.ABSOLUTE, PathSensitivity.NAME_ONLY]].combinations()
    }

    def "Non-empty directories are tracked when empty directories are ignored (#api, #pathSensitivity)"() {
        createTaskWithSensitivity(DirectorySensitivity.IGNORE_DIRECTORIES, api, pathSensitivity)
        buildFile << """
            taskWithInputs {
                sources.from(project.files("foo", "bar"))
                outputFile = project.file("\${buildDir}/output.txt")
            }
        """
        file('foo').mkdir()
        file('foo/a').mkdir()
        file('foo/a/b').createFile()
        file('bar').mkdir()
        file('bar/a').createFile()

        when:
        execute("taskWithInputs")

        then:
        executedAndNotSkipped(":taskWithInputs")

        when:
        cleanWorkspace()
        execute("taskWithInputs")

        then:
        reused(":taskWithInputs")

        when:
        cleanWorkspace()
        file('foo/a/b') << "foo"
        execute("taskWithInputs")

        then:
        executedAndNotSkipped(":taskWithInputs")

        when:
        cleanWorkspace()
        file('foo/a').renameTo(file('foo/c'))
        execute("taskWithInputs")

        then:
        executedUnlessNameOnly(":taskWithInputs", pathSensitivity)

        when:
        cleanWorkspace()
        file('foo').deleteDir()
        execute("taskWithInputs")

        then:
        executedAndNotSkipped(":taskWithInputs")

        where:
        [api, pathSensitivity] << [Api.values(), [PathSensitivity.RELATIVE, PathSensitivity.ABSOLUTE, PathSensitivity.NAME_ONLY]].combinations()
    }

    @ToBeFixedForIsolatedProjects(because = "allprojects, extensive cross-project access")
    def "artifact transforms are sensitive to empty directories by default"() {
        // Immutable artifact transforms are cached to the GradleUserHome,
        // so to avoid flakiness we need to request own GradleUserHome
        executer.requireOwnGradleUserHomeDir()
        createParameterizedTransformWithSensitivity(DirectorySensitivity.DEFAULT)
        file('augmented').mkdir()
        file('augmented/a').mkdir()
        file('augmented/b').mkdir()
        file('augmented/b/b1').createFile()

        file('bar/foo').mkdir()
        file('bar/foo/c').mkdir()
        file('bar/foo/d').mkdir()
        file('bar/foo/d1').createFile()

        when:
        execute('showTransformedFiles')

        then:
        assertTransformExecuted()

        when:
        execute('showTransformedFiles')

        then:
        assertTransformSkipped()

        when:
        file('augmented/e').mkdir()
        execute('showTransformedFiles')

        then:
        assertTransformExecuted()

        when:
        file('bar/foo/f').mkdir()
        execute('showTransformedFiles')

        then:
        assertTransformExecuted()
    }

    @ToBeFixedForIsolatedProjects(because = "allprojects, extensive cross-project access")
    def "artifact transforms ignore empty directories when specified"() {
        // Immutable artifact transforms are cached to the GradleUserHome,
        // so to avoid flakiness we need to request own GradleUserHome
        executer.requireOwnGradleUserHomeDir()
        createParameterizedTransformWithSensitivity(DirectorySensitivity.IGNORE_DIRECTORIES)
        file('augmented').mkdir()
        file('augmented/a').mkdir()
        file('augmented/b').mkdir()
        file('augmented/b/b1').createFile()

        file('bar/foo').mkdir()
        file('bar/foo/c').mkdir()
        file('bar/foo/d').mkdir()
        file('bar/foo/d1').createFile()

        when:
        execute('showTransformedFiles')

        then:
        assertTransformExecuted()

        when:
        execute('showTransformedFiles')

        then:
        assertTransformSkipped()

        when:
        file('augmented/e').mkdir()
        execute('showTransformedFiles')

        then:
        assertTransformSkipped()

        when:
        file('bar/foo/f').mkdir()
        execute('showTransformedFiles')

        then:
        assertTransformSkipped()

        when:
        file('augmented/b/b1') << "foo"
        execute('showTransformedFiles')

        then:
        assertTransformExecuted()

        when:
        file('bar/foo/d1') << "foo"
        execute('showTransformedFiles')

        then:
        assertTransformExecuted()
    }

    def reused(String taskPath) {
        assert result.groupedOutput.task(taskPath).outcome == statusForReusedOutput
        return true
    }

    def executedUnlessNameOnly(String taskPath, PathSensitivity pathSensitivity) {
        if (pathSensitivity == PathSensitivity.NAME_ONLY) {
            reused(taskPath)
        } else {
            executedAndNotSkipped(taskPath)
        }
        return true
    }

    def assertTransformSkipped() {
        outputDoesNotContain(TRANSFORM_EXECUTED)
        return true
    }

    def assertTransformExecuted() {
        outputContains(TRANSFORM_EXECUTED)
        return true
    }

    enum Api {
        RUNTIME_API, ANNOTATION_API
    }

    void createTaskWithSensitivity(DirectorySensitivity emptyDirectorySensitivity, Api api, PathSensitivity pathSensitivity = PathSensitivity.RELATIVE) {
        buildFile << """
            task taskWithInputs(type: TaskWithInputs)
        """
        if (api == Api.RUNTIME_API) {
            createRuntimeApiTaskWithSensitivity(emptyDirectorySensitivity, pathSensitivity)
        } else if (api == Api.ANNOTATION_API) {
            createAnnotatedTaskWithSensitivity(emptyDirectorySensitivity, pathSensitivity)
        } else {
            throw new IllegalArgumentException()
        }
    }

    void createAnnotatedTaskWithSensitivity(DirectorySensitivity directorySensitivity, PathSensitivity pathSensitivity) {
        buildFile << """
            @CacheableTask
            class TaskWithInputs extends DefaultTask {
                @InputFiles
                @PathSensitive(PathSensitivity.${pathSensitivity.name()})
                ${directorySensitivity == DirectorySensitivity.IGNORE_DIRECTORIES ? "@${IgnoreEmptyDirectories.class.simpleName}" : ''}
                FileCollection sources

                @OutputFile
                File outputFile

                public TaskWithInputs() {
                    sources = project.files()
                }

                @TaskAction
                void doSomething() {
                    outputFile.withWriter { writer ->
                        sources.each { writer.println it }
                    }
                }
            }
        """
    }

    void createRuntimeApiTaskWithSensitivity(DirectorySensitivity directorySensitivity, PathSensitivity pathSensitivity) {
        buildFile << """
            @CacheableTask
            class TaskWithInputs extends DefaultTask {
                @Internal FileCollection sources
                @OutputFile File outputFile

                public TaskWithInputs() {
                    sources = project.files()

                    inputs.files(sources)
                        .withPathSensitivity(PathSensitivity.${pathSensitivity.name()})
                        ${directorySensitivity == DirectorySensitivity.IGNORE_DIRECTORIES ? '.ignoreEmptyDirectories()' : ''}
                        .withPropertyName('sources')
                }

                @TaskAction
                void doSomething() {
                    outputFile.withWriter { writer ->
                        sources.each { writer.println it }
                    }
                }
            }
        """
    }

    void createParameterizedTransformWithSensitivity(DirectorySensitivity directorySensitivity) {
        settingsFile << """
            include(':bar')
        """
        buildFile << """
            ${showTransformedFilesTask}
            ${unzipTransform}

            @CacheableTransform
            abstract class AugmentTransform implements TransformAction<Parameters> {
                interface Parameters extends TransformParameters {
                    @InputFiles
                    @PathSensitive(PathSensitivity.RELATIVE)
                    ${directorySensitivity == DirectorySensitivity.IGNORE_DIRECTORIES ? "@${IgnoreEmptyDirectories.class.simpleName}" : ''}
                    ConfigurableFileCollection getFiles()
                }

                @InputArtifact
                ${directorySensitivity == DirectorySensitivity.IGNORE_DIRECTORIES ? "@${IgnoreEmptyDirectories.class.simpleName}" : ''}
                @PathSensitive(PathSensitivity.RELATIVE)
                abstract Provider<FileSystemLocation> getInput()

                @javax.inject.Inject
                abstract FileSystemOperations getFileSystemOperations()

                @Override
                void transform(TransformOutputs outputs) {
                    File augmentedDir = outputs.dir("augmented")

                    System.out.println "Augmenting..."
                    fileSystemOperations.copy {
                        it.from input
                        it.into augmentedDir
                    }
                    parameters.files.each { file ->
                        fileSystemOperations.copy {
                            it.from(file)
                            it.into(augmentedDir)
                        }
                    }
                }
            }

            apply plugin: 'base'

            def augmented = Attribute.of('augmented', Boolean)
            def artifactType = Attribute.of('artifactType', String)

            allprojects {
                dependencies {
                    attributesSchema {
                        attribute(augmented)
                    }

                    artifactTypes {
                        zip {
                            attributes.attribute(augmented, false)
                        }
                    }
                }
            }

            dependencies {
                registerTransform(UnzipTransform) {
                    from.attribute(augmented, false).attribute(artifactType, "zip")
                    to.attribute(augmented, false).attribute(artifactType, "directory")
                }
                registerTransform(AugmentTransform) {
                    from.attribute(augmented, false).attribute(artifactType, "directory")
                    to.attribute(augmented, true).attribute(artifactType, "directory")

                    parameters {
                        files.from('augmented')
                    }
                }
            }

            configurations {
                foo {
                    attributes.attribute(augmented, true)
                }
            }

            dependencies {
                foo project(path: ':bar', configuration: 'foo')
            }

            task showTransformedFiles(type: ShowTransformedFiles) {
                artifacts.from(configurations.foo)
            }

            project(':bar') {
                apply plugin: 'base'

                configurations {
                    foo
                }

                task zip(type: Zip) {
                    from('foo')
                }

                artifacts {
                    foo zip
                }
            }
        """
    }

    static String getUnzipTransform() {
        return """
            import java.util.zip.ZipEntry
            import java.util.zip.ZipInputStream
            import org.gradle.api.artifacts.transform.TransformParameters

            @CacheableTransform
            abstract class UnzipTransform implements TransformAction<TransformParameters.None> {
                @InputArtifact
                @PathSensitive(PathSensitivity.RELATIVE)
                abstract Provider<FileSystemLocation> getZippedFile()

                @Override
                void transform(TransformOutputs outputs) {
                    File zippedFile = getZippedFile().get().getAsFile()
                    File unzipDir = outputs.dir(zippedFile.getName() + "/unzipped")
                    try {
                        unzipTo(zippedFile, unzipDir)
                    } catch (IOException e) {
                        throw UncheckedException.throwAsUncheckedException(e)
                    }
                }

                static void unzipTo(File inputZip, File unzipDir) throws IOException {
                    inputZip.withInputStream { stream ->
                        ZipInputStream inputStream = new ZipInputStream(stream)
                        ZipEntry entry
                        while ((entry = inputStream.getNextEntry()) != null) {
                            if (entry.isDirectory()) {
                                new File(unzipDir, entry.getName()).mkdirs()
                                continue
                            }
                            File outFile = new File(unzipDir, entry.getName())
                            outFile.withOutputStream { outputStream ->
                                copy(inputStream, outputStream)
                            }
                        }
                    }
                }

                static void copy(InputStream source, OutputStream target) throws IOException {
                    byte[] buf = new byte[8192]
                    int length
                    while ((length = source.read(buf)) > 0) {
                        target.write(buf, 0, length)
                    }
                }
            }
        """
    }

    static String getShowTransformedFilesTask() {
        return """
            class ShowTransformedFiles extends DefaultTask {
                @InputFiles
                FileCollection artifacts = project.objects.fileCollection()

                @TaskAction
                void showFiles() {
                    artifacts.files.each { println it }
                }
            }
        """
    }
}
