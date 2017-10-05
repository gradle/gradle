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

import com.google.common.collect.Sets
import org.gradle.internal.BiAction
import org.gradle.util.ports.FixedAvailablePortAllocator
import org.gradle.util.ports.PortAllocator
import org.mortbay.io.EndPoint
import org.mortbay.jetty.Connector
import org.mortbay.jetty.Handler
import org.mortbay.jetty.HttpHeaders
import org.mortbay.jetty.Request
import org.mortbay.jetty.Server
import org.mortbay.jetty.bio.SocketConnector
import org.mortbay.jetty.handler.AbstractHandler
import org.mortbay.jetty.handler.HandlerCollection
import org.mortbay.jetty.security.SecurityHandler
import org.mortbay.jetty.security.SslSocketConnector

import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

trait HttpServerFixture {
    private final PortAllocator portAllocator = FixedAvailablePortAllocator.instance
    private final Server server = new Server()
    private Connector connector
    private SslSocketConnector sslConnector
    private final HandlerCollection collection = new HandlerCollection()
    private TestUserRealm realm
    private SecurityHandler securityHandler
    private AuthScheme authenticationScheme = AuthScheme.BASIC
    private boolean logRequests = true
    private final Set<String> authenticationAttempts = Sets.newLinkedHashSet()
    private boolean configured
    private int assignedPort

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
        assert server.started
        return sslConnector ? URI.create("https://localhost:${sslConnector.localPort}") : URI.create("http://localhost:${connector.localPort}")
    }

    boolean isRunning() {
        server.running
    }

    abstract Handler getCustomHandler()

    HandlerCollection getCollection() {
        return collection
    }

    Set<String> getAuthenticationAttempts() {
        return authenticationAttempts
    }

    boolean getLogRequests() {
        return logRequests
    }

    void setLogRequests(boolean logRequests) {
        this.logRequests = logRequests
    }

    AuthScheme getAuthenticationScheme() {
        return authenticationScheme
    }

    void setAuthenticationScheme(AuthScheme authenticationScheme) {
        this.authenticationScheme = authenticationScheme
    }

    void setSslPreHandler(BiAction<EndPoint,Request> handler) {
        server.connectors.each { connector ->
            if (connector instanceof InterceptableSslSocketConnector) {
                connector.sslPreHandler = handler
            }
        }
    }

    void start() {
        if (!configured) {
            HandlerCollection handlers = new HandlerCollection()
            handlers.addHandler(new LoggingHandler(authenticationAttempts, logRequests))
            handlers.addHandler(collection)
            handlers.addHandler(getCustomHandler())
            server.setHandler(handlers)
            configured = true
        }

        assignedPort = portAllocator.assignPort()
        connector = new SocketConnector()
        connector.port = assignedPort
        server.addConnector(connector)
        server.start()
        for (int i = 0; i < 5; i++) {
            if (connector.localPort > 0) {
                return
            }
            // Has failed to start for some reason - try again
            server.removeConnector(connector)
            connector.stop()
            portAllocator.releasePort(assignedPort)
            assignedPort = portAllocator.assignPort()
            connector = new SocketConnector()
            connector.port = assignedPort
            server.addConnector(connector)
            connector.start()
        }
        throw new AssertionError("SocketConnector failed to start.")
    }

    private static class LoggingHandler extends AbstractHandler {
        private final Set<String> authenticationAttempts
        private final boolean logRequests

        LoggingHandler(Set<String> authenticationAttempts, boolean logRequests) {
            this.logRequests = logRequests
            this.authenticationAttempts = authenticationAttempts
        }

        void handle(String target, HttpServletRequest request, HttpServletResponse response, int dispatch) {
            String authorization = request.getHeader(HttpHeaders.AUTHORIZATION)
            if (authorization != null) {
                synchronized (authenticationAttempts) {
                    authenticationAttempts << authorization.split(" ")[0]
                }
            } else {
                synchronized (authenticationAttempts) {
                    authenticationAttempts << "None"
                }
            }
            if (logRequests) {
                println("handling http request: $request.method $target")
            }
        }
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
        portAllocator.releasePort(assignedPort)
    }

    void reset() {
        realm = null
        collection.setHandlers()
    }

    void enableSsl(String keyStore, String keyPassword, String trustStore = null, String trustPassword = null) {
        sslConnector = new InterceptableSslSocketConnector()
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

    void requireAuthentication(String path, String username, String password) {
        if (realm != null) {
            assert realm.username == username
            assert realm.password == password
            authenticationScheme.handler.addConstraint(securityHandler, path)
        } else {
            realm = new TestUserRealm()
            realm.username = username
            realm.password = password
            securityHandler = authenticationScheme.handler.createSecurityHandler(path, realm)
            collection.addHandler(securityHandler)
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

class InterceptableSslSocketConnector extends SslSocketConnector {
    BiAction<EndPoint, Request> sslPreHandler

    void customize(EndPoint endpoint, Request request) {
        if (sslPreHandler) {
            sslPreHandler.execute(endpoint, request)
        }
        super.customize(endpoint, request)
    }
}
