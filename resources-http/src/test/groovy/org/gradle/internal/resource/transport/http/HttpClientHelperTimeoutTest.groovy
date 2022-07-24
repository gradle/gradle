/*
 * Copyright 2017 the original author or authors.
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

import org.apache.http.client.methods.HttpGet
import org.apache.http.ssl.SSLContexts
import org.gradle.api.internal.DocumentationRegistry
import org.gradle.test.fixtures.server.http.HttpServer
import org.gradle.util.SetSystemProperties
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Subject

import static org.gradle.internal.resource.transport.http.JavaSystemPropertiesHttpTimeoutSettings.SOCKET_TIMEOUT_SYSTEM_PROPERTY

class HttpClientHelperTimeoutTest extends Specification {

    @Rule SetSystemProperties systemProperties = new SetSystemProperties((SOCKET_TIMEOUT_SYSTEM_PROPERTY): "500")
    @Rule HttpServer httpServer = new HttpServer()
    @Subject HttpClientHelper client = new HttpClientHelper(new DocumentationRegistry(), httpSettings)

    def "throws exception if socket timeout is reached"() {
        given:
        httpServer.expectGetBlocking("/")
        httpServer.start()

        when:
        client.performRequest(new HttpGet("${httpServer.uri}/"), false)

        then:
        def e = thrown(HttpRequestException)
        e.cause instanceof SocketTimeoutException
        e.cause.message == 'Read timed out'
    }

    private HttpSettings getHttpSettings() {
        Stub(HttpSettings) {
            getProxySettings() >> Mock(HttpProxySettings)
            getSecureProxySettings() >> Mock(HttpProxySettings)
            getTimeoutSettings() >> { new JavaSystemPropertiesHttpTimeoutSettings() }
            getSslContextFactory() >> Mock(SslContextFactory) {
                createSslContext() >> SSLContexts.createDefault()
            }
        }
    }
}
