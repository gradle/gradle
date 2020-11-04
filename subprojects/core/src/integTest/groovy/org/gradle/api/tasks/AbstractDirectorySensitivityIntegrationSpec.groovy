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
import org.gradle.internal.fingerprint.impl.DirectorySensitivity
import spock.lang.Unroll


abstract class AbstractDirectorySensitivityIntegrationSpec extends AbstractIntegrationSpec {
    abstract void execute(String... tasks)

    abstract void cleanWorkspace()

    abstract String getStatusForReusedOutput()

    @Unroll
    def "task is sensitive to empty directories by default (#api)"() {
        createTaskWithSensitivity(DirectorySensitivity.FINGERPRINT_DIRECTORIES, api)
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
        result.groupedOutput.task(":taskWithInputs").outcome == statusForReusedOutput

        when:
        cleanWorkspace()
        file('foo/c').mkdir()
        file('foo/c/1').mkdir()
        file('foo/c/2').mkdir()
        execute("taskWithInputs")

        then:
        executedAndNotSkipped(":taskWithInputs")

        where:
        api << (API.values() as List<API>)
    }

    @Unroll
    def "empty directories are ignored when specified (#api)"() {
        createTaskWithSensitivity(DirectorySensitivity.IGNORE_DIRECTORIES, api)
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
        result.groupedOutput.task(":taskWithInputs").outcome == statusForReusedOutput

        when:
        cleanWorkspace()
        file('foo/c').mkdir()
        file('foo/c/1').mkdir()
        file('foo/c/2').mkdir()
        execute("taskWithInputs")

        then:
        result.groupedOutput.task(":taskWithInputs").outcome == statusForReusedOutput

        where:
        api << (API.values() as List<API>)
    }

    enum API {
        RUNTIME_API, ANNOTATION_API
    }

    void createTaskWithSensitivity(DirectorySensitivity emptyDirectorySensitivity, API api) {
        buildFile << """
            task taskWithInputs(type: TaskWithInputs)
        """
        if (api == API.RUNTIME_API) {
            createRuntimeApiTaskWithSensitivity(emptyDirectorySensitivity)
        } else if (api == API.ANNOTATION_API) {
            createAnnotatedTaskWithSensitivity(emptyDirectorySensitivity)
        } else {
            throw new IllegalArgumentException()
        }
    }

    void createAnnotatedTaskWithSensitivity(DirectorySensitivity directorySensitivity) {
        buildFile << """
            @CacheableTask
            class TaskWithInputs extends DefaultTask {
                @InputFiles
                @PathSensitive(PathSensitivity.RELATIVE)
                ${directorySensitivity == DirectorySensitivity.IGNORE_DIRECTORIES ? "@${IgnoreDirectories.class.simpleName}" : ''}
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

    void createRuntimeApiTaskWithSensitivity(DirectorySensitivity directorySensitivity) {
        buildFile << """
            @CacheableTask
            class TaskWithInputs extends DefaultTask {
                @Internal FileCollection sources
                @OutputFile File outputFile

                public TaskWithInputs() {
                    sources = project.files()

                    inputs.files(sources)
                        .withPathSensitivity(PathSensitivity.RELATIVE)
                        ${directorySensitivity == DirectorySensitivity.IGNORE_DIRECTORIES ? '.ignoreDirectories()' : ''}
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
}
