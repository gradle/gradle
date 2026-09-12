/*
 * Copyright 2026 Gradle and contributors.
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

import org.apache.http.HttpHeaders
import org.apache.http.HttpHost
import org.apache.http.HttpRequest
import org.apache.http.HttpResponse
import org.apache.http.HttpStatus
import org.apache.http.HttpVersion
import org.apache.http.ProtocolException
import org.apache.http.RequestLine
import org.apache.http.client.CircularRedirectException
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.protocol.HttpClientContext
import org.apache.http.message.BasicHeader
import org.apache.http.message.BasicStatusLine
import spock.lang.Specification

class EncodingPreservingRedirectStrategyTest extends Specification {

    private static final String HOST = "https://repo.example.com"
    private static final String REQUEST_URI = "/m2/g/m/1.0/m-1.0.jar"

    def strategy = new EncodingPreservingRedirectStrategy()
    def context = HttpClientContext.create()

    def setup() {
        context.targetHost = new HttpHost("repo.example.com", -1, "https")
    }

    def "keeps the escaping the server sent for #escaped"() {
        given:
        def location = "$HOST/module/1.0-dev01${escaped}abc/module.pom"

        expect:
        follow(location) == location

        where:
        escaped << ['%2B', '%3A', '%40', '%2C', '%3B', '%3D', '%21', '%24', '%26',
                    '%2F', '%20', '%25', '%3F', '%23']
    }

    def "keeps the escaping whether or not the client asks for normalisation"() {
        given:
        def location = "$HOST/module/1.0%2Bdev/module.pom"

        expect:
        follow(location, RequestConfig.custom().setNormalizeUri(normalize).build()) == location

        where:
        normalize << [true, false]
    }

    def "keeps the escaping of a query"() {
        given:
        def location = "$HOST/module.jar?sig=ab%2Bcd%3D"

        expect:
        follow(location) == location
    }

    def "resolves a relative location against the request"() {
        expect:
        follow(location) == "$HOST/$expected"

        where:
        location               | expected
        '/other/1.0%2Bdev/pom' | 'other/1.0%2Bdev/pom'
        'sub/1.0%2Bdev/pom'    | 'm2/g/m/1.0/sub/1.0%2Bdev/pom'
    }

    def "resolves a relative location against an escaped request path"() {
        expect:
        follow('sub/2.0%2Bdev/pom', RequestConfig.DEFAULT, '/a/1.0%2Bdev/m.pom') ==
            "$HOST/a/1.0%2Bdev/sub/2.0%2Bdev/pom"
    }

    def "resolves a location that carries only a query against the request path"() {
        expect:
        follow('?token=a%2Bb') == "$HOST$REQUEST_URI?token=a%2Bb"
    }

    def "resolves an empty location to the request itself"() {
        expect:
        follow('') == "$HOST$REQUEST_URI"
    }

    def "passes an absolute location through as it arrived"() {
        expect:
        follow(location) == location

        where:
        location << [
            "$HOST/module/1.0/../2.0/module.pom",
            "$HOST//module//1.0/module.pom",
            "HTTPS://REPO.EXAMPLE.COM/module/1.0/module.pom"
        ]
    }

    def "removes the dot segments of a relative location while resolving it"() {
        expect:
        follow('../2.0%2Bdev/module.pom') == "$HOST/m2/g/m/2.0%2Bdev/module.pom"
    }

    def "fails when the redirect response carries no location"() {
        when:
        strategy.getLocationURI(request(REQUEST_URI), responseWithoutLocation(), context)

        then:
        thrown(ProtocolException)
    }

    def "fails on a relative location when the client forbids one"() {
        given:
        def config = RequestConfig.custom()
            .setRelativeRedirectsAllowed(false)
            .build()

        when:
        follow('/other/module.pom', config)

        then:
        thrown(ProtocolException)
    }

    def "fails when the same location is visited twice"() {
        given:
        def location = "$HOST/module/1.0/module.pom"

        when:
        follow(location)
        follow(location)

        then:
        thrown(CircularRedirectException)
    }

    def "visits the same location twice when the client allows it"() {
        given:
        def location = "$HOST/module/1.0/module.pom"
        def config = RequestConfig.custom()
            .setCircularRedirectsAllowed(true)
            .build()

        expect:
        follow(location, config) == location
        follow(location, config) == location
    }

    private String follow(
        String location,
        RequestConfig config = RequestConfig.DEFAULT,
        String requestUri = REQUEST_URI
    ) {
        context.setAttribute(HttpClientContext.REQUEST_CONFIG, config)
        strategy.getLocationURI(request(requestUri), redirectTo(location), context).toASCIIString()
    }

    private HttpRequest request(String requestUri) {
        Stub(HttpRequest) {
            getRequestLine() >> Stub(RequestLine) { getUri() >> requestUri }
        }
    }

    private HttpResponse redirectTo(String location) {
        Stub(HttpResponse) {
            getFirstHeader(HttpHeaders.LOCATION) >> new BasicHeader(HttpHeaders.LOCATION, location)
        }
    }

    private HttpResponse responseWithoutLocation() {
        Stub(HttpResponse) {
            getFirstHeader(HttpHeaders.LOCATION) >> null
            getStatusLine() >> new BasicStatusLine(
                HttpVersion.HTTP_1_1, HttpStatus.SC_MOVED_TEMPORARILY, 'Found'
            )
        }
    }
}
