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
import spock.lang.Unroll

class HttpResourceListerTest extends Specification {

    def "loadResourceContent opens, reads and closes resource when reading"() {
        setup:
        def accessorMock = Mock(HttpResourceAccessor)
        def externalResource = Mock(ExternalResource)
        def lister = new HttpResourceLister(accessorMock)
        when:
        def content = lister.loadResourceContent(new URL("http://testrepo"))
        then:
        1 * accessorMock.getResource(_) >> externalResource
        1 * externalResource.openStream() >> new ByteArrayInputStream("resourceContent".getBytes())
        1 * externalResource.close()
        content == "resourceContent"
    }

    def "loadResourceContent adds trailing slashes to relative input URL before performing http request"() {
        setup:
        def accessorMock = Mock(HttpResourceAccessor)
        def externalResource = Mock(ExternalResource)
        def lister = new HttpResourceLister(accessorMock)
        1 * externalResource.openStream() >> new ByteArrayInputStream("resourceContent".getBytes())
        when:
        lister.loadResourceContent(new URL("http://testrepo"))
        then:
        accessorMock.getResource("http://testrepo/") >> externalResource
    }

    @Unroll
    def "parseHtml ignores #descr"() {
        given:
        def httpResourceLister = new HttpResourceLister(Mock(HttpResourceAccessor))
        expect:
        def urls = httpResourceLister.parseHtml(new URL("http://testrepo/"), "<a href=\"$href\">$text</a>" as String)
        urls.isEmpty()
        where:
        href                            | text         | descr
        "http://anothertestrepo/"       | "directory1" | "URLs which aren't children of base URL"
        "../"                           | "directory1" | "links to parent URLs of base URL"
        "http://[2h:23:3]"              | "directory1" | "invalid URLs"
        "dir1/subdir1"                  | "directory1" | "links to nested subdirectories"
    }

    @Unroll
    def "parseHtml handles #urlType URLs"() {
        given:
        def httpResourceLister = new HttpResourceLister(Mock(HttpResourceAccessor))
        expect:
        def urls = httpResourceLister.parseHtml(new URL("http://testrepo/"), "<a href=\"$href\">$text</a>" as String)
        urls.size() == 1
        urls.collect {it.toString()} == ["http://testrepo/directory1"]
        where:
        href                          | text           | urlType
        "directory1"                  | "directory1"   | "relative URLS"
        "/directory1"                 | "directory1"   | "absolute URLS"
        "./directory1"                | "directory1"   | "absolute URLS"
        "http://testrepo/directory1"  | "directory1"   | "complete URLS"
        "http://testrepo/directory1"  | "directory1"   | "complete URLS"
        "http://testrepo/directory1"  | "direct..&gt;" | "hrefs with truncated text"
    }
}
