/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.integtests.fixtures

import groovy.json.JsonSlurper
import org.gradle.integtests.fixtures.executer.GradleExecuter
import org.gradle.integtests.fixtures.executer.InitScriptExecuterFixture
import org.gradle.internal.progress.BuildOperationInternal
import org.gradle.internal.progress.BuildOperationListener
import org.gradle.internal.progress.BuildOperationService
import org.gradle.internal.progress.OperationResult
import org.gradle.internal.progress.OperationStartEvent
import org.gradle.test.fixtures.file.TestDirectoryProvider
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.TextUtil

class BuildOperationsFixture extends InitScriptExecuterFixture {
    private final TestFile operationsDir
    private Map operations

    BuildOperationsFixture(GradleExecuter executer, TestDirectoryProvider projectDir) {
        super(executer, projectDir)
        this.operationsDir = projectDir.testDirectory.file("operations")
    }

    @Override
    String initScriptContent() {
        return """
            import ${BuildOperationService.name}
            import ${BuildOperationListener.name}
            import ${BuildOperationInternal.name}
            import ${OperationStartEvent.name}
            import ${OperationResult.name}

            def operations = [:]
            def operationListener = new BuildOperationListener() {
                
                void started(BuildOperationInternal buildOperation, OperationStartEvent startEvent) {
                    operations[buildOperation.id] = [
                        id: "\${buildOperation.id}",
                        displayName: "\${buildOperation.displayName}",
                        parentId: "\${buildOperation.parentId}",
                        name: "\${buildOperation.name}",
                        startTime: startEvent.startTime
                    ]
                }

                void finished(BuildOperationInternal buildOperation, OperationResult finishEvent) {
                    if (!operations[buildOperation.id]) {
                        operations[buildOperation.id] = [
                            id: "\${buildOperation.id}",
                            displayName: "\${buildOperation.displayName}",
                            parentId: "\${buildOperation.parentId}",
                            name: "\${buildOperation.name}"
                        ]
                    }
                    operations[buildOperation.id].endTime = finishEvent.endTime
                    if (finishEvent.failure != null) {
                        operations[buildOperation.id].failure = finishEvent.failure.message
                    }
                }
            }

            gradle.services.get(${BuildOperationService.name}).addListener(operationListener)

            gradle.buildFinished {
                gradle.services.get(${BuildOperationService.name}).removeListener(operationListener)

                def operationsDir = new File("${TextUtil.normaliseFileSeparators(operationsDir.absolutePath)}")
                operationsDir.mkdirs()
                def jsonFile = new File(operationsDir, "operations.json")
                def json = new groovy.json.JsonBuilder()
                json.operations(operations)
                jsonFile.text = json.toPrettyString()
            }
        """
    }

    @Override
    void afterBuild() {
        def jsonFile = new File(operationsDir, "operations.json")
        def slurper = new JsonSlurper()
        operations = slurper.parseText(jsonFile.text).operations
    }

    boolean hasOperation(String displayName) {
        return operation(displayName) != null
    }

    Object operation(String displayName) {
        return operations.find { it.value.displayName == displayName }.value
    }

    Map getOperations() {
        return operations
    }
}
