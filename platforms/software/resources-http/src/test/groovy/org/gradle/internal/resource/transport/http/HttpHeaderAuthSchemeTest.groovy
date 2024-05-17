/*
 * Copyright 2018 the original author or authors.
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

import org.apache.http.auth.Credentials
import spock.lang.Specification

class HttpHeaderAuthSchemeTest extends Specification {
    def "assure scheme name is correct"() {
        when:
        HttpHeaderAuthScheme headerAuthScheme = new HttpHeaderAuthScheme()

        then:
        headerAuthScheme.schemeName == "header"
    }

    def "test only correct credentials are supported"() {
        given:
        HttpHeaderAuthScheme headerAuthScheme = new HttpHeaderAuthScheme()

        when:
        def header = headerAuthScheme.authenticate(Stub(Credentials), null, null)

        then:
        thrown(IllegalArgumentException)
    }

    def "test authenticate"() {
        given:
        HttpHeaderAuthScheme headerAuthScheme = new HttpHeaderAuthScheme()
        def credentials = new HttpClientHttpHeaderCredentials("TestHttpHeaderName", "TestHttpHeaderValue")

        when:
        def header = headerAuthScheme.authenticate(credentials, null, null)

        then:
        header.name == "TestHttpHeaderName"
        header.value == "TestHttpHeaderValue"
    }
}
