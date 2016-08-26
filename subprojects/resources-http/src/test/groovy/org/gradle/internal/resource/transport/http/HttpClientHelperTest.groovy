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

import org.apache.http.HttpEntity
import org.apache.http.ProtocolVersion
import org.apache.http.client.methods.CloseableHttpResponse
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.methods.HttpRequestBase
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.message.BasicStatusLine
import org.apache.http.ssl.SSLContexts
import org.gradle.util.SetSystemProperties
import org.junit.Rule
import spock.lang.Specification

class HttpClientHelperTest extends Specification {
    @Rule SetSystemProperties sysProp = new SetSystemProperties()

    def "throws HttpRequestException if an IO error occurs during a request"() {
        def client = new HttpClientHelper(httpSettings) {
            @Override
            protected CloseableHttpResponse executeGetOrHead(HttpRequestBase method) {
                throw new IOException("ouch")
            }
        }

        when:
        client.performRequest(new HttpGet("http://gradle.org"))

        then:
        HttpRequestException e = thrown()
        e.cause.message == "ouch"
    }

    def "response is closed if an error occurs during a request"() {
        def client = new HttpClientHelper(httpSettings)
        CloseableHttpClient httpClient = Mock()
        client.client = httpClient
        CloseableHttpResponse response = Mock()
        HttpEntity entity = Mock()
        InputStream content = Mock()

        when:
        client.performRequest(new HttpGet("http://gradle.org"))

        then:
        1 * httpClient.execute(_, _) >> response
        _ * response.getStatusLine() >> new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), 400, "I'm broken")
        1 * response.close()
        1 * response.getEntity() >> entity
        1 * entity.isStreaming() >> true
        1 * entity.content >> content
        1 * content.close()
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
