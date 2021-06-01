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
import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.initialization.BuildCancellationToken
import org.gradle.internal.build.BuildState
import org.gradle.internal.build.BuildStateRegistry
import org.gradle.internal.concurrent.GradleThread
import org.gradle.internal.operations.MultipleBuildOperationFailures
import org.gradle.internal.operations.TestBuildOperationExecutor
import org.gradle.internal.resources.ProjectLeaseRegistry
import org.gradle.internal.service.ServiceRegistry
import org.gradle.tooling.internal.gradle.GradleBuildIdentity
import org.gradle.tooling.internal.gradle.GradleProjectIdentity
import org.gradle.tooling.internal.protocol.InternalUnsupportedModelException
import org.gradle.tooling.internal.protocol.ModelIdentifier
import org.gradle.tooling.provider.model.UnknownModelException
import org.gradle.tooling.provider.model.internal.ToolingModelBuilderLookup
import spock.lang.Specification

import java.util.function.Consumer
import java.util.function.Supplier

class DefaultBuildControllerTest extends Specification {
    def cancellationToken = Stub(BuildCancellationToken)
    def gradle = Stub(GradleInternal) {
        getServices() >> Stub(ServiceRegistry) {
            get(BuildCancellationToken) >> cancellationToken
        }
    }
    def registry = Stub(ToolingModelBuilderLookup)
    def project = Stub(ProjectInternal) {
        getServices() >> Stub(ServiceRegistry) {
            get(ToolingModelBuilderLookup) >> registry
        }
    }
    def modelId = Stub(ModelIdentifier) {
        getName() >> 'some.model'
    }
    def modelBuilder = Stub(ToolingModelBuilderLookup.Builder)
    def projectLeaseRegistry = Stub(ProjectLeaseRegistry)
    def buildOperationExecutor = new TestBuildOperationExecutor()
    def buildStateRegistry = Stub(BuildStateRegistry)
    def controller = new DefaultBuildController(gradle, cancellationToken, buildOperationExecutor, projectLeaseRegistry, buildStateRegistry)

    def setup() {
        GradleThread.setManaged()
    }

    def cleanup() {
        GradleThread.setUnmanaged()
    }

    def "cannot get build model from unmanaged thread"() {
        given:
        GradleThread.setUnmanaged()

        when:
        controller.getBuildModel()

        then:
        IllegalStateException e = thrown()
        e.message == "A build controller cannot be used from a thread that is not managed by Gradle."
    }

    def "adapts model not found exception to protocol exception"() {
        def failure = new UnknownModelException("not found")

        given:
        _ * gradle.defaultProject >> project
        _ * registry.locateForClientOperation('some.model', false, gradle) >> { throw failure }

        when:
        controller.getModel(null, modelId)

        then:
        InternalUnsupportedModelException e = thrown()
        e.cause == failure
    }

    def "cannot get model from unmanaged thread"() {
        given:
        GradleThread.setUnmanaged()

        when:
        controller.getModel(null, modelId)

        then:
        IllegalStateException e = thrown()
        e.message == "A build controller cannot be used from a thread that is not managed by Gradle."
    }

    def "uses builder for specified project"() {
        def rootDir = new File("dummy")
        def target = Stub(GradleProjectIdentity)
        def rootProject = Stub(ProjectInternal)
        def buildState1 = Stub(BuildState)
        def buildState2 = Stub(BuildState)
        def buildState3 = Stub(BuildState)
        def model = new Object()

        given:
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
        _ * buildState3.mutableModel >> gradle
        _ * gradle.rootProject >> rootProject
        _ * rootProject.project(":some:path") >> project
        _ * registry.locateForClientOperation("some.model", false, project) >> modelBuilder
        _ * modelBuilder.build(null) >> model

        when:
        def result = controller.getModel(target, modelId)

        then:
        result.getModel() == model
    }

    def "uses builder for specified build"() {
        def rootDir = new File("dummy")
        def target = Stub(GradleBuildIdentity)
        def rootProject = Stub(ProjectInternal)
        def buildState1 = Stub(BuildState)
        def buildState2 = Stub(BuildState)
        def model = new Object()

        given:
        _ * target.rootDir >> rootDir
        _ * buildStateRegistry.visitBuilds(_) >> { Consumer consumer ->
            consumer.accept(buildState1)
            consumer.accept(buildState2)
        }
        _ * buildState1.importableBuild >> false
        _ * buildState2.importableBuild >> true
        _ * buildState2.buildRootDir >> rootDir
        _ * buildState2.mutableModel >> gradle
        _ * gradle.rootProject >> rootProject
        _ * gradle.defaultProject >> project
        _ * registry.locateForClientOperation("some.model", false, gradle) >> modelBuilder
        _ * modelBuilder.build(null) >> model

        when:
        def result = controller.getModel(target, modelId)

        then:
        result.getModel() == model
    }

    def "uses builder for default project when none specified"() {
        def model = new Object()

        given:
        _ * gradle.defaultProject >> project
        _ * registry.locateForClientOperation("some.model", false, gradle) >> modelBuilder
        _ * modelBuilder.build(null) >> model

        when:
        def result = controller.getModel(null, modelId)

        then:
        result.getModel() == model
    }

    def "throws an exception when cancel was requested"() {
        given:
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
        _ * gradle.defaultProject >> project
        _ * registry.locateForClientOperation("some.model", true, gradle) >> modelBuilder
        _ * modelBuilder.getParameterType() >> parameterType
        _ * modelBuilder.build(_) >> { CustomParameter param ->
            assert param != null
            assert param.getValue() == "myValue"
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

        1 * action1.get() >> "one"
        1 * action2.get() >> "two"
        1 * action3.get() >> "three"
        0 * _
    }

    def "collects all failures from actions"() {
        def action1 = Mock(Supplier)
        def action2 = Mock(Supplier)
        def action3 = Mock(Supplier)
        def failure1 = new RuntimeException()
        def failure2 = new RuntimeException()

        when:
        controller.run([action1, action2, action3])

        then:
        def e = thrown(MultipleBuildOperationFailures)
        e.causes == [failure1, failure2]

        1 * action1.get() >> { throw failure1 }
        1 * action2.get() >> { throw failure2 }
        1 * action3.get() >> "three"
        0 * _
    }

    def "cannot run actions from unmanaged thread"() {
        given:
        GradleThread.setUnmanaged()

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
