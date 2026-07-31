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
package org.gradle.launcher.daemon.server.grpc

import com.google.protobuf.Any
import io.grpc.stub.StreamObserver
import org.gradle.initialization.BuildCancellationToken
import org.gradle.initialization.layout.BuildLayoutFactory
import org.gradle.internal.logging.LoggingOutputInternal
import org.gradle.internal.service.ServiceRegistry
import org.gradle.internal.service.scopes.GradleUserHomeScopeServiceRegistry
import org.gradle.launcher.daemon.server.api.DaemonStateControl
import org.gradle.launcher.exec.BuildActionResult
import org.gradle.launcher.exec.BuildExecutor
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.tooling.internal.grpc.proto.BuildEnvironment
import org.gradle.tooling.internal.grpc.proto.BuildEvent
import org.gradle.tooling.internal.grpc.proto.BuildRequest
import org.gradle.tooling.internal.grpc.proto.CancelRequest
import org.gradle.tooling.internal.grpc.proto.CancelResponse
import org.gradle.tooling.internal.grpc.proto.ConnectRequest
import org.gradle.tooling.internal.grpc.proto.ConnectResponse
import org.gradle.tooling.internal.grpc.proto.ModelRequest
import org.gradle.tooling.internal.grpc.proto.ModelResponse
import org.gradle.tooling.internal.grpc.proto.ModelType
import org.gradle.tooling.internal.provider.serialization.PayloadSerializer
import org.gradle.tooling.internal.provider.serialization.SerializedPayload
import org.junit.Rule
import spock.lang.Specification

class ToolingServiceImplTest extends Specification {

    @Rule
    TestNameTestDirectoryProvider temp = new TestNameTestDirectoryProvider(getClass())

    def buildExecutor = Mock(BuildExecutor)
    def loggingOutput = Mock(LoggingOutputInternal)
    def stateControl = Mock(DaemonStateControl)
    def buildLayoutFactory = new BuildLayoutFactory()
    def userHomeServiceRegistry = Mock(GradleUserHomeScopeServiceRegistry)
    def userHomeServices = Mock(ServiceRegistry)
    def payloadSerializer = Mock(PayloadSerializer)

    ToolingServiceImpl service

    def setup() {
        service = new ToolingServiceImpl(buildExecutor, loggingOutput, stateControl, buildLayoutFactory, userHomeServiceRegistry)
        stateControl.getCancellationToken() >> Mock(BuildCancellationToken)
        // Run the build command inline so the executor interaction happens during the query.
        stateControl.runCommand(_, _) >> { Runnable command, String name -> command.run() }
        userHomeServiceRegistry.getServicesFor(_) >> userHomeServices
        userHomeServices.get(PayloadSerializer) >> payloadSerializer
    }

    def "forwards a plugin model's protobuf Any bytes to the client"() {
        given:
        def model = BuildEnvironment.newBuilder().setGradleVersion("9.9-test").build()
        def anyBytes = Any.pack(model).toByteArray()
        def observer = new CapturingObserver()

        when:
        service.queryModel(modelRequest("com.example.SomeModel"), observer)

        then:
        1 * buildExecutor.execute(_, _, _) >> BuildActionResult.of(Mock(SerializedPayload))
        1 * payloadSerializer.deserialize(_) >> anyBytes

        and:
        observer.completed
        observer.value.success
        observer.value.hasModelAny()
        // The daemon forwarded the Any untouched; a client that knows the schema unpacks it.
        observer.value.modelAny.unpack(BuildEnvironment).gradleVersion == "9.9-test"
    }

    def "packs a plugin model returned as a protobuf message into an Any"() {
        given:
        // With protobuf-java shared between the plugin and the daemon, the builder returns the
        // protobuf message directly and the daemon packs it into an Any for the native client.
        def model = BuildEnvironment.newBuilder().setGradleVersion("9.9-msg").build()
        def observer = new CapturingObserver()

        when:
        service.queryModel(modelRequest("com.example.SomeModel"), observer)

        then:
        1 * buildExecutor.execute(_, _, _) >> BuildActionResult.of(Mock(SerializedPayload))
        1 * payloadSerializer.deserialize(_) >> model

        and:
        observer.completed
        observer.value.success
        observer.value.hasModelAny()
        observer.value.modelAny.unpack(BuildEnvironment).gradleVersion == "9.9-msg"
    }

    def "reports an error when the model result is not protobuf Any bytes"() {
        given:
        def observer = new CapturingObserver()

        when:
        service.queryModel(modelRequest("com.example.SomeModel"), observer)

        then:
        1 * buildExecutor.execute(_, _, _) >> BuildActionResult.of(Mock(SerializedPayload))
        1 * payloadSerializer.deserialize(_) >> "not-protobuf-bytes"

        and:
        observer.completed
        !observer.value.success
        observer.value.error.contains("did not return a protobuf message or Any bytes")
    }

