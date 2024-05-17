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
import org.gradle.internal.operations.RunnableBuildOperation
import org.gradle.internal.resource.ExternalResourceName
import org.gradle.internal.resource.ExternalResourceWriteBuildOperationType
import org.gradle.internal.resource.ReadableContent
import spock.lang.Specification

class ProgressLoggingExternalResourceUploaderTest extends Specification {
    def delegate = Mock(ExternalResourceUploader)
    def buildOperationExecutor = Mock(BuildOperationExecutor)
    def context = Mock(BuildOperationContext)
    def uploader = new ProgressLoggingExternalResourceUploader(delegate, buildOperationExecutor)
    def inputStream = Mock(InputStream)
    def resource = Mock(ReadableContent)
    def location = new ExternalResourceName(new URI("https://location/thing.jar"))

    def "delegates upload to delegate uploader and logs progress"() {
        setup:
        expectPutBuildOperation(1072)

        when:
        uploader.upload(resource, location)

        then:
        1 * resource.open() >> inputStream
        1 * resource.contentLength >> 1072
        1 * delegate.upload(_, location) >> { resource, destination ->
            def stream = resource.open();
            assert stream.read(new byte[1024]) == 1024
            assert stream.read(new byte[1024]) == 48
        }
        1 * inputStream.read(_, 0, 1024) >> 1024
        1 * inputStream.read(_, 0, 1024) >> 48
        1 * context.progress(1024, 1072, "bytes", "1 KiB/1 KiB uploaded")
        0 * context.progress(_)
    }

    def "uploads empty file"() {
        setup:
        expectPutBuildOperation(0)

        when:
        uploader.upload(resource, location)

        then:
        1 * resource.open() >> inputStream
        1 * resource.contentLength >> 0
        1 * delegate.upload(_, location) >> { resource, destination ->
            resource.open()
        }
        0 * context.progress(_)
    }

    def expectPutBuildOperation(long bytesWritten) {
        1 * buildOperationExecutor.run(_) >> { RunnableBuildOperation action ->
            def descriptor = action.description().build()
            assert descriptor.name == "Upload https://location/thing.jar"
            assert descriptor.displayName == "Upload https://location/thing.jar"
            assert descriptor.progressDisplayName == "thing.jar"

            assert descriptor.details instanceof ExternalResourceWriteBuildOperationType.Details
            assert descriptor.details.location == location.getUri().toASCIIString()
            action.run(context)
        }
        1 * context.setResult(_) >> { ExternalResourceWriteBuildOperationType.Result opResult ->
            assert opResult.bytesWritten == bytesWritten
        }
    }
}
