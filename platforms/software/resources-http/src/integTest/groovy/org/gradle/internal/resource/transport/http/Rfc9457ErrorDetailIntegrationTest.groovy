/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.internal.resource.transport.http

import com.google.common.collect.ImmutableMap
import org.apache.http.impl.client.HttpClientBuilder
import org.gradle.api.internal.DocumentationRegistry
import org.gradle.api.logging.LogLevel
import org.gradle.internal.logging.CollectingTestOutputEventListener
import org.gradle.internal.logging.ConfigureLogging
import org.gradle.internal.resource.HttpErrorStatusCodeException
import org.gradle.test.fixtures.server.http.HttpRequest
import org.gradle.test.fixtures.server.http.HttpResponse
import org.gradle.test.fixtures.server.http.HttpServer
import org.junit.Rule
import spock.lang.Issue
import spock.lang.Specification

/**
 * Integration tests for RFC 9457 Problem Details support in HTTP error handling.
 * Tests that ApacheCommonsHttpClient correctly extracts and uses detailed error messages
 * from repositories that support RFC 9457 (e.g., Nexus Repository Manager 3.75+).
 */
class Rfc9457ErrorDetailIntegrationTest extends Specification {
    @Rule
    private HttpServer httpServer = new HttpServer()
    private CollectingTestOutputEventListener listener = new CollectingTestOutputEventListener()
    @Rule
    private ConfigureLogging logging = new ConfigureLogging(listener, LogLevel.WARN)
    private sslContextFactory = new DefaultSslContextFactory()
    private settings = DefaultHttpSettings.builder()
        .withAuthenticationSettings([])
        .withSslContextFactory(sslContextFactory)
        .withRedirectVerifier({})
        .build()
    private client = new ApacheCommonsHttpClient(new DocumentationRegistry(), settings, () -> HttpClientBuilder.create())

    def setup() {
        httpServer.start()
    }

    def "uses title field when detail is missing from RFC 9457 response"() {
        given:
        def rfc9457Json = '''
            {
                "type": "about:blank",
                "title": "Artifact Quarantined",
                "status": 403
            }
        '''
        httpServer.expect("/artifact.jar", ['GET'], new Rfc9457ErrorAction(403, rfc9457Json))

        when:
        client.performGet(URI.create("${httpServer.address}/artifact.jar"), ImmutableMap.of())

        then:
        HttpRequestException outer = thrown()
        HttpErrorStatusCodeException e = outer.cause as HttpErrorStatusCodeException
        e.statusCode == 403
        e.message.contains("Artifact Quarantined")
    }

    def "handles RFC 9457 response with charset in content-type"() {
        given:
        def rfc9457Json = '''
            {
                "detail": "Repository policy violation: artifact contains known vulnerabilities"
            }
        '''
        httpServer.expect("/artifact.jar", ['GET'], new Rfc9457ErrorAction(403, rfc9457Json, "application/problem+json; charset=utf-8"))

        when:
        client.performGet(URI.create("${httpServer.address}/artifact.jar"), ImmutableMap.of())

        then:
        HttpRequestException outer = thrown()
        HttpErrorStatusCodeException e = outer.cause as HttpErrorStatusCodeException
        e.statusCode == 403
        e.message.contains("Repository policy violation: artifact contains known vulnerabilities")
    }

    def "handles RFC 9457 response with all standard fields"() {
        given:
        def rfc9457Json = '''
            {
                "type": "https://example.com/problems/license-violation",
                "title": "License Policy Violation",
                "status": 451,
                "detail": "The artifact com.example:my-library:1.0.0 violates the organization's license policy (GPL license not allowed)",
                "instance": "/repository/maven-central/com/example/my-library/1.0.0/my-library-1.0.0.jar"
            }
        '''
        httpServer.expect("/artifact.jar", ['GET'], new Rfc9457ErrorAction(451, rfc9457Json))

        when:
        client.performGet(URI.create("${httpServer.address}/artifact.jar"), ImmutableMap.of())

        then:
        HttpRequestException outer = thrown()
        HttpErrorStatusCodeException e = outer.cause as HttpErrorStatusCodeException
        e.statusCode == 451
        e.message.contains("The artifact com.example:my-library:1.0.0 violates the organization's license policy (GPL license not allowed)")
    }

    def "falls back to reason phrase for non-RFC 9457 responses"() {
        given:
        httpServer.expect("/artifact.jar", ['GET'], new NonRfc9457ErrorAction(403))

        when:
        client.performGet(URI.create("${httpServer.address}/artifact.jar"), ImmutableMap.of())

        then:
        HttpRequestException outer = thrown()
        HttpErrorStatusCodeException e = outer.cause as HttpErrorStatusCodeException
        e.statusCode == 403
        e.message.contains("Forbidden")
    }

