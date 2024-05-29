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
import org.gradle.internal.fingerprint.LineEndingSensitivity
import org.gradle.work.NormalizeLineEndings

import java.lang.annotation.Annotation

abstract class AbstractLineEndingSensitivityIntegrationSpec extends AbstractIntegrationSpec {
    private static final byte[] BINARY_CONTENT_WITH_LF = [0xff, 0xd8, 0xff, 0xe0, 0x00, 0x10, 0x4a, 0x46, 0x49, 0x46, 0x00, 0xff, 0xda, 0x0a] as byte[]
    private static final byte[] BINARY_CONTENT_WITH_CRLF = [0xff, 0xd8, 0xff, 0xe0, 0x00, 0x10, 0x4a, 0x46, 0x49, 0x46, 0x00, 0xff, 0xda, 0x0d, 0x0a] as byte[]
    public static final String TRANSFORM_EXECUTED = 'Transform producer.zip (project :producer) with AugmentTransform'
    public static final String TEXT_WITH_LINE_ENDINGS = "\nhere's a line\nhere's another line\n\n"

    abstract String getStatusForReusedOutput()

    abstract void execute(String... tasks)

    abstract void cleanWorkspace()

    def "input files properties are sensitive to line endings by default (#api, #pathsensitivity)"() {
        createTaskWithNormalization(InputFiles, LineEndingSensitivity.DEFAULT, pathsensitivity, api)

        buildFile << """
            taskWithInputs {
                sources.from(project.files("foo"))
                outputFile = project.file("\${buildDir}/output.txt")
            }
        """
        file('foo/Changing.java') << toUnix(TEXT_WITH_LINE_ENDINGS)
        file('foo/Changing.jpg').bytes = BINARY_CONTENT_WITH_LF

        when:
        execute("taskWithInputs")

        then:
        executedAndNotSkipped(":taskWithInputs")

        when:
        file('foo/Changing.java').text = toWindows(TEXT_WITH_LINE_ENDINGS)
        cleanWorkspace()
        execute("taskWithInputs")

        then:
        executedAndNotSkipped(":taskWithInputs")

        when:
        file('foo/Changing.jpg').bytes = BINARY_CONTENT_WITH_CRLF
        cleanWorkspace()
        execute("taskWithInputs")

        then:
        executedAndNotSkipped(":taskWithInputs")

        where:
        [api, pathsensitivity] << [Api.values(), PathSensitivity.values()].combinations()
    }

    def "input files properties can ignore line endings when specified (#api, #pathsensitivity)"() {
        createTaskWithNormalization(InputFiles, LineEndingSensitivity.NORMALIZE_LINE_ENDINGS, pathsensitivity, api)

        buildFile << """
            taskWithInputs {
                sources.from(project.files("foo"))
                outputFile = project.file("\${buildDir}/output.txt")
            }
        """
        file('foo/Changing.java') << toUnix(TEXT_WITH_LINE_ENDINGS)
        file('foo/Changing.jpg').bytes = BINARY_CONTENT_WITH_LF

        when:
        execute("taskWithInputs")

        then:
        executedAndNotSkipped(":taskWithInputs")

        when:
        file('foo/Changing.java').text = toWindows(TEXT_WITH_LINE_ENDINGS)
        cleanWorkspace()
        execute("taskWithInputs")

        then:
        reused(":taskWithInputs")

        when:
        file('foo/Changing.jpg').bytes = BINARY_CONTENT_WITH_CRLF
        cleanWorkspace()
        execute("taskWithInputs")

        then:
        executedAndNotSkipped(":taskWithInputs")

        where:
        [api, pathsensitivity] << [Api.values(), PathSensitivity.values()].combinations()
    }

