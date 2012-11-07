/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.test.fixtures.server.http

import ch.qos.logback.classic.Level
import org.gradle.util.AvailablePortFinder
import org.jboss.netty.handler.codec.http.HttpRequest
import org.junit.rules.ExternalResource
import org.slf4j.LoggerFactory
import org.littleshoot.proxy.*

/**
 * A Proxy Server used for testing that http proxies are correctly supported.
 * This proxy server will forward all requests to the supplied HttpServer. The true target of the request is ignored.
 * This is necessary because we can't force java to use a proxy for localhost addresses (using the default java ProxySelector).
 */
class TestProxyServer extends ExternalResource {
    private HttpProxyServer proxyServer
    private HttpServer httpServer

    int port
    int requestCount

    TestProxyServer(HttpServer httpServer) {
        this.httpServer = httpServer
    }

    @Override
    protected void after() {
        stop()
    }

    void start() {
        // Ignore warnings from this class
        LoggerFactory.getLogger(HttpRequestHandler).level = Level.ERROR

        port = AvailablePortFinder.createPrivate().nextAvailable
        String remote = "localhost:${httpServer.port}"
        proxyServer = new DefaultHttpProxyServer(port, [:], remote, null, new HttpRequestFilter() {
            void filter(HttpRequest httpRequest) {
                requestCount++
            }
        })
        proxyServer.start()
    }

    void stop() {
        proxyServer?.stop()
    }

    void requireAuthentication(final String expectedUsername, final String expectedPassword) {
        proxyServer.addProxyAuthenticationHandler(new ProxyAuthorizationHandler() {
            boolean authenticate(String username, String password) {
                return username == expectedUsername && password == expectedPassword
            }
        })
    }
}

