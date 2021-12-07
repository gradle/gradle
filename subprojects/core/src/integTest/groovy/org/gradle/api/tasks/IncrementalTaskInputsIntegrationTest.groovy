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

package org.gradle.api.tasks

import org.gradle.internal.execution.history.changes.ChangeTypeInternal
import spock.lang.Issue

class IncrementalTaskInputsIntegrationTest extends AbstractIncrementalTasksIntegrationTest {

    String getTaskAction() {
        """
            void execute(IncrementalTaskInputs inputs) {
                assert !(inputs instanceof ExtensionAware)

                if (System.getProperty('forceFail')) {
                    throw new RuntimeException('failed')
                }

                incrementalExecution = inputs.incremental

                inputs.outOfDate { change ->
                    if (change.added) {
                        addedFiles << change.file
                    } else {
                        modifiedFiles << change.file
                    }
                }

                inputs.removed { change ->
                    removedFiles << change.file
                }

                if (!inputs.incremental) {
                    createOutputsNonIncremental()
                }

                touchOutputs()
            }
        """
    }

    @Override
    ChangeTypeInternal getRebuildChangeType() {
        ChangeTypeInternal.MODIFIED
    }

    @Override
    String getPrimaryInputAnnotation() {
        return ""
    }

    def "incremental task is executed non-incrementally when input file property has been added"() {
        given:
        file('new-input.txt').text = "new input file"
        previousExecution()

        when:
        buildFile << "incremental.inputs.file('new-input.txt')"

        then:
        executesNonIncrementally(preexistingInputs + ['new-input.txt'])
    }

    @Issue("https://github.com/gradle/gradle/issues/4166")
    def "file in input dir appears in task inputs for #inputAnnotation"() {
        buildFile << """
            class MyTask extends DefaultTask {
                @${inputAnnotation}
                File input
                @OutputFile
                File output

                @TaskAction
                void doStuff(IncrementalTaskInputs inputs) {
                    def out = []
                    inputs.outOfDate {
                        out << file.name
                    }
                    assert out.contains('child')
                    output.text = out.join('\\n')
                }
            }

            task myTask(type: MyTask) {
                input = mkdir(inputDir)
                output = file("build/output.txt")
            }
        """
        String myTask = ':myTask'

        when:
        file("inputDir1/child") << "inputFile1"
        run myTask, '-PinputDir=inputDir1'
        then:
        executedAndNotSkipped(myTask)

        when:
        file("inputDir2/child") << "inputFile2"
        run myTask, '-PinputDir=inputDir2'
        then:
        executedAndNotSkipped(myTask)

        where:
        inputAnnotation << [InputFiles.name, InputDirectory.name]
    }
}
