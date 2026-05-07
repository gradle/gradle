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

package org.gradle.launcher.daemon.toolchain

import com.google.common.collect.ImmutableMap
import org.apache.http.HttpHeaders
import org.gradle.internal.resource.ExternalResource
import org.gradle.internal.resource.ExternalResourceName
import org.gradle.internal.resource.transport.http.HttpClient
import org.gradle.internal.time.Clock
import spock.lang.Specification

class ToolchainExternalResourceAccessorTest extends Specification {

    ToolchainExternalResourceAccessor externalResourceAccessor
    def httpClient = Mock(HttpClient)
    def progressListener = Mock(DownloadProgressListener)
    def action = Mock(ExternalResource.ContentAndMetadataAction)
    def clock = Mock(Clock)
    def uri = new URI("https://server.com/toolchain.zip")

    def setup() {
        externalResourceAccessor = new ToolchainExternalResourceAccessor(httpClient, clock, progressListener)
    }

    def "should call close() on CloseableHttpResource when withContent is called"() {
        given:
        def response = mockSuccessfulHttpResponse(0)
        httpClient.performGet(_ as URI, _ as ImmutableMap) >> response

        when:
        externalResourceAccessor.withContent(new ExternalResourceName(uri), false, action)

        then:
        1 * response.close()
    }

    def "returns null when resource does not exist"() {
        when:
        def response = mockSuccessfulHttpResponse(0)
        httpClient.performGet(_ as URI, _ as ImmutableMap) >> response
        def result = externalResourceAccessor.withContent(new ExternalResourceName(uri), false, action)

        then:
        result == null
        1 * action.execute(_, _) >> null
    }

    def "reads empty content"() {
        when:
        def response = mockSuccessfulHttpResponse(0)
        httpClient.performGet(_ as URI, _ as ImmutableMap) >> response
        def result = externalResourceAccessor.withContent(new ExternalResourceName(uri), false, action)

        then:
        result == "result"
        1 * action.execute(_, _) >> "result"
    }

    def "fires progress events as content is read"() {
        when:
        def response = mockSuccessfulHttpResponse(4096)
        httpClient.performGet(_ as URI, _ as ImmutableMap) >> response
        def result = externalResourceAccessor.withContent(new ExternalResourceName(uri), false, action)

        then:
        result == "result"
        1 * action.execute(_, _) >> { inputStream, metaData ->
            inputStream.read(new byte[2])
            inputStream.read(new byte[560])
            inputStream.read(new byte[1000])
            inputStream.read(new byte[1600])
            inputStream.read(new byte[1024])
            inputStream.read(new byte[1024])
            "result"
        }
        1 * progressListener.downloadStarted(uri, 4096, _)
        1 * progressListener.downloadStatusChanged(uri, 2, 4096, _)
        1 * progressListener.downloadStatusChanged(uri, 562, 4096, _)
        1 * progressListener.downloadStatusChanged(uri, 1562, 4096, _)
        1 * progressListener.downloadStatusChanged(uri, 3162, 4096, _)
        1 * progressListener.downloadStatusChanged(uri, 4096, 4096, _)
        1 * progressListener.downloadFinished(uri, 4096, _, _)
        0 * progressListener._
    }

    def "fires complete event when action complete with partially read stream"() {
        when:
        def response = mockSuccessfulHttpResponse(4096)
        httpClient.performGet(_ as URI, _ as ImmutableMap) >> response
        externalResourceAccessor.withContent(new ExternalResourceName(uri), false, action)

        then:
        1 * action.execute(_, _) >> { inputStream, metaData ->
            inputStream.read(new byte[1600])
            "result"
        }
        1 * progressListener.downloadStarted(uri, 4096, _)
        1 * progressListener.downloadStatusChanged(uri, 1600, 4096, _)
        0 * progressListener._
    }

    def "fires progress events when content size is not known"() {
        when:
        def response = mockSuccessfulHttpResponse(4096, false)
        httpClient.performGet(_ as URI, _ as ImmutableMap) >> response
        def result = externalResourceAccessor.withContent(new ExternalResourceName(uri), false, action)

        then:
        result == "result"
        1 * action.execute(_, _) >> { inputStream, metaData ->
            inputStream.read(new byte[2])
            inputStream.read(new byte[560])
            inputStream.read(new byte[1000])
            inputStream.read(new byte[1600])
            inputStream.read(new byte[1024])
            inputStream.read(new byte[1024])
            "result"
        }
        1 * progressListener.downloadStarted(uri, -1, _)
        1 * progressListener.downloadStatusChanged(uri, 2, -1, _)
        1 * progressListener.downloadStatusChanged(uri, 562, -1, _)
        1 * progressListener.downloadStatusChanged(uri, 1562, -1, _)
        1 * progressListener.downloadStatusChanged(uri, 3162, -1, _)
        1 * progressListener.downloadStatusChanged(uri, 4096, -1, _)
        0 * progressListener._
    }

    private HttpClient.Response mockSuccessfulHttpResponse(long responseStreamBytes, boolean hasHeaderContentLength = true) {
        def response = Mock(HttpClient.Response) {
            getStatusCode() >> 200
            getContent() >> new ByteArrayInputStream(new byte[responseStreamBytes])
        }
        if (hasHeaderContentLength){
            response.getHeader(HttpHeaders.CONTENT_LENGTH) >> responseStreamBytes.toString()
        }
        response
    }

    private HttpClient.Response mockFailureHttpResponse() {
        Mock(HttpClient.Response) {
            getStatusCode() >> 500
        }
    }

}
