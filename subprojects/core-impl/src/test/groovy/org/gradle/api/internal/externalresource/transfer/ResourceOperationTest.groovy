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

package org.gradle.api.internal.externalresource.transfer

import spock.lang.Specification
import org.gradle.logging.ProgressLogger

class ResourceOperationTest extends Specification {

    ProgressLogger progressLogger = Mock()

    def "no progress event is logged for files < 1024bytes"(){
        given:
        def operation = new ResourceOperation(progressLogger, ResourceOperation.Type.download, 1023)
        when:
        operation.logProcessedBytes(1023)
        then:
        0 * progressLogger.progress(_)
    }

    def "logs processed bytes in kbyte intervalls"() {
        given:
        def operation = new ResourceOperation(progressLogger, ResourceOperation.Type.download, 1024 * 10)
        when:
        operation.logProcessedBytes(512 * 0)
        operation.logProcessedBytes(512 * 1)
        then:
        0 * progressLogger.progress(_)

        when:
        operation.logProcessedBytes(512 * 1)
        operation.logProcessedBytes(512 * 2)
        then:
        1 * progressLogger.progress("1 KB/10 KB downloaded")
        1 * progressLogger.progress("2 KB/10 KB downloaded")
        0 * progressLogger.progress(_)
    }

    def "last chunk of bytes <1k is not logged"(){
        given:
        def operation = new ResourceOperation(progressLogger, ResourceOperation.Type.download, 2000)
        when:
        operation.logProcessedBytes(1000)
        operation.logProcessedBytes(1000)
        then:
        1 * progressLogger.progress("1 KB/1 KB downloaded")
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
        type                            | message
        ResourceOperation.Type.download | "1 KB/10 KB downloaded"
        ResourceOperation.Type.upload   | "1 KB/10 KB uploaded"
    }

    void "completed completes progressLogger"() {
        given:
        def operation = new ResourceOperation(progressLogger, ResourceOperation.Type.upload, 1)
        when:
        operation.completed()
        then:
        1 * progressLogger.completed()
    }

    void "handles unknown content length"() {
        given:
        def operation = new ResourceOperation(progressLogger, ResourceOperation.Type.upload, 0)
        when:
        operation.logProcessedBytes(1024)
        then:
        1 * progressLogger.progress("1 KB/unknown size uploaded")
    }
}

