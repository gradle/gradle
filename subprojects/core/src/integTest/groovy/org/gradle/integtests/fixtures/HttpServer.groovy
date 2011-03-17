/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.integtests.fixtures

import org.apache.commons.lang.StringUtils
import org.mortbay.jetty.Server
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.mortbay.jetty.handler.*
import org.mortbay.jetty.Handler
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

class HttpServer {
    private Logger logger = LoggerFactory.getLogger(HttpServer.class)
    private final Server server = new Server(0)
    private final ContextHandlerCollection collection = new ContextHandlerCollection()

    def HttpServer() {
        HandlerCollection handlers = new HandlerCollection()
        handlers.addHandler(collection)
        handlers.addHandler(new DefaultHandler())
        server.setHandler(handlers)
    }

    def start() {
        server.start()
    }

    def stop() {
        server.stop()
    }

    /**
     * Adds a given file at the given URL.
     */
    def add(String path, File srcFile) {
        assert path.startsWith('/')
        ContextHandler context = new ContextHandler()
        String contextPath = StringUtils.substringBeforeLast(path, '/')
        context.contextPath = contextPath ?: '/'
        context.resourceBase = srcFile.parentFile.path
        context.addHandler(new ResourceHandler())
        collection.addHandler(context)
    }

    def addBroken(String path) {
        assert path.startsWith('/')
        ContextHandler context = new ContextHandler()
        String contextPath = StringUtils.substringBeforeLast(path, '/')
        context.contextPath = contextPath ?: '/'
        context.addHandler(new AbstractHandler() {
            void handle(String target, HttpServletRequest request, HttpServletResponse response, int dispatch) {
                response.sendError(500, "broken")
            }
        })
        collection.addHandler(context)
    }

    def int getPort() {
        return server.connectors[0].localPort
    }
}
