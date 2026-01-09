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

package org.gradle.internal.resource.transport.http

import org.gradle.internal.resource.ExternalResourceName
import org.gradle.internal.resource.metadata.ExternalResourceMetaData
import spock.lang.Specification

class HttpResourceListerTest extends Specification {
    HttpResourceAccessor accessorMock = Mock()
    ExternalResourceMetaData metaData = Mock()
    HttpResourceLister lister = new HttpResourceLister(accessorMock)

    def "parses resource content"() {
        setup:
        def inputStream = new ByteArrayInputStream("<a href='child'/>".bytes)
        def name = new ExternalResourceName("http://testrepo/")

        when:
        lister.list(name)
        then:
        1 * accessorMock.withContent(name, true, _) >> {  uri, revalidate, action ->
            return action.execute(inputStream, metaData)
        }
        _ * metaData.getContentType() >> "text/html"
    }

    def "list returns null if HttpAccessor returns null"(){
        setup:
        accessorMock.openResource(new ExternalResourceName("http://testrepo/"), true, null) >> null
        expect:
        null == lister.list(new ExternalResourceName("http://testrepo"))
    }
}
