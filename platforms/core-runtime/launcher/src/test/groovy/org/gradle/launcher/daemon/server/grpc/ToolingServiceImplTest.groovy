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
        observer.value.error.contains("did not return protobuf Any bytes")
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

    private ModelRequest modelRequest(String modelName) {
        ModelRequest.newBuilder()
            .setProjectDir(temp.testDirectory.absolutePath)
            .setModelName(modelName)
            .build()
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
}
