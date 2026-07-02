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
import jcifs.ntlmssp.Type1Message
import jcifs.ntlmssp.Type2Message
import jcifs.ntlmssp.Type3Message
import jcifs.smb.NtlmPasswordAuthentication
import jcifs.util.Base64

import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap

@CompileStatic
class NtlmAuthenticator implements AuthScheme.Authenticator {
    private static final int TYPE_1_NEGOTIATE = 1
    private static final int TYPE_3_AUTHENTICATE = 3
    private static final int SERVER_CHALLENGE_LENGTH = 8

    private final TestUserRealm realm
    private final Map<String, byte[]> challenges = new ConcurrentHashMap<>()
    private final SecureRandom random = new SecureRandom()

    NtlmAuthenticator(TestUserRealm realm) {
        this.realm = realm
    }

    @Override
    boolean authenticate(HttpRequest request, HttpResponse response) {
        // Keep the connection alive so the multi-leg handshake stays on one socket.
        response.setHeader("Connection", "Keep-Alive")
        String connection = connectionKey(request)

        String header = request.getHeader("Authorization")
        if (header == null || !header.startsWith("NTLM ")) {
            // No NTLM token yet: ask the client to start the handshake.
            challenges.remove(connection)
            return unauthorized(response, "NTLM")
        }

        byte[] token = decodeToken(header)
        if (token == null) {
            return unauthorized(response)
        }

        switch (messageType(token)) {
            case TYPE_1_NEGOTIATE:
                byte[] serverChallenge = newServerChallenge()
                challenges.put(connection, serverChallenge)
                return unauthorized(response, "NTLM " + type2Token(token, serverChallenge))
            case TYPE_3_AUTHENTICATE:
                byte[] serverChallenge = challenges.get(connection)
                if (serverChallenge == null) {
                    return unauthorized(response)
                }
                if (passwordMatches(token, serverChallenge)) {
                    challenges.remove(connection)
                    return true
                }
                return unauthorized(response, "basic realm=\"" + realm.name + "\"")
            default:
                return unauthorized(response)
        }
    }

    private static String connectionKey(HttpRequest request) {
        return request.getRemoteAddr() + ":" + request.getRemotePort()
    }

    private static boolean unauthorized(HttpResponse response, String wwwAuthenticate = null) {
        if (wwwAuthenticate != null) {
            response.setHeader("WWW-Authenticate", wwwAuthenticate)
        }
        response.sendError(401)
        return false
    }

    // --- Low-level NTLM wire format and password crypto (jcifs) ---

    private static byte[] decodeToken(String header) {
        try {
            byte[] token = Base64.decode(header.substring("NTLM ".length()))
            return token.length >= 9 ? token : null
        } catch (Exception ignore) {
            return null
        }
    }

    private static int messageType(byte[] token) {
        return token[8]
    }

    private byte[] newServerChallenge() {
        byte[] challenge = new byte[SERVER_CHALLENGE_LENGTH]
        random.nextBytes(challenge)
        return challenge
    }

    private static String type2Token(byte[] type1Token, byte[] serverChallenge) {
        Type1Message type1 = new Type1Message(type1Token)
        Type2Message type2 = new Type2Message(type1, serverChallenge, null)
        return Base64.encode(type2.toByteArray())
    }

    private boolean passwordMatches(byte[] type3Token, byte[] serverChallenge) {
        Type3Message type3 = new Type3Message(type3Token)
        if (type3.getUser() != realm.username) {
            // The response must be for the realm's user, not merely prove knowledge of the password.
            return false
        }
        NtlmPasswordAuthentication authentication = new NtlmPasswordAuthentication(
            type3.getDomain(), type3.getUser(), serverChallenge, type3.getLMResponse(), type3.getNTResponse())
        byte[] lm = authentication.getAnsiHash(serverChallenge)
        byte[] nt = authentication.getUnicodeHash(serverChallenge)
        // A Type 3 with an NT response longer than 24 bytes carries an NTLMv2 blob; otherwise it is NTLMv1.
        return (nt != null && nt.length > 24)
            ? matchesLmv2(authentication, serverChallenge, lm)
            : matchesNtlmv1(nt, serverChallenge)
    }

    private boolean matchesLmv2(NtlmPasswordAuthentication authentication, byte[] serverChallenge, byte[] lm) {
        // NTLMv2: the LM field is a 16-byte HMAC followed by the client challenge.
        if (lm == null || lm.length <= 16) {
            return false
        }
        byte[] clientChallenge = Arrays.copyOfRange(lm, 16, lm.length)
        for (String domain : [authentication.getDomain(), ""] as String[]) {
            byte[] expected = NtlmPasswordAuthentication.getLMv2Response(
                domain, authentication.getUsername(), realm.password, serverChallenge, clientChallenge)
            if (Arrays.equals(lm, expected)) {
                return true
            }
        }
        return false
    }

    private boolean matchesNtlmv1(byte[] nt, byte[] serverChallenge) {
        // NTLMv1: the NT response is DES(challenge) keyed by the NT hash of the password.
        byte[] expected = NtlmPasswordAuthentication.getNTLMResponse(realm.password, serverChallenge)
        return nt != null && Arrays.equals(nt, expected)
    }
}
