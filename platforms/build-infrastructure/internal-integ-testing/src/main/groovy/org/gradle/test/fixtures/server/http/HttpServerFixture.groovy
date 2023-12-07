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
import org.eclipse.jetty.http.HttpVersion
import org.eclipse.jetty.server.Connector
import org.eclipse.jetty.server.Handler
import org.eclipse.jetty.server.HttpConfiguration
import org.eclipse.jetty.server.HttpConnectionFactory
import org.eclipse.jetty.server.Request
import org.eclipse.jetty.server.SecureRequestCustomizer
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.ServerConnector
import org.eclipse.jetty.server.SslConnectionFactory
import org.eclipse.jetty.server.handler.HandlerCollection
import org.eclipse.jetty.util.ssl.SslContextFactory
import org.gradle.api.Action
import org.gradle.internal.Actions
import org.gradle.util.ports.FixedAvailablePortAllocator
import org.gradle.util.ports.PortAllocator

import java.util.function.Consumer

@CompileStatic
trait HttpServerFixture {
    private final PortAllocator portAllocator = FixedAvailablePortAllocator.instance
    private final Server server = new Server()
    private ServerConnector connector
    private ServerConnector sslConnector
    private final SslPreHandler sslPreHandler = new SslPreHandler()
    private final SecuredHandlerCollection securityHandlerWrapper = new SecuredHandlerCollection()

    private boolean logRequests = true
    private boolean useHostnameForUrl = false
    private final Set<String> authenticationAttempts = Sets.newLinkedHashSet()
    private final Set<Map<String, String>> allHeaders = Sets.newLinkedHashSet()
    private boolean configured

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
        return securityHandlerWrapper.handlers
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
        return securityHandlerWrapper.authenticationScheme
    }

    void setAuthenticationScheme(AuthScheme authenticationScheme) {
        securityHandlerWrapper.authenticationScheme = authenticationScheme
    }

    void sslPreHandler(Consumer<Request> consumer) {
        sslPreHandler.registerCustomizer(consumer)
    }

    void start() {
        if (!configured) {
            HandlerCollection handlers = new HandlerCollection(true)
            handlers.addHandler(new LoggingHandler(authenticationAttempts, allHeaders, logRequests))
            handlers.addHandler(securityHandlerWrapper)
            handlers.addHandler(getCustomHandler())
            server.setHandler(handlers)
            server.setStopTimeout(0)
            configured = true
        }

        if (!server.started) {
            server.start()
            for (int i = 0; i < 5; i++) {
                if (createConnector() && connector.localPort > 0) {
                    return
                }
                // Has failed to start for some reason - try again
                releaseConnector()
            }
            throw new AssertionError((Object) "SocketConnector failed to start.") // cast because of Groovy bug
        }
    }

    private void releaseConnector() {
        def port = connector.port
        server.removeConnector(connector)
        connector.stop()
        portAllocator.releasePort(port)
    }

    private boolean createConnector() {
        def assignedPort = portAllocator.assignPort()
        connector = new ServerConnector(server)
        connector.port = assignedPort
        server.addConnector(connector)
        try {
            connector.start()
            return true
        } catch (e) {
            println "Unable to start connector on port ${assignedPort}"
        }
        return false
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

    void reset() {
        securityHandlerWrapper.reset()
    }

    void enableSsl(
        String keyStore,
        String keyPassword,
        String trustStore = null,
        String trustPassword = null,
        Action<SslContextFactory.Server> configureServer = Actions.doNothing()
    ) {
        def sslContextFactory = new SslContextFactory.Server()
        sslContextFactory.setIncludeProtocols("TLSv1.2")
        sslContextFactory.setKeyStorePath(keyStore)
        sslContextFactory.setKeyStorePassword(keyPassword)
        if (trustStore) {
            sslContextFactory.needClientAuth = true
            sslContextFactory.setTrustStorePath(trustStore)
        }
        if (trustPassword) {
            sslContextFactory.setTrustStorePassword(trustPassword)
        }
        configureServer.execute(sslContextFactory)
        def httpsConfig = new HttpConfiguration()
        httpsConfig.addCustomizer(new SecureRequestCustomizer())
        httpsConfig.addCustomizer(sslPreHandler)
        def connectionFactory = new SslConnectionFactory(sslContextFactory, HttpVersion.HTTP_1_1.asString())
        def httpConnectionFactory = new HttpConnectionFactory(httpsConfig)

        sslConnector = new ServerConnector(server,
            connectionFactory, httpConnectionFactory)
        sslConnector.setPort(portAllocator.assignPort())
        server.addConnector(sslConnector)
        if (server.started) {
            sslConnector.start()
        }

    }

    void requireAuthentication(String path = '/*', String username, String password) {
        securityHandlerWrapper.requireAuthentication(path, username, password)
    }

    int getPort() {
        return connector.localPort
    }

    int getSslPort() {
        sslConnector.localPort
    }

    private void shutdownConnector(ServerConnector connector) {
        def port = connector.port
        connector.stop()
        connector.close()
        server?.removeConnector(connector)
        portAllocator.releasePort(port)
    }
}

@CompileStatic
class SslPreHandler implements HttpConfiguration.Customizer {

    private final List<Consumer<Request>> consumers = []

    void registerCustomizer(Consumer<Request> consumer) {
        consumers << consumer
    }

    @Override
    void customize(Connector connector, HttpConfiguration channelConfig, Request request) {
        consumers.each {
            it.accept(request)
        }
    }
}
