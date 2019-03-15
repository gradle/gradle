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

import org.gradle.internal.change.ChangeTypeInternal

class IncrementalInputsIntegrationTest extends AbstractIncrementalTasksIntegrationTest {

    String getTaskAction() {
        """
            void execute(InputChanges inputChanges) {
                assert !(inputChanges instanceof ExtensionAware)
    
                if (project.hasProperty('forceFail')) {
                    throw new RuntimeException('failed')
                }
    
                incrementalExecution = inputChanges.incremental
    
                inputChanges.getFileChanges(inputDir).each { change ->
                    switch (change.changeType) {
                        case ChangeType.ADDED:
                            addedFiles << change.file
                            break
                        case ChangeType.MODIFIED:
                            modifiedFiles << change.file
                            break
                        case ChangeType.REMOVED:
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
    ChangeTypeInternal getRebuildChangeType() {
        return ChangeTypeInternal.ADDED
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
