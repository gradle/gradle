/*
 * Copyright 2021 the original author or authors.
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
import org.apache.hc.core5.http.message.BasicHeader
import org.apache.hc.core5.http.message.BasicHttpResponse
import org.apache.hc.core5.http.protocol.HttpContext
import spock.lang.Specification

class AllowFollowForMutatingMethodRedirectStrategyTest extends Specification {

    private static final List<String> NON_MUTATING_HTTP_METHODS = ["HEAD", "GET"]
    private static final List<String> MUTATING_HTTP_METHODS = ["POST", "PUT", "DELETE", "OPTIONS", "TRACE", "PATCH"]
    private static final List<String> HTTP_METHODS = NON_MUTATING_HTTP_METHODS + MUTATING_HTTP_METHODS

    private static final List<Integer> REDIRECTS = [301, 302, 303, 307, 308]

    def strategy = new AllowFollowForMutatingMethodRedirectStrategy()

    def "should redirect for http method [#httpMethod,#redirect]"(String httpMethod, int redirect) {
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

        expect:
        strategy.isRedirected(request, response, context)

        where:
        [httpMethod, redirect] << [HTTP_METHODS, REDIRECTS].combinations()
    }

}
