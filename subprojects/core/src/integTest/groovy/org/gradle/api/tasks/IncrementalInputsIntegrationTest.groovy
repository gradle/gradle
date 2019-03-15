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
import org.gradle.work.Incremental

class IncrementalInputsIntegrationTest extends AbstractIncrementalTasksIntegrationTest {

    String getTaskAction() {
        """
            void execute(InputChanges inputChanges) {
                assert !(inputChanges instanceof ExtensionAware)
    
                if (project.hasProperty('forceFail')) {
                    throw new RuntimeException('failed')
                }
    
                incrementalExecution = inputChanges.incremental
                queryChangesFor.each { parameterName ->
                    inputChanges.getFileChanges(this."\$parameterName")
                }
    
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
            
            @Optional
            @Incremental
            @InputFile
            File anotherIncrementalInput
            
            @Optional
            @InputFile
            File nonIncrementalInput
            
            @Internal
            List<String> queryChangesFor = ["inputDir"]
        """
    }

    @Override
    ChangeTypeInternal getRebuildChangeType() {
        return ChangeTypeInternal.ADDED
    }

    @Override
    String getPrimaryInputAnnotation() {
        return "@${Incremental.simpleName}"
    }

    def setup() {
        buildFile << """
            tasks.withType(IncrementalTask).configureEach {
                anotherIncrementalInput = project.file('anotherIncrementalInput')
                nonIncrementalInput = project.file('nonIncrementalInput')
            }
        """
        file('anotherIncrementalInput').text = "anotherIncrementalInput"
        file('nonIncrementalInput').text = "nonIncrementalInput"
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

    def "cannot query non-incremental file input parameters"() {
        given:
        previousExecution()

        when:
        file("inputs/new-input-file.txt") << "new file"
        buildFile << """
            tasks.withType(IncrementalTask).configureEach {
                queryChangesFor.add("nonIncrementalInput")
            }
        """
        then:
        fails("incremental")
        failure.assertHasCause("Cannot query incremental changes: No property found for value ${file("nonIncrementalInput").absolutePath}. Incremental properties: anotherIncrementalInput, inputDir.")
    }

    def "changes to non-incremental input parameters cause a rebuild"() {
        given:
        file("nonIncrementalInput").makeOlder()
        previousExecution()

        when:
        file("inputs/new-input-file.txt") << "new file"
        file("nonIncrementalInput").text = 'changed'
        then:        
        executesWithRebuildContext("ext.added += ['new-input-file.txt']")
    }
    
    def "properties annotated with SkipWhenEmpty are incremental"() {
        setupTaskSources("@${SkipWhenEmpty.simpleName}")

        given:
        previousExecution()

        when:
        file('inputs/file1.txt') << "changed content"

        then:
        executesWithIncrementalContext("ext.modified = ['file1.txt']")
    }

}
