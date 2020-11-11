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
import spock.lang.Unroll


class DirectorySensitivityErrorHandlingIntegrationSpec extends AbstractIntegrationSpec {

    @Unroll
    def "deprecation warning when @IgnoreDirectories is applied to an @#nonDirectoryInput.annotation annotation"() {
        createAnnotatedInputFileTask(nonDirectoryInput)
        buildFile << """
            task taskWithInputs(type: TaskWithInputs) {
                input = ${nonDirectoryInput.value}
                outputFile = file("\${buildDir}/output")
            }
        """

        file('foo').createFile()

        given:
        executer.expectDocumentedDeprecationWarning("Property 'input' is annotated with @IgnoreDirectories that is not allowed for ${nonDirectoryInput.annotation} properties. This behaviour has been deprecated and is scheduled to be removed in Gradle 7.0. See https://docs.gradle.org/current/userguide/more_about_tasks.html#sec:up_to_date_checks for more details.")

        expect:
        succeeds("taskWithInputs")

        where:
        nonDirectoryInput << NonDirectoryInput.values()
    }

    enum NonDirectoryInput {
        INPUTFILE("@${InputFile.class.simpleName}", 'File', 'file("foo")', "@${PathSensitive.class.simpleName}(${PathSensitivity.class.simpleName}.${PathSensitivity.RELATIVE.name()})"),
        INPUT("@${Input.class.simpleName}", 'String', '"foo"')

        String annotation
        String type
        String value
        String additionalAnnotations

        NonDirectoryInput(String annotation, String type, String value, String additionalAnnotations = '') {
            this.annotation = annotation
            this.type = type
            this.value = value
            this.additionalAnnotations = additionalAnnotations
        }
    }

    void createAnnotatedInputFileTask(NonDirectoryInput nonDirectoryInput) {
        buildFile << """
            @CacheableTask
            class TaskWithInputs extends DefaultTask {
                ${nonDirectoryInput.annotation}
                ${nonDirectoryInput.additionalAnnotations}
                @IgnoreDirectories
                ${nonDirectoryInput.type} input

                @InputFiles
                @PathSensitive(PathSensitivity.RELATIVE)
                FileCollection sources

                @OutputFile
                File outputFile

                public TaskWithInputs() {
                    sources = project.files().from { input }
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
