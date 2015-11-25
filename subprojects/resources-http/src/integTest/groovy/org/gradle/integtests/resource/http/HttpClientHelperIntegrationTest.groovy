/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.integtests.resource.http

import jcifs.ntlmssp.Type1Message
import jcifs.ntlmssp.Type2Message
import jcifs.ntlmssp.Type3Message
import jcifs.util.Base64
import org.apache.http.HttpHeaders
import org.apache.http.HttpResponse
import org.apache.http.HttpStatus
import org.apache.http.client.methods.HttpGet
import org.gradle.authentication.Authentication
import org.gradle.api.artifacts.repositories.PasswordCredentials
import org.gradle.api.internal.artifacts.repositories.DefaultPasswordCredentials
import org.gradle.internal.authentication.AllSchemesAuthentication
import org.gradle.internal.resource.transport.http.DefaultHttpSettings
import org.gradle.internal.resource.transport.http.HttpClientHelper
import org.gradle.internal.resource.transport.http.HttpSettings
import org.gradle.test.fixtures.server.http.HttpServer
import org.junit.Rule
import org.mortbay.jetty.handler.AbstractHandler
import spock.lang.Specification

import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

class HttpClientHelperIntegrationTest extends Specification {

    @Rule HttpServer server

    def setup() {
        server.start()
    }

    def cleanup() {
        server.stop()
    }

    static class NtlmResponseHandler extends AbstractHandler {
        private boolean msg2Sent = false;

        void handle(String target, HttpServletRequest request, HttpServletResponse response, int dispatch) {
            response.setHeader("Connection", "Keep-Alive");
            if (msg2Sent == false && request.getHeader(HttpHeaders.AUTHORIZATION) == null ) {
                response.setStatus(HttpStatus.SC_UNAUTHORIZED);
                response.addHeader(HttpHeaders.WWW_AUTHENTICATE, "NTLM")
                request.handled = true
            } else if( msg2Sent == false ) {
                msg2Sent = true;
                response.setStatus(HttpStatus.SC_UNAUTHORIZED);
                // Message 1 received, sending message 2 to client
                String auth = request.getHeader(HttpHeaders.AUTHORIZATION)
                auth = auth.substring("NTLM ".length())
                byte[] msg1Bytes = Base64.decode(auth)
                Type1Message msg1 = new Type1Message()
                Type2Message msg2 = new Type2Message(msg1)
                /*
                Type2Message decoding from http://blogs.technet.com/b/tristank/archive/2006/08/02/negotiate-this.aspx
                Type2Message msg2FromWeb = new Type2Message(Base64.decode("TlRMTVNTUAACAAAABgAGADgAAAAFgomiCQs+k8e625YAA" +
                        "AAAAAAAAGIAYgA+AAAABQLODgAAAA9EAEkAQgACAAYARABJAEIAAQAMADIAMAAwADMARA" +
                        "BDAAQADgBkAGkAYgAuAGQAbwBtAAMAHAAyADAAMAAzAEQAQwAuAGQAaQBiAC4AZABvA" +
                        "G0ABQAOAGQAaQBiAC4AZABvAG0AAAAAAA=="))
                byte[] challengeBytes = msg2FromWeb.challenge;*/
                byte[] challengeBytes = new byte[8];
                new Random().nextBytes(challengeBytes);
                msg2.setChallenge(challengeBytes);
                String msg2Str = Base64.encode(msg2.toByteArray());
                response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "NTLM " + msg2Str)

                request.handled = true
            } else {
                String auth = request.getHeader(HttpHeaders.AUTHORIZATION)
                auth = auth.substring("NTLM ".length())
                byte[] msg3bytes = Base64.decode(auth)
                Type3Message msg3 = new Type3Message(msg3bytes)
                if( msg3.user == "administrator" ) {
                    response.setStatus(HttpStatus.SC_OK);
                } else {
                    response.setStatus(HttpStatus.SC_BAD_REQUEST);
                }

                request.handled = true
            }
        }
    }

    def "testNTLMAuthenticationFailure"() {
        server.addHandler(new NtlmResponseHandler())

        PasswordCredentials credentials = new DefaultPasswordCredentials("example\\administrator", "test")
        AllSchemesAuthentication auth = new AllSchemesAuthentication(credentials)
        Collection<Authentication> credentialsCollection = new HashSet<Authentication>()
        credentialsCollection.add(auth)
        HttpSettings httpSettings = new DefaultHttpSettings(credentialsCollection)
        HttpClientHelper http = new HttpClientHelper(httpSettings)
        final HttpGet httpGet = new HttpGet(server.getUri());

        when:
        final HttpResponse response = http.performHttpRequest(httpGet)

        then:
        response.getStatusLine().getStatusCode() == HttpStatus.SC_OK
    }

}
