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
        thrown(HttpErrorStatusCodeException)
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
