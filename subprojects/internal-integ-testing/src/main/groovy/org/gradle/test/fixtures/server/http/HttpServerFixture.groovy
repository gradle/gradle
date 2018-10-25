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
import org.eclipse.jetty.http.CookieCompliance
import org.eclipse.jetty.http.HttpHeader
import org.eclipse.jetty.http.HttpVersion
import org.eclipse.jetty.security.SecurityHandler
import org.eclipse.jetty.server.Connector
import org.eclipse.jetty.server.Handler
import org.eclipse.jetty.server.HttpConfiguration
import org.eclipse.jetty.server.HttpConnectionFactory
import org.eclipse.jetty.server.Request
import org.eclipse.jetty.server.SecureRequestCustomizer
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.ServerConnector
import org.eclipse.jetty.server.SslConnectionFactory
import org.eclipse.jetty.server.handler.AbstractHandler
import org.eclipse.jetty.server.handler.HandlerCollection
import org.eclipse.jetty.util.ssl.SslContextFactory
import org.gradle.internal.TriAction
import org.gradle.util.ports.FixedAvailablePortAllocator
import org.gradle.util.ports.PortAllocator

import javax.servlet.ServletException
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

@CompileStatic
trait HttpServerFixture {
    private final PortAllocator portAllocator = FixedAvailablePortAllocator.instance
    private final Server server = new Server()
    private ServerConnector connector
    private ServerConnector sslConnector
    private final HandlerCollection collection = new HandlerCollection(true)
    private TestUserRealm realm
    private SecurityHandler securityHandler
    private AuthScheme authenticationScheme = AuthScheme.BASIC
    private boolean logRequests = true
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
        return sslConnector ? URI.create("https://localhost:${sslConnector.localPort}") : URI.create("http://localhost:${connector.localPort}")
    }

    boolean isRunning() {
        server.running
    }

    abstract Handler getCustomHandler()

    HandlerCollection getCollection() {
        return collection
    }

    HandlerCollection getSecuredCollection() {
        return (HandlerCollection)securityHandler.getHandler()
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

    AuthScheme getAuthenticationScheme() {
        return authenticationScheme
    }

    void setAuthenticationScheme(AuthScheme authenticationScheme) {
        this.authenticationScheme = authenticationScheme
    }

    void setSslPreHandler(TriAction<Connector, HttpConfiguration, Request> handler) {
        server.connectors.each { connector ->
            SslConnectionFactory factory = connector.getConnectionFactory(SslConnectionFactory);
            if (factory == null) {
                return
            }
            HttpConfiguration configuration = connector.getConnectionFactory(HttpConnectionFactory).httpConfiguration;
            if (configuration == null) {
                return
            }
            InterceptableCustomizer customizer = configuration.getCustomizer(InterceptableCustomizer.class)
            if (customizer != null) {
                customizer.sslPreHandler = handler
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
        HttpConfiguration httpConfiguration = new HttpConfiguration()
        httpConfiguration.setCookieCompliance(CookieCompliance.RFC2965);

        assignedPort = portAllocator.assignPort()
        connector = new ServerConnector(server, new HttpConnectionFactory(httpConfiguration))
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

    private static class LoggingHandler extends AbstractHandler {
        private final Set<String> authenticationAttempts
        private final Set<Map<String, String>> allHeaders
        private final boolean logRequests

        LoggingHandler(Set<String> authenticationAttempts, Set<Map<String, String>> allHeaders, boolean logRequests) {
            this.logRequests = logRequests
            this.authenticationAttempts = authenticationAttempts
            this.allHeaders = allHeaders
        }

        @Override
        void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
            allHeaders.add request.getHeaderNames().toList().collectEntries { headerName -> [headerName, request.getHeader(headerName as String)] }
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
                println("handling http request: $request.method $target")
            }
        }

        protected String getAuthorizationHeader(HttpServletRequest request) {
            def header = request.getHeader(HttpHeader.AUTHORIZATION.asString())
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
        SslContextFactory sslContextFactory = new SslContextFactory()
        sslContextFactory.setKeyStorePath(keyStore)
        sslContextFactory.setKeyStorePassword(keyPassword)
        if (trustStore) {
            sslContextFactory.setNeedClientAuth(true)
            sslContextFactory.setTrustStorePath(trustStore)
            sslContextFactory.setTrustStorePassword(trustPassword)
        }

        HttpConfiguration httpConfiguration = connector.getConnectionFactory(HttpConnectionFactory).httpConfiguration

        HttpConfiguration httpsConfiguration = new HttpConfiguration(httpConfiguration)
        httpsConfiguration.addCustomizer(new SecureRequestCustomizer())
        httpsConfiguration.addCustomizer(new InterceptableCustomizer())

        sslConnector = new ServerConnector(server,
                new SslConnectionFactory(sslContextFactory, HttpVersion.HTTP_1_1.asString()),
                new HttpConnectionFactory(httpsConfiguration))

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
            securityHandler.setHandler(new HandlerCollection(true))
            collection.addHandler(securityHandler)
            if (server.isStarted()) {
                securityHandler.start()
            }
        }
    }

    int getPort() {
        return connector.localPort
    }

    int getSslPort() {
        sslConnector.localPort
    }

    private void shutdownConnector(ServerConnector connector) {
        connector.stop()
        connector.close()
        server?.removeConnector(connector)
    }
}

class InterceptableCustomizer implements HttpConfiguration.Customizer {
    TriAction<Connector, HttpConfiguration, Request> sslPreHandler

    @Override
    void customize(Connector connector, HttpConfiguration channelConfig, Request request) {
        if (sslPreHandler) {
            sslPreHandler.execute(connector, channelConfig, request)
        }
    }
}
