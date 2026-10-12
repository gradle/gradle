/*
 * Copyright 2026 the original author or authors.
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

import com.google.common.io.ByteStreams
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpsExchange
import groovy.transform.CompileStatic
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@CompileStatic
class HttpServerDispatcher implements com.sun.net.httpserver.HttpHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(HttpServerDispatcher)

    private final LoggingHandler loggingHandler
    private final SecuredHandlerCollection securityHandlerWrapper
    private final HttpResourceHandler customHandler
    private final SslPreHandler sslPreHandler

    HttpServerDispatcher(LoggingHandler loggingHandler, SecuredHandlerCollection securityHandlerWrapper, HttpResourceHandler customHandler, SslPreHandler sslPreHandler) {
        this.loggingHandler = loggingHandler
        this.securityHandlerWrapper = securityHandlerWrapper
        this.customHandler = customHandler
        this.sslPreHandler = sslPreHandler
    }

    @Override
    void handle(HttpExchange exchange) throws IOException {
        HttpRequest request = new HttpRequest(exchange)
        HttpResponse response = new HttpResponse(exchange)
        try {
            if (exchange instanceof HttpsExchange) {
                sslPreHandler.handshakeStarted()
            }
            loggingHandler.log(request)
            securityHandlerWrapper.handle(request.pathInfo, request, response)
            if (!request.handled) {
                customHandler.handle(request.pathInfo, request, response)
            }
        } catch (Throwable t) {
            LOGGER.warn("Failed to handle request: " + request.method + " " + request.pathInfo, t)
            if (!response.isCommitted()) {
                response.resetBuffer()
                response.sendError(500, String.valueOf(t))
            }
        } finally {
            if (!response.isCommitted()) {
                drainRequestBody(exchange)
            }
            try {
                response.commit(request.method)
            } catch (Exception ignore) {
            }
            exchange.close()
        }
    }

    private static void drainRequestBody(HttpExchange exchange) {
        try {
            ByteStreams.exhaust(exchange.requestBody)
        } catch (IOException ignore) {
        }
    }
}
