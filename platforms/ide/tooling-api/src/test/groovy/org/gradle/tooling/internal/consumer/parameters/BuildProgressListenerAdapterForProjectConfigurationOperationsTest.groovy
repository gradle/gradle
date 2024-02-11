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

package org.gradle.tooling.internal.consumer.parameters

import spock.lang.Specification
import org.gradle.tooling.events.BinaryPluginIdentifier
import org.gradle.tooling.events.OperationType
import org.gradle.tooling.events.ProgressListener
import org.gradle.tooling.events.ScriptPluginIdentifier
import org.gradle.tooling.events.configuration.ProjectConfigurationFailureResult
import org.gradle.tooling.events.configuration.ProjectConfigurationFinishEvent
import org.gradle.tooling.events.configuration.ProjectConfigurationStartEvent
import org.gradle.tooling.events.configuration.ProjectConfigurationSuccessResult
import org.gradle.tooling.internal.protocol.InternalBuildProgressListener
import org.gradle.tooling.internal.protocol.InternalFailure
import org.gradle.tooling.internal.protocol.events.InternalBinaryPluginIdentifier
import org.gradle.tooling.internal.protocol.events.InternalFailureResult
import org.gradle.tooling.internal.protocol.events.InternalOperationFinishedProgressEvent
import org.gradle.tooling.internal.protocol.events.InternalOperationStartedProgressEvent
import org.gradle.tooling.internal.protocol.events.InternalProjectConfigurationDescriptor
import org.gradle.tooling.internal.protocol.events.InternalProjectConfigurationResult
import org.gradle.tooling.internal.protocol.events.InternalProjectConfigurationResult.InternalPluginApplicationResult
import org.gradle.tooling.internal.protocol.events.InternalScriptPluginIdentifier
import org.gradle.tooling.internal.protocol.events.InternalSuccessResult

import java.time.Duration

import static org.junit.Assert.assertTrue

class BuildProgressListenerAdapterForProjectConfigurationOperationsTest extends Specification {

    def "adapter is only subscribing to test progress events if at least one test progress listener is attached"() {
        when:
        def adapter = createAdapter()

        then:
        adapter.subscribedOperations == []

        when:
        def listener = Mock(ProgressListener)
        adapter = createAdapter(listener)

        then:
        adapter.subscribedOperations == [InternalBuildProgressListener.PROJECT_CONFIGURATION_EXECUTION]
    }

    def "convert to ProjectConfigurationStartEvent"() {
        given:
        def listener = Mock(ProgressListener)
        def adapter = createAdapter(listener)
        def rootDir = new File(".").getAbsoluteFile()

        when:
        def projectConfigurationDescriptor = Mock(InternalProjectConfigurationDescriptor)
        _ * projectConfigurationDescriptor.getId() >> 1
        _ * projectConfigurationDescriptor.getName() >> 'Project configuration'
        _ * projectConfigurationDescriptor.getDisplayName() >> 'Project configuration'
        _ * projectConfigurationDescriptor.getRootDir() >> rootDir
        _ * projectConfigurationDescriptor.getProjectPath() >> ":some:project"

        def startEvent = Mock(InternalOperationStartedProgressEvent)
        _ * startEvent.getEventTime() >> 999
        _ * startEvent.getDisplayName() >> 'Project configuration started'
        _ * startEvent.getDescriptor() >> projectConfigurationDescriptor

        adapter.onEvent(startEvent)

        then:
        1 * listener.statusChanged(_ as ProjectConfigurationStartEvent) >> { ProjectConfigurationStartEvent event ->
            assertTrue event.eventTime == 999
            assertTrue event.displayName == "Project configuration started"
            assertTrue event.descriptor.displayName == 'Project configuration'
            assertTrue event.descriptor.project.buildIdentifier.rootDir == rootDir
            assertTrue event.descriptor.project.projectPath == ":some:project"
        }
    }

