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

import org.apache.http.HttpHeaders
import org.eclipse.jetty.http.HttpHeader
import org.eclipse.jetty.security.Authenticator
import org.eclipse.jetty.security.ConstraintMapping
import org.eclipse.jetty.security.ConstraintSecurityHandler
import org.eclipse.jetty.security.ServerAuthException
import org.eclipse.jetty.security.authentication.BasicAuthenticator
import org.eclipse.jetty.security.authentication.DigestAuthenticator
import org.eclipse.jetty.server.Authentication
import org.eclipse.jetty.server.ServletResponseHttpWrapper
import org.eclipse.jetty.util.security.Constraint

import javax.servlet.ServletRequest
import javax.servlet.ServletResponse
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

enum AuthScheme {
    BASIC(new BasicAuthHandler()),
    DIGEST(new DigestAuthHandler()),
    HIDE_UNAUTHORIZED(new HideUnauthorizedBasicAuthHandler()),
    NTLM(new NtlmAuthHandler()),
    HEADER(new HttpHeaderAuthHandler())

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

        @Override
        protected boolean containsUnexpectedAuthentication(HttpServletRequest request) {
            return request.getHeader(HttpHeaders.AUTHORIZATION)
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
                Authentication validateRequest(ServletRequest req, ServletResponse res, boolean mandatory) throws ServerAuthException {
                    def auth = super.validateRequest(req, new ServletResponseHttpWrapper(res), mandatory)
                    if (!(auth instanceof Authentication.User)) {
                        res.setHeader(HttpHeader.WWW_AUTHENTICATE.asString(), "basic realm=\"" + _loginService.getName() + '"')
                        res.sendError(HttpServletResponse.SC_NOT_FOUND)
                        return Authentication.SEND_CONTINUE
                    }
                    return super.validateRequest(req, res, mandatory)
                }
            }
        }
    }

    abstract static class AuthSchemeHandler {
        ConstraintSecurityHandler createSecurityHandler(String path, TestUserRealm realm) {
            def constraintMapping = createConstraintMapping(path)
            def securityHandler = new ConstraintSecurityHandler()
            securityHandler.realmName = TestUserRealm.REALM_NAME
            securityHandler.addConstraintMapping(constraintMapping)
            securityHandler.authenticator = authenticator
            securityHandler.loginService = realm
            return securityHandler
        }

        void addConstraint(ConstraintSecurityHandler securityHandler, String path) {
            securityHandler.addConstraintMapping(createConstraintMapping(path))
        }

        private ConstraintMapping createConstraintMapping(String path) {
            def constraint = new Constraint()
            constraint.name = constraintName()
            constraint.authenticate = true
            constraint.roles = [Constraint.ANY_ROLE, Constraint.ANY_AUTH, TestUserRealm.ROLE] as String[]
            def constraintMapping = new ConstraintMapping()
            constraintMapping.pathSpec = path
            constraintMapping.constraint = constraint
            return constraintMapping
        }

        protected abstract String constraintName()

        protected abstract Authenticator getAuthenticator()

        protected boolean containsUnexpectedAuthentication(HttpServletRequest request) {
            false
        }
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

    private static class HttpHeaderAuthHandler extends AuthSchemeHandler {
        @Override
        protected String constraintName() {
            return TestHttpHeaderAuthenticator.AUTH_SCHEME_NAME;
        }

        @Override
        protected Authenticator getAuthenticator() {
            return new TestHttpHeaderAuthenticator()
        }
    }
}
