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
import org.gradle.api.internal.tasks.OriginTaskExecutionMetadata
import org.gradle.integtests.fixtures.executer.GradleExecuter
import org.gradle.integtests.fixtures.executer.UserInitScriptExecuterFixture
import org.gradle.internal.execution.ExecuteTaskBuildOperationType
import org.gradle.internal.id.UniqueId
import org.gradle.internal.progress.BuildOperationDescriptor
import org.gradle.internal.progress.BuildOperationListener
import org.gradle.internal.progress.BuildOperationListenerManager
import org.gradle.internal.progress.OperationFinishEvent
import org.gradle.internal.progress.OperationProgressEvent
import org.gradle.internal.progress.OperationStartEvent
import org.gradle.test.fixtures.file.TestDirectoryProvider
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.TextUtil

class TaskOutputOriginFixture extends UserInitScriptExecuterFixture {

    Map<String, OriginTaskExecutionMetadata> origins = [:]

    TaskOutputOriginFixture(GradleExecuter executer, TestDirectoryProvider testDir) {
        super(executer, testDir)
    }

    private TestFile getFile() {
        testDir.testDirectory.file("outputOrigin.json")
    }

    @Override
    String initScriptContent() {
        """
            if (gradle.parent == null) {
                def origins = Collections.synchronizedMap([:])
                gradle.ext.origins = origins
                
                gradle.services.get($BuildOperationListenerManager.name).addListener(new $BuildOperationListener.name() {
                    void started($BuildOperationDescriptor.name buildOperation, $OperationStartEvent.name startEvent) {}
                    void finished($BuildOperationDescriptor.name buildOperation, $OperationFinishEvent.name finishEvent) {
                        if (finishEvent.result instanceof $ExecuteTaskBuildOperationType.Result.name) {
                            def buildInvocationId = finishEvent.result.originBuildInvocationId
                            def executionTime = finishEvent.result.originExecutionTime
                            def entry = null
                            if (buildInvocationId) {
                                assert executionTime != null
                                entry = [
                                    buildInvocationId: buildInvocationId,
                                    executionTime: executionTime
                                ]                                    
                            } else {
                                assert executionTime == null
                            }
                            gradle.ext.origins[buildOperation.details.task.identityPath] = entry
                            
                            println "Finished task: " + buildOperation.details.task.identityPath
                        }
                    }
                    void progress($BuildOperationDescriptor.name buildOperation, ${OperationProgressEvent.name} progressEvent){
                    }
                })
                
                gradle.buildFinished {
                    println "Build finished"
                    println "--------------"
                    gradle.rootProject.file("${TextUtil.normaliseFileSeparators(file.absolutePath)}").text = groovy.json.JsonOutput.toJson(origins)
                }
            }
        """
    }

    @Override
    void afterBuild() {
        def rawOrigins = (Map<String, Map<String, String>>) new JsonSlurper().parse(file)
        origins.clear()
        rawOrigins.each {
            origins[it.key] = it.value == null ? null : new OriginTaskExecutionMetadata(
                UniqueId.from(it.value.buildInvocationId as String),
                it.value.executionTime as long
            )
        }
    }

    UniqueId originId(String path) {
        origin(path)?.buildInvocationId
    }

    OriginTaskExecutionMetadata origin(String path) {
        def tasks = origins.keySet()
        assert path in tasks
        origins[path]
    }

}
