/*
 * Copyright 2024 the original author or authors.
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

import spock.lang.Specification

class ProgressLoggingInputStreamTest extends Specification {

    def "Given input stream When read data Then listener counted all the bytes"() {
        given:
        def inputBytes = "Test data".getBytes()
        def processedBytes = 0
        def loggingInputStream = new ProgressLoggingInputStream(new ByteArrayInputStream(inputBytes), new ProgressLoggingInputSteamListener() {
            @Override
            void onProcessedBytes(int numberOfBytes) {
                processedBytes += numberOfBytes
            }
        })

        when:
        def buffer = new byte[5]
        while (loggingInputStream.read(buffer) != -1) { /* Read until end */ }

        then:
        processedBytes == inputBytes.length
    }

    def "Given empty input When read data Then listener didn't processed any byte"() {
        given:
        def processedBytes = 0
        def loggingInputStream = new ProgressLoggingInputStream(new ByteArrayInputStream(new byte[0]), new ProgressLoggingInputSteamListener() {
            @Override
            void onProcessedBytes(int numberOfBytes) {
                processedBytes += numberOfBytes
            }
        })

        when:
        def buffer = new byte[5]
        while (loggingInputStream.read(buffer) != -1) { /* Read until end */ }

        then:
        processedBytes == 0
    }

    def "Given input When read data without buffer Then fails with expected exception message"() {
        given:
        def loggingInputStream = new ProgressLoggingInputStream(new ByteArrayInputStream(new byte[0]), {})

        when:
        loggingInputStream.read()

        then:
        def e = thrown(UnsupportedOperationException)
        e.message == "Reading from a remote resource should be buffered."
        e.cause == null
    }
}
