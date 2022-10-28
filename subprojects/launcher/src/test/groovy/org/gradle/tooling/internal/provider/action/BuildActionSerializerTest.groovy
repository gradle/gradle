/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.tooling.internal.provider.action

import org.gradle.api.internal.StartParameterInternal
import org.gradle.internal.build.event.BuildEventSubscriptions
import org.gradle.internal.serialize.SerializerSpec
import org.gradle.tooling.events.OperationType
import org.gradle.tooling.events.test.internal.DefaultDebugOptions
import org.gradle.tooling.internal.provider.serialization.SerializedPayload

import java.beans.Introspector

class BuildActionSerializerTest extends SerializerSpec {
    def "serializes ExecuteBuildAction with all defaults"() {
        def action = new ExecuteBuildAction(new StartParameterInternal())

        expect:
        def result = serialize(action, BuildActionSerializer.create())
        result instanceof ExecuteBuildAction
    }

    def "serializes ExecuteBuildAction with non-defaults"() {
        def startParameter = new StartParameterInternal()
        startParameter.taskNames = ['a', 'b']
        def action = new ExecuteBuildAction(startParameter)

        expect:
        def result = serialize(action, BuildActionSerializer.create())
        result instanceof ExecuteBuildAction
        result.startParameter.taskNames == ['a', 'b']
    }

    def "serializes #buildOptionName boolean build option"() {
        def startParameter = new StartParameterInternal()
        boolean expectedValue = !startParameter."${buildOptionName}"
        startParameter."${buildOptionName}" = expectedValue
        def action = new ExecuteBuildAction(startParameter)

        expect:
        def result = serialize(action, BuildActionSerializer.create())
        result instanceof ExecuteBuildAction
        result.startParameter."${buildOptionName}" == expectedValue

        where:
        // Check all mutable boolean properties (must manually check for setters as many of them return StartParameter)
        buildOptionName << Introspector.getBeanInfo(StartParameterInternal).propertyDescriptors
            .findAll { it.propertyType == boolean }
            .findAll { p -> StartParameterInternal.methods.find { m -> m.name == "set" + p.name.capitalize() && m.parameterCount == 1 && m.parameterTypes[0] == Boolean.TYPE } }
            .collect { it.name }
    }

    def "serializes BuildModelAction"() {
        def startParameter = new StartParameterInternal()
        startParameter.taskNames = ['a', 'b']
        def action = new BuildModelAction(startParameter, "model", true, new BuildEventSubscriptions([OperationType.TASK] as Set))

        expect:
        def result = serialize(action, BuildActionSerializer.create())
        result instanceof BuildModelAction
        result.startParameter.taskNames == ['a', 'b']
        result.modelName == "model"
        result.runTasks
        result.clientSubscriptions.operationTypes == [OperationType.TASK] as Set
    }

    def "serializes ClientProvidedBuildAction"() {
        def startParameter = new StartParameterInternal()
        startParameter.taskNames = ['a', 'b']
        def action = new ClientProvidedBuildAction(startParameter, new SerializedPayload("12", []), true, new BuildEventSubscriptions([OperationType.TASK] as Set))

        expect:
        def result = serialize(action, BuildActionSerializer.create())
        result instanceof ClientProvidedBuildAction
        result.startParameter.taskNames == ['a', 'b']
        result.action.header == "12"
        result.runTasks
        result.clientSubscriptions.operationTypes == [OperationType.TASK] as Set
    }

    def "serializes ClientProvidedPhasedAction"() {
        def startParameter = new StartParameterInternal()
        startParameter.taskNames = ['a', 'b']
        def action = new ClientProvidedPhasedAction(startParameter, new SerializedPayload("12", []), true, new BuildEventSubscriptions([OperationType.TASK] as Set))

        expect:
        def result = serialize(action, BuildActionSerializer.create())
        result instanceof ClientProvidedPhasedAction
        result.startParameter.taskNames == ['a', 'b']
        result.phasedAction.header == "12"
        result.runTasks
        result.clientSubscriptions.operationTypes == [OperationType.TASK] as Set
    }

    def "serializes TestExecutionRequestAction"() {
        def startParameter = new StartParameterInternal()
        def action = new TestExecutionRequestAction(new BuildEventSubscriptions([OperationType.TASK] as Set), startParameter, Collections.emptySet(), Collections.emptySet(), Collections.emptySet(), new DefaultDebugOptions(), Collections.emptyMap(), false, Collections.emptyList())

        expect:
        def result = serialize(action, BuildActionSerializer.create())
        result instanceof TestExecutionRequestAction
        result.clientSubscriptions.operationTypes == [OperationType.TASK] as Set
    }
}