    def "runtime classpath properties are sensitive to line endings by default (#api)"() {
        createTaskWithNormalization(Classpath, LineEndingSensitivity.DEFAULT, null, api)

        buildFile << """
            taskWithInputs {
                sources.from(project.files("foo"))
                outputFile = project.file("\${buildDir}/output.txt")
            }
        """
        file('foo/Changing.txt') << toUnix(TEXT_WITH_LINE_ENDINGS)
        file('foo/Changing.jpg').bytes = BINARY_CONTENT_WITH_LF

        when:
        execute("taskWithInputs")

        then:
        executedAndNotSkipped(":taskWithInputs")

        when:
        file('foo/Changing.txt').text = toWindows(TEXT_WITH_LINE_ENDINGS)
        cleanWorkspace()
        execute("taskWithInputs")

        then:
        executedAndNotSkipped(":taskWithInputs")

        when:
        file('foo/Changing.jpg').bytes = BINARY_CONTENT_WITH_CRLF
        cleanWorkspace()
        execute("taskWithInputs")

        then:
        executedAndNotSkipped(":taskWithInputs")

        where:
        api << Api.values()
    }

    def "runtime classpath properties can ignore line endings when specified (#api)"() {
        createTaskWithNormalization(Classpath, LineEndingSensitivity.NORMALIZE_LINE_ENDINGS, null, api)

        buildFile << """
            taskWithInputs {
                sources.from(project.files("foo"))
                outputFile = project.file("\${buildDir}/output.txt")
            }
        """
        file('foo/Changing.txt') << toUnix(TEXT_WITH_LINE_ENDINGS)
        file('foo/Changing.class').bytes = BINARY_CONTENT_WITH_LF

        when:
        execute("taskWithInputs")

        then:
        executedAndNotSkipped(":taskWithInputs")

        when:
        file('foo/Changing.txt').text = toWindows(TEXT_WITH_LINE_ENDINGS)
        cleanWorkspace()
        execute("taskWithInputs")

        then:
        reused(":taskWithInputs")

        when:
        file('foo/Changing.class').bytes = BINARY_CONTENT_WITH_CRLF
        cleanWorkspace()
        execute("taskWithInputs")

        then:
        executedAndNotSkipped(":taskWithInputs")

        where:
        api << Api.values()
    }

    def "runtime classpath properties with zip entries can ignore line endings when specified (#api)"() {
        createTaskWithNormalization(Classpath, LineEndingSensitivity.NORMALIZE_LINE_ENDINGS, null, api)

        buildFile << """
            task jarTask(type: Jar) {
                from(project.files("foo"))
                archiveFile.set(project.file("\${buildDir}/foo.jar"))
            }
            taskWithInputs {
                sources.from(jarTask)
                outputFile = project.file("\${buildDir}/output.txt")
            }
        """
        file('foo/Changing.txt') << toUnix(TEXT_WITH_LINE_ENDINGS)
        file('foo/Changing.jpg').bytes = BINARY_CONTENT_WITH_LF

        when:
        execute("taskWithInputs")

        then:
        executedAndNotSkipped(":taskWithInputs")

        when:
        file('foo/Changing.txt').text = toWindows(TEXT_WITH_LINE_ENDINGS)
        cleanWorkspace()
        execute("taskWithInputs")

        then:
        reused(":taskWithInputs")

        when:
        file('foo/Changing.jpg').bytes = BINARY_CONTENT_WITH_CRLF
        cleanWorkspace()
        execute("taskWithInputs")

        then:
        executedAndNotSkipped(":taskWithInputs")

        where:
        api << Api.values()
    }

