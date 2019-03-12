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

import org.gradle.internal.change.ChangeType

class IncrementalInputsIntegrationTest extends IncrementalTasksIntegrationTest {

    String getTaskAction() {
        """
            void execute(InputChanges inputChanges) {
                assert !(inputChanges instanceof ExtensionAware)
    
                if (project.hasProperty('forceFail')) {
                    throw new RuntimeException('failed')
                }
    
                incrementalExecution = inputChanges.incremental
    
                inputChanges.getChanges(inputDir).each { change ->
                    switch (change) {
                        case { it.added }:
                            addedFiles << change.file
                            break
                        case { it.modified }:
                            modifiedFiles << change.file
                            break
                        case { it.removed }:
                            removedFiles << change.file
                            break
                        default:
                            throw new IllegalStateException()
                    }
                }
    
                if (!inputChanges.incremental) {
                    createOutputsNonIncremental()
                }
                
                touchOutputs()
            }
        """
    }

    @Override
    ChangeType getRebuildChangeType() {
        return ChangeType.ADDED
    }

    def "incremental task is executed non-incrementally when input file property has been added"() {
        given:
        file('new-input.txt').text = "new input file"
        previousExecution()

        when:
        buildFile << "incremental.inputs.file('new-input.txt')"

        then:
        executesWithRebuildContext()
    }

}
