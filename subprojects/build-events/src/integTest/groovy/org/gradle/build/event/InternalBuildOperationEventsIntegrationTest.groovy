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

package org.gradle.build.event

import org.gradle.api.internal.tasks.execution.ExecuteTaskBuildOperationType
import org.gradle.api.services.BuildServiceParameters
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.internal.operations.BuildOperationDescriptor
import org.gradle.internal.operations.BuildOperationListener
import org.gradle.internal.operations.OperationFinishEvent
import org.gradle.internal.operations.OperationIdentifier
import org.gradle.internal.operations.OperationProgressEvent
import org.gradle.internal.operations.OperationStartEvent
import org.gradle.test.fixtures.file.TestFile

class InternalBuildOperationEventsIntegrationTest extends AbstractIntegrationSpec {
    def "init script can inject listener via use internal API to subscribe to build operation events"() {
        def initScript = file("init.gradle")
        loggingListener(initScript)
        initScript << """
            def listener = gradle.sharedServices.registerIfAbsent("listener", LoggingListener) { }
            def registry = services.get(BuildEventsListenerRegistry)
            registry.onOperationCompletion(listener)
        """

        buildFile << """
            task a
            task b
        """
        executer.beforeExecute {
            usingInitScript(initScript)
        }

        when:
        run("a")

        then:
        output.count("EVENT:") == 1

        when:
        run("a")

        then:
        output.count("EVENT:") == 1

        when:
        run("a", "b")

        then:
        output.count("EVENT:") == 2

        when:
        run("a", "b")

        then:
        output.count("EVENT:") == 2
    }

    def "init script listener receives events from buildSrc and main build"() {
        def initScript = file("init.gradle")
        loggingListener(initScript)
        initScript << """
            if (gradle.parent == null) {
                def listener = gradle.sharedServices.registerIfAbsent("listener", LoggingListener) { }
                def registry = services.get(BuildEventsListenerRegistry)
                registry.onOperationCompletion(listener)
            }
        """

        file("buildSrc/src/main/java/Thing.java") << """
            class Thing { }
        """
        buildFile << """
            task a
        """
        executer.beforeExecute {
            usingInitScript(initScript)
        }

        when:
        run("a")

        then:
        output.count("EVENT:") == 6
        outputContains("EVENT: task ':a'")

        when:
        run("a")

        then:
        outputContains("EVENT: task ':a'")
    }

    void loggingListener(TestFile file) {
        file << """
            import ${BuildEventsListenerRegistry.name}
            import ${BuildOperationListener.name}
            import ${BuildOperationDescriptor.name}
            import ${OperationStartEvent.name}
            import ${OperationFinishEvent.name}
            import ${OperationProgressEvent.name}
            import ${OperationIdentifier.name}
            import ${ExecuteTaskBuildOperationType.name}
            import ${BuildServiceParameters.name}

            abstract class LoggingListener implements BuildOperationListener, BuildService<BuildServiceParameters.None> {
                void started(BuildOperationDescriptor buildOperation, OperationStartEvent startEvent) {
                    throw new RuntimeException()
                }
                void progress(OperationIdentifier operationIdentifier, OperationProgressEvent progressEvent) {
                    throw new RuntimeException()
                }
                void finished(BuildOperationDescriptor buildOperation, OperationFinishEvent finishEvent) {
                    if (buildOperation.details instanceof ExecuteTaskBuildOperationType.Details) {
                        println("EVENT: " + buildOperation.details.task)
                    }
                }
            }
        """
    }
}
