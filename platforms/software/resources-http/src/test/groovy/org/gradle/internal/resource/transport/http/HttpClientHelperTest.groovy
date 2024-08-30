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

import org.apache.http.HttpHeaders
import org.apache.http.HttpStatus
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.methods.HttpRequestBase
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.ssl.SSLContexts
import org.gradle.api.internal.DocumentationRegistry
import org.gradle.util.SetSystemProperties
import org.junit.Rule
import org.junit.jupiter.api.Nested

class HttpClientHelperTest extends AbstractHttpClientTest {
    @Rule
    SetSystemProperties sysProp = new SetSystemProperties()

    def "throws HttpRequestException if an IO error occurs during a request"() {
        def client = new HttpClientHelper(new DocumentationRegistry(), httpSettings) {
            @Override
            protected HttpClientResponse executeGetOrHead(HttpRequestBase method) {
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
            assertIsClosedCorrectly(mockedHttpResponse)
        }
    }

    def "request with revalidate adds Cache-Control header"() {
        def client = new HttpClientHelper(new DocumentationRegistry(), httpSettings) {
            @Override
            protected HttpClientResponse executeGetOrHead(HttpRequestBase method) {
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

    private HttpSettings getHttpSettings() {
        return Stub(HttpSettings) {
            getProxySettings() >> Mock(HttpProxySettings)
            getSecureProxySettings() >> Mock(HttpProxySettings)
            getSslContextFactory() >> Mock(SslContextFactory) {
                createSslContext() >> SSLContexts.createDefault()
            }
        }
    }

    @Nested
    class RangeGet {
        def "get valid range"() {
            def client = new HttpClientHelper(new DocumentationRegistry(), httpSettings)

            when:
            def source = "https://gradle.org/icon/favicon.ico" // 5083 bytes at 2024-08-30
            def response = client.performGet(source, true, 0, 100)

            then:
            response.statusLine.statusCode == HttpStatus.SC_PARTIAL_CONTENT

            def contentRange = response.getHeader(HttpHeaders.CONTENT_RANGE)
            contentRange != null

            contentRange.matches("bytes 0-100/\\d+")

            response.getHeader(HttpHeaders.CONTENT_LENGTH) == null
        }

        def "get invalid range"() {
            def client = new HttpClientHelper(new DocumentationRegistry(), httpSettings)

            when:
            def source = "https://gradle.org/icon/favicon.ico" // 5083 bytes at 2024-08-30
            def requestRangeStart = 0
            def requestRangeEnd = 6000
            def response = client.performGet(source, true, requestRangeStart, requestRangeEnd)

            then:
            response.statusLine.statusCode == HttpStatus.SC_PARTIAL_CONTENT

            def contentRange = response.getHeader(HttpHeaders.CONTENT_RANGE)
            contentRange != null

            def split = contentRange.replace("bytes ", "").split("/")
            def receivedBytesRange = split[0].split("-")
            def totalBytes = split[1]
            receivedBytesRange[0] == requestRangeStart.toString()
            receivedBytesRange[1] != requestRangeEnd
            receivedBytesRange[1] == Long.parseLong(totalBytes) - 1

            response.getHeader(HttpHeaders.CONTENT_LENGTH) == null
        }
    }
}
