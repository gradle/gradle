/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.internal.resource.transfer


import org.gradle.internal.operations.BuildOperationContext
import org.gradle.internal.operations.BuildOperationExecutor
import org.gradle.internal.operations.CallableBuildOperation
import org.gradle.internal.resource.ExternalResource
import org.gradle.internal.resource.ExternalResourceName
import org.gradle.internal.resource.ExternalResourceReadBuildOperationType
import org.gradle.internal.resource.ExternalResourceReadMetadataBuildOperationType
import org.gradle.internal.resource.metadata.ExternalResourceMetaData
import spock.lang.Specification

class ProgressLoggingExternalResourceAccessorTest extends Specification {

    ExternalResourceAccessor delegate = Mock()
    BuildOperationExecutor buildOperationExecutor = Mock()
    BuildOperationContext context = Mock()
    ProgressLoggingExternalResourceAccessor accessor = new ProgressLoggingExternalResourceAccessor(delegate, buildOperationExecutor)
    ExternalResourceMetaData metaData = Mock()
    ExternalResource.ContentAndMetadataAction action = Mock()
    def location = new ExternalResourceName(new URI("https://location/thing.jar"))

    def "returns null when resource does not exist"() {
        expectReadBuildOperation(0)

        when:
        def result = accessor.withContent(location, false, action)

        then:
        result == null

        and:
        1 * delegate.withContent(location, false, _) >> null

        and:
        0 * action._
    }

    def "reads empty content"() {
        setup:
        expectReadBuildOperation(0)
        expectResourceRead(new ByteArrayInputStream())

        when:
        def result = accessor.withContent(location, false, action)

        then:
        result == "result"

        and:
        1 * action.execute(_, _) >> "result"
    }

    def "fires progress events as content is read"() {
        setup:
        metaData.getContentLength() >> 4096
        expectReadBuildOperation(4096)
        expectResourceRead(new ByteArrayInputStream(new byte[4096]))

        when:
        def result = accessor.withContent(location, false, action)

        then:
        result == "result"

        and:
        1 * action.execute(_, _) >> { inputStream, metaData ->
            inputStream.read(new byte[2])
            inputStream.read(new byte[560])
            inputStream.read(new byte[1000])
            inputStream.read(new byte[1600])
            inputStream.read(new byte[1024])
            inputStream.read(new byte[1024])
            "result"
        }
        1 * context.progress(1562, 4096, 'bytes', '1.5 KiB/4 KiB downloaded')
        1 * context.progress(3162, 4096, 'bytes', '3 KiB/4 KiB downloaded')
        1 * context.progress(4096, 4096, 'bytes', '4 KiB/4 KiB downloaded')
        0 * context.progress(_)
    }

    def "fires complete event when action complete with partially read stream"() {
        setup:
        metaData.getContentLength() >> 4096
        expectReadBuildOperation(1600)
        expectResourceRead(new ByteArrayInputStream(new byte[4096]))

        when:
        accessor.withContent(location, false, action)

        then:
        1 * action.execute(_, _) >> { inputStream, metaData ->
            inputStream.read(new byte[1600])
            "result"
        }
        1 * context.progress(1600, 4096, 'bytes', '1.5 KiB/4 KiB downloaded')
        0 * context.progress(_)
    }

    def "no progress events logged for resources smaller 1024 bytes"() {
        setup:
        metaData.getContentLength() >> 1023
        expectReadBuildOperation(1023)
        expectResourceRead(new ByteArrayInputStream(new byte[1023]))

        when:
        accessor.withContent(location, false, action)

        then:
        1 * action.execute(_, _) >> { inputStream, metaData ->
            inputStream.read(new byte[1024])
            "result"
        }
        0 * context.progress(_)
    }

    def "fires progress events when content size is not known"() {
        setup:
        metaData.getContentLength() >> -1
        expectReadBuildOperation(4096)
        expectResourceRead(new ByteArrayInputStream(new byte[4096]))

        when:
        def result = accessor.withContent(location, false, action)

        then:
        result == "result"

        and:
        1 * action.execute(_, _) >> { inputStream, metaData ->
            inputStream.read(new byte[2])
            inputStream.read(new byte[560])
            inputStream.read(new byte[1000])
            inputStream.read(new byte[1600])
            inputStream.read(new byte[1024])
            inputStream.read(new byte[1024])
            "result"
        }
        1 * context.progress(1562, -1, 'bytes', '1.5 KiB downloaded')
        1 * context.progress(3162, -1, 'bytes', '3 KiB downloaded')
        1 * context.progress(4096, -1, 'bytes', '4 KiB downloaded')
        0 * context.progress(_)
    }

    def "returns null metadata when resource does not exist"() {
        expectMetadataBuildOperation()

        when:
        def result = accessor.getMetaData(location, false)

        then:
        result == null

        and:
        1 * delegate.getMetaData(location, false) >> null
    }

    def "returns metadata for resource"() {
        expectMetadataBuildOperation()

        when:
        def result = accessor.getMetaData(location, false)

        then:
        result == metaData

        and:
        1 * delegate.getMetaData(location, false) >> metaData
    }

    def expectMetadataBuildOperation() {
        1 * buildOperationExecutor.call(_) >> { CallableBuildOperation action ->
            def descriptor = action.description().build()
            assert descriptor.name == "Metadata of $location"
            assert descriptor.displayName == "Metadata of $location"

            assert descriptor.details instanceof ExternalResourceReadMetadataBuildOperationType.Details
            assert descriptor.details.location == location.getUri().toASCIIString()

            action.call(context)
        }
        1 * context.setResult({ it instanceof ExternalResourceReadMetadataBuildOperationType.Result })
    }

    def expectReadBuildOperation(long bytesRead) {
        1 * buildOperationExecutor.call(_) >> { CallableBuildOperation action ->
            def descriptor = action.description().build()
            assert descriptor.name == "Download https://location/thing.jar"
            assert descriptor.displayName == "Download https://location/thing.jar"
            assert descriptor.progressDisplayName == "thing.jar"

            assert descriptor.details instanceof ExternalResourceReadBuildOperationType.Details
            assert descriptor.details.location == location.getUri().toASCIIString()
            action.call(context)
        }
        1 * context.setResult(_) >> { ExternalResourceReadBuildOperationType.Result opResult ->
            assert opResult.bytesRead == bytesRead
        }
    }

    def expectResourceRead(InputStream inputStream) {
        1 * delegate.withContent(location, false, _) >> { uri, revalidate, action ->
            action.execute(inputStream, metaData)
        }
    }
}
