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

package org.gradle.api.internal.externalresource.transport.http

import org.apache.http.Header
import org.apache.http.HttpHeaders
import org.apache.http.HttpResponse
import org.apache.http.message.BasicHeader
import org.gradle.api.internal.externalresource.ExternalResource
import spock.lang.Specification
import org.apache.http.HttpEntity

class HttpResponseResourceTest extends Specification {

    def sourceUrl = "http://gradle.org"
    def method = "GET"
    def response = Mock(HttpResponse)

    def "extracts etag"() {
        given:
        addHeader(HttpHeaders.ETAG, "abc")

        expect:
        resource().metaData.etag == "abc"
    }

    def "handles no etag"() {
        expect:
        resource().metaData.etag == null
    }

    def "is not openable more than once"() {
        setup:
        1 * response.entity >> Mock(HttpEntity)
        when:
        def resource = resource();
        resource.openStream();
        and:
        resource.openStream()
        then:
        def ex = thrown(IOException);
        ex.message == "Unable to open Stream as it was opened before."
    }

    ExternalResource resource() {
        new HttpResponseResource(method, sourceUrl, response)
    }

    void addHeader(String name, String value) {
        interaction {
            1 * response.getFirstHeader(name) >> header(name, value)
        }
    }

    Header header(String name, String value) {
        new BasicHeader(name, value)
    }
}
