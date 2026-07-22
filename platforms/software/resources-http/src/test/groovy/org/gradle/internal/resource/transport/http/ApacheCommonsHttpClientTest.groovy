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

import com.google.common.collect.ImmutableMap
import org.apache.http.HttpEntity
import org.apache.http.StatusLine
import org.apache.http.client.methods.CloseableHttpResponse
import org.apache.http.client.methods.HttpUriRequest
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClientBuilder
import org.apache.http.protocol.HttpContext
import org.apache.http.ssl.SSLContexts
import org.gradle.api.internal.DocumentationRegistry
import org.gradle.util.SetSystemProperties
import org.junit.Rule
import spock.lang.Specification

class ApacheCommonsHttpClientTest extends Specification {

    @Rule SetSystemProperties sysProp = new SetSystemProperties()

    private ApacheCommonsHttpClient client

    def setup() {
        client = new ApacheCommonsHttpClient(new DocumentationRegistry(), httpSettings, () -> HttpClientBuilder.create())
    }

    def "throws HttpRequestException if an IO error occurs during a request"() {
        def client = new ApacheCommonsHttpClient(new DocumentationRegistry(), httpSettings, () -> {
            Stub(HttpClientBuilder) {
                build() >> Mock(CloseableHttpClient) {
                    execute(_ as HttpUriRequest, _ as HttpContext) >> {
                        throw new IOException("ouch")
                    }
                }
            }
        })

        when:
        client.performGet(URI.create("http://gradle.org"), ImmutableMap.of())

        then:
        HttpRequestException e = thrown()
        e.cause.message == "ouch"
    }

    def "response is closed if an error occurs during a request"() {
        def response = Mock(CloseableHttpResponse) {
            getStatusLine() >> Mock(StatusLine) {
                getStatusCode() >> 500
            }
            getEntity() >> Mock(HttpEntity) {
                isStreaming() >> true
                getContent() >> Mock(InputStream)
            }
            // extractErrorDetail() checks Content-Type before attempting RFC9457 parsing
            getFirstHeader("Content-Type") >> null
        }

        def client = new ApacheCommonsHttpClient(new DocumentationRegistry(), httpSettings, () -> {
            Stub(HttpClientBuilder) {
                build() >> Mock(CloseableHttpClient) {
                    execute(_ as HttpUriRequest, _ as HttpContext) >> {
                        return response
                    }
                }
            }
        })

        when:
        client.performGet(URI.create("http://gradle.org"), ImmutableMap.of())

        then:
        def e = thrown(HttpRequestException)
        e.cause instanceof HttpErrorStatusCodeException
        1 * response.close()
        1 * response.entity.content.close()
    }

    def "stripping user credentials removes username and password"() {
        given:
        def uri = new URI("https", "admin:password", "foo.example", 80, null, null, null)

        when:
        def strippedUri = ApacheCommonsHttpClient.stripUserCredentials(uri)

        then:
        strippedUri.userInfo == null
        strippedUri.scheme == "https"
        strippedUri.host == "foo.example"
    }

    def "parseRFC9457Response extracts detail field"() {
        given:
        def responseBody = '{"type": "about:blank", "title": "Not Found", "status": 404, "detail": "The requested artifact was not found in the repository"}'
        def response = createMockResponse("application/problem+json", responseBody)

        when:
        def result = client.parseRFC9457Response(response)

        then:
        result == Optional.of("The requested artifact was not found in the repository")
    }

    def "parseRFC9457Response falls back to title if detail is missing"() {
        given:
        def responseBody = '{"type": "about:blank", "title": "Not Found", "status": 404}'
        def response = createMockResponse("application/problem+json", responseBody)

        when:
        def result = client.parseRFC9457Response(response)

        then:
        result == Optional.of("Not Found")
    }

    def "parseRFC9457Response falls back to title if detail is empty string"() {
        given:
        def responseBody = '{"title": "Server Overloaded", "detail": ""}'
        def response = createMockResponse("application/problem+json", responseBody)

        when:
        def result = client.parseRFC9457Response(response)

        then:
        result == Optional.of("Server Overloaded")
    }

