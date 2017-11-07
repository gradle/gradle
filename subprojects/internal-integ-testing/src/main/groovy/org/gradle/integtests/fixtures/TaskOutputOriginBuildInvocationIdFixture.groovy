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

class TaskOutputOriginBuildInvocationIdFixture extends UserInitScriptExecuterFixture {

    Map<String, UniqueId> originIds = [:]

    TaskOutputOriginBuildInvocationIdFixture(GradleExecuter executer, TestDirectoryProvider testDir) {
        super(executer, testDir)
    }

    private TestFile getFile() {
        testDir.testDirectory.file("originIds.json")
    }

    @Override
    String initScriptContent() {
        """
            if (gradle.parent == null) {
                def ids = Collections.synchronizedMap([:])
                gradle.ext.originIds = ids
                
                gradle.services.get($BuildOperationListenerManager.name).addListener(new $BuildOperationListener.name() {
                    void started($BuildOperationDescriptor.name buildOperation, $OperationStartEvent.name startEvent) {}
                    void finished($BuildOperationDescriptor.name buildOperation, $OperationFinishEvent.name finishEvent) {
                        if (finishEvent.result instanceof $ExecuteTaskBuildOperationType.Result.name) {
                            gradle.ext.originIds[buildOperation.details.task.identityPath] = finishEvent.result.originBuildInvocationId        
                            println "Finished task: " + buildOperation.details.task.identityPath
                        }
                    }
                    void progress($BuildOperationDescriptor.name buildOperation, ${OperationProgressEvent.name} progressEvent){
                    }
                })
                
                gradle.buildFinished {
                    println "Build finished"
                    println "--------------"
                    gradle.rootProject.file("${TextUtil.normaliseFileSeparators(file.absolutePath)}").text = groovy.json.JsonOutput.toJson(ids)
                }
            }
        """
    }

    @Override
    void afterBuild() {
        def rawOriginIds = new JsonSlurper().parse(file) as Map<String, String>
        originIds.clear()
        rawOriginIds.each {
            originIds[it.key] = it.value == null ? null : UniqueId.from(it.value)
        }
    }

    UniqueId originId(String path) {
        def tasks = originIds.keySet()
        assert path in tasks
        originIds[path]
    }

}
