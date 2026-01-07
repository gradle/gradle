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

import org.apache.http.ProtocolVersion
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.methods.HttpRequestBase
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.message.BasicStatusLine
import org.apache.http.ssl.SSLContexts
import org.gradle.api.internal.DocumentationRegistry
import org.gradle.util.SetSystemProperties
import org.junit.Rule

class HttpClientHelperTest extends AbstractHttpClientTest {
    @Rule SetSystemProperties sysProp = new SetSystemProperties()

    def "throws HttpRequestException if an IO error occurs during a request"() {
        def client = new HttpClientHelper(new DocumentationRegistry(), httpSettings) {
            @Override
            protected HttpClientResponse executeGetOrHead(HttpRequestBase method, boolean closeOnError) {
                throw new IOException("ouch")
            }
        }

        when:
        client.performRequest(new HttpGet("http://gradle.org"), false)

        then:
        HttpRequestException e = thrown()
        e.cause.message == "ouch"
    }

    def "response is closed if an error occurs during a request"() {
        def client = new HttpClientHelper(new DocumentationRegistry(), httpSettings)
        CloseableHttpClient httpClient = Mock()
        client.client = httpClient
        MockedHttpResponse mockedHttpResponse = mockedHttpResponse()

        when:
        client.performRequest(new HttpGet("http://gradle.org"), false)

        then:
        interaction {
            1 * httpClient.execute(_, _) >> mockedHttpResponse.response
            // Response will check for RFC9457 content type before closing
            _ * mockedHttpResponse.response.getFirstHeader("Content-Type") >> null
            assertIsClosedCorrectly(mockedHttpResponse)
        }
    }

    def "request with revalidate adds Cache-Control header"() {
        def client = new HttpClientHelper(new DocumentationRegistry(), httpSettings) {
            @Override
            protected HttpClientResponse executeGetOrHead(HttpRequestBase method, boolean closeOnError) {
                return null
            }
        }

        when:
        def request = new HttpGet("http://gradle.org")
        client.performRequest(request, true)

        then:
        request.getHeaders("Cache-Control")[0].value == "max-age=0"
    }

    def "stripping user credentials removes username and password"() {
        given:
        def uri = new URI("https", "admin:password", "foo.example", 80, null, null, null)

        when:
        def strippedUri = HttpClientHelper.stripUserCredentials(uri)

        then:
        strippedUri.userInfo == null
        strippedUri.scheme == "https"
        strippedUri.host == "foo.example"
    }

    def "parseRFC9457Response extracts detail field"() {
        given:
        def client = new HttpClientHelper(new DocumentationRegistry(), httpSettings)
        def responseBody = '{"type": "about:blank", "title": "Not Found", "status": 404, "detail": "The requested artifact was not found in the repository"}'
        def response = createMockResponse("application/problem+json", responseBody)

        when:
        def result = client.parseRFC9457Response(response)

        then:
        result == "The requested artifact was not found in the repository"
    }

    def "parseRFC9457Response falls back to title if detail is missing"() {
        given:
        def client = new HttpClientHelper(new DocumentationRegistry(), httpSettings)
        def responseBody = '{"type": "about:blank", "title": "Not Found", "status": 404}'
        def response = createMockResponse("application/problem+json", responseBody)

        when:
        def result = client.parseRFC9457Response(response)

        then:
        result == "Not Found"
    }

    def "parseRFC9457Response returns null for invalid JSON"() {
        given:
        def client = new HttpClientHelper(new DocumentationRegistry(), httpSettings)
        def responseBody = 'not a json'
        def response = createMockResponse("application/problem+json", responseBody)

        when:
        def result = client.parseRFC9457Response(response)

        then:
        result == null
    }

    def "parseRFC9457Response returns null when content is null"() {
        given:
        def client = new HttpClientHelper(new DocumentationRegistry(), httpSettings)
        def response = Mock(HttpClientResponse) {
            getContent() >> null
        }

        when:
        def result = client.parseRFC9457Response(response)

        then:
        result == null
    }

    def "extractErrorDetail uses RFC9457 when content-type is application/problem+json"() {
        given:
        def client = new HttpClientHelper(new DocumentationRegistry(), httpSettings)
        def responseBody = '{"detail": "Custom error message from registry"}'
        def response = createMockResponse("application/problem+json", responseBody)

        when:
        def result = client.extractErrorDetail(response)

        then:
        result == "Custom error message from registry"
    }

    def "extractErrorDetail falls back to reason phrase for non-RFC9457 responses"() {
        given:
        def client = new HttpClientHelper(new DocumentationRegistry(), httpSettings)
        def response = Mock(HttpClientResponse) {
            getHeader("Content-Type") >> "text/html"
            getStatusLine() >> new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), 404, "Not Found")
        }

        when:
        def result = client.extractErrorDetail(response)

        then:
        result == "Not Found"
    }

    def "extractErrorDetail returns empty string when reason phrase is null"() {
        given:
        def client = new HttpClientHelper(new DocumentationRegistry(), httpSettings)
        def response = Mock(HttpClientResponse) {
            getHeader("Content-Type") >> "text/html"
            getStatusLine() >> new BasicStatusLine(new ProtocolVersion("HTTP", 2, 0), 404, null)
        }

        when:
        def result = client.extractErrorDetail(response)

        then:
        result == ""
    }

    def "extractErrorDetail handles RFC9457 with charset in content-type"() {
        given:
        def client = new HttpClientHelper(new DocumentationRegistry(), httpSettings)
        def responseBody = '{"detail": "Error with charset"}'
        def response = createMockResponse("application/problem+json; charset=utf-8", responseBody)

        when:
        def result = client.extractErrorDetail(response)

        then:
        result == "Error with charset"
    }

    def "parseRFC9457Response handles empty JSON object"() {
        given:
        def client = new HttpClientHelper(new DocumentationRegistry(), httpSettings)
        def responseBody = '{}'
        def response = createMockResponse("application/problem+json", responseBody)

        when:
        def result = client.parseRFC9457Response(response)

        then:
        result == null
    }

    def "parseRFC9457Response handles all RFC9457 fields"() {
        given:
        def client = new HttpClientHelper(new DocumentationRegistry(), httpSettings)
        def responseBody = '{"type": "https://example.com/error", "title": "Not Found", "status": 404, "detail": "The requested resource was not found", "instance": "/resource/123"}'
        def response = createMockResponse("application/problem+json", responseBody)

        when:
        def result = client.parseRFC9457Response(response)

        then:
        result == "The requested resource was not found"
    }

    private HttpClientResponse createMockResponse(String contentType, String responseBody) {
        def inputStream = new ByteArrayInputStream(responseBody.getBytes("UTF-8"))
        return Mock(HttpClientResponse) {
            getHeader("Content-Type") >> contentType
            getContent() >> inputStream
            getStatusLine() >> new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), 400, "Bad Request")
        }
    }

    private HttpSettings getHttpSettings() {
        return Stub(HttpSettings) {
            getProxySettings() >> Mock(HttpProxySettings)
            getSecureProxySettings() >> Mock(HttpProxySettings)
            getSslContextFactory() >> Mock(SslContextFactory) {
                createSslContext() >> SSLContexts.createDefault()
            }
        }
    }
}
