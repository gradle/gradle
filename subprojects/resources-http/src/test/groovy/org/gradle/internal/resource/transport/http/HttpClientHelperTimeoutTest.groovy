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
import org.junit.Rule
import org.junit.rules.ExternalResource
import org.mortbay.jetty.Server
import org.mortbay.jetty.handler.AbstractHandler
import spock.lang.Specification
import spock.lang.Subject

import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import java.util.concurrent.CountDownLatch

class HttpClientHelperTimeoutTest extends Specification {

    @Rule BlockingHttpServer httpServer = new BlockingHttpServer()
    @Subject HttpClientHelper client = new HttpClientHelper(httpSettings)

    def "throws exception if socket timeout is reached"() {
        when:
        client.performRequest(new HttpGet(httpServer.uri), false)

        then:
        def e = thrown(HttpRequestException)
        e.cause instanceof SocketTimeoutException
        e.cause.message == 'Read timed out'
    }

    static class BlockingHttpServer extends ExternalResource {
        private final Server server = new Server(0)
        private final CountDownLatch latch = new CountDownLatch(1)

        @Override
        protected void before() {
            server.addHandler(new AbstractHandler() {
                void handle(String target, HttpServletRequest request, HttpServletResponse response, int dispatch) {
                    try {
                        latch.await()
                    } catch (InterruptedException e) {
                        // ignore
                    }
                }
            })
            server.start()
        }

        @Override
        protected void after() {
            server.stop()
        }

        URI getUri() {
            new URI("http://localhost:${server.connectors[0].localPort}/")
        }
    }

    private HttpSettings getHttpSettings() {
        Stub(HttpSettings) {
            getProxySettings() >> Mock(HttpProxySettings)
            getSecureProxySettings() >> Mock(HttpProxySettings)
            getTimeoutSettings() >> new JavaSystemPropertiesHttpTimeoutSettings()
            getSslContextFactory() >> Mock(SslContextFactory) {
                createSslContext() >> SSLContexts.createDefault()
            }
        }
    }
}
