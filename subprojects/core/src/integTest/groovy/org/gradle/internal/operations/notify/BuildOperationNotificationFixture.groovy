/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.internal.operations.notify

import com.google.common.base.Predicate
import groovy.json.JsonSlurper
import org.gradle.api.internal.artifacts.configurations.ResolveConfigurationDependenciesBuildOperationType
import org.gradle.test.fixtures.file.TestFile

class BuildOperationNotificationFixture {

    TestFile dir

    BuildOperationNotificationFixture(TestFile dir) {
        this.dir = dir
    }

    def op(Class<?> detailsClass, Map<String, String> details = [:]) {
        def found = recordedOps.findAll { op ->
            return op.detailsType == detailsClass.name && op.details.subMap(details.keySet()) == details
        }
        assert found.size() == 1
        found.first()
    }

    void started(Class<?> type, Predicate<? super Map<String, ?>> payloadTest) {
        has(true, type, payloadTest)
    }

    void started(Class<?> type, Map<String, ?> payload = null) {
        has(true, type, (Map) payload)
    }

    void finished(Class<?> type, Map<String, ?> payload = null) {
        has(false, type, (Map) payload)
    }

    void has(boolean started, Class<?> type, Map<String, ?> payload) {
        has(started, type, payload ? payloadTest(payload) : { true } as Predicate)
    }

    private static Predicate<? super Map<String, ?>> payloadTest(Map<String, ?> expectedPayload) {
        { actualPayload ->
            if (actualPayload.keySet() != expectedPayload.keySet()) {
                return false
            }
            for (String key : actualPayload.keySet()) {
                def expectedValue = expectedPayload[key]
                def actualValue = actualPayload[key]
                def matches = expectedValue instanceof Predicate ? expectedValue.apply(actualValue) : expectedValue == actualValue
                if (!matches) {
                    return false
                }
            }
            true
        } as Predicate
    }

    void has(boolean started, Class<?> type, Predicate<? super Map<String, ?>> payloadTest) {
        def typedOps = recordedOps.findAll { op ->
            return started ? op.detailsType == type.name : op.resultType == type.name
        }
        assert typedOps.size() > 0

        if (payloadTest != null) {
            def matchingOps = typedOps.findAll { matchingOp ->
                started ? payloadTest.apply(matchingOp.details) : payloadTest.apply(matchingOp.result)
            }
            assert matchingOps.size()
        }
    }

    def getRecordedOps() {
        new JsonSlurper().parse(jsonFile())
    }

    void notIncluded(Class<?> type) {
        assert !recordedOps.any { it.detailsType == type.name }
    }

    String registerListener() {
        listenerClass() + """
        registrar.registerBuildScopeListener(listener)
        """
    }

    String registerListenerWithDrainRecordings() {
        listenerClass() + """
        registrar.registerBuildScopeListenerAndReceiveStoredOperations(listener)
        """
    }

    String listenerClass() {
        """
            def listener = new ${BuildOperationNotificationListener.name}() {

                LinkedHashMap<Object,BuildOpsEntry> ops = new LinkedHashMap().asSynchronized()
            
                @Override
                void started(${BuildOperationStartedNotification.name} startedNotification) {
            
                    def details = startedNotification.notificationOperationDetails
                    if (details instanceof org.gradle.internal.execution.ExecuteTaskBuildOperationType.Details) {
                        details = [taskPath: details.taskPath, buildPath: details.buildPath, taskClass: details.taskClass.name]
                    } else  if (details instanceof org.gradle.api.internal.plugins.ApplyPluginBuildOperationType.Details) {
                        details = [pluginId: details.pluginId, pluginClass: details.pluginClass.name, targetType: details.targetType, targetPath: details.targetPath, buildPath: details.buildPath]
                    }

                    ops.put(startedNotification.notificationOperationId, new BuildOpsEntry(id: startedNotification.notificationOperationId?.id,
                            parentId: startedNotification.notificationOperationParentId?.id,
                            detailsType: startedNotification.notificationOperationDetails.getClass().getInterfaces()[0].getName(),
                            details: details, 
                            started: startedNotification.notificationOperationStartedTimestamp))
                }
            
                @Override
                void finished(${BuildOperationFinishedNotification.name} finishedNotification) {
                    def result = finishedNotification.getNotificationOperationResult()
                    if (result instanceof ${ResolveConfigurationDependenciesBuildOperationType.Result.name}) {
                        result = []
                    }
                    def op = ops.get(finishedNotification.notificationOperationId)
                    op.resultType = finishedNotification.getNotificationOperationResult().getClass().getInterfaces()[0].getName()
                    op.result = result
                    op.finished = finishedNotification.getNotificationOperationFinishedTimestamp()
                }
            
                void store(File target){
                    target.withPrintWriter { pw ->
                        String json = groovy.json.JsonOutput.toJson(ops.values());
                        pw.append(json)
                    }
                }
            
                static class BuildOpsEntry {
                    Object id
                    Object parentId
                    Object details
                    Object result
                    String detailsType
                    String resultType
                    long started
                    long finished
                }
            }

            def registrar = services.get($BuildOperationNotificationListenerRegistrar.name)            
            gradle.buildFinished {
                listener.store(file('${jsonFile().toURI()}'))
            }
        """
    }

    private TestFile jsonFile() {
        dir.file('buildOpNotifications.json')
    }


}
