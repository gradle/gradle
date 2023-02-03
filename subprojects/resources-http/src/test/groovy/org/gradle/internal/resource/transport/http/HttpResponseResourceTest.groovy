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

package org.gradle.internal.resource.transport.http

import org.apache.http.Header
import org.apache.http.HttpEntity
import org.apache.http.HttpHeaders
import org.apache.http.StatusLine
import org.apache.http.client.methods.CloseableHttpResponse
import org.apache.http.message.BasicHeader

class HttpResponseResourceTest extends AbstractHttpClientTest {

    def sourceUrl = new URI("http://gradle.org")
    def method = "GET"
    def response = mockResponse()

    private mockResponse() {
        def response = Mock(CloseableHttpResponse)
        response.getStatusLine() >> Mock(StatusLine)
        response
    }

    def "extracts etag"() {
        given:
        addHeader(HttpHeaders.ETAG, "abc")

        expect:
        resource().metaData.etag == "abc"
    }

    def "handles no etag"() {
        expect:
        resource().metaData.etag == null
    }

    def "is not openable more than once"() {
        setup:
        1 * response.entity >> Mock(HttpEntity)
        when:
        def resource = this.resource()
        resource.openStream()
        and:
        resource.openStream()
        then:
        def ex = thrown(IOException);
        ex.message == "Unable to open Stream as it was opened before."
    }

    def "provides access to arbitrary headers"() {
        given:
        addHeader(name, value)

        expect:
        resource().getHeaderValue(name) == value

        where:
        name = "X-Client-Deprecation-Message"
        value = "Some message"
    }

    def "returns null when accessing value of a non existing header"() {
        expect:
        resource().getHeaderValue("X-No-Such-Header") == null
    }

    def "close closes the response"() {
        given:
        def mockedHttpResponse = mockedHttpResponse(response)

        when:
        resource().close()

        then:
        interaction {
            assertIsClosedCorrectly(mockedHttpResponse)
        }
    }

    HttpResponseResource resource() {
        new HttpResponseResource(method, sourceUrl, new HttpClientResponse("GET", sourceUrl, response))
    }

    void addHeader(String name, String value) {
        interaction {
            1 * response.getFirstHeader(name) >> header(name, value)
        }
    }

    Header header(String name, String value) {
        new BasicHeader(name, value)
    }
}
