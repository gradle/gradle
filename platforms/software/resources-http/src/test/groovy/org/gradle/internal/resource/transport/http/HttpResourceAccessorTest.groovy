/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.internal.resource.transport.http

import com.google.common.collect.ImmutableMap
import org.apache.http.HttpHeaders
import org.apache.http.HttpStatus
import org.gradle.internal.resource.ExternalResourceName
import spock.lang.Specification

class HttpResourceAccessorTest extends Specification {
    def uri = new URI("http://somewhere")
    def name = new ExternalResourceName(uri)

    def "should call close() on CloseableHttpResource when getMetaData is called"() {
        def response = mockHttpResponse()
        def client = Mock(HttpClient) {
            performHead(_, _) >> response
        }

        when:
        new HttpResourceAccessor(client).getMetaData(name, false)

        then:
        1 * response.close()
    }

    def "request with revalidate adds Cache-Control header"() {
        def client = Mock(HttpClient)
        def headResponse = mockHttpResponse()
        def getResponse = mockHttpResponse()

        when:
        new HttpResourceAccessor(client).getMetaData(name, true)

        then:
        1 * client.performHead(uri, ImmutableMap.of(HttpHeaders.CACHE_CONTROL, "max-age=0")) >> headResponse
        1 * headResponse.close()

        when:
        def resource = new HttpResourceAccessor(client).openResource(name, true, null)
        resource.close()

        then:
        1 * client.performGet(uri, ImmutableMap.of(HttpHeaders.CACHE_CONTROL, "max-age=0")) >> getResponse
        1 * getResponse.close()
    }

    def "when cache position is valid, then perform range request and append content"() {
        given:
        def tempFile = File.createTempFile("http-resource-accessor", ".bin")
        tempFile.bytes = "abc".bytes
        def response = Mock(HttpClient.Response) {
            getStatusCode() >> HttpStatus.SC_PARTIAL_CONTENT
            getContent() >> new ByteArrayInputStream("def".bytes)
        }
        def client = Mock(HttpClient) {
            performRawGet(uri, ImmutableMap.of(
                HttpHeaders.CACHE_CONTROL, "max-age=0",
                HttpHeaders.RANGE, "bytes=3-"
            )) >> response
        }
        def accessor = new HttpResourceAccessor(client)
        accessor.setChunkSize(2)

        when:
        def resource = accessor.openResource(name, true, tempFile)
        def bytes = resource.openStream().bytes
        resource.close()

        then:
        bytes == "abcdef".bytes
        tempFile.bytes == "abcdef".bytes
        1 * response.close()

        cleanup:
        tempFile?.delete()
    }

    def "when server ignores the range request, then the remote response is returned"() {
        given:
        def tempFile = File.createTempFile("http-resource-accessor", ".bin")
        tempFile.bytes = "abc".bytes
        def response = Mock(HttpClient.Response) {
            getStatusCode() >> HttpStatus.SC_OK
            getContent() >> new ByteArrayInputStream("xyz".bytes)
        }
        def client = Mock(HttpClient) {
            performRawGet(uri, ImmutableMap.of(HttpHeaders.RANGE, "bytes=3-")) >> response
        }

        when:
        def resource = new HttpResourceAccessor(client).openResource(name, false, tempFile)
        def bytes = resource.openStream().bytes
        resource.close()

        then:
        bytes == "xyz".bytes
        tempFile.bytes == "abc".bytes
        1 * response.close()

        cleanup:
        tempFile?.delete()
    }

    private HttpClient.Response mockHttpResponse() {
        Mock(HttpClient.Response) {
            getStatusCode() >> 200
        }
    }

}
