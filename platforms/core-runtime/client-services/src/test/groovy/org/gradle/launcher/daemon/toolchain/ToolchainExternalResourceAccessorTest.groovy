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

import org.apache.http.HttpHeaders
import org.apache.http.StatusLine
import org.apache.http.client.methods.CloseableHttpResponse
import org.apache.http.entity.InputStreamEntity
import org.apache.http.message.BasicHeader
import org.gradle.api.resources.ResourceException
import org.gradle.internal.resource.ExternalResource
import org.gradle.internal.resource.ExternalResourceName
import org.gradle.internal.resource.transport.http.HttpClientHelper
import org.gradle.internal.resource.transport.http.HttpClientResponse
import org.gradle.internal.time.Clock
import spock.lang.Specification

class ToolchainExternalResourceAccessorTest extends Specification {

    ToolchainExternalResourceAccessor externalResourceAccessor
    def httpClientHelper = Mock(HttpClientHelper)
    def progressListener = Mock(DownloadProgressListener)
    def action = Mock(ExternalResource.ContentAndMetadataAction)
    def clock = Mock(Clock)
    def uri = new URI("https://server.com/toolchain.zip")

    def setup() {
        externalResourceAccessor = new ToolchainExternalResourceAccessor(httpClientHelper, clock, progressListener)
    }

    def "fails with expected message when response has no content"() {
        def response = mockFailureHttpResponse()
        httpClientHelper.performGet(_ as String, _ as Boolean) >> new HttpClientResponse("GET", uri, response)

        when:
        externalResourceAccessor.withContent(new ExternalResourceName(uri), false, action)

        then:
        def e = thrown(ResourceException)
        e.message == "Could not get resource 'https://server.com/toolchain.zip'."
        e.cause.message == "Response 500: null has no content!"
    }

    def "should call close() on CloseableHttpResource when withContent is called"() {
        given:
        def response = mockSuccessfulHttpResponse(0)
        httpClientHelper.performGet(_ as String, _ as Boolean) >> new HttpClientResponse("GET", uri, response)

        when:
        externalResourceAccessor.withContent(new ExternalResourceName(uri), false, action)

        then:
        1 * response.close()
    }

    def "returns null when resource does not exist"() {
        when:
        def response = mockSuccessfulHttpResponse(0)
        httpClientHelper.performGet(_ as String, _ as Boolean) >> new HttpClientResponse("GET", uri, response)
        def result = externalResourceAccessor.withContent(new ExternalResourceName(uri), false, action)

        then:
        result == null
        1 * action.execute(_, _) >> null
    }

    def "reads empty content"() {
        when:
        def response = mockSuccessfulHttpResponse(0)
        httpClientHelper.performGet(_ as String, _ as Boolean) >> new HttpClientResponse("GET", uri, response)
        def result = externalResourceAccessor.withContent(new ExternalResourceName(uri), false, action)

        then:
        result == "result"
        1 * action.execute(_, _) >> "result"
    }

    def "fires progress events as content is read"() {
        when:
        def response = mockSuccessfulHttpResponse(4096)
        httpClientHelper.performGet(_ as String, _ as Boolean) >> new HttpClientResponse("GET", uri, response)
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
        httpClientHelper.performGet(_ as String, _ as Boolean) >> new HttpClientResponse("GET", uri, response)
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
        httpClientHelper.performGet(_ as String, _ as Boolean) >> new HttpClientResponse("GET", uri, response)
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

    private CloseableHttpResponse mockSuccessfulHttpResponse(long responseStreamBytes, boolean hasHeaderContentLength = true) {
        def response = Mock(CloseableHttpResponse)
        response.getStatusLine() >> Mock(StatusLine) {
            getStatusCode() >> 200
        }
        response.getEntity() >> new InputStreamEntity(new ByteArrayInputStream(new byte[responseStreamBytes]))
        if (hasHeaderContentLength) {
            response.getFirstHeader(HttpHeaders.CONTENT_LENGTH) >> new BasicHeader(HttpHeaders.CONTENT_LENGTH, responseStreamBytes.toString())
        }
        response
    }

    private CloseableHttpResponse mockFailureHttpResponse() {
        def response = Mock(CloseableHttpResponse)
        response.getStatusLine() >> Mock(StatusLine) {
            getStatusCode() >> 500
        }
        response.getEntity() >> null
        response
    }
}
