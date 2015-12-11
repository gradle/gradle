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

import org.gradle.util.ports.FixedAvailablePortAllocator
import org.jboss.netty.handler.codec.http.HttpRequest
import org.junit.rules.ExternalResource
import org.littleshoot.proxy.*

import javax.net.ssl.TrustManager

/**
 * A Proxy Server used for testing that http proxies are correctly supported.
 * This proxy server will forward all requests to the supplied HttpServer. The true target of the request is ignored.
 * This is necessary because we can't force java to use a proxy for localhost addresses (using the default java ProxySelector).
 */
class TestProxyServer extends ExternalResource {
    private HttpProxyServer proxyServer
    private HttpServer httpServer
    private portFinder = FixedAvailablePortAllocator.getInstance()
    private File trustStore
    private String trustStorePassword

    int port
    int requestCount

    TestProxyServer(HttpServer httpServer) {
        this.httpServer = httpServer
    }

    TestProxyServer(HttpServer httpServer, File trustStore, String trustStorePassword) {
        this(httpServer)
        this.trustStore = trustStore
        this.trustStorePassword = trustStorePassword
    }

    @Override
    protected void after() {
        stop()
    }

    void start() {
        port = portFinder.assignPort()
        String remote = "localhost:${httpServer.port}"
        KeyStoreManager keyStoreManager = null
        if (trustStore != null) {
            keyStoreManager = new TestKeyStoreManager(trustStore, trustStorePassword)
        }
        proxyServer = new DefaultHttpProxyServer(port, [:], remote, keyStoreManager, new HttpRequestFilter() {
            void filter(HttpRequest httpRequest) {
                requestCount++
            }
        })
        proxyServer.start()
    }

    void stop() {
        proxyServer?.stop()
        portFinder.releasePort(port)
    }

    void requireAuthentication(final String expectedUsername, final String expectedPassword) {
        proxyServer.addProxyAuthenticationHandler(new ProxyAuthorizationHandler() {
            boolean authenticate(String username, String password) {
                return username == expectedUsername && password == expectedPassword
            }
        })
    }

    private static class TestKeyStoreManager implements KeyStoreManager {
        private File trustStore
        private String trustStorePassword

        TestKeyStoreManager(File trustStore, String trustStorePassword) {
            this.trustStore = trustStore
            this.trustStorePassword = trustStorePassword
        }

        @Override
        void addBase64Cert(String alias, String base64Cert) throws IOException { }

        @Override
        String getBase64Cert() {
            return null
        }

        @Override
        InputStream keyStoreAsInputStream() {
            return new FileInputStream(trustStore)
        }

        @Override
        char[] getCertificatePassword() {
            return trustStorePassword.toCharArray()
        }

        @Override
        char[] getKeyStorePassword() {
            return trustStorePassword.toCharArray()
        }

        @Override
        TrustManager[] getTrustManagers() {
            return null
        }

        @Override
        InputStream trustStoreAsInputStream() {
            return null
        }
    }
}