    def "handles RFC 9457 response with invalid JSON gracefully"() {
        given:
        def invalidJson = '{"detail": "Missing closing brace"'
        httpServer.expect("/artifact.jar", ['GET'], new Rfc9457ErrorAction(500, invalidJson))

        when:
        client.performGet(URI.create("${httpServer.address}/artifact.jar"), ImmutableMap.of())

        then:
        HttpRequestException outer = thrown()
        HttpErrorStatusCodeException e = outer.cause as HttpErrorStatusCodeException
        e.statusCode == 500

        and: "should fall back to reason phrase or empty string on the outer wrapper"
        outer.message.contains("Could not GET")
    }

    def "5xx responses without RFC9457 body still produce a server-error exception"() {
        given:
        httpServer.expect("/broken.jar", ['GET'], new NonRfc9457ErrorAction(500))

        when:
        client.performGet(URI.create("${httpServer.address}/broken.jar"), ImmutableMap.of())

        then:
        HttpRequestException outer = thrown()
        HttpErrorStatusCodeException e = outer.cause as HttpErrorStatusCodeException
        e.statusCode == 500
        e.serverError
        outer.message.contains("Could not GET")
        e.message.contains("500")
    }

    def "handles RFC 9457 response with empty JSON object"() {
        given:
        def emptyJson = '{}'
        httpServer.expect("/artifact.jar", ['GET'], new Rfc9457ErrorAction(403, emptyJson))

        when:
        client.performGet(URI.create("${httpServer.address}/artifact.jar"), ImmutableMap.of())

        then:
        HttpRequestException outer = thrown()
        HttpErrorStatusCodeException e = outer.cause as HttpErrorStatusCodeException
        e.statusCode == 403

        and: "should fall back to reason phrase or empty string on the outer wrapper"
        outer.message.contains("Could not GET")
    }

    @Issue("https://github.com/gradle/gradle/issues/39412")
    def "does not try to parse a body for a failed HEAD request with RFC 9457 content-type"() {
        given:
        httpServer.expect("/artifact.jar", ['HEAD'], new HeadRfc9457ErrorAction(403))

        when:
        client.performHead(URI.create("${httpServer.address}/artifact.jar"), ImmutableMap.of())

        then:
        HttpRequestException outer = thrown()
        HttpErrorStatusCodeException e = outer.cause as HttpErrorStatusCodeException
        e.statusCode == 403
        outer.message.contains("Could not HEAD")

        and: "no warning about an unparseable RFC 9457 body is logged"
        listener.events.findAll { it.message.contains("Failed to parse RFC9457 response") }.empty
    }

    /**
     * Action that answers a HEAD request with an RFC 9457 content-type, but no body, as HEAD responses never have one.
     */
    @SuppressWarnings('GroovyAccessibility')
    private static class HeadRfc9457ErrorAction extends HttpServer.ActionSupport {
        private final int statusCode

        HeadRfc9457ErrorAction(int statusCode) {
            super("Return RFC 9457 content-type without a body for HEAD with status ${statusCode}")
            this.statusCode = statusCode
        }

        @Override
        void handle(HttpRequest request, HttpResponse response) {
            response.setStatus(statusCode)
            response.setContentType("application/problem+json")
        }
    }

    /**
     * Action that returns an RFC 9457 Problem Details JSON response.
     */
    @SuppressWarnings('GroovyAccessibility')
    private static class Rfc9457ErrorAction extends HttpServer.ActionSupport {
        private final int statusCode
        private final String jsonBody
        private final String contentType

        Rfc9457ErrorAction(int statusCode, String jsonBody, String contentType = "application/problem+json") {
            super("Return RFC 9457 error with status ${statusCode}")
            this.statusCode = statusCode
            this.jsonBody = jsonBody
            this.contentType = contentType
        }

        @Override
        void handle(HttpRequest request, HttpResponse response) {
            response.setStatus(statusCode)
            response.setContentType(contentType)
            byte[] bodyBytes = jsonBody.getBytes("UTF-8")
            response.setContentLength(bodyBytes.length)
            response.outputStream.write(bodyBytes)
            response.outputStream.flush()
        }
    }

    /**
     * Action that returns a non-RFC 9457 error response (traditional HTML error).
     */
    @SuppressWarnings('GroovyAccessibility')
    private static class NonRfc9457ErrorAction extends HttpServer.ActionSupport {
        private final int statusCode

        NonRfc9457ErrorAction(int statusCode) {
            super("Return non-RFC 9457 error with status ${statusCode}")
            this.statusCode = statusCode
        }

        @Override
        void handle(HttpRequest request, HttpResponse response) {
            response.setStatus(statusCode)
        }
    }
}
