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

import com.sun.net.httpserver.HttpsConfigurator
import com.sun.net.httpserver.HttpsParameters
import com.sun.net.httpserver.HttpsServer
import groovy.transform.CompileStatic
import org.gradle.api.Action
import org.gradle.internal.Actions

import javax.net.ssl.SSLContext
import javax.net.ssl.SSLParameters
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory

@CompileStatic
trait HttpServerFixture {
    private com.sun.net.httpserver.HttpServer httpServer
    private HttpsServer httpsServer
    private HttpServerDispatcher dispatcher
    private LoggingHandler loggingHandler
    private boolean running
    private final SslPreHandler sslPreHandler = new SslPreHandler()
    private final SecuredHandlerCollection securityHandlerWrapper = new SecuredHandlerCollection()
    private final ExecutorService executor = Executors.newCachedThreadPool({ Runnable r ->
        Thread thread = new Thread(r, "http-fixture")
        thread.setDaemon(true)
        return thread
    } as ThreadFactory)

    private boolean logRequests = true
    private boolean useHostnameForUrl = false
    private final Set<String> authenticationAttempts = new LinkedHashSet<>()
    private final Set<Map<String, String>> allHeaders = new LinkedHashSet<>()

    com.sun.net.httpserver.HttpServer getServer() {
        httpServer
    }

    String getAddress() {
        if (!running) {
            start()
        }
        getUri().toString()
    }

    URI getUri() {
        assert running
        if (httpsServer != null) {
            return URI.create("https://localhost:${getSslPort()}")
        } else if (useHostnameForUrl) {
            // If used in a code-path that interacts with the HttpClientHelper, this will fail validation.
            return URI.create("http://localhost:${getPort()}")
        } else {
            // The HttpClientHelper will not do HTTPS validation if the host matches 127.0.0.1
            // This allows us to run integration tests without needing to use the TestKeyStore in every single test.
            return URI.create("http://127.0.0.1:${getPort()}")
        }
    }

    URI uri(String path) {
        return getUri().resolve(path)
    }

    boolean isRunning() {
        running
    }

    abstract HttpResourceHandler getCustomHandler()

    HandlerChain getCollection() {
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

    void sslPreHandler(Runnable consumer) {
        sslPreHandler.registerCustomizer(consumer)
    }

    private void ensureDispatcher() {
        if (dispatcher == null) {
            loggingHandler = new LoggingHandler(authenticationAttempts, allHeaders, logRequests)
            dispatcher = new HttpServerDispatcher(loggingHandler, securityHandlerWrapper, getCustomHandler())
        }
    }

    void start() {
        if (running) {
            return
        }
        ensureDispatcher()
        if (httpServer == null) {
            httpServer = com.sun.net.httpserver.HttpServer.create(new InetSocketAddress(0), 0)
            httpServer.setExecutor(executor)
            httpServer.createContext("/", dispatcher)
            httpServer.start()
        }
        if (httpsServer != null) {
            httpsServer.start()
        }
        running = true
        assert getPort() > 0
    }

    void stop() {
        if (httpsServer != null) {
            httpsServer.stop(0)
            httpsServer = null
        }
        if (httpServer != null) {
            httpServer.stop(0)
            httpServer = null
        }
        running = false
    }

    void reset() {
        securityHandlerWrapper.reset()
    }

    void enableSsl(
        SSLContext sslContext,
        boolean needClientAuth,
        Action<SslConfiguration> configureServer = Actions.doNothing()
    ) {
        ensureDispatcher()
        SslConfiguration configuration = new SslConfiguration()
        configureServer.execute(configuration)
        String[] protocols = configuration.effectiveProtocols as String[]

        httpsServer = HttpsServer.create(new InetSocketAddress(0), 0)
        httpsServer.setHttpsConfigurator(new FixtureHttpsConfigurator(sslContext, sslPreHandler, protocols, needClientAuth))
        httpsServer.setExecutor(executor)
        httpsServer.createContext("/", dispatcher)
        if (running) {
            httpsServer.start()
        }
    }

    void requireAuthentication(String path = '/*', String username, String password) {
        securityHandlerWrapper.requireAuthentication(path, username, password)
    }

    int getPort() {
        return httpServer.address.port
    }

    int getSslPort() {
        return httpsServer.address.port
    }

    @CompileStatic
    static class FixtureHttpsConfigurator extends HttpsConfigurator {
        private final SslPreHandler sslPreHandler
        private final String[] protocols
        private final boolean needClientAuth

        FixtureHttpsConfigurator(SSLContext sslContext, SslPreHandler sslPreHandler, String[] protocols, boolean needClientAuth) {
            super(sslContext)
            this.sslPreHandler = sslPreHandler
            this.protocols = protocols
            this.needClientAuth = needClientAuth
        }

        @Override
        void configure(HttpsParameters params) {
            sslPreHandler.handshakeStarted()
            SSLParameters sslParameters = getSSLContext().getDefaultSSLParameters()
            sslParameters.setProtocols(protocols)
            sslParameters.setNeedClientAuth(needClientAuth)
            params.setProtocols(protocols)
            params.setNeedClientAuth(needClientAuth)
            params.setSSLParameters(sslParameters)
        }
    }
}
