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
import org.gradle.internal.reflect.problems.ValidationProblemId
import org.gradle.internal.reflect.validation.ValidationMessageChecker
import org.gradle.internal.reflect.validation.ValidationTestFor

import static org.gradle.integtests.fixtures.SuggestionsMessages.GET_HELP
import static org.gradle.integtests.fixtures.SuggestionsMessages.INFO_DEBUG
import static org.gradle.integtests.fixtures.SuggestionsMessages.SCAN
import static org.gradle.integtests.fixtures.SuggestionsMessages.STACKTRACE_MESSAGE

class DirectorySensitivityErrorHandlingIntegrationSpec extends AbstractIntegrationSpec implements ValidationMessageChecker {

    def setup() {
        expectReindentedValidationMessage()
    }

    @ValidationTestFor(
        ValidationProblemId.INCOMPATIBLE_ANNOTATIONS
    )
    def "fails when @IgnoreEmptyDirectories is applied to an #nonDirectoryInput.annotation annotation"() {
        createAnnotatedInputFileTask(nonDirectoryInput)
        buildFile << """
            task taskWithInputs(type: TaskWithInputs) {
                input = ${nonDirectoryInput.value}
                outputFile = file("\${buildDir}/output")
            }
        """

        file('foo').createFile()

        when:
        fails("taskWithInputs")

        then:
        failureDescriptionContains(
            incompatibleAnnotations {
                type('TaskWithInputs').property('input')
                annotatedWith('IgnoreEmptyDirectories')
                incompatibleWith(nonDirectoryInput.annotation - '@')
                includeLink()
                forceSolutionSkip()
            }
        )
        failure.assertHasResolutions(
            "Remove the '@IgnoreEmptyDirectories' annotation.",
            STACKTRACE_MESSAGE,
            INFO_DEBUG,
            SCAN,
            GET_HELP)

        where:
        nonDirectoryInput << NonDirectoryInput.values()
    }

    @ValidationTestFor(
        ValidationProblemId.INCOMPATIBLE_ANNOTATIONS
    )
    def "fails when @IgnoreEmptyDirectories is applied to an #output.annotation annotation"() {
        createAnnotatedOutputFileTask(output)
        buildFile << """
            task taskWithOutputs(type: TaskWithOutputs) {
                input = file('foo')
                output = ${output.value}
            }
        """

        file('foo').createFile()

        when:
        fails("taskWithOutputs")

        then:
        failureDescriptionContains(
            incompatibleAnnotations {
                type('TaskWithOutputs').property('output')
                annotatedWith('IgnoreEmptyDirectories')
                incompatibleWith(output.annotation - '@')
                includeLink()
                forceSolutionSkip()
            }
        )

        failure.assertHasResolutions(
            "Remove the '@IgnoreEmptyDirectories' annotation.",
            STACKTRACE_MESSAGE,
            INFO_DEBUG,
            SCAN,
            GET_HELP)
        where:
        output << Output.values()
    }

    enum NonDirectoryInput {
        INPUTFILE(InputFile, 'File', 'file("foo")', "@${PathSensitive.class.simpleName}(${PathSensitivity.class.simpleName}.${PathSensitivity.RELATIVE.name()})"),
        INPUT(Input, 'String', '"foo"')

        String annotation
        String type
        String value
        String additionalAnnotations

        NonDirectoryInput(Class<?> annotation, String type, String value, String additionalAnnotations = '') {
            this.annotation = "@${annotation.simpleName}"
            this.type = type
            this.value = value
            this.additionalAnnotations = additionalAnnotations
        }
    }

    enum Output {
        OUTPUTFILE(OutputFile, 'File', 'file("${buildDir}/foo")', false),
        OUTPUTFILES(OutputFiles, 'FileCollection', 'files("${buildDir}/foo")', false),
        OUTPUTDIRECTORY(OutputDirectory, 'File', 'file("${buildDir}/foo")', true),
        OUTPUTDIRECTORIES(OutputDirectories, 'FileCollection', 'files("${buildDir}/foo")', true)

        String annotation
        String type
        String value
        String directoryType

        Output(Class<?> annotation, String type, String value, directoryType) {
            this.annotation = "@${annotation.simpleName}"
            this.type = type
            this.value = value
            this.directoryType = directoryType
        }
    }

    void createAnnotatedInputFileTask(NonDirectoryInput nonDirectoryInput) {
        buildFile << """
            @CacheableTask
            class TaskWithInputs extends DefaultTask {
                ${nonDirectoryInput.annotation}
                ${nonDirectoryInput.additionalAnnotations}
                @IgnoreEmptyDirectories
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

    void createAnnotatedOutputFileTask(Output output) {
        buildFile << """
            @CacheableTask
            class TaskWithOutputs extends DefaultTask {
                @InputFile
                @PathSensitive(PathSensitivity.RELATIVE)
                File input

                @InputFiles
                @PathSensitive(PathSensitivity.RELATIVE)
                FileCollection sources

                ${output.annotation}
                @IgnoreEmptyDirectories
                ${output.type} output

                @Internal
                FileCollection outputFiles

                public TaskWithOutputs() {
                    sources = project.files().from { input }
                    outputFiles = project.files().from { output }
                }

                @TaskAction
                void doSomething() {
                    outputFiles.each { outputFile ->
                        def file = ${output.directoryType ? 'new File(outputFile, "output")' : 'outputFile'}
                        file.parentFile.mkdirs()
                        file.withWriter { writer ->
                            sources.each { writer.println it }
                        }
                    }
                }
            }
        """
    }
}
