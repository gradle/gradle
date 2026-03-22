/*
 * Copyright 2012 the original author or authors.
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
import spock.lang.Specification

class HttpResponseResourceTest extends Specification {

    def sourceUrl = new URI("http://gradle.org")
    def method = "GET"
    def response = Mock(HttpClient.Response)

    def "extracts etag"() {
        given:
        response.getHeader(HttpHeaders.ETAG) >> "abc"

        expect:
        resource().metaData.etag == "abc"
    }

    def "handles no etag"() {
        expect:
        resource().metaData.etag == null
    }

    def "is not openable more than once"() {
        when:
        def resource = this.resource()
        resource.openStream()

        and:
        resource.openStream()

        then:
        def ex = thrown(IOException);
        ex.message == "Unable to open Stream as it was opened before."
    }

    def "provides access to arbitrary headers"() {
        given:
        response.getHeader(name) >> value

        expect:
        resource().getHeaderValue(name) == value

        where:
        name = "X-Client-Deprecation-Message"
        value = "Some message"
    }

    def "returns null when accessing value of a non existing header"() {
        expect:
        resource().getHeaderValue("X-No-Such-Header") == null
    }

    def "close closes the response"() {
        when:
        resource().close()

        then:
        1 * response.close()
    }

    def "falls back to URL path when Content-Disposition has no filename"() {
        given:
        def url = new URI("https://example.com/jdks-bin/jdk-17.0.1.tar.gz")
        response.getHeader("Content-Disposition") >> 'attachment'

        expect:
        new HttpResponseResource(method, url, response).metaData.filename == "jdk-17.0.1.tar.gz"
    }

    def "extracts filename from URL when no Content-Disposition header"() {
        given:
        def url = new URI("https://example.com/jdks-bin/jdk-17.0.1.tar.gz")

        expect:
        new HttpResponseResource(method, url, response).metaData.filename == "jdk-17.0.1.tar.gz"
    }

    def "extracts filename from effective URI when available"() {
        given:
        def effectiveUri = new URI("https://cdn.example.com/jdk-redirected.tar.gz")
        response.getEffectiveUri() >> effectiveUri

        expect:
        resource().metaData.filename == "jdk-redirected.tar.gz"
    }

    def "extracts filename from URL path ignoring query parameters"() {
        given:
        def url = new URI("https://example.com/jdks/jdk-17.tar.gz?token=abc&expires=123")

        expect:
        new HttpResponseResource(method, url, response).metaData.filename == "jdk-17.tar.gz"
    }

    def "extracts filename from Content-Disposition header"() {
        expect:
        HttpResponseResource.extractFilenameFromContentDisposition(header) == expected

        where:
        header                                                                          | expected
        null                                                                            | null
        'attachment'                                                                    | null
        'inline'                                                                        | null
        'attachment; creation-date="today"'                                             | null
        'attachment; filename=""'                                                        | null
        'attachment; filename='                                                          | null
        'attachment; filename="example.tar.gz"'                                         | "example.tar.gz"
        'attachment; filename=example.tar.gz'                                           | "example.tar.gz"
        'attachment; FILENAME="Example.TXT"'                                            | "Example.TXT"
        'attachment; FileName=Example.TXT'                                              | "Example.TXT"
        'attachment ; filename = "a.txt" '                                              | "a.txt"
        'attachment; filename="jdk-17.tar.gz"; size=12345'                              | "jdk-17.tar.gz"
        'attachment; filename="a;b.txt"; size=123'                                      | "a;b.txt"
        'attachment; filename="a\\"b.txt"'                                              | 'a"b.txt'
        "attachment; filename*=UTF-8''na%C3%AFve%20file.txt"                            | "na\u00EFve file.txt"
        "attachment; filename*=iso-8859-1'en'%A3%20rates.txt"                           | "\u00A3 rates.txt"
        "attachment; filename=\"fallback.txt\"; filename*=UTF-8''%E2%82%AC%20rates.txt" | "\u20AC rates.txt"
        "attachment; filename=\"fallback.txt\"; filename*=UTF-8''%ZZ%20bad"             | "fallback.txt"
    }

    HttpResponseResource resource() {
        new HttpResponseResource(method, sourceUrl, response)
    }

}
