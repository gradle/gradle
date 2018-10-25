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

import jcifs.http.NtlmSsp
import jcifs.smb.NtlmPasswordAuthentication
import org.eclipse.jetty.http.HttpHeader
import org.eclipse.jetty.security.Authenticator
import org.eclipse.jetty.security.ServerAuthException
import org.eclipse.jetty.security.UserAuthentication
import org.eclipse.jetty.server.Authentication
import org.eclipse.jetty.server.Response
import org.eclipse.jetty.server.UserIdentity
import org.eclipse.jetty.util.security.Credential

import javax.servlet.ServletRequest
import javax.servlet.ServletResponse
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

class NtlmAuthenticator implements Authenticator {
    static final String NTLM_AUTH_METHOD = 'NTLM'
    private AuthConfiguration configuration

    @Override
    void setConfiguration(AuthConfiguration configuration) {
        this.configuration = configuration
    }

    @Override
    void prepareRequest(ServletRequest request) {

    }

    private NtlmConnectionAuthentication connectionAuth

    @Override
    Authentication validateRequest(ServletRequest request, ServletResponse response, boolean mandatory) throws ServerAuthException {
//        NtlmConnectionAuthentication connectionAuth = (NtlmConnectionAuthentication)request.getAttribute("connectionAuth")

        if (connectionAuth == null) {
            connectionAuth = new NtlmConnectionAuthentication(challenge: new byte[8])
            new Random().nextBytes(connectionAuth.challenge)

//                ((HttpServletRequest)request).servletContext.setAttribute("connectionAuth", connectionAuth)
        }

        if (connectionAuth.authenticated) {
            return new UserAuthentication(authMethod, connectionAuth.userIdentity)
        } else {
            NtlmPasswordAuthentication authentication = NtlmSsp.authenticate((HttpServletRequest) request, (HttpServletResponse) response, connectionAuth.challenge)

            if (authentication != null) {
                UserIdentity userIdentity = configuration.loginService.login(authentication.username, new TestNtlmCredentials(authentication, connectionAuth.challenge), request)

                if (userIdentity != null) {
                    connectionAuth.userIdentity = userIdentity

                    return new UserAuthentication(authMethod, userIdentity)
                } else {
                    badCredentials((Response) response)

                    return Authentication.SEND_FAILURE
                }
            }
        }
        return Authentication.SEND_SUCCESS
    }

    @Override
    boolean secureResponse(ServletRequest request, ServletResponse response, boolean mandatory, Authentication.User validatedUser) throws ServerAuthException {
        return false
    }

    @Override
    String getAuthMethod() {
        return NTLM_AUTH_METHOD
    }

    private void badCredentials(Response response) {
        response.setHeader(HttpHeader.WWW_AUTHENTICATE, authMethod)
        response.sendError(HttpServletResponse.SC_UNAUTHORIZED)
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
                byte[] clientChallenge = hash[16..-1]

                return Arrays.equals(hash, NtlmPasswordAuthentication.getLMv2Response(authentication.domain, authentication.username, credentials, challenge, clientChallenge))
            }

            return false
        }
    }

    private static class NtlmConnectionAuthentication {
        byte[] challenge
        UserIdentity userIdentity

        boolean isAuthenticated() { userIdentity != null}
    }
}
