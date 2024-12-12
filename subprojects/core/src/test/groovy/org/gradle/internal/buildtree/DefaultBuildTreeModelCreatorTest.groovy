/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.buildtree

import org.gradle.internal.build.BuildState
import org.gradle.internal.build.BuildStateRegistry
import org.gradle.internal.build.BuildToolingModelController
import org.gradle.internal.operations.TestBuildOperationRunner
import org.gradle.tooling.provider.model.internal.ToolingModelParameterCarrier
import org.gradle.tooling.provider.model.internal.ToolingModelScope
import spock.lang.Specification

import javax.annotation.Nullable
import java.util.function.Consumer
import java.util.function.Function

class DefaultBuildTreeModelCreatorTest extends Specification {

    def buildOperationRunner = new TestBuildOperationRunner()

    def "importable builds can be used as targets for model building"() {
        given:
        def model = new Object()

        def modelScope = Mock(ToolingModelScope) {
            getModel(_, _) >> model
        }
        def modelController = Mock(BuildToolingModelController) {
            locateBuilderForTarget(_, _) >> modelScope
        }

        def buildRootDir = new File("dummy")
        def buildState1 = Stub(BuildState) {
            isImportableBuild() >> true
            getBuildRootDir() >> buildRootDir
            withToolingModels(_) >> { Function action ->
                action.apply(modelController)
            }
        }

        def buildStateRegistry = Mock(BuildStateRegistry) {
            visitBuilds(_) >> { Consumer consumer ->
                consumer.accept(buildState1)
            }
        }

        def modelCreator = new DefaultBuildTreeModelCreator(
            Mock(BuildState),
            Mock(IntermediateBuildActionRunner),
            Mock(ToolingModelParameterCarrier.Factory),
            buildStateRegistry,
            buildOperationRunner
        )

        when:
        def actualModel = getModel(modelCreator, BuildTreeModelTarget.ofBuild(buildRootDir), "model", null)

        then:
        actualModel == model
    }

    def "non-importable builds cannot be used as targets for model building"() {
        given:
        def buildRootDir = new File("dummy")

        def buildState1 = Stub(BuildState) {
            isImportableBuild() >> false
            getBuildRootDir() >> buildRootDir
        }

        def buildStateRegistry = Mock(BuildStateRegistry) {
            visitBuilds(_) >> { Consumer consumer ->
                consumer.accept(buildState1)
            }
        }

        def modelCreator = new DefaultBuildTreeModelCreator(
            Mock(BuildState),
            Mock(IntermediateBuildActionRunner),
            Mock(ToolingModelParameterCarrier.Factory),
            buildStateRegistry,
            buildOperationRunner
        )

        when:
        getModel(modelCreator, BuildTreeModelTarget.ofBuild(buildRootDir), "model", null)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "dummy is not included in this build"
    }

    private static Object getModel(DefaultBuildTreeModelCreator modelCreator, BuildTreeModelTarget target, String modelName, @Nullable Object parameter) {
        return modelCreator.fromBuildModel(new BuildTreeModelAction<Object>() {
            @Override
            void beforeTasks(BuildTreeModelController controller) {}

            @Override
            Object fromBuildModel(BuildTreeModelController controller) {
                return controller.getModel(target, modelName, parameter)
            }
        })
    }
}
