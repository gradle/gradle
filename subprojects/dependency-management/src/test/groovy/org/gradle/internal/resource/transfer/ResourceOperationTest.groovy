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

import org.gradle.internal.logging.progress.ProgressLogger
import spock.lang.Specification

import static org.gradle.internal.resource.transfer.ResourceOperation.Type

class ResourceOperationTest extends Specification {

    ProgressLogger progressLogger = Mock()

    def "no progress event is logged for files < 1024 bytes"() {
        given:
        def operation = new ResourceOperation(progressLogger, Type.download, 1023)
        when:
        operation.logProcessedBytes(1023)
        then:
        0 * progressLogger.progress(_)
    }

    def "logs processed bytes in KiB intervals"() {
        given:
        def operation = new ResourceOperation(progressLogger, Type.download, 1024 * 10)
        when:
        operation.logProcessedBytes(512 * 0)
        operation.logProcessedBytes(512 * 1)
        then:
        0 * progressLogger.progress(_)

        when:
        operation.logProcessedBytes(512 * 1)
        operation.logProcessedBytes(512 * 2)
        then:
        1 * progressLogger.progress("1 KiB/10 KiB downloaded")
        1 * progressLogger.progress("2 KiB/10 KiB downloaded")
        0 * progressLogger.progress(_)
    }

    def "last chunk of bytes <1KiB is not logged"() {
        given:
        def operation = new ResourceOperation(progressLogger, Type.download, 2000)
        when:
        operation.logProcessedBytes(1000)
        operation.logProcessedBytes(1000)
        then:
        1 * progressLogger.progress("1.9 KiB/1.9 KiB downloaded")
        0 * progressLogger.progress(_)
    }

    def "adds operationtype information in progress output"() {
        given:
        def operation = new ResourceOperation(progressLogger, type, 1024 * 10)
        when:
        operation.logProcessedBytes(1024)
        then:
        1 * progressLogger.progress(message)
        where:
        type          | message
        Type.download | "1 KiB/10 KiB downloaded"
        Type.upload   | "1 KiB/10 KiB uploaded"
    }

    def "completed completes progressLogger"() {
        given:
        def operation = new ResourceOperation(progressLogger, Type.upload, 1)
        when:
        operation.completed()
        then:
        1 * progressLogger.completed()
    }

    def "handles unknown content length"() {
        given:
        def operation = new ResourceOperation(progressLogger, Type.upload, 0)
        when:
        operation.logProcessedBytes(1024)
        then:
        1 * progressLogger.progress("1 KiB/unknown size uploaded")
    }
}

