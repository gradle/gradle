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

import org.apache.http.HttpResponse
import org.apache.http.client.methods.HttpRequestBase
import org.gradle.api.artifacts.repositories.PasswordCredentials
import org.apache.http.client.methods.HttpGet

import spock.lang.Specification

class HttpClientHelperTest extends Specification {
    def "throws HttpRequestException if an IO error occurs during a request"() {
        def settings = Stub(HttpSettings) {
            getCredentials() >> Stub(PasswordCredentials)
            getProxySettings() >> Stub(HttpProxySettings)
        }
        def client = new HttpClientHelper(settings) {
            @Override
            protected HttpResponse executeGetOrHead(HttpRequestBase method) {
                throw new IOException("ouch")
            }
        }

        when:
        client.performRequest(new HttpGet("http://gradle.org"))

        then:
        HttpRequestException e = thrown()
        e.cause.message == "ouch"
    }
}