    def "runtime classpath properties with nested zip files can ignore line endings when specified (#api)"() {
        createTaskWithNormalization(Classpath, LineEndingSensitivity.NORMALIZE_LINE_ENDINGS, null, api)

        buildFile << """
            task zipTask(type: Zip) {
                from(project.files("foo"))
                archiveFile.set(project.file("\${buildDir}/foo.zip"))
            }
            task jarTask(type: Jar) {
                from(zipTask)
                archiveFile.set(project.file("\${buildDir}/foo.jar"))
            }
            taskWithInputs {
                sources.from(jarTask)
                outputFile = project.file("\${buildDir}/output.txt")
            }
        """
        file('foo/Changing.txt') << toUnix(TEXT_WITH_LINE_ENDINGS)
        file('foo/Changing.jpg').bytes = BINARY_CONTENT_WITH_LF

        when:
        execute("taskWithInputs")

        then:
        executedAndNotSkipped(":taskWithInputs")

        when:
        file('foo/Changing.txt').text = toWindows(TEXT_WITH_LINE_ENDINGS)
        cleanWorkspace()
        execute("taskWithInputs")

        then:
        reused(":taskWithInputs")

        when:
        file('foo/Changing.jpg').bytes = BINARY_CONTENT_WITH_CRLF
        cleanWorkspace()
        execute("taskWithInputs")

        then:
        executedAndNotSkipped(":taskWithInputs")

        where:
        api << Api.values()
    }

    @ToBeFixedForIsolatedProjects(because = "allprojects, extensive cross-project access")
    def "artifact transforms are sensitive to line endings by default"() {
        createParameterizedTransformWithLineEndingNormalization(LineEndingSensitivity.DEFAULT)
        file('producer/foo/bar.txt') << toUnix(TEXT_WITH_LINE_ENDINGS)
        file('inputs/baz.txt') << toUnix(TEXT_WITH_LINE_ENDINGS)

        when:
        execute('showTransformedFiles')

        then:
        assertTransformExecuted()

        when:
        cleanWorkspace()
        execute('showTransformedFiles')

        then:
        assertTransformSkipped()

        when:
        cleanWorkspace()
        file('producer/foo/bar.txt').text = toWindows(TEXT_WITH_LINE_ENDINGS)
        execute('showTransformedFiles')

        then:
        assertTransformExecuted()

        when:
        cleanWorkspace()
        file('inputs/baz.txt').text = file('inputs/baz.txt').text.replaceAll('\\n', '\r\n')
        execute('showTransformedFiles')

        then:
        assertTransformExecuted()
    }

    @ToBeFixedForIsolatedProjects(because = "allprojects, extensive cross-project access")
    def "artifact transforms can ignore line endings when specified"() {
        createParameterizedTransformWithLineEndingNormalization(LineEndingSensitivity.NORMALIZE_LINE_ENDINGS)
        file('producer/foo/bar.txt') << toUnix(TEXT_WITH_LINE_ENDINGS)
        file('inputs/baz.txt') << toUnix(TEXT_WITH_LINE_ENDINGS)
        file('inputs/baz.jpg').bytes = BINARY_CONTENT_WITH_LF

        when:
        execute('showTransformedFiles')

        then:
        assertTransformExecuted()

        when:
        cleanWorkspace()
        execute('showTransformedFiles')

        then:
        assertTransformSkipped()

        when:
        cleanWorkspace()
        file('producer/foo/bar.txt').text = toWindows(TEXT_WITH_LINE_ENDINGS)
        execute('showTransformedFiles')

        then:
        assertTransformSkipped()

        when:
        cleanWorkspace()
        file('inputs/baz.txt').text = toWindows(TEXT_WITH_LINE_ENDINGS)
        execute('showTransformedFiles')

        then:
        assertTransformSkipped()

        when:
        cleanWorkspace()
        file('inputs/baz.jpg').bytes = BINARY_CONTENT_WITH_CRLF
        execute('showTransformedFiles')

        then:
        assertTransformExecuted()
    }

