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
import spock.lang.Unroll

import static junit.framework.Assert.assertNotNull
import static org.junit.Assert.assertNull

class ApacheDirectoryListingParserTest extends Specification {

    private URL baseUrl = new URL("http://testrepo/")
    private ApacheDirectoryListingParser parser = new ApacheDirectoryListingParser(baseUrl);

    def "parse returns empty List if no link can be found"() {
        expect:
        List urls = parser.parse("<html>no link here</html>")
        assertNotNull(urls)
        urls.isEmpty()
    }

    def "parse handles multiple listed links"() {
        given:
        def html = """
        <a href="directory1">directory1</a>
        <a href="directory2">directory2</a>
        <a href="directory3">directory3</a>
        <a href="directory4">directory4</a>"""
        expect:
        List urls = parser.parse(html)
        assertNotNull(urls)
        urls.collect {it.toString()} == ["http://testrepo/directory1", "http://testrepo/directory2", "http://testrepo/directory3", "http://testrepo/directory4"]
    }

    @Unroll
    def "parseLink ignores #descr"() {
        expect:
        assertNull parser.parseLink(href, text)
        where:
        href                      | text         | descr
        "http://anothertestrepo/" | "directory1" | "URLs which aren't children of base URL"
        "../"                     | "directory1" | "links to parent URLs of base URL"
        "http://[2h:23:3]"        | "directory1" | "invalid URLs"
        "dir1/subdir1"            | "directory1" | "links to nested subdirectories"
    }


    @Unroll
    def "parseLink handles #urlDescr"() {
        expect:
        def url = parser.parseLink(href, text)
        assertNotNull url
        url.toString() == "http://testrepo/directory1"
        where:
        href                         | text           | urlDescr
        "directory1"                 | "directory1"   | "relative URLS"
        "/directory1"                | "directory1"   | "absolute URLS"
        "./directory1"               | "directory1"   | "absolute URLS"
        "http://testrepo/directory1" | "directory1"   | "complete URLS"
        "http://testrepo/directory1" | "direct..&gt;" | "hrefs with truncated text"
    }

    @Unroll
    def "#method returns null if input is null"() {
        expect:
        assertNull parser."$method"(null)
        where:
        method << ["stripBaseURL", "skipParentUrl", "convertRelativeHrefToUrl"]
    }
}
