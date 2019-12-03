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
import com.google.common.collect.Sets
import groovy.json.JsonSlurper
import org.gradle.internal.operations.trace.BuildOperationTrace
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
        has(started, type, payload ? payloadEquals(payload) : { true } as Predicate)
    }

    private static Predicate<? super Map<String, ?>> payloadEquals(Map<String, ?> expectedPayload) {
        { actualPayload ->
            def present = Sets.intersection(actualPayload.keySet(), expectedPayload.keySet())
            for (String key : present) {
                if (!testValue(expectedPayload[key], actualPayload[key])) {
                    return false
                }
            }
            true
        } as Predicate
    }

    private static boolean testValue(expectedValue, actualValue) {
        if (expectedValue instanceof Closure) {
            expectedValue.call(actualValue)
        } else if (expectedValue instanceof Predicate) {
            expectedValue.apply(actualValue)
        } else {
            expectedValue == actualValue
        }
    }

    void has(boolean started, Class<?> type, Predicate<? super Map<String, ?>> payloadTest) {
        def typedOps = recordedOps.findAll { op ->
            return started ? op.detailsType == type.name : op.resultType == type.name
        }

        assert typedOps.size() > 0: "no operations of type $type"

        if (payloadTest != null) {
            def tested = []
            def matchingOps = typedOps.findAll { matchingOp ->
                def toTest = started ? matchingOp.details : matchingOp.result
                tested << toTest
                payloadTest.apply(toTest)
            }

            if (matchingOps.empty) {
                throw new AssertionError("Did not find match among:\n\n ${tested.join("\n")}")
            }
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
        registrar.register(listener)
        """
    }

    String listenerClass() {
        """
            def listener = new ${BuildOperationNotificationListener.name}() {

                def ops = [:]
            
                @Override
                synchronized void started(${BuildOperationStartedNotification.name} startedNotification) {
            
                    def details = ${BuildOperationTrace.name}.toSerializableModel(startedNotification.notificationOperationDetails)
                    def detailsType = startedNotification.notificationOperationDetails.getClass()
                    if (detailsType.interfaces.length > 0) {
                        detailsType = detailsType.interfaces[0]
                    }

                    ops.put(startedNotification.notificationOperationId, new BuildOpsEntry(id: startedNotification.notificationOperationId?.id,
                            parentId: startedNotification.notificationOperationParentId?.id,
                            detailsType: detailsType.name,
                            details: details, 
                            started: startedNotification.notificationOperationStartedTimestamp))
                }
                
                @Override
                synchronized void progress(${BuildOperationProgressNotification.name} progressNotification){
                    // Do nothing
                }
            
                @Override
                synchronized void finished(${BuildOperationFinishedNotification.name} finishedNotification) {
                    def result = ${BuildOperationTrace.name}.toSerializableModel(finishedNotification.getNotificationOperationResult())
                    def op = ops.get(finishedNotification.notificationOperationId)
                    op.resultType = finishedNotification.getNotificationOperationResult().getClass().getInterfaces()[0].getName()
                    op.result = result
                    op.finished = finishedNotification.getNotificationOperationFinishedTimestamp()
                }
            
                synchronized void store(File target){
                    target.withPrintWriter { pw ->
                        String json = groovy.json.JsonOutput.toJson(ops.values())
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
