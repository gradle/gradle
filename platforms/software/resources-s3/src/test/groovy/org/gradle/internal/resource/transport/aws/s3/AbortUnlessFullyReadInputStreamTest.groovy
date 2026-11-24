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

package org.gradle.internal.resource.transport.aws.s3

import software.amazon.awssdk.core.ResponseInputStream
import software.amazon.awssdk.http.Abortable
import software.amazon.awssdk.http.AbortableInputStream
import software.amazon.awssdk.services.s3.model.GetObjectResponse
import spock.lang.Specification

class AbortUnlessFullyReadInputStreamTest extends Specification {
    static final int LENGTH = 16

    Abortable abortable = Mock()

    def "on close, #description"() {
        given:
        def stream = stream(contentLength)

        when:
        read(stream)
        stream.close()

        then:
        expectedAborts * abortable.abort()

        where:
        description                                                             | contentLength | read                                                    | expectedAborts
        "aborts when nothing was read"                                          | LENGTH        | { InputStream s -> }                                    | 1
        "aborts after reading part of the object byte by byte"                  | LENGTH        | { InputStream s -> 8.times { s.read() } }               | 1
        "aborts after reading part of the object into a buffer"                 | LENGTH        | { InputStream s -> s.read(new byte[8]) }                | 1
        "aborts after skipping part of the object"                              | LENGTH        | { InputStream s -> s.skip(8) }                          | 1
        "reuses after reading the content length byte by byte"                  | LENGTH        | { InputStream s -> LENGTH.times { s.read() } }          | 0
        "reuses after reading the content length into a buffer"                 | LENGTH        | { InputStream s -> s.readNBytes(LENGTH) }               | 0
        "reuses after skipping the content length"                              | LENGTH        | { InputStream s -> s.skip(LENGTH) }                     | 0
        "aborts without a content length until the end is seen"                 | -1            | { InputStream s -> s.read(new byte[LENGTH]) }           | 1
        "reuses without a content length at the end of a buffered read"         | -1            | { InputStream s -> s.readAllBytes() }                   | 0
        "reuses without a content length once a byte-by-byte read sees the end" | -1            | { InputStream s -> s.read(new byte[LENGTH]); s.read() } | 0
    }

    def "aborts only once when closed twice"() {
        given:
        def stream = stream(LENGTH)
        stream.read()

        when:
        stream.close()
        stream.close()

        then:
        1 * abortable.abort()
    }

    def "does not support mark and reset"() {
        given:
        def stream = stream(LENGTH)

        when:
        stream.mark(LENGTH)
        stream.readNBytes(8)
        stream.reset()

        then:
        !stream.markSupported()
        thrown(IOException)

        when:
        def remaining = stream.read(new byte[LENGTH])
        stream.close()

        then:
        // The stream stays where it was
        remaining == LENGTH - 8
        0 * abortable.abort()
    }

    private AbortUnlessFullyReadInputStream stream(long contentLength) {
        def body = AbortableInputStream.create(new ByteArrayInputStream(new byte[LENGTH]), abortable)
        new AbortUnlessFullyReadInputStream(new ResponseInputStream<>(GetObjectResponse.builder().build(), body), contentLength)
    }
}
