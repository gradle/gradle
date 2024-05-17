/*
 * Copyright 2013 the original author or authors.
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

import org.gradle.api.BuildCancelledException
import org.gradle.api.internal.project.ProjectState
import org.gradle.initialization.BuildCancellationToken
import org.gradle.internal.build.BuildProjectRegistry
import org.gradle.internal.build.BuildState
import org.gradle.internal.build.BuildStateRegistry
import org.gradle.internal.buildtree.BuildTreeModelController
import org.gradle.internal.work.WorkerThreadRegistry
import org.gradle.tooling.internal.gradle.GradleBuildIdentity
import org.gradle.tooling.internal.gradle.GradleProjectIdentity
import org.gradle.tooling.internal.protocol.InternalUnsupportedModelException
import org.gradle.tooling.internal.protocol.ModelIdentifier
import org.gradle.tooling.provider.model.UnknownModelException
import org.gradle.tooling.provider.model.internal.ToolingModelScope
import org.gradle.util.Path
import spock.lang.Specification

import java.util.function.Consumer
import java.util.function.Function
import java.util.function.Supplier

class DefaultBuildControllerTest extends Specification {
    def cancellationToken = Stub(BuildCancellationToken)
    def modelId = Stub(ModelIdentifier) {
        getName() >> 'some.model'
    }
    def modelScope = Mock(ToolingModelScope)
    def buildStateRegistry = Mock(BuildStateRegistry)
    def modelController = Mock(BuildTreeModelController)
    def workerThreadRegistry = Mock(WorkerThreadRegistry)
    def controller = new DefaultBuildController(modelController, workerThreadRegistry, cancellationToken, buildStateRegistry)

    def "cannot get build model from unmanaged thread"() {
        given:
        _ * workerThreadRegistry.workerThread >> false

        when:
        controller.getBuildModel()

        then:
        IllegalStateException e = thrown()
        e.message == "A build controller cannot be used from a thread that is not managed by Gradle."
    }

    def "adapts model not found exception to protocol exception"() {
        def failure = new UnknownModelException("not found")

        given:
        _ * workerThreadRegistry.workerThread >> true
        1 * modelController.locateBuilderForDefaultTarget('some.model', false) >> modelScope
        1 * modelScope.getModel("some.model", null) >> { throw failure }

        when:
        controller.getModel(null, modelId)

        then:
        InternalUnsupportedModelException e = thrown()
        e.cause == failure
    }

    def "cannot get model from unmanaged thread"() {
        given:
        _ * workerThreadRegistry.workerThread >> false

        when:
        controller.getModel(null, modelId)

        then:
        IllegalStateException e = thrown()
        e.message == "A build controller cannot be used from a thread that is not managed by Gradle."
    }

    def "uses builder for specified project"() {
        def rootDir = new File("dummy")
        def target = Stub(GradleProjectIdentity)
        def buildState1 = Stub(BuildState)
        def buildState2 = Stub(BuildState)
        def buildState3 = Stub(BuildState)
        def projects3 = Stub(BuildProjectRegistry)
        def projectState = Stub(ProjectState)
        def model = new Object()

        given:
        _ * workerThreadRegistry.workerThread >> true
        _ * target.projectPath >> ":some:path"
        _ * target.rootDir >> rootDir
        _ * buildStateRegistry.visitBuilds(_) >> { Consumer consumer ->
            consumer.accept(buildState1)
            consumer.accept(buildState2)
            consumer.accept(buildState3)
        }
        _ * buildState1.importableBuild >> false
        _ * buildState2.importableBuild >> true
        _ * buildState2.buildRootDir >> new File("different")
        _ * buildState3.importableBuild >> true
        _ * buildState3.buildRootDir >> rootDir
        _ * buildState3.projects >> projects3
        _ * projects3.getProject(Path.path(":some:path")) >> projectState
        _ * modelController.locateBuilderForTarget(projectState, "some.model", false) >> modelScope
        _ * modelScope.getModel("some.model", null) >> model

        when:
        def result = controller.getModel(target, modelId)

        then:
        result.getModel() == model
    }

    def "uses builder for specified build"() {
        def rootDir = new File("dummy")
        def target = Stub(GradleBuildIdentity)
        def buildState1 = Stub(BuildState)
        def buildState2 = Stub(BuildState)
        def model = new Object()

        given:
        _ * workerThreadRegistry.workerThread >> true
        _ * target.rootDir >> rootDir
        _ * buildStateRegistry.visitBuilds(_) >> { Consumer consumer ->
            consumer.accept(buildState1)
            consumer.accept(buildState2)
        }
        _ * buildState1.importableBuild >> false
        _ * buildState2.importableBuild >> true
        _ * buildState2.buildRootDir >> rootDir
        _ * modelController.locateBuilderForTarget(buildState2, "some.model", false) >> modelScope
        _ * modelScope.getModel("some.model", null) >> model

        when:
        def result = controller.getModel(target, modelId)

        then:
        result.getModel() == model
    }

    def "uses builder for default project when none specified"() {
        def model = new Object()

        given:
        _ * workerThreadRegistry.workerThread >> true
        _ * modelController.locateBuilderForDefaultTarget("some.model", false) >> modelScope
        _ * modelScope.getModel("some.model", null) >> model

        when:
        def result = controller.getModel(null, modelId)

        then:
        result.getModel() == model
    }

    def "throws an exception when cancel was requested"() {
        given:
        _ * workerThreadRegistry.workerThread >> true
        _ * cancellationToken.cancellationRequested >> true
        def target = Stub(GradleProjectIdentity)

        when:
        controller.getModel(target, modelId)

        then:
        thrown(BuildCancelledException)
    }

    def "uses parameterized builder when parameter is not null"() {
        def model = new Object()
        def parameterType = CustomParameter.class
        def parameter = new CustomParameter() {
            @Override
            String getValue() {
                return "myValue"
            }

            @Override
            void setValue(String value) {}
        }

        given:
        _ * workerThreadRegistry.workerThread >> true
        _ * modelController.locateBuilderForDefaultTarget("some.model", true) >> modelScope
        _ * modelScope.getParameterType() >> parameterType
        _ * modelScope.getModel("some.model", _) >> { String name, Function param ->
            assert param != null
            assert param.apply(CustomParameter.class) == parameter
            return model
        }

        when:
        def result = controller.getModel(null, modelId, parameter)

        then:
        result.getModel() == model
    }

    def "runs supplied actions"() {
        def action1 = Mock(Supplier)
        def action2 = Mock(Supplier)
        def action3 = Mock(Supplier)

        when:
        def result = controller.run([action1, action2, action3])

        then:
        result == ["one", "two", "three"]

        _ * workerThreadRegistry.workerThread >> true
        1 * modelController.runQueryModelActions(_) >> { def params ->
            def actions = params[0]
            actions.collect { it.get() }
        }
        1 * action1.get() >> "one"
        1 * action2.get() >> "two"
        1 * action3.get() >> "three"
        0 * _
    }

    def "cannot run actions from unmanaged thread"() {
        given:
        _ * workerThreadRegistry.workerThread >> false

        when:
        controller.run([Stub(Supplier)])

        then:
        def e = thrown(IllegalStateException)
        e.message == "A build controller cannot be used from a thread that is not managed by Gradle."
    }

    interface CustomParameter {
        String getValue()

        void setValue(String value)
    }
}
