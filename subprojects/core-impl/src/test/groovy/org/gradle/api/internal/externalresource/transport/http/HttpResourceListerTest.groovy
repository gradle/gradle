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

import org.gradle.api.internal.externalresource.ExternalResource
import spock.lang.Specification

class HttpResourceListerTest extends Specification {

    HttpResourceAccessor accessorMock = Mock(HttpResourceAccessor)
    ExternalResource externalResource = Mock(ExternalResource)
    HttpResourceLister lister = new HttpResourceLister(accessorMock)

    def "loadResourceContent adds trailing slashes to relative input URL before performing http request"() {
        when:
        lister.loadResourceContent(new URL("http://testrepo"))
        then:
        1 * accessorMock.getResource("http://testrepo/") >> externalResource
        1 * externalResource.writeTo(_, _)
        1 * externalResource.close()
    }

    def "loadResourceContent returns null when nested HttpResourceAccessor returns null for getResource"() {
        setup:
        1 * accessorMock.getResource("http://testrepo/") >> null;
        expect:
        null == lister.loadResourceContent(new URL("http://testrepo"))
    }
}
