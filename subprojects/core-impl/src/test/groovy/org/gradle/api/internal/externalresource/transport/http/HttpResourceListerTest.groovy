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

import spock.lang.Specification

class HttpResourceListerTest extends Specification {

    HttpResourceAccessor accessorMock = Mock()
    HttpResponseResource externalResource = Mock()
    HttpResourceLister lister = new HttpResourceLister(accessorMock)

    def "consumeExternalResource closes resource after reading into stream"() {
        setup:
        accessorMock.getResource("http://testrepo/") >> externalResource;
        when:
        lister.list("http://testrepo/")
        then:
        1 * externalResource.writeTo(_) >> {OutputStream outputStream -> outputStream.write("<a href='child'/>".bytes)}
        1 * externalResource.getContentType() >> "text/html"
        1 * externalResource.close()
    }

    def "list returns null if HttpAccessor returns null"(){
        setup:
        accessorMock.getResource("http://testrepo/")  >> null
        expect:
        null == lister.list("http://testrepo")
    }
}