    def "parseRFC9457Response returns empty for invalid JSON"() {
        given:
        def responseBody = 'not a json'
        def response = createMockResponse("application/problem+json", responseBody)

        when:
        def result = client.parseRFC9457Response(response)

        then:
        result == Optional.empty()
    }

    def "parseRFC9457Response returns empty when content is missing"() {
        given:
        def response = Mock(HttpClient.Response) {
            getContent() >> { throw new IOException("no content") }
        }

        when:
        def result = client.parseRFC9457Response(response)

        then:
        result == Optional.empty()
    }

    def "extractErrorDetail uses RFC9457 when content-type is application/problem+json"() {
        given:
        def responseBody = '{"detail": "Custom error message from registry"}'
        def response = createMockResponse("application/problem+json", responseBody)

        when:
        def result = client.extractErrorDetail(response)

        then:
        result == Optional.of("Custom error message from registry")
    }

    def "extractErrorDetail matches content-type case-insensitively"() {
        given:
        def responseBody = '{"detail": "Weird casing but still problem+json"}'
        def response = createMockResponse("Application/Problem+JSON; charset=UTF-8", responseBody)

        when:
        def result = client.extractErrorDetail(response)

        then:
        result == Optional.of("Weird casing but still problem+json")
    }

    def "extractErrorDetail falls back to reason phrase for non-RFC9457 responses"() {
        given:
        def response = Mock(HttpClient.Response) {
            getHeader("Content-Type") >> "text/html"
            getStatusReason() >> "Not Found"
        }

        when:
        def result = client.extractErrorDetail(response)

        then:
        result == Optional.of("Not Found")
    }

    def "extractErrorDetail returns empty when reason phrase is null"() {
        given:
        def response = Mock(HttpClient.Response) {
            getHeader("Content-Type") >> "text/html"
            getStatusReason() >> null
        }

        when:
        def result = client.extractErrorDetail(response)

        then:
        result == Optional.empty()
    }

    def "extractErrorDetail returns empty when reason phrase is empty string"() {
        given:
        def response = Mock(HttpClient.Response) {
            getHeader("Content-Type") >> null
            getStatusReason() >> ""
        }

        when:
        def result = client.extractErrorDetail(response)

        then:
        result == Optional.empty()
    }

    def "extractErrorDetail falls back to reason phrase when RFC9457 body has no usable fields"() {
        given:
        def response = createMockResponse("application/problem+json", '{"type": "about:blank"}')

        when:
        def result = client.extractErrorDetail(response)

        then: "type-only responses fall through to reason phrase via createMockResponse"
        result == Optional.of("Bad Request")
    }

    def "extractErrorDetail handles RFC9457 with charset in content-type"() {
        given:
        def responseBody = '{"detail": "Error with charset"}'
        def response = createMockResponse("application/problem+json; charset=utf-8", responseBody)

        when:
        def result = client.extractErrorDetail(response)

        then:
        result == Optional.of("Error with charset")
    }

    def "parseRFC9457Response returns empty for empty JSON object"() {
        given:
        def responseBody = '{}'
        def response = createMockResponse("application/problem+json", responseBody)

        when:
        def result = client.parseRFC9457Response(response)

        then:
        result == Optional.empty()
    }

    def "parseRFC9457Response handles all RFC9457 fields"() {
        given:
        def responseBody = '{"type": "https://example.com/error", "title": "Not Found", "status": 404, "detail": "The requested resource was not found", "instance": "/resource/123"}'
        def response = createMockResponse("application/problem+json", responseBody)

        when:
        def result = client.parseRFC9457Response(response)

        then:
        result == Optional.of("The requested resource was not found")
    }

    def "parseRFC9457Response ignores unknown fields (vendor extensions)"() {
        given:
        def responseBody = '{"detail": "Rate limited", "retryAfter": 60, "quotaRemaining": 0}'
        def response = createMockResponse("application/problem+json", responseBody)

        when:
        def result = client.parseRFC9457Response(response)

        then:
        result == Optional.of("Rate limited")
    }

    private HttpClient.Response createMockResponse(String contentType, String responseBody) {
        def inputStream = new ByteArrayInputStream(responseBody.getBytes("UTF-8"))
        return Mock(HttpClient.Response) {
            getHeader("Content-Type") >> contentType
            getContent() >> inputStream
            getStatusReason() >> "Bad Request"
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
