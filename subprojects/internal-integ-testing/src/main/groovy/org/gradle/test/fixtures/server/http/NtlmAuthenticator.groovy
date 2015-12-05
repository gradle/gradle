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
import org.mortbay.jetty.HttpHeaders
import org.mortbay.jetty.Request
import org.mortbay.jetty.Response
import org.mortbay.jetty.security.Authenticator
import org.mortbay.jetty.security.Credential
import org.mortbay.jetty.security.UserRealm

import javax.servlet.http.HttpServletResponse
import javax.servlet.http.HttpSession
import java.security.Principal

class NtlmAuthenticator implements Authenticator {
    static final String NTLM_AUTH_METHOD = 'NTLM'
    private static final String NTLM_CHALLENGE = 'ntlm_challenge'

    @Override
    Principal authenticate(UserRealm realm, String pathInContext, Request request, Response response) throws IOException {
        HttpSession session = request.getSession(true)
        byte[] challenge = session.getAttribute(NTLM_CHALLENGE) as byte[]

        if (challenge == null) {
            challenge = new byte[8]
            new Random().nextBytes(challenge)
            session.setAttribute(NTLM_CHALLENGE, challenge)
        }

        NtlmPasswordAuthentication authentication = NtlmSsp.authenticate(request, response, challenge)

        if (authentication != null) {
            session.setAttribute(NTLM_CHALLENGE, null)
            Principal principal = realm.authenticate(authentication.username, new TestNtlmCredentials(authentication, challenge), request)

            if (principal != null) {
                request.authType = authMethod
                request.userPrincipal = principal

                return principal
            } else {
                badCredentials(response)
            }
        }
    }

    @Override
    String getAuthMethod() {
        return NTLM_AUTH_METHOD
    }

    private void badCredentials(Response response) {
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, authMethod)
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
}
