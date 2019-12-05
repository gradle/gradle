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
import org.gradle.integtests.fixtures.InstantExecutionRunner
import org.gradle.internal.operations.BuildOperationDescriptor
import org.gradle.internal.operations.BuildOperationListener
import org.gradle.internal.operations.OperationFinishEvent
import org.gradle.internal.operations.OperationIdentifier
import org.gradle.internal.operations.OperationProgressEvent
import org.gradle.internal.operations.OperationStartEvent
import org.junit.runner.RunWith

@RunWith(InstantExecutionRunner)
class InternalBuildOperationEventsIntegrationTest extends AbstractIntegrationSpec {
    def "init script can inject listener via use internal API to subscribe to build operation events"() {
        def initScript = file("init.gradle") << """
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
                        println("EVENT: " + buildOperation.details)
                    }
                }
            }

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
}
