/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.internal.resource.transport

import org.apache.commons.io.input.NullInputStream
import org.gradle.api.Action
import org.gradle.api.Transformer
import org.gradle.api.resources.ResourceException
import org.gradle.internal.operations.BuildOperationContext
import org.gradle.internal.operations.BuildOperationExecutor
import org.gradle.internal.operations.CallableBuildOperation
import org.gradle.internal.resource.ExternalResource
import org.gradle.internal.resource.ExternalResourceReadResult
import org.gradle.internal.resource.metadata.DefaultExternalResourceMetaData
import org.gradle.internal.resource.metadata.ExternalResourceMetaData
import org.gradle.internal.resource.transfer.DownloadBuildOperationDetails
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

class BuildOperationExternalResourceTest extends Specification {

    @Shared
    def tmpDir = new TestNameTestDirectoryProvider()

    @Unroll
    def "delegates method call #methodName"() {
        given:
        def delegate = Mock(ExternalResource)
        def buildOperationExecutor = Mock(BuildOperationExecutor)

        def resource = new BuildOperationExternalResource(buildOperationExecutor, delegate)

        when:
        resource."$methodName"()

        then:
        1 * delegate."$methodName"()

        where:
        methodName << ['getURI', 'getDisplayName', 'getMetaData', 'close', 'isLocal']
    }

    static class TestExternalResource implements ExternalResource {

        public static final int READ_CONTENT_LENGTH = 2048
        public static
        final DefaultExternalResourceMetaData METADATA = new DefaultExternalResourceMetaData(new URI("http://some/uri"), 0, 1024)

        private final ExternalResource mock

        TestExternalResource(ExternalResource mock) {
            this.mock = mock
        }

        @Override
        URI getURI() {
            throw new UnsupportedOperationException()
        }

        @Override
        String getDisplayName() {
            throw new UnsupportedOperationException()
        }

        @Override
        boolean isLocal() {
            throw new UnsupportedOperationException()
        }

        @Override
        ExternalResourceReadResult<Void> writeTo(File destination) throws ResourceException {
            mock.writeTo(destination)
            ExternalResourceReadResult.of(READ_CONTENT_LENGTH)
        }

        @Override
        ExternalResourceReadResult<Void> writeTo(OutputStream destination) throws ResourceException {
            mock.writeTo(destination)
            ExternalResourceReadResult.of(READ_CONTENT_LENGTH)
        }

        @Override
        ExternalResourceReadResult<Void> withContent(Action<? super InputStream> readAction) throws ResourceException {
            mock.withContent(readAction)
            ExternalResourceReadResult.of(READ_CONTENT_LENGTH)
        }

        @Override
        def <T> ExternalResourceReadResult<T> withContent(Transformer<? extends T, ? super InputStream> readAction) throws ResourceException {
            mock.withContent(readAction)
            ExternalResourceReadResult.of(READ_CONTENT_LENGTH, readAction.transform(new NullInputStream(0)))
        }

        @Override
        def <T> ExternalResourceReadResult<T> withContent(ExternalResource.ContentAction<? extends T> readAction) throws ResourceException {
            mock.withContent(readAction)
            ExternalResourceReadResult.of(READ_CONTENT_LENGTH, readAction.execute(new NullInputStream(0), getMetaData()))
        }

        @Override
        void close() {

        }

        @Override
        ExternalResourceMetaData getMetaData() {
            METADATA
        }
    }

    @Unroll
    def "wraps #methodSignature method call in a build operation"() {
        given:
        def delegateMock = Mock(ExternalResource)
        def delegate = new TestExternalResource(delegateMock)
        def buildOperationExecuter = Mock(BuildOperationExecutor)
        def resource = new BuildOperationExternalResource(buildOperationExecuter, delegate)
        1 * buildOperationExecuter.call(_) >> { CallableBuildOperation op ->
            def operationContextMock = Mock(BuildOperationContext) {
                1 * setResult(_) >> { DownloadBuildOperationDetails.Result result ->
                    assert result.readContentLength == TestExternalResource.READ_CONTENT_LENGTH
                }
            }

            op.call(operationContextMock)

            def descriptor = op.description().build()
            assert descriptor.name == "Download http://some/uri"
            assert descriptor.displayName == "Download http://some/uri"

            def details = descriptor.details
            assert details instanceof DownloadBuildOperationDetails
            assert details.location == TestExternalResource.METADATA.location
            assert details.contentLength == TestExternalResource.METADATA.contentLength
            assert details.contentType == TestExternalResource.METADATA.contentType
        }

        when:
        resource."$methodName"(parameter)

        then:
        1 * delegateMock."$methodName"(parameter)

        where:
        methodName    | parameter                            | methodSignature
        'writeTo'     | tmpDir.createFile("tempFile")        | "writeTo(File)"
        'writeTo'     | Mock(OutputStream)                   | "writeTo(OutputStream)"
        'withContent' | Mock(Action)                         | "withContent(Action<InputStream>)"
        'withContent' | Mock(ExternalResource.ContentAction) | "withContent(ContentAction<InputStream>)"
        'withContent' | Mock(Transformer)                    | "withContent(Transformer<T, ? extends InputStream)"
    }

    @Unroll
    def "fails build operation if ResourceException is thrown in #methodSignature "() {
        given:
        def delegate = Mock(ExternalResource)
        def buildOperationExecuter = Mock(BuildOperationExecutor)
        def uri = new URI("http://some/uri")
        def resource = new BuildOperationExternalResource(buildOperationExecuter, delegate)
        def buildOperationContext = Mock(BuildOperationContext)

        1 * buildOperationExecuter.call(_) >> { CallableBuildOperation op ->
            op.call(buildOperationContext)
        }

        when:
        resource."$methodName"(parameter)

        then:
        thrown(ResourceException)

        1 * delegate."$methodName"(parameter) >> { throw new ResourceException("test resource exception") }

        where:
        methodName    | parameter                            | methodSignature
        'writeTo'     | tmpDir.createFile("tempFile")        | "writeTo(File)"
        'writeTo'     | Mock(OutputStream)                   | "writeTo(OutputStream)"
        'withContent' | Mock(Action)                         | "withContent(Action<InputStream>)"
        'withContent' | Mock(ExternalResource.ContentAction) | "withContent(ContentAction<InputStream>)"
        'withContent' | Mock(Transformer)                    | "withContent(Transformer<T, ? extends InputStream)"
    }

    def <T> T invokeAction(CallableBuildOperation<T> op, BuildOperationContext buildOperationContext) {
        op.call(buildOperationContext)
    }

}
