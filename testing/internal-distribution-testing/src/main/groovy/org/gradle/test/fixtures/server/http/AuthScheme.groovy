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

import com.google.common.io.BaseEncoding
import groovy.transform.CompileStatic
import org.apache.http.HeaderElement
import org.apache.http.message.BasicHeaderValueParser

import java.security.MessageDigest
import java.security.SecureRandom
import java.nio.charset.StandardCharsets

@CompileStatic
enum AuthScheme {
    BASIC(new BasicAuthHandler(false)),
    DIGEST(new DigestAuthHandler()),
    HIDE_UNAUTHORIZED(new BasicAuthHandler(true)),
    NTLM(new NtlmAuthHandler()),
    HEADER(new HttpHeaderAuthHandler())

    final AuthSchemeHandler handler

    AuthScheme(AuthSchemeHandler handler) {
        this.handler = handler
    }

    interface Authenticator {
        boolean authenticate(HttpRequest request, HttpResponse response)
    }

    static abstract class AuthSchemeHandler {
        abstract Authenticator createAuthenticator(TestUserRealm realm)

        /** Whether a request to an unauthenticated path unexpectedly carries authentication (used to reject stray auth). */
        abstract boolean containsUnexpectedAuthentication(HttpRequest request)
    }

    private static class BasicAuthHandler extends AuthSchemeHandler {
        private final boolean hideUnauthorized

        BasicAuthHandler(boolean hideUnauthorized) {
            this.hideUnauthorized = hideUnauthorized
        }

        @Override
        Authenticator createAuthenticator(TestUserRealm realm) {
            return new BasicAuthenticator(realm, hideUnauthorized)
        }

        @Override
        boolean containsUnexpectedAuthentication(HttpRequest request) {
            // A client must not send Basic credentials to a path that does not require them.
            return request.getHeader("Authorization") != null
        }
    }

    private static class DigestAuthHandler extends AuthSchemeHandler {
        @Override
        Authenticator createAuthenticator(TestUserRealm realm) {
            return new DigestAuthenticator(realm)
        }

        @Override
        boolean containsUnexpectedAuthentication(HttpRequest request) {
            return false
        }
    }

    private static class NtlmAuthHandler extends AuthSchemeHandler {
        @Override
        Authenticator createAuthenticator(TestUserRealm realm) {
            return new NtlmAuthenticator(realm)
        }

        @Override
        boolean containsUnexpectedAuthentication(HttpRequest request) {
            return false
        }
    }

    private static class HttpHeaderAuthHandler extends AuthSchemeHandler {
        @Override
        Authenticator createAuthenticator(TestUserRealm realm) {
            return new TestHttpHeaderAuthenticator()
        }

        @Override
        boolean containsUnexpectedAuthentication(HttpRequest request) {
            return false
        }
    }

    @CompileStatic
    private static class BasicAuthenticator implements Authenticator {
        private final TestUserRealm realm
        private final boolean hideUnauthorized

        BasicAuthenticator(TestUserRealm realm, boolean hideUnauthorized) {
            this.realm = realm
            this.hideUnauthorized = hideUnauthorized
        }

        @Override
        boolean authenticate(HttpRequest request, HttpResponse response) {
            String header = request.getHeader("Authorization")
            if (header != null && header.regionMatches(true, 0, "Basic ", 0, 6)) {
                try {
                    String decoded = new String(Base64.decoder.decode(header.substring(6).trim()), StandardCharsets.UTF_8)
                    int colon = decoded.indexOf(":")
                    if (colon >= 0) {
                        String user = decoded.substring(0, colon)
                        String password = decoded.substring(colon + 1)
                        if (realm.authenticate(user, password)) {
                            return true
                        }
                    }
                } catch (IllegalArgumentException ignore) {
                    // Malformed Base64 credentials: fall through and treat as an authentication failure.
                }
            }
            response.setHeader("WWW-Authenticate", "Basic realm=\"" + realm.name + "\"")
            response.sendError(hideUnauthorized ? 404 : 401)
            return false
        }
    }

    @CompileStatic
    private static class DigestAuthenticator implements Authenticator {
        private final TestUserRealm realm
        private final SecureRandom random = new SecureRandom()

        DigestAuthenticator(TestUserRealm realm) {
            this.realm = realm
        }

        @Override
        boolean authenticate(HttpRequest request, HttpResponse response) {
            String header = request.getHeader("Authorization")
            if (header != null && header.regionMatches(true, 0, "Digest ", 0, 7)) {
                Map<String, String> params = parse(header.substring(7))
                if (validate(request.getMethod(), params)) {
                    return true
                }
            }
            challenge(response)
            return false
        }

        private boolean validate(String method, Map<String, String> params) {
            String username = params.get("username")
            if (username == null || username != realm.username) {
                return false
            }
            String nonce = params.get("nonce")
            String uri = params.get("uri")
            String qop = params.get("qop")
            String response = params.get("response")
            if (nonce == null || uri == null || response == null) {
                return false
            }
            String ha1 = md5(realm.username + ":" + realm.name + ":" + realm.password)
            String ha2 = md5(method + ":" + uri)
            String expected
            if (qop != null) {
                expected = md5(ha1 + ":" + nonce + ":" + params.get("nc") + ":" + params.get("cnonce") + ":" + qop + ":" + ha2)
            } else {
                expected = md5(ha1 + ":" + nonce + ":" + ha2)
            }
            return expected == response
        }

        private void challenge(HttpResponse response) {
            byte[] nonceBytes = new byte[16]
            random.nextBytes(nonceBytes)
            String nonce = BaseEncoding.base16().lowerCase().encode(nonceBytes)
            response.setHeader("WWW-Authenticate", "Digest realm=\"" + realm.name + "\", qop=\"auth\", nonce=\"" + nonce + "\", opaque=\"" + md5("opaque") + "\"")
            response.sendError(401)
        }

        private static Map<String, String> parse(String params) {
            Map<String, String> result = [:]
            for (HeaderElement element : BasicHeaderValueParser.parseElements(params, BasicHeaderValueParser.INSTANCE)) {
                result.put(element.name, element.value)
            }
            return result
        }

        private static String md5(String input) {
            MessageDigest digest = MessageDigest.getInstance("MD5")
            return BaseEncoding.base16().lowerCase().encode(digest.digest(input.getBytes(StandardCharsets.ISO_8859_1)))
        }
    }
}
