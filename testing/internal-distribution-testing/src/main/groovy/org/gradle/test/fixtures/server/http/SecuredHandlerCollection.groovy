/*
 * Copyright 2020 the original author or authors.
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

import groovy.transform.CompileStatic
import org.eclipse.jetty.security.ConstraintSecurityHandler
import org.eclipse.jetty.server.Handler
import org.eclipse.jetty.server.Request
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.handler.HandlerCollection

import javax.servlet.ServletException
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

@CompileStatic
class SecuredHandlerCollection implements Handler {
    private final HandlerCollection handlers = new HandlerCollection(true)
    private ConstraintSecurityHandler securityHandler
    private TestUserRealm realm

    AuthScheme authenticationScheme = AuthScheme.BASIC

    private Handler delegate = handlers

    SecuredHandlerCollection() {
    }

    HandlerCollection getHandlers() {
        handlers
    }

    void reset() {
        realm = null
        securityHandler?.stop()
        securityHandler = null
        handlers.stop()
        delegate = handlers
        handlers.setHandlers(new Handler[0])
        handlers.start()
    }

    @Override
    void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        delegate.handle(target, baseRequest, request, response)
    }

    @Override
    void setServer(Server server) {
        delegate.server = server
    }

    @Override
    Server getServer() {
        delegate.server
    }

    @Override
    void destroy() {
        delegate.destroy()
    }

    void requireAuthentication(String path, String username, String password) {
        if (realm != null) {
            assert realm.username == username
            assert realm.password == password
            authenticationScheme.handler.addConstraint(securityHandler, path)
        } else {
            realm = new TestUserRealm(username, password)
            securityHandler = authenticationScheme.handler.createSecurityHandler(path, realm)
            def shouldRestart = handlers.server?.isStarted() ?: false
            if (shouldRestart) {
                handlers.stop()
            }
            securityHandler.handler = handlers
            delegate = securityHandler
            delegate.server = handlers.server
            if (shouldRestart) {
                delegate.start()
            }
        }
    }

    @Override
    void start() throws Exception {
        delegate.start()
    }

    @Override
    void stop() throws Exception {
        delegate.stop()
    }

    @Override
    boolean isRunning() {
        delegate.running
    }

    @Override
    boolean isStarted() {
        delegate.started
    }

    @Override
    boolean isStarting() {
        delegate.starting
    }

    @Override
    boolean isStopping() {
        delegate.stopping
    }

    @Override
    boolean isStopped() {
        delegate.stopped
    }

    @Override
    boolean isFailed() {
        delegate.failed
    }

    @Override
    void addLifeCycleListener(Listener listener) {
        delegate.addLifeCycleListener(listener)
    }

    @Override
    void removeLifeCycleListener(Listener listener) {
        delegate.removeLifeCycleListener(listener)
    }
}
