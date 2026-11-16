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

class S3ResourceTest extends Specification {
    static final byte[] CONTENT = new byte[16]

    Abortable abortable = Mock()

    def "aborts when closed without opening the stream"() {
        when:
        resource(16).close()

        then:
        1 * abortable.abort()
    }

    def "reuses the connection after the whole object was read"() {
        given:
        def resource = resource(16)

        when:
        def stream = resource.openStream()
        stream.readAllBytes()
        stream.close()
        resource.close()

        then:
        0 * abortable.abort()
    }

    def "aborts once when the resource is closed before a partially read stream"() {
        given:
        def resource = resource(16)
        def stream = resource.openStream()
        stream.read(new byte[8])

        when:
        resource.close()
        stream.close()

        then:
        1 * abortable.abort()
    }

    def "treats a missing content length as unknown"() {
        given:
        def resource = resource(contentLength)

        when:
        resource.openStream().read(new byte[16])
        resource.close()

        then:
        expectedAborts * abortable.abort()

        where:
        contentLength | expectedAborts
        16            | 0
        null          | 1
    }

    private S3Resource resource(Long contentLength) {
        def response = GetObjectResponse.builder().contentLength(contentLength).build()
        def body = AbortableInputStream.create(new ByteArrayInputStream(CONTENT), abortable)
        new S3Resource(new ResponseInputStream<>(response, body), new URI("s3://bucket/key"))
    }
}
