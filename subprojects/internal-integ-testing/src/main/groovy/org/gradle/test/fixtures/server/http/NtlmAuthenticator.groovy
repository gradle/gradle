/*
 * Copyright 2015 the original author or authors.
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
import jcifs.http.NtlmSsp
import jcifs.smb.NtlmPasswordAuthentication
import org.eclipse.jetty.http.HttpHeader
import org.eclipse.jetty.security.ServerAuthException
import org.eclipse.jetty.security.UserAuthentication
import org.eclipse.jetty.security.authentication.LoginAuthenticator
import org.eclipse.jetty.server.Authentication
import org.eclipse.jetty.server.HttpConnection
import org.eclipse.jetty.util.security.Credential

import javax.servlet.ServletRequest
import javax.servlet.ServletResponse
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

@CompileStatic
class NtlmAuthenticator extends LoginAuthenticator {
    static final String NTLM_AUTH_METHOD = 'NTLM'

    // There is absolutely no doubt that no one should ever on a map like that
    // and that a proper implementation of an NTLM authenticator shouldn't do this
    // but those are test fixtures and I couldn't find a better way to do this
    // without deeper understanding of the Jetty APIs. Feel free to provide an
    // alternate solution
    private final Map<HttpConnection, NtlmConnectionAuthentication> connections = [:]

    @Override
    String getAuthMethod() {
        return NTLM_AUTH_METHOD
    }

    @Override
    Authentication validateRequest(ServletRequest request, ServletResponse response, boolean mandatory) throws ServerAuthException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response
        NtlmConnectionAuthentication connectionAuth = connections[HttpConnection.currentConnection]

        if (connectionAuth == null) {
            connectionAuth = new NtlmConnectionAuthentication(challenge: new byte[8])
            new Random().nextBytes(connectionAuth.challenge)
            connections[HttpConnection.currentConnection] = connectionAuth
        }

        if (connectionAuth.failed) {
            httpResponse.setHeader(HttpHeader.WWW_AUTHENTICATE.asString(), "basic realm=\"" + _loginService.getName() + '"')
            httpResponse.sendError(HttpServletResponse.SC_UNAUTHORIZED)
            return Authentication.SEND_CONTINUE
        } else if (connectionAuth.user != null) {
           return connectionAuth.user
        } else {
            NtlmPasswordAuthentication authentication = NtlmSsp.authenticate(
                (HttpServletRequest) request,
                (HttpServletResponse) response,
                connectionAuth.challenge)

            if (authentication != null) {
                def userIdentity = login(authentication.username, new TestNtlmCredentials(authentication, connectionAuth.challenge), request)

                if (userIdentity != null) {
                    def user = new UserAuthentication(getAuthMethod(), userIdentity)
                    connections[HttpConnection.currentConnection].user = user
                    return user
                } else {
                    httpResponse.setHeader(HttpHeader.WWW_AUTHENTICATE.asString(), "basic realm=\"" + _loginService.getName() + '"')
                    httpResponse.sendError(HttpServletResponse.SC_UNAUTHORIZED)
                    connections[HttpConnection.currentConnection].failed = true
                    return Authentication.SEND_CONTINUE
                }
            }
        }
        return Authentication.SEND_CONTINUE
    }

    @Override
    boolean secureResponse(ServletRequest request, ServletResponse response, boolean mandatory, Authentication.User validatedUser) throws ServerAuthException {
        return true
    }

    private static class TestNtlmCredentials extends Credential {
        private NtlmPasswordAuthentication authentication
        private byte[] challenge

        TestNtlmCredentials(NtlmPasswordAuthentication authentication, byte[] challenge) {
            this.authentication = authentication
            this.challenge = challenge
        }

        @Override
        boolean check(Object credentials) {
            if (credentials instanceof String) {
                byte[] hash = authentication.getAnsiHash(challenge)
                byte[] clientChallenge = hash[16..-1] as byte[]

                def response = NtlmPasswordAuthentication.getLMv2Response(authentication.domain, authentication.username, credentials, challenge, clientChallenge)
                return Arrays.equals(hash, response)
            }

            return false
        }
    }

    private static class NtlmConnectionAuthentication {
        byte[] challenge
        Authentication.User user
        boolean failed

        boolean isAuthenticated() { user != null}

    }
}
