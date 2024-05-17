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

import org.apache.http.HttpRequest
import org.apache.http.ProtocolVersion
import org.apache.http.RequestLine
import org.apache.http.client.methods.CloseableHttpResponse
import org.apache.http.message.BasicHeader
import org.apache.http.message.BasicStatusLine
import org.apache.http.protocol.HttpContext
import spock.lang.Specification

class AllowFollowForMutatingMethodRedirectStrategyTest extends Specification {

    private static final List<String> NON_MUTATING_HTTP_METHODS = ["HEAD", "GET"]
    private static final List<String> MUTATING_HTTP_METHODS = ["POST", "PUT", "DELETE", "OPTIONS", "TRACE", "PATCH"]
    private static final List<String> HTTP_METHODS = NON_MUTATING_HTTP_METHODS + MUTATING_HTTP_METHODS

    private static final List<Integer> NON_METHOD_PRESERVING_REDIRECTS = [301, 302, 303]
    private static final List<Integer> METHOD_PRESERVING_REDIRECTS = [307, 308]
    private static final List<Integer> REDIRECTS = NON_METHOD_PRESERVING_REDIRECTS + METHOD_PRESERVING_REDIRECTS

    def strategy = new AllowFollowForMutatingMethodRedirectStrategy()

    def "should consider all requests redirectable"() {
        expect:
        strategy.isRedirectable(method)

        where:
        method << HTTP_METHODS
    }

    def "should redirect for http method [#httpMethod,#redirect]"(String httpMethod, int redirect) {
        setup:
        HttpRequest request = Mock()
        CloseableHttpResponse response = Mock()
        HttpContext context = Mock()
        response.getFirstHeader("location") >> new BasicHeader('location', 'http://redirectTo')
        response.getStatusLine() >> new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), redirect, "ignored")
        request.getRequestLine() >> Mock(RequestLine) {
            getMethod() >> httpMethod
            getUri() >> "http://original.com"
        }

        when:
        def redirectRequest = strategy.getRedirect(request, response, context)

        then:
        def expectedMethod = redirect in METHOD_PRESERVING_REDIRECTS ? httpMethod : httpMethod == "HEAD" ? "HEAD" : "GET"
        redirectRequest.method.toUpperCase() == expectedMethod

        where:
        [httpMethod, redirect] << [HTTP_METHODS, REDIRECTS].combinations()
    }

}
