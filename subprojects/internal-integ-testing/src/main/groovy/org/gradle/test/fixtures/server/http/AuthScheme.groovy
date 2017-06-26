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

import org.mortbay.jetty.Response
import org.mortbay.jetty.security.Authenticator
import org.mortbay.jetty.security.BasicAuthenticator
import org.mortbay.jetty.security.Constraint
import org.mortbay.jetty.security.ConstraintMapping
import org.mortbay.jetty.security.DigestAuthenticator
import org.mortbay.jetty.security.SecurityHandler
import org.mortbay.jetty.security.UserRealm

import javax.servlet.http.HttpServletResponse

enum AuthScheme {
    BASIC(new BasicAuthHandler()),
    DIGEST(new DigestAuthHandler()),
    HIDE_UNAUTHORIZED(new HideUnauthorizedBasicAuthHandler()),
    NTLM(new NtlmAuthHandler())

    final AuthSchemeHandler handler

    AuthScheme(AuthSchemeHandler handler) {
        this.handler = handler
    }

    private static class BasicAuthHandler extends AuthSchemeHandler {
        @Override
        protected String constraintName() {
            return Constraint.__BASIC_AUTH
        }

        @Override
        protected Authenticator getAuthenticator() {
            return new BasicAuthenticator()
        }
    }

    private static class HideUnauthorizedBasicAuthHandler extends AuthSchemeHandler {
        @Override
        protected String constraintName() {
            return Constraint.__BASIC_AUTH
        }

        @Override
        protected Authenticator getAuthenticator() {
            return new BasicAuthenticator() {
                @Override
                void sendChallenge(UserRealm realm, Response response) throws IOException {
                    response.sendError(HttpServletResponse.SC_NOT_FOUND)
                }
            }
        }
    }

    abstract static class AuthSchemeHandler {
        SecurityHandler createSecurityHandler(String path, TestUserRealm realm) {
            def constraintMapping = createConstraintMapping(path)
            def securityHandler = new SecurityHandler()
            securityHandler.userRealm = realm
            securityHandler.constraintMappings = [constraintMapping] as ConstraintMapping[]
            securityHandler.authenticator = authenticator
            return securityHandler
        }

        void addConstraint(SecurityHandler securityHandler, String path) {
            securityHandler.constraintMappings = (securityHandler.constraintMappings as List) + createConstraintMapping(path)
        }

        private ConstraintMapping createConstraintMapping(String path) {
            def constraint = new Constraint()
            constraint.name = constraintName()
            constraint.authenticate = true
            constraint.roles = ['*'] as String[]
            def constraintMapping = new ConstraintMapping()
            constraintMapping.pathSpec = path
            constraintMapping.constraint = constraint
            return constraintMapping
        }

        protected abstract String constraintName()

        protected abstract Authenticator getAuthenticator()
    }

    private static class NtlmAuthHandler extends AuthSchemeHandler {
        @Override
        protected String constraintName() {
            return NtlmAuthenticator.NTLM_AUTH_METHOD
        }

        @Override
        protected Authenticator getAuthenticator() {
            return new NtlmAuthenticator()
        }
    }

    private static class DigestAuthHandler extends AuthSchemeHandler {
        @Override
        protected String constraintName() {
            return Constraint.__DIGEST_AUTH
        }

        @Override
        protected Authenticator getAuthenticator() {
            return new DigestAuthenticator()
        }
    }
}
