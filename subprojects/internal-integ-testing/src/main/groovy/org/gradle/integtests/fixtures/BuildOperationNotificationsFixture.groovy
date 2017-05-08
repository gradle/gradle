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
import org.gradle.api.execution.internal.TaskOperationDetails
import org.gradle.integtests.fixtures.executer.GradleExecuter
import org.gradle.integtests.fixtures.executer.InitScriptExecuterFixture
import org.gradle.internal.operations.notify.BuildOperationFinishedNotification
import org.gradle.internal.operations.notify.BuildOperationNotificationListener
import org.gradle.internal.operations.notify.BuildOperationNotificationListenerRegistrar
import org.gradle.internal.operations.notify.BuildOperationStartedNotification
import org.gradle.test.fixtures.file.TestDirectoryProvider
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.TextUtil

class BuildOperationNotificationsFixture extends InitScriptExecuterFixture {

    private final TestFile operationsDir
    private final Map<Object, CompleteOperation> operations = [:]

    BuildOperationNotificationsFixture(GradleExecuter executer, TestDirectoryProvider projectDir) {
        super(executer, projectDir)
        this.operationsDir = projectDir.testDirectory.file("operations")
    }

    @Override
    String initScriptContent() {
        return """
            import ${BuildOperationNotificationListener.name}
            import ${BuildOperationStartedNotification.name}
            import ${BuildOperationFinishedNotification.name}

            def operations = [:]
            def listener = new BuildOperationNotificationListener() {
                void started(BuildOperationStartedNotification notification) {
                    if (notification.details instanceof ${TaskOperationDetails.name}) {
                        return // this type is not serializable
                    }
                    
                    operations[notification.id] = [
                        id: notification.id,
                        details: notification.details,
                        detailsType: notification.details.class.name
                    ] 
                }

                void finished(BuildOperationFinishedNotification notification) {
                    def o = operations[notification.id]
                    if (o != null && notification.result != null) {
                        o.putAll(
                            result: notification.result,
                            resultType: notification.result.class.name
                        )
                    }
                }
            }

            gradle.services.get(${BuildOperationNotificationListenerRegistrar.name}).registerBuildScopeListener(listener)

            gradle.buildFinished {
                def operationsDir = new File("${TextUtil.normaliseFileSeparators(operationsDir.absolutePath)}")
                operationsDir.mkdirs()
                def jsonFile = new File(operationsDir, "operations.json")
                jsonFile.text = groovy.json.JsonOutput.toJson(operations)
            }
        """
    }

    @Override
    void afterBuild() {
        def jsonFile = new File(operationsDir, "operations.json")
        def slurper = new JsonSlurper()
        def rawOperations = slurper.parseText(jsonFile.text)
        rawOperations.each { k, v ->
            operations.put(k, new CompleteOperation(
                v.id,
                v.parentId,
                getClass().classLoader.loadClass(v.detailsType.toString()),
                v.details as Map<String, ?>,
                v.resultType == null ? null : getClass().classLoader.loadClass(v.resultType.toString()),
                v.result as Map<String, ?>
            ))
        }
    }

    CompleteOperation first(Class<?> detailsType) {
        def operation = operations.values().find { it.detailsType == detailsType }
        if (operation == null) {
            throw new AssertionError("No operation with details type $detailsType found (found types: ${operations.values().detailsType.toSet()*.name.join(", ")})")
        }
        operation
    }

    static class CompleteOperation {

        final Object id
        final Object parentId
        final Class<?> detailsType
        final Map<String, ?> details
        final Class<?> resultType
        final Map<String, ?> result

        CompleteOperation(Object id, Object parentId, Class<?> detailsType, Map<String, ?> details, Class<?> resultType, Map<String, ?> result) {
            this.id = id
            this.parentId = parentId
            this.resultType = resultType
            this.detailsType = detailsType
            this.details = details
            this.result = result
        }
    }

}