    def "reports the build failure when the model query build fails"() {
        given:
        def observer = new CapturingObserver()

        when:
        service.queryModel(modelRequest("com.example.SomeModel"), observer)

        then:
        1 * buildExecutor.execute(_, _, _) >> BuildActionResult.failed(new RuntimeException("configuration blew up"))
        0 * payloadSerializer.deserialize(_)

        and:
        observer.completed
        !observer.value.success
        observer.value.error.contains("configuration blew up")
    }

    def "answers the built-in build environment model without running a build"() {
        given:
        def observer = new CapturingObserver()
        def request = ModelRequest.newBuilder()
            .setProjectDir(temp.testDirectory.absolutePath)
            .setType(ModelType.MODEL_BUILD_ENVIRONMENT)
            .build()

        when:
        service.queryModel(request, observer)

        then:
        0 * buildExecutor.execute(_, _, _)

        and:
        observer.completed
        observer.value.success
        observer.value.hasBuildEnvironment()
        observer.value.buildEnvironment.gradleVersion
    }

    def "connect advertises the contract version and the direct-mode capabilities"() {
        given:
        def observer = new CapturingConnectObserver()

        when:
        service.connect(ConnectRequest.newBuilder().setClientName("test").build(), observer)

        then:
        observer.completed
        observer.value.contractVersion == 1
        observer.value.gradleVersion
        observer.value.capabilitiesList.containsAll(["build.run", "control.cancel", "models.plugin", "models.parameterized"])
    }

    def "reports not cancelled when no build with the id is running"() {
        given:
        def observer = new CapturingCancelObserver()

        when:
        service.cancel(cancelRequest("build-x"), observer)

        then:
        0 * stateControl.requestCancel()

        and:
        observer.completed
        !observer.value.cancelled
        observer.value.message.contains("No running build")
    }

    def "cancels the running build when the build id matches"() {
        given:
        def buildObserver = new CapturingBuildObserver()
        def cancelObserver = new CapturingCancelObserver()

        when:
        service.runBuild(buildRequest("build-1"), buildObserver)

        then:
        // A Cancel arriving while this build runs (its id is registered) must trip the daemon's cancel.
        1 * buildExecutor.execute(_, _, _) >> {
            service.cancel(cancelRequest("build-1"), cancelObserver)
            BuildActionResult.of(Mock(SerializedPayload))
        }
        1 * stateControl.requestCancel()

        and:
        cancelObserver.completed
        cancelObserver.value.cancelled
        cancelObserver.value.message.contains("build-1")
    }

    private ModelRequest modelRequest(String modelName) {
        ModelRequest.newBuilder()
            .setProjectDir(temp.testDirectory.absolutePath)
            .setModelName(modelName)
            .build()
    }

    private BuildRequest buildRequest(String buildId) {
        BuildRequest.newBuilder()
            .setProjectDir(temp.testDirectory.absolutePath)
            .setBuildId(buildId)
            .build()
    }

    private static CancelRequest cancelRequest(String buildId) {
        CancelRequest.newBuilder().setBuildId(buildId).build()
    }

    static class CapturingObserver implements StreamObserver<ModelResponse> {
        ModelResponse value
        Throwable error
        boolean completed

        @Override
        void onNext(ModelResponse response) {
            value = response
        }

        @Override
        void onError(Throwable t) {
            error = t
        }

        @Override
        void onCompleted() {
            completed = true
        }
    }

    static class CapturingConnectObserver implements StreamObserver<ConnectResponse> {
        ConnectResponse value
        boolean completed

        @Override
        void onNext(ConnectResponse response) {
            value = response
        }

        @Override
        void onError(Throwable t) {
        }

        @Override
        void onCompleted() {
            completed = true
        }
    }

    static class CapturingCancelObserver implements StreamObserver<CancelResponse> {
        CancelResponse value
        boolean completed

        @Override
        void onNext(CancelResponse response) {
            value = response
        }

        @Override
        void onError(Throwable t) {
        }

        @Override
        void onCompleted() {
            completed = true
        }
    }

    static class CapturingBuildObserver implements StreamObserver<BuildEvent> {
        List<BuildEvent> events = []
        boolean completed

        @Override
        void onNext(BuildEvent event) {
            events << event
        }

        @Override
        void onError(Throwable t) {
        }

        @Override
        void onCompleted() {
            completed = true
        }
    }
}
