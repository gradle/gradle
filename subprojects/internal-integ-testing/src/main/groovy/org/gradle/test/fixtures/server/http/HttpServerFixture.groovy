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

package org.gradle.test.fixtures.server.http

import org.mortbay.jetty.Connector
import org.mortbay.jetty.Server
import org.mortbay.jetty.bio.SocketConnector
import org.mortbay.jetty.security.SslSocketConnector

trait HttpServerFixture {
    private final Server server = new Server(0)
    private Connector connector
    private SslSocketConnector sslConnector

    Server getServer() {
        server
    }

    String getAddress() {
        if (!server.started) {
            server.start()
        }
        getUri().toString()
    }

    URI getUri() {
        return sslConnector ? URI.create("https://localhost:${sslConnector.localPort}") : URI.create("http://localhost:${connector.localPort}")
    }

    boolean isRunning() {
        server.running
    }

    void start() {
        connector = new SocketConnector()
        connector.port = 0
        server.addConnector(connector)
        server.start()
        for (int i = 0; i < 5; i++) {
            if (connector.localPort > 0) {
                return;
            }
            // Has failed to start for some reason - try again
            server.removeConnector(connector)
            connector.stop()
            connector = new SocketConnector()
            connector.port = 0
            server.addConnector(connector)
            connector.start()
        }
        throw new AssertionError("SocketConnector failed to start.");
    }

    void stop() {
        if (sslConnector) {
            shutdownConnector(sslConnector)
            sslConnector = null
        }

        if (connector) {
            shutdownConnector(connector)
            connector = null
        }

        server?.stop()
    }

    void enableSsl(String keyStore, String keyPassword, String trustStore = null, String trustPassword = null) {
        sslConnector = new SslSocketConnector()
        sslConnector.keystore = keyStore
        sslConnector.keyPassword = keyPassword
        if (trustStore) {
            sslConnector.needClientAuth = true
            sslConnector.truststore = trustStore
            sslConnector.trustPassword = trustPassword
        }
        server.addConnector(sslConnector)
        if (server.started) {
            sslConnector.start()
        }
    }

    int getPort() {
        return connector.localPort
    }

    int getSslPort() {
        sslConnector.localPort
    }

    private void shutdownConnector(Connector connector) {
        connector.stop()
        connector.close()
        server?.removeConnector(connector)
    }
}