    def "convert to ProjectConfigurationSuccessResult"() {
        given:
        def listener = Mock(ProgressListener)
        def adapter = createAdapter(listener)
        def rootDir = new File(".").getAbsoluteFile()

        when:
        def projectConfigurationDescriptor = Mock(InternalProjectConfigurationDescriptor)
        _ * projectConfigurationDescriptor.getId() >> 1
        _ * projectConfigurationDescriptor.getName() >> 'Project configuration'
        _ * projectConfigurationDescriptor.getDisplayName() >> 'Project configuration'
        _ * projectConfigurationDescriptor.getRootDir() >> new File(".").getAbsoluteFile()
        _ * projectConfigurationDescriptor.getProjectPath() >> ":some:project"

        def startEvent = Mock(InternalOperationStartedProgressEvent)
        _ * startEvent.getEventTime() >> 999
        _ * startEvent.getDisplayName() >> 'Project configuration started'
        _ * startEvent.getDescriptor() >> projectConfigurationDescriptor

        def binaryPluginApplicationResult = Stub(InternalPluginApplicationResult)
        _ * binaryPluginApplicationResult.getPlugin() >> Stub(InternalBinaryPluginIdentifier) {
            getClassName() >> 'com.acme.SomePlugin'
            getPluginId() >> 'com.acme.some'
        }
        _ * binaryPluginApplicationResult.getTotalConfigurationTime() >> Duration.ofMillis(23)

        def scriptPluginApplicationResult = Stub(InternalPluginApplicationResult)
        _ * scriptPluginApplicationResult.getPlugin() >> Stub(InternalScriptPluginIdentifier) {
            getUri() >> new File(rootDir, "build.gradle").toURI()
        }
        _ * scriptPluginApplicationResult.getTotalConfigurationTime() >> Duration.ofMillis(42)

        def projectConfigurationResult = Mock(InternalProjectConfigurationResult, additionalInterfaces: [InternalSuccessResult])
        _ * projectConfigurationResult.getStartTime() >> 1
        _ * projectConfigurationResult.getEndTime() >> 2
        _ * projectConfigurationResult.getPluginApplicationResults() >> [binaryPluginApplicationResult, scriptPluginApplicationResult]

        def succeededEvent = Mock(InternalOperationFinishedProgressEvent)
        _ * succeededEvent.getEventTime() >> 999
        _ * succeededEvent.getDisplayName() >> 'Project configuration succeeded'
        _ * succeededEvent.getDescriptor() >> projectConfigurationDescriptor
        _ * succeededEvent.getResult() >> projectConfigurationResult

        adapter.onEvent(startEvent) // succeededEvent always assumes a previous startEvent
        adapter.onEvent(succeededEvent)

        then:
        1 * listener.statusChanged(_ as ProjectConfigurationFinishEvent) >> { ProjectConfigurationFinishEvent event ->
            assertTrue event.eventTime == 999
            assertTrue event.displayName == "Project configuration succeeded"
            assertTrue event.descriptor.displayName == 'Project configuration'
            assertTrue event.descriptor.project.buildIdentifier.rootDir == rootDir
            assertTrue event.descriptor.project.projectPath == ":some:project"
            assertTrue event.descriptor.parent == null
            assertTrue event.result instanceof ProjectConfigurationSuccessResult
            assertTrue event.result.startTime == 1
            assertTrue event.result.endTime == 2
            with((ProjectConfigurationSuccessResult) event.result) {
                assertTrue pluginApplicationResults.size() == 2
                assertTrue pluginApplicationResults[0].plugin instanceof BinaryPluginIdentifier
                assertTrue pluginApplicationResults[0].plugin.className == 'com.acme.SomePlugin'
                assertTrue pluginApplicationResults[0].plugin.pluginId == 'com.acme.some'
                assertTrue pluginApplicationResults[0].totalConfigurationTime == Duration.ofMillis(23)
                assertTrue pluginApplicationResults[1].plugin instanceof ScriptPluginIdentifier
                assertTrue pluginApplicationResults[1].plugin.uri == new File(rootDir, "build.gradle").toURI()
                assertTrue pluginApplicationResults[1].totalConfigurationTime == Duration.ofMillis(42)
            }
        }
    }

