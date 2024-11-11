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
import org.gradle.initialization.BuildCancellationToken
import org.gradle.initialization.BuildEventConsumer
import org.gradle.internal.buildtree.BuildTreeModelController
import org.gradle.internal.buildtree.BuildTreeModelSideEffectExecutor
import org.gradle.internal.buildtree.BuildTreeModelTarget
import org.gradle.internal.work.WorkerThreadRegistry
import org.gradle.tooling.internal.gradle.GradleBuildIdentity
import org.gradle.tooling.internal.gradle.GradleProjectIdentity
import org.gradle.tooling.internal.protocol.InternalUnsupportedModelException
import org.gradle.tooling.internal.protocol.ModelIdentifier
import org.gradle.tooling.internal.provider.serialization.PayloadSerializer
import org.gradle.tooling.provider.model.UnknownModelException
import org.gradle.util.Path
import spock.lang.Specification

import java.util.function.Supplier

class DefaultBuildControllerTest extends Specification {
    def cancellationToken = Stub(BuildCancellationToken)
    def modelId = Stub(ModelIdentifier) {
        getName() >> 'some.model'
    }
    def modelController = Mock(BuildTreeModelController)
    def workerThreadRegistry = Mock(WorkerThreadRegistry)
    def buildEventConsumer = Mock(BuildEventConsumer)
    def sideEffectExecutor = Mock(BuildTreeModelSideEffectExecutor)
    def payloadSerializer = Mock(PayloadSerializer)

    def controller = new DefaultBuildController(
        modelController,
        workerThreadRegistry,
        cancellationToken,
        buildEventConsumer,
        sideEffectExecutor,
        payloadSerializer
    )

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
        1 * modelController.getModel(null, 'some.model', null) >> { throw failure }

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
        def model = new Object()

        given:
        _ * workerThreadRegistry.workerThread >> true
        _ * target.projectPath >> ":some:path"
        _ * target.rootDir >> rootDir
        1 * modelController.getModel(_, "some.model", null) >> { BuildTreeModelTarget t, m, p ->
            assert t.buildRootDir == rootDir && t.projectPath == Path.path(":some:path")
            model
        }

        when:
        def result = controller.getModel(target, modelId)

        then:
        result.getModel() == model
    }

    def "uses builder for specified build"() {
        def rootDir = new File("dummy")
        def target = Stub(GradleBuildIdentity)
        def model = new Object()

        given:
        _ * workerThreadRegistry.workerThread >> true
        _ * target.rootDir >> rootDir
        1 * modelController.getModel(_, "some.model", null) >> { BuildTreeModelTarget t, m, p ->
            assert t.buildRootDir == rootDir && t.projectPath == null
            model
        }

        when:
        def result = controller.getModel(target, modelId)

        then:
        result.getModel() == model
    }

    def "uses builder for default project when none specified"() {
        def model = new Object()

        given:
        _ * workerThreadRegistry.workerThread >> true
        1 * modelController.getModel(null, "some.model", null) >> model

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
        1 * modelController.getModel(null, 'some.model', parameter) >> model

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
