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
import spock.lang.Specification

import static org.gradle.internal.resource.transfer.ResourceOperation.Type

class ResourceOperationTest extends Specification {
    BuildOperationContext context = Mock()

    def "no progress event is logged for files < 1024 bytes"() {
        given:
        def operation = new ResourceOperation(context, Type.download)
        operation.contentLength = 1023
        when:
        operation.logProcessedBytes(1023)
        then:
        0 * context.progress(_)
    }

    def "logs processed bytes in KiB intervals"() {
        given:
        def operation = new ResourceOperation(context, Type.download)
        operation.contentLength = 1024 * 10
        when:
        operation.logProcessedBytes(512 * 0)
        operation.logProcessedBytes(512 * 1)
        then:
        0 * context.progress(_)

        when:
        operation.logProcessedBytes(512 * 1)
        operation.logProcessedBytes(512 * 2)
        then:
        1 * context.progress(1024, 10240, "bytes", "1 KiB/10 KiB downloaded")
        1 * context.progress(2048, 10240, "bytes", "2 KiB/10 KiB downloaded")
        0 * context.progress(_)
    }

    def "last chunk of bytes <1KiB is not logged"() {
        given:
        def operation = new ResourceOperation(context, Type.download)
        operation.contentLength = 2000
        when:
        operation.logProcessedBytes(1000)
        operation.logProcessedBytes(1000)
        then:
        1 * context.progress(2000, 2000, "bytes", "1.9 KiB/1.9 KiB downloaded")
        0 * context.progress(_)
    }

    def "adds operationtype information in progress output"() {
        given:
        def operation = new ResourceOperation(context, type)
        operation.contentLength = 1024 * 10
        when:
        operation.logProcessedBytes(1024)
        then:
        1 * context.progress(1024, 10240, "bytes", message)
        where:
        type          | message
        Type.download | "1 KiB/10 KiB downloaded"
        Type.upload   | "1 KiB/10 KiB uploaded"
    }

    def "handles unknown content length"() {
        given:
        def operation = new ResourceOperation(context, Type.upload)
        operation.contentLength = -1
        when:
        operation.logProcessedBytes(1024)
        then:
        1 * context.progress(1024, -1, "bytes", "1 KiB uploaded")
    }
}