    def reused(String taskPath) {
        assert result.groupedOutput.task(taskPath).outcome == statusForReusedOutput
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

    static def toUnix(String text) {
        return text
    }

    static def toWindows(String text) {
        return text.replaceAll('\\n', '\r\n')
    }

    def createTaskWithNormalization(Class<? extends Annotation> inputAnnotation, LineEndingSensitivity lineEndingSensitivity, PathSensitivity pathSensitivity, Api api) {
        buildFile << """
            task taskWithInputs(type: TaskWithInputs)
        """

        if (api == Api.ANNOTATION_API) {
            createAnnotatedTaskWithNormalization(inputAnnotation, lineEndingSensitivity, pathSensitivity)
        } else if (api == Api.RUNTIME_API) {
            Class<?> normalizer;
            if (inputAnnotation == Classpath) {
                normalizer = ClasspathNormalizer
            } else if (inputAnnotation == CompileClasspath) {
                normalizer = CompileClasspathNormalizer
            } else {
                normalizer = null
            }
            createRuntimeApiTaskWithNormalization(lineEndingSensitivity, pathSensitivity, normalizer)
        } else {
            throw new IllegalArgumentException()
        }
    }

    def createAnnotatedTaskWithNormalization(Class<? extends Annotation> inputAnnotation, LineEndingSensitivity lineEndingSensitivity, PathSensitivity pathSensitivity) {
        buildFile << """
            @CacheableTask
            class TaskWithInputs extends DefaultTask {
                @${inputAnnotation.simpleName}
                ${pathSensitivityAnnotation(pathSensitivity)}
                ${lineEndingSensitivity == LineEndingSensitivity.NORMALIZE_LINE_ENDINGS ? "@${NormalizeLineEndings.class.simpleName}" : ''}
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

    static def pathSensitivityAnnotation(PathSensitivity pathSensitivity) {
        return pathSensitivity != null ? "@PathSensitive(PathSensitivity.${pathSensitivity.name()})" : ""
    }

    def createRuntimeApiTaskWithNormalization(LineEndingSensitivity lineEndingSensitivity, PathSensitivity pathSensitivity, Class<?> normalizer) {
        buildFile << """
            @CacheableTask
            class TaskWithInputs extends DefaultTask {
                @Internal FileCollection sources
                @OutputFile File outputFile

                public TaskWithInputs() {
                    sources = project.files()

                    inputs.files(sources)
                        ${withNormalizer(normalizer)}
                        ${withPathSensitivity(pathSensitivity)}
                        ${lineEndingSensitivity == LineEndingSensitivity.NORMALIZE_LINE_ENDINGS ? '.normalizeLineEndings()' : ''}
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

    static def withNormalizer(Class<?> normalizer) {
        return normalizer != null ? ".withNormalizer(${normalizer.simpleName})" : ""
    }

    static def withPathSensitivity(PathSensitivity pathSensitivity) {
        return pathSensitivity != null ? ".withPathSensitivity(PathSensitivity.${pathSensitivity.name()})" : ""
    }

    void createParameterizedTransformWithLineEndingNormalization(LineEndingSensitivity lineEndingNormalization) {
        settingsFile << """
            include(':producer')
        """
        buildFile << """
            ${showTransformedFilesTask}
            ${unzipTransform}

            @CacheableTransform
            abstract class AugmentTransform implements TransformAction<Parameters> {
                interface Parameters extends TransformParameters {
                    @InputFiles
                    @PathSensitive(PathSensitivity.RELATIVE)
                    ${lineEndingNormalization == LineEndingSensitivity.NORMALIZE_LINE_ENDINGS ? "@${NormalizeLineEndings.class.simpleName}" : ''}
                    ConfigurableFileCollection getFiles()
                }

                @InputArtifact
                ${lineEndingNormalization == LineEndingSensitivity.NORMALIZE_LINE_ENDINGS ? "@${NormalizeLineEndings.class.simpleName}" : ''}
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
                        files.from('inputs')
                    }
                }
            }

            configurations {
                foo {
                    attributes.attribute(augmented, true)
                }
            }

            dependencies {
                foo project(path: ':producer', configuration: 'foo')
            }

            task showTransformedFiles(type: ShowTransformedFiles) {
                artifacts.from(configurations.foo)
            }

            project(':producer') {
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

    enum Api {
        RUNTIME_API, ANNOTATION_API
    }
}
