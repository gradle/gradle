/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.tooling.internal.provider.runner

import org.gradle.api.internal.StartParameterInternal
import org.gradle.internal.build.event.BuildEventSubscriptions
import org.gradle.internal.buildtree.BuildTreeLifecycleController
import org.gradle.internal.buildtree.BuildTreeModelAction
import org.gradle.internal.buildtree.BuildTreeModelController
import org.gradle.internal.buildtree.BuildTreeModelTarget
import org.gradle.internal.buildtree.ToolingModelRequestContext
import org.gradle.tooling.internal.provider.action.GrpcModelQueryAction
import org.gradle.tooling.internal.provider.serialization.PayloadSerializer
import org.gradle.tooling.internal.provider.serialization.SerializedPayload
import org.gradle.tooling.provider.model.internal.ToolingModelBuilderResultInternal
import spock.lang.Specification

class GrpcModelQueryActionRunnerTest extends Specification {

    def startParameter = Mock(StartParameterInternal)
    def clientSubscriptions = Mock(BuildEventSubscriptions)
    def payloadSerializer = Mock(PayloadSerializer)
    def buildController = Mock(BuildTreeLifecycleController)
    def modelController = Mock(BuildTreeModelController)
    def runner = new GrpcModelQueryActionRunner(payloadSerializer)

    def "targets the requested project by its logical path"() {
        given:
        def rootDir = new File("root")
        def action = new GrpcModelQueryAction(startParameter, "com.example.Model", rootDir, ":app", new byte[0], clientSubscriptions)
        def modelResult = Mock(ToolingModelBuilderResultInternal)
        def model = new Object()
        def serialized = Stub(SerializedPayload)
        BuildTreeModelTarget capturedTarget = null

        when:
        def result = runner.run(action, buildController)

        then:
        1 * buildController.fromBuildModel(false, _) >> { boolean runTasks, BuildTreeModelAction a -> a.fromBuildModel(modelController) }
        1 * modelController.getModel(_, _) >> { BuildTreeModelTarget target, ignored -> capturedTarget = target; modelResult }
        1 * modelResult.getModel() >> model
        1 * payloadSerializer.serialize(model) >> serialized

        and:
        result.clientResult == serialized
        capturedTarget instanceof BuildTreeModelTarget.Project
        (capturedTarget as BuildTreeModelTarget.Project).buildRootDir == rootDir
        (capturedTarget as BuildTreeModelTarget.Project).projectPath.toString() == ":app"
    }

    def "targets the default project when no path is given"() {
        given:
        def action = new GrpcModelQueryAction(startParameter, "com.example.Model", new File("root"), "", new byte[0], clientSubscriptions)
        def modelResult = Mock(ToolingModelBuilderResultInternal)
        BuildTreeModelTarget capturedTarget = null

        when:
        runner.run(action, buildController)

        then:
        1 * buildController.fromBuildModel(false, _) >> { boolean runTasks, BuildTreeModelAction a -> a.fromBuildModel(modelController) }
        1 * modelController.getModel(_, _) >> { BuildTreeModelTarget target, ignored -> capturedTarget = target; modelResult }
        1 * modelResult.getModel() >> new Object()
        1 * payloadSerializer.serialize(_) >> Stub(SerializedPayload)

        and:
        capturedTarget instanceof BuildTreeModelTarget.Default
    }

    def "forwards the parameter bytes as a model request parameter"() {
        given:
        def bytes = [1, 2, 3] as byte[]
        def action = new GrpcModelQueryAction(startParameter, "com.example.Model", new File("root"), "", bytes, clientSubscriptions)
        def modelResult = Mock(ToolingModelBuilderResultInternal)
        ToolingModelRequestContext capturedContext = null

        when:
        runner.run(action, buildController)

        then:
        1 * buildController.fromBuildModel(false, _) >> { boolean runTasks, BuildTreeModelAction a -> a.fromBuildModel(modelController) }
        1 * modelController.getModel(_, _) >> { ignored, ToolingModelRequestContext ctx -> capturedContext = ctx; modelResult }
        1 * modelResult.getModel() >> new Object()
        1 * payloadSerializer.serialize(_) >> Stub(SerializedPayload)

        and:
        capturedContext.parameter.present
        capturedContext.parameter.get() instanceof GrpcToolingModelParameter
        (capturedContext.parameter.get() as GrpcToolingModelParameter).parameterBytes == bytes
    }

    def "sends no parameter when the parameter bytes are empty"() {
        given:
        def action = new GrpcModelQueryAction(startParameter, "com.example.Model", new File("root"), "", new byte[0], clientSubscriptions)
        def modelResult = Mock(ToolingModelBuilderResultInternal)
        ToolingModelRequestContext capturedContext = null

        when:
        runner.run(action, buildController)

        then:
        1 * buildController.fromBuildModel(false, _) >> { boolean runTasks, BuildTreeModelAction a -> a.fromBuildModel(modelController) }
        1 * modelController.getModel(_, _) >> { ignored, ToolingModelRequestContext ctx -> capturedContext = ctx; modelResult }
        1 * modelResult.getModel() >> new Object()
        1 * payloadSerializer.serialize(_) >> Stub(SerializedPayload)

        and:
        !capturedContext.parameter.present
    }

    def "ignores actions it does not handle"() {
        expect:
        runner.run(Mock(org.gradle.internal.invocation.BuildAction), buildController) == org.gradle.internal.buildtree.BuildActionRunner.Result.nothing()
    }
}
