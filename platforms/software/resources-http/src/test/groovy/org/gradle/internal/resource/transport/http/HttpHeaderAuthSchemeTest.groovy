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

import org.apache.hc.client5.http.auth.AuthScope
import org.apache.hc.client5.http.auth.AuthenticationException
import org.apache.hc.client5.http.auth.CredentialsProvider
import org.apache.hc.core5.http.HttpHost
import spock.lang.Specification

class HttpHeaderAuthSchemeTest extends Specification {
    def "assure scheme name is correct"() {
        when:
        HttpHeaderAuthScheme headerAuthScheme = new HttpHeaderAuthScheme()

        then:
        headerAuthScheme.name == "header"
    }

    def "test non-matching credentials are not accepted"() {
        given:
        HttpHeaderAuthScheme headerAuthScheme = new HttpHeaderAuthScheme()
        def host = new HttpHost("localhost")
        def credentialsProvider = Mock(CredentialsProvider) {
            getCredentials(_ as AuthScope, _) >> null
        }

        expect:
        !headerAuthScheme.isResponseReady(host, credentialsProvider, null)
    }

    def "test generateAuthResponse throws when no credentials set"() {
        given:
        HttpHeaderAuthScheme headerAuthScheme = new HttpHeaderAuthScheme()

        when:
        headerAuthScheme.generateAuthResponse(new HttpHost("localhost"), null, null)

        then:
        thrown(AuthenticationException)
    }

    def "test authenticate"() {
        given:
        HttpHeaderAuthScheme headerAuthScheme = new HttpHeaderAuthScheme()
        def credentials = new HttpClientHttpHeaderCredentials("TestHttpHeaderName", "TestHttpHeaderValue")
        def host = new HttpHost("localhost")
        def credentialsProvider = Mock(CredentialsProvider) {
            getCredentials(_ as AuthScope, _) >> credentials
        }

        when:
        headerAuthScheme.isResponseReady(host, credentialsProvider, null)
        def authResponse = headerAuthScheme.generateAuthResponse(host, null, null)

        then:
        authResponse == "TestHttpHeaderValue"
    }
}
