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

package org.gradle.internal.resource

import org.apache.commons.io.input.NullInputStream
import org.gradle.api.Action
import org.gradle.api.Transformer
import org.gradle.api.resources.ResourceException
import org.gradle.internal.operations.BuildOperationContext
import org.gradle.internal.operations.BuildOperationExecutor
import org.gradle.internal.operations.CallableBuildOperation
import org.gradle.internal.resource.metadata.DefaultExternalResourceMetaData
import org.gradle.internal.resource.metadata.ExternalResourceMetaData
import spock.lang.Specification
import spock.lang.Unroll

import static org.junit.Assert.assertTrue

class BuildOperationFiringExternalResourceDecoratorTest extends Specification {

    @Unroll
    def "delegates method call #methodName"() {
        given:
        def delegate = Mock(ExternalResource)
        def buildOperationExecutor = Mock(BuildOperationExecutor)

        def resource = new BuildOperationFiringExternalResourceDecorator(new ExternalResourceName("resource"), buildOperationExecutor, delegate)

        when:
        resource."$methodName"()

        then:
        1 * delegate."$methodName"()

        where:
        methodName << ['getURI', 'getDisplayName']
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
        ExternalResourceReadResult<Void> writeToIfPresent(File destination) throws ResourceException {
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
        def <T> ExternalResourceReadResult<T> withContentIfPresent(Transformer<? extends T, ? super InputStream> readAction) throws ResourceException {
            throw new UnsupportedOperationException()
        }

        @Override
        def <T> ExternalResourceReadResult<T> withContentIfPresent(ExternalResource.ContentAction<? extends T> readAction) throws ResourceException {
            throw new UnsupportedOperationException()
        }

        @Override
        ExternalResourceWriteResult put(ReadableContent source) throws ResourceException {
            throw new UnsupportedOperationException()
        }

        @Override
        List<String> list() throws ResourceException {
            throw new UnsupportedOperationException()
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
        def resource = new BuildOperationFiringExternalResourceDecorator(new ExternalResourceName(new URI("http://some/uri")), buildOperationExecuter, delegate)
        1 * buildOperationExecuter.call(_) >> { CallableBuildOperation op ->
            def operationContextMock = Mock(BuildOperationContext) {
                1 * setResult(_) >> { ExternalResourceReadBuildOperationType.Result result ->
                    assertTrue result.bytesRead == TestExternalResource.READ_CONTENT_LENGTH
                }
            }

            op.call(operationContextMock)

            def descriptor = op.description().build()
            assert descriptor.name == "Download http://some/uri"
            assert descriptor.displayName == "Download http://some/uri"

            def details = descriptor.details
            assert details instanceof ExternalResourceReadBuildOperationType.Details
            assert details.location == TestExternalResource.METADATA.location.toASCIIString()
        }

        when:
        resource."$methodName"(parameter)

        then:
        1 * delegateMock."$methodName"(parameter)

        where:
        methodName    | parameter                            | methodSignature
        'writeTo'     | Mock(File)                           | "writeTo(File)"
        'writeTo'     | Mock(OutputStream)                   | "writeTo(OutputStream)"
        'withContent' | Mock(Action)                         | "withContent(Action<InputStream>)"
        'withContent' | Mock(ExternalResource.ContentAction) | "withContent(ContentAction<InputStream>)"
        'withContent' | Mock(Transformer)                    | "withContent(Transformer<T, ? extends InputStream)"
    }

    def "wraps metaData get in a build operation"() {
        given:
        def metaData = Mock(ExternalResourceMetaData)
        def delegate = Mock(ExternalResource)
        def buildOperationExecuter = Mock(BuildOperationExecutor)
        def operationContextMock = Mock(BuildOperationContext)
        def location = new ExternalResourceName(new URI("http://some/uri"))
        def resource = new BuildOperationFiringExternalResourceDecorator(location, buildOperationExecuter, delegate)

        when:
        def result = resource.getMetaData()

        then:
        result == metaData
        1 * buildOperationExecuter.call(_) >> { CallableBuildOperation op ->
            def descriptor = op.description().build()
            assert descriptor.name == "Metadata of http://some/uri"
            assert descriptor.displayName == "Metadata of http://some/uri"

            def details = descriptor.details
            assert details instanceof ExternalResourceReadMetadataBuildOperationType.Details
            assert details.location == location.getUri().toASCIIString()

            return op.call(operationContextMock)
        }
        1 * delegate.getMetaData() >> metaData
    }

    def "wraps list in a build operation"() {
        given:
        def delegate = Mock(ExternalResource)
        def buildOperationExecuter = Mock(BuildOperationExecutor)
        def operationContextMock = Mock(BuildOperationContext)
        def location = new ExternalResourceName(new URI("http://some/uri"))
        def resource = new BuildOperationFiringExternalResourceDecorator(location, buildOperationExecuter, delegate)

        when:
        def result = resource.list()

        then:
        result == ["a"]
        1 * buildOperationExecuter.call(_) >> { CallableBuildOperation op ->
            def descriptor = op.description().build()
            assert descriptor.name == "List http://some/uri"
            assert descriptor.displayName == "List http://some/uri"

            def details = descriptor.details
            assert details instanceof ExternalResourceListBuildOperationType.Details
            assert details.location == location.getUri().toASCIIString()

            return op.call(operationContextMock)
        }
        1 * delegate.list() >> ["a"]
    }

    def "wraps put in a build operation"() {
        given:
        def delegate = Mock(ExternalResource)
        def source = Mock(ReadableContent)
        def buildOperationExecuter = Mock(BuildOperationExecutor)
        def operationContextMock = Mock(BuildOperationContext)
        def result = Stub(ExternalResourceWriteResult)
        def location = new ExternalResourceName(new URI("http://some/uri"))
        def resource = new BuildOperationFiringExternalResourceDecorator(location, buildOperationExecuter, delegate)

        when:
        resource.put(source)

        then:
        1 * buildOperationExecuter.call(_) >> { CallableBuildOperation op ->
            def descriptor = op.description().build()
            assert descriptor.name == "Upload http://some/uri"
            assert descriptor.displayName == "Upload http://some/uri"

            def details = descriptor.details
            assert details instanceof ExternalResourceWriteBuildOperationType.Details
            assert details.location == location.getUri().toASCIIString()

            return op.call(operationContextMock)
        }
        1 * delegate.put(source) >> result
        _ * result.bytesWritten >> 4
        1 * operationContextMock.setResult(_) >> { ExternalResourceWriteBuildOperationType.Result r ->
            assert r.bytesWritten == 4
        }
    }

    @Unroll
    def "fails build operation if ResourceException is thrown in #methodSignature "() {
        given:
        def delegate = Mock(ExternalResource)
        def buildOperationExecuter = Mock(BuildOperationExecutor)
        def resource = new BuildOperationFiringExternalResourceDecorator(new ExternalResourceName("resource"), buildOperationExecuter, delegate)
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
        'writeTo'     | Mock(File)                           | "writeTo(File)"
        'writeTo'     | Mock(OutputStream)                   | "writeTo(OutputStream)"
        'withContent' | Mock(Action)                         | "withContent(Action<InputStream>)"
        'withContent' | Mock(ExternalResource.ContentAction) | "withContent(ContentAction<InputStream>)"
        'withContent' | Mock(Transformer)                    | "withContent(Transformer<T, ? extends InputStream)"
    }

    def <T> T invokeAction(CallableBuildOperation<T> op, BuildOperationContext buildOperationContext) {
        op.call(buildOperationContext)
    }

}
