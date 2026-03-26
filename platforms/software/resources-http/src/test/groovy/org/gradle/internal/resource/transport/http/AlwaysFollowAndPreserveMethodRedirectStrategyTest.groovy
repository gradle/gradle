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

package org.gradle.internal.resource.transport.http

import org.apache.hc.core5.http.HttpRequest
import org.apache.hc.core5.http.HttpResponse
import org.apache.hc.core5.http.HttpStatus
import org.apache.hc.core5.http.message.BasicHeader
import org.apache.hc.core5.http.message.BasicHttpResponse
import org.apache.hc.core5.http.protocol.HttpContext
import spock.lang.Specification

class AlwaysFollowAndPreserveMethodRedirectStrategyTest extends Specification {

    static final String[] HTTP_METHODS = ['GET', 'POST', 'PUT', 'HEAD', 'DELETE', 'OPTIONS', 'TRACE', 'PATCH']

    def "should consider all requests redirectable"() {
        setup:
        HttpRequest request = Mock()
        HttpResponse response = new BasicHttpResponse(301)
        response.setHeader(new BasicHeader('location', 'http://redirectTo'))
        HttpContext context = Mock()
        request.getMethod() >> method
        request.getUri() >> new URI("http://original.com")
        request.getScheme() >> "http"
        request.getAuthority() >> null
        request.getPath() >> "/"

        expect:
        new AllowFollowForMutatingMethodRedirectStrategy().isRedirected(request, response, context)

        where:
        method << HTTP_METHODS
    }

    def "should upgrade non-method-preserving redirects to 307 for unsafe methods [#httpMethod]"() {
        setup:
        HttpRequest request = Mock()
        HttpResponse response = new BasicHttpResponse(redirect)
        response.setHeader(new BasicHeader('location', 'http://redirectTo'))
        HttpContext context = Mock()
        request.getMethod() >> httpMethod
        request.getUri() >> new URI("http://original.com")
        request.getScheme() >> "http"
        request.getAuthority() >> null
        request.getPath() >> "/"

        when:
        new AlwaysFollowAndPreserveMethodRedirectStrategy().getLocationURI(request, response, context)

        then:
        // For unsafe methods with 301/302/303, the strategy upgrades to 307 to preserve the method
        response.getCode() == HttpStatus.SC_TEMPORARY_REDIRECT

        where:
        [httpMethod, redirect] << [['POST', 'PUT', 'DELETE', 'PATCH'], [301, 302, 303]].combinations()
    }

    def "should not modify redirect code for safe methods [#httpMethod,#redirect]"() {
        setup:
        HttpRequest request = Mock()
        HttpResponse response = new BasicHttpResponse(redirect)
        response.setHeader(new BasicHeader('location', 'http://redirectTo'))
        HttpContext context = Mock()
        request.getMethod() >> httpMethod
        request.getUri() >> new URI("http://original.com")
        request.getScheme() >> "http"
        request.getAuthority() >> null
        request.getPath() >> "/"

        when:
        new AlwaysFollowAndPreserveMethodRedirectStrategy().getLocationURI(request, response, context)

        then:
        // For safe methods, the redirect code is not modified
        response.getCode() == redirect

        where:
        [httpMethod, redirect] << [['GET', 'HEAD'], [301, 302, 303]].combinations()
    }
}
