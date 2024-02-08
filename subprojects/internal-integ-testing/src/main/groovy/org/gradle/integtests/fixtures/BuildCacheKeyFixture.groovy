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
import org.gradle.api.internal.tasks.SnapshotTaskInputsBuildOperationType
import org.gradle.api.internal.tasks.execution.ExecuteTaskBuildOperationType
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.integtests.fixtures.executer.GradleExecuter
import org.gradle.integtests.fixtures.executer.UserInitScriptExecuterFixture
import org.gradle.internal.build.event.BuildEventListenerRegistryInternal
import org.gradle.internal.hash.HashCode
import org.gradle.internal.operations.BuildOperationDescriptor
import org.gradle.internal.operations.BuildOperationListener
import org.gradle.internal.operations.OperationFinishEvent
import org.gradle.internal.operations.OperationIdentifier
import org.gradle.internal.operations.OperationProgressEvent
import org.gradle.internal.operations.OperationStartEvent
import org.gradle.test.fixtures.file.TestDirectoryProvider
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.internal.TextUtil

class BuildCacheKeyFixture extends UserInitScriptExecuterFixture {

    Map<String, HashCode> buildCacheKeys = [:]

    BuildCacheKeyFixture(GradleExecuter executer, TestDirectoryProvider testDir) {
        super(executer, testDir)
    }

    private TestFile getFile() {
        testDir.testDirectory.file("outputBuildCacheKey.json")
    }

    @Override
    String initScriptContent() {
        """
            interface BuildCacheKeyCollectorParams extends ${BuildServiceParameters.name} {
                RegularFileProperty getBuildCacheKeyJson()
            }

            abstract class BuildCacheKeyCollector implements ${BuildService.name}<BuildCacheKeyCollectorParams>, ${BuildOperationListener.name}, AutoCloseable {

                private final Map<Long, byte[]> buildCacheKeysByParentId = [:]
                private final Map<String, byte[]> buildCacheKeys = [:]

                @Override
                void started($BuildOperationDescriptor.name buildOperation, $OperationStartEvent.name startEvent) {}

                @Override
                void finished($BuildOperationDescriptor.name buildOperation, $OperationFinishEvent.name finishEvent) {
                    if (finishEvent.result instanceof $ExecuteTaskBuildOperationType.Result.name) {
                        def buildCacheKey = buildCacheKeysByParentId.remove(buildOperation.id.id)
                        if (buildCacheKey != null) {
                            buildCacheKeys.put(buildOperation.details.task.getIdentityPath(), buildCacheKey)
                        }
                    }
                    if (finishEvent.result instanceof $SnapshotTaskInputsBuildOperationType.Result.name) {
                        if (finishEvent.result.hashBytes != null) {
                            buildCacheKeysByParentId.put(buildOperation.parentId.id, finishEvent.result.hashBytes)
                        }
                    }
                }

                @Override
                void progress(${OperationIdentifier.name} buildOperationId, ${OperationProgressEvent.name} progressEvent) {}

                @Override
                void close() {
                    parameters.buildCacheKeyJson.get().asFile.text = groovy.json.JsonOutput.toJson(buildCacheKeys)
                }
            }

            if (gradle.parent == null) {
                def collector = gradle.sharedServices.registerIfAbsent("buildCacheKeyCollector", BuildCacheKeyCollector) {
                    parameters.buildCacheKeyJson.fileValue(new File("${TextUtil.normaliseFileSeparators(file.absolutePath)}"))
                }
                gradle.services.get(${BuildEventListenerRegistryInternal.name}).onOperationCompletion(collector)
            }
        """
    }

    @Override
    void afterBuild() {
        def rawBuildCacheKeys = (Map<String, List<Integer>>) new JsonSlurper().parse(file)
        buildCacheKeys.clear()
        rawBuildCacheKeys.each {
            buildCacheKeys[it.key] = HashCode.fromBytes(it.value as byte[])
        }
    }

    HashCode buildCacheKey(String path) {
        buildCacheKeys[path]
    }

}