    def "convert to ProjectConfigurationFailureResult"() {
        given:
        def listener = Mock(ProgressListener)
        def adapter = createAdapter(listener)
        def rootDir = new File(".").getAbsoluteFile()

        when:
        def projectConfigurationDescriptor = Mock(InternalProjectConfigurationDescriptor)
        _ * projectConfigurationDescriptor.getId() >> 1
        _ * projectConfigurationDescriptor.getName() >> 'Project configuration'
        _ * projectConfigurationDescriptor.getDisplayName() >> 'Project configuration'
        _ * projectConfigurationDescriptor.getRootDir() >> new File(".").getAbsoluteFile()
        _ * projectConfigurationDescriptor.getProjectPath() >> ":some:project"

        def startEvent = Mock(InternalOperationStartedProgressEvent)
        _ * startEvent.getEventTime() >> 999
        _ * startEvent.getDisplayName() >> 'Project configuration started'
        _ * startEvent.getDescriptor() >> projectConfigurationDescriptor

        def binaryPluginApplicationResult = Stub(InternalPluginApplicationResult)
        _ * binaryPluginApplicationResult.getPlugin() >> Stub(InternalBinaryPluginIdentifier) {
            getClassName() >> 'com.acme.SomePlugin'
            getPluginId() >> 'com.acme.some'
        }
        _ * binaryPluginApplicationResult.getTotalConfigurationTime() >> Duration.ofMillis(23)

        def scriptPluginApplicationResult = Stub(InternalPluginApplicationResult)
        _ * scriptPluginApplicationResult.getPlugin() >> Stub(InternalScriptPluginIdentifier) {
            getUri() >> new File(rootDir, "build.gradle").toURI()
        }
        _ * scriptPluginApplicationResult.getTotalConfigurationTime() >> Duration.ofMillis(42)

        def projectConfigurationResult = Mock(InternalProjectConfigurationResult, additionalInterfaces: [InternalFailureResult])
        _ * projectConfigurationResult.getStartTime() >> 1
        _ * projectConfigurationResult.getEndTime() >> 2
        _ * projectConfigurationResult.getFailures() >> [Stub(InternalFailure)]
        _ * projectConfigurationResult.getPluginApplicationResults() >> [binaryPluginApplicationResult, scriptPluginApplicationResult]

        def failedEvent = Mock(InternalOperationFinishedProgressEvent)
        _ * failedEvent.getEventTime() >> 999
        _ * failedEvent.getDisplayName() >> 'Project configuration failed'
        _ * failedEvent.getDescriptor() >> projectConfigurationDescriptor
        _ * failedEvent.getResult() >> projectConfigurationResult

        adapter.onEvent(startEvent) // failedEvent always assumes a previous startEvent
        adapter.onEvent(failedEvent)

        then:
        1 * listener.statusChanged(_ as ProjectConfigurationFinishEvent) >> { ProjectConfigurationFinishEvent event ->
            assertTrue event.eventTime == 999
            assertTrue event.displayName == "Project configuration failed"
            assertTrue event.descriptor.displayName == 'Project configuration'
            assertTrue event.descriptor.project.buildIdentifier.rootDir == rootDir
            assertTrue event.descriptor.project.projectPath == ":some:project"
            assertTrue event.result instanceof ProjectConfigurationFailureResult
            assertTrue event.result.startTime == 1
            assertTrue event.result.endTime == 2
            assertTrue event.result.failures.size() == 1
            with((ProjectConfigurationFailureResult) event.result) {
                assertTrue pluginApplicationResults.size() == 2
                assertTrue pluginApplicationResults[0].plugin instanceof BinaryPluginIdentifier
                assertTrue pluginApplicationResults[0].plugin.className == 'com.acme.SomePlugin'
                assertTrue pluginApplicationResults[0].plugin.pluginId == 'com.acme.some'
                assertTrue pluginApplicationResults[0].totalConfigurationTime == Duration.ofMillis(23)
                assertTrue pluginApplicationResults[1].plugin instanceof ScriptPluginIdentifier
                assertTrue pluginApplicationResults[1].plugin.uri == new File(rootDir, "build.gradle").toURI()
                assertTrue pluginApplicationResults[1].totalConfigurationTime == Duration.ofMillis(42)
            }
        }
    }

    private static BuildProgressListenerAdapter createAdapter() {
        new BuildProgressListenerAdapter([:])
    }

    private static BuildProgressListenerAdapter createAdapter(ProgressListener testListener) {
        new BuildProgressListenerAdapter([(OperationType.PROJECT_CONFIGURATION): [testListener]])
    }

}
