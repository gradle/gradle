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
import org.gradle.api.internal.tasks.execution.ExecuteTaskBuildOperationType
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.caching.internal.origin.OriginMetadata
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

import java.time.Duration

class OriginFixture extends UserInitScriptExecuterFixture {

    Map<String, OriginMetadata> origins = [:]

    OriginFixture(GradleExecuter executer, TestDirectoryProvider testDir) {
        super(executer, testDir)
    }

    private TestFile getFile() {
        testDir.testDirectory.file("outputOrigin.json")
    }

    @Override
    String initScriptContent() {
        """
            interface OriginCollectorParams extends ${BuildServiceParameters.name} {
                RegularFileProperty getOriginJson()
            }

            abstract class OriginCollector implements ${BuildService.name}<OriginCollectorParams>, ${BuildOperationListener.name}, AutoCloseable {

                private final Map<String, Map<String, Object>> origins = [:]

                @Override
                void started($BuildOperationDescriptor.name buildOperation, $OperationStartEvent.name startEvent) {}

                @Override
                void finished($BuildOperationDescriptor.name buildOperation, $OperationFinishEvent.name finishEvent) {
                    if (finishEvent.result instanceof $ExecuteTaskBuildOperationType.Result.name) {
                        def buildInvocationId = finishEvent.result.originBuildInvocationId
                        def originBuildCacheKey = finishEvent.result.originBuildCacheKeyBytes
                        def executionTime = finishEvent.result.originExecutionTime
                        def entry = null
                        if (buildInvocationId) {
                            assert executionTime != null
                            entry = [
                                buildInvocationId: buildInvocationId,
                                originBuildCacheKey: originBuildCacheKey,
                                executionTime: executionTime
                            ]
                        } else {
                            assert executionTime == null
                            assert originBuildCacheKey == null
                        }
                        origins[buildOperation.details.task.getIdentityPath()] = entry
                    }
                }

                @Override
                void progress(${OperationIdentifier.name} buildOperationId, ${OperationProgressEvent.name} progressEvent) {}

                @Override
                void close() {
                    parameters.originJson.get().asFile.text = groovy.json.JsonOutput.toJson(origins)
                }
            }

            if (gradle.parent == null) {
                def collector = gradle.sharedServices.registerIfAbsent("originsCollector", OriginCollector) {
                    parameters.originJson.fileValue(new File("${TextUtil.normaliseFileSeparators(file.absolutePath)}"))
                }
                gradle.services.get(${BuildEventListenerRegistryInternal.name}).onOperationCompletion(collector)
            }
        """
    }

    @Override
    void afterBuild() {
        def rawOrigins = (Map<String, Map<String, String>>) new JsonSlurper().parse(file)
        origins.clear()
        rawOrigins.each {
            origins[it.key] = it.value == null ? null : new OriginMetadata(
                it.value.buildInvocationId as String,
                HashCode.fromBytes(it.value.originBuildCacheKey as byte[]),
                Duration.ofMillis(it.value.executionTime as long)
            )
        }
    }

    String originId(String path) {
        origin(path)?.buildInvocationId
    }

    OriginMetadata origin(String path) {
        def tasks = origins.keySet()
        assert path in tasks
        origins[path]
    }

}
