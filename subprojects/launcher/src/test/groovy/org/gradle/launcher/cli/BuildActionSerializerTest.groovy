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

package org.gradle.launcher.cli

import org.gradle.api.internal.StartParameterInternal
import org.gradle.internal.serialize.SerializerSpec
import org.gradle.launcher.cli.action.BuildActionSerializer
import org.gradle.launcher.cli.action.ExecuteBuildAction
import org.gradle.tooling.internal.provider.BuildClientSubscriptions
import org.gradle.tooling.internal.provider.BuildModelAction
import org.gradle.tooling.internal.provider.ClientProvidedBuildAction
import org.gradle.tooling.internal.provider.TestExecutionRequestAction
import org.gradle.tooling.internal.provider.serialization.SerializedPayload

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
        startParameter.addDeprecation('warning 1')
        startParameter.addDeprecation('warning 2')
        def action = new ExecuteBuildAction(startParameter)

        expect:
        def result = serialize(action, BuildActionSerializer.create())
        result instanceof ExecuteBuildAction
        result.startParameter.taskNames == ['a', 'b']
        result.startParameter.deprecations == ['warning 1', 'warning 2'] as Set
    }

    def "serializes other actions"() {
        expect:
        def result = serialize(action, BuildActionSerializer.create())
        result.class == action.class

        where:
        action << [
            new ClientProvidedBuildAction(new StartParameterInternal(), new SerializedPayload(null, []), true, new BuildClientSubscriptions(true, true, true)),
            new TestExecutionRequestAction(new BuildClientSubscriptions(true, true, true), new StartParameterInternal(), Collections.emptySet(), Collections.emptySet(), Collections.emptySet()),
            new BuildModelAction(new StartParameterInternal(), "model", false, new BuildClientSubscriptions(true, true, true))
        ]
    }
}
