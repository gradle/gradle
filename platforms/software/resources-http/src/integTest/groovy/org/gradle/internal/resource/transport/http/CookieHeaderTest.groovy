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

package org.gradle.internal.resource.transport.http

import org.gradle.api.internal.DocumentationRegistry
import org.gradle.api.logging.LogLevel
import org.gradle.internal.logging.CollectingTestOutputEventListener
import org.gradle.internal.logging.ConfigureLogging
import org.gradle.test.fixtures.server.http.HttpServer
import org.junit.Rule
import spock.lang.Specification

import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

class CookieHeaderTest extends Specification {

    @Rule
    HttpServer httpServer = new HttpServer()
    CollectingTestOutputEventListener listener = new CollectingTestOutputEventListener()
    @Rule
    ConfigureLogging logging = new ConfigureLogging(listener, LogLevel.WARN)
    SslContextFactory sslContextFactory = new DefaultSslContextFactory()
    HttpSettings settings = DefaultHttpSettings.builder()
        .withAuthenticationSettings([])
        .withSslContextFactory(sslContextFactory)
        .withRedirectVerifier({})
        .build()
    HttpClientHelper client = new HttpClientHelper(new DocumentationRegistry(), settings)

    def "cookie header with attributes #attributes can be parsed"() {
        httpServer.start()
        httpServer.expect("/cookie", ['GET'],
            new RespondWithCookieAction("some", attributes))
        when:
        client.performGet("${httpServer.address}/cookie", false)

        then:
        listener.events*.message.forEach { assert !it.contains('Invalid cookie header:') }

        where:
        attributes << [
                'Max-Age=31536000; Expires=Sun, 24 Jun 2018 16:26:36 GMT;',
                'Max-Age=31536000; Expires=Sun Jun 24 16:26:36 2018;',
                'Max-Age=31536000; Expires=Sun, 24-Jun-18 16:26:36 GMT;',
                'Max-Age=31536000; Expires=Sun, 24-Jun-18 16:26:36 GMT-08:00;',
        ]
    }

}

class RespondWithCookieAction extends HttpServer.ActionSupport {
    private final String cookie
    private final String attributes

    RespondWithCookieAction(String cookie, String attributes) {
        super("Return cookie header ${cookie}")
        this.cookie = cookie
        this.attributes = attributes
    }

    @Override
    void handle(HttpServletRequest request, HttpServletResponse response) {
        response.addHeader("Set-Cookie", "cookie_name=$cookie; $attributes")
        String message = "Cookie sent"
        response.setContentLength(message.bytes.length)
        response.setContentType("text/html")
        response.setCharacterEncoding("utf8")
        response.outputStream.bytes = message.bytes
    }
}
