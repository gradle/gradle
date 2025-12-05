/*
 * Copyright 2016 the original author or authors.
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

import com.google.common.collect.ImmutableMap
import org.gradle.internal.resource.ExternalResourceName
import spock.lang.Specification

class HttpResourceAccessorTest extends Specification {
    def uri = new URI("http://somewhere")
    def name = new ExternalResourceName(uri)

    def "should call close() on CloseableHttpResource when getMetaData is called"() {
        def response = mockHttpResponse()
        def client = Mock(HttpClient) {
            performHead(_, _) >> response
        }

        when:
        new HttpResourceAccessor(client).getMetaData(name, false)

        then:
        1 * response.close()
    }

    def "request with revalidate adds Cache-Control header"() {
        def client = Mock(HttpClient)

        when:
        new HttpResourceAccessor(client).getMetaData(name, true)

        then:
        1 * client.performHead(_, ImmutableMap.of("Cache-Control", "max-age=0")) >> mockHttpResponse()

        when:
        new HttpResourceAccessor(client).openResource(name, true)

        then:
        1 * client.performGet(_, ImmutableMap.of("Cache-Control", "max-age=0")) >> mockHttpResponse()
    }

    private HttpClient.Response mockHttpResponse() {
        Mock(HttpClient.Response) {
            getStatusCode() >> 200
        }
    }

}
