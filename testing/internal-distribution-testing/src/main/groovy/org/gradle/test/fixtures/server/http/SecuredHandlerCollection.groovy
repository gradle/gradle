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


@CompileStatic
class SecuredHandlerCollection {
    private final HandlerChain handlers = new HandlerChain()
    private TestUserRealm realm
    private AuthScheme.Authenticator authenticator
    private final List<String> securedPaths = []

    AuthScheme authenticationScheme = AuthScheme.BASIC

    HandlerChain getHandlers() {
        return handlers
    }

    void reset() {
        realm = null
        authenticator = null
        securedPaths.clear()
        handlers.clear()
    }

    void handle(String target, HttpRequest request, HttpResponse response) throws IOException {
        if (authenticator != null && isSecured(target)) {
            if (!authenticator.authenticate(request, response)) {
                request.handled = true
                return
            }
            request.remoteUser = realm.username
        }
        handlers.handle(target, request, response)
    }

    void requireAuthentication(String path, String username, String password) {
        if (realm != null) {
            assert realm.username == username
            assert realm.password == password
        } else {
            realm = new TestUserRealm(username, password)
            authenticator = authenticationScheme.handler.createAuthenticator(realm)
        }
        if (!securedPaths.contains(path)) {
            securedPaths.add(path)
        }
    }

    private boolean isSecured(String target) {
        return securedPaths.any { matches(it, target) }
    }

    private static boolean matches(String spec, String target) {
        if (spec == '/*' || spec == '/') {
            return true
        }
        if (spec.endsWith('/*')) {
            String prefix = spec.substring(0, spec.length() - 2)
            return target == prefix || target.startsWith(prefix + '/')
        }
        return target == spec
    }
}
