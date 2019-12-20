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
import groovy.transform.CompileStatic
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

@CompileStatic
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
    private boolean useHostnameForUrl = false
    private final Set<String> authenticationAttempts = Sets.newLinkedHashSet()
    private final Set<Map<String, String>> allHeaders = Sets.newLinkedHashSet()
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
        if (sslConnector) {
            return URI.create("https://localhost:${sslConnector.localPort}")
        } else if (useHostnameForUrl) {
            // If used in a code-path that interacts with the HttpClientHelper, this will fail validation.
            return URI.create("http://localhost:${connector.localPort}")
        } else {
            // The HttpClientHelper will not do HTTPS validation if the host matches 127.0.0.1
            // This allows us to run integration tests without needing to use the TestKeyStore in every single test.
            return URI.create("http://127.0.0.1:${connector.localPort}")
        }
    }

    URI uri(String path) {
        return getUri().resolve(path)
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

    Set<Map<String, String>> getAllHeaders() {
        return allHeaders
    }

    boolean getLogRequests() {
        return logRequests
    }

    void setLogRequests(boolean logRequests) {
        this.logRequests = logRequests
    }

    /**
     * Use the hostname for the server's URL instead of the IP.
     */
    void useHostname() {
        this.useHostnameForUrl = true
    }

    AuthScheme getAuthenticationScheme() {
        return authenticationScheme
    }

    void setAuthenticationScheme(AuthScheme authenticationScheme) {
        this.authenticationScheme = authenticationScheme
    }

    void setSslPreHandler(BiAction<EndPoint, Request> handler) {
        server.connectors.each { connector ->
            if (connector instanceof InterceptableSslSocketConnector) {
                connector.sslPreHandler = handler
            }
        }
    }

    void start() {
        if (!configured) {
            HandlerCollection handlers = new HandlerCollection()
            handlers.addHandler(new LoggingHandler(authenticationAttempts, allHeaders, logRequests))
            handlers.addHandler(collection)
            handlers.addHandler(getCustomHandler())
            server.setHandler(handlers)
            configured = true
        }

        server.start()
        for (int i = 0; i < 5; i++) {
            if (createConnector() && connector.localPort > 0) {
                return
            }
            // Has failed to start for some reason - try again
            releaseConnector()
        }
        throw new AssertionError((Object)"SocketConnector failed to start.") // cast because of Groovy bug
    }

    private void releaseConnector() {
        server.removeConnector(connector)
        connector.stop()
        portAllocator.releasePort(assignedPort)
    }

    private boolean createConnector() {
        assignedPort = portAllocator.assignPort()
        connector = new SocketConnector()
        connector.port = assignedPort
        server.addConnector(connector)
        try {
            connector.start()
            return true
        } catch (e) {
            println "Unable to start connector on port $assignedPort"
        }
        return false
    }

    static class LoggingHandler extends AbstractHandler {
        private final Set<String> authenticationAttempts
        private final Set<Map<String, String>> allHeaders
        private final boolean logRequests

        LoggingHandler(Set<String> authenticationAttempts, Set<Map<String, String>> allHeaders, boolean logRequests) {
            this.logRequests = logRequests
            this.authenticationAttempts = authenticationAttempts
            this.allHeaders = allHeaders
        }

        void handle(String target, HttpServletRequest request, HttpServletResponse response, int dispatch) {
            allHeaders.add(request.getHeaderNames().toList().collectEntries { headerName -> [headerName, request.getHeader(headerName as String)] })
            String authorization = getAuthorizationHeader(request)
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
                println("handling http request: $request.method $target${request.queryString ? "?" + request.queryString : ''}")
            }
        }

        protected String getAuthorizationHeader(HttpServletRequest request) {
            def header = request.getHeader(HttpHeaders.AUTHORIZATION)
            return header
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
