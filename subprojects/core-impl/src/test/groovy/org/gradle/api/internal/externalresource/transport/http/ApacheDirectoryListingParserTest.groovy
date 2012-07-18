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

class ApacheDirectoryListingParserTest extends Specification {
    @Unroll
    def "ignores #descr"() {
        given:
        def baseUrl = new URL("http://testrepo/")
        ApacheDirectoryListingParser parser = new ApacheDirectoryListingParser(baseUrl);
        expect:
        def urls = parser.parse("<a href=\"$href\">$text</a>" as String)
        urls.isEmpty()
        where:
        href                      | text         | descr
        "http://anothertestrepo/" | "directory1" | "URLs which aren't children of base URL"
        "../"                     | "directory1" | "links to parent URLs of base URL"
        "http://[2h:23:3]"        | "directory1" | "invalid URLs"
        "dir1/subdir1"            | "directory1" | "links to nested subdirectories"
    }

    @Unroll
    def "handles #urlType URLs"() {
        given:
        def baseUrl = new URL("http://testrepo/")
        ApacheDirectoryListingParser parser = new ApacheDirectoryListingParser(baseUrl);
        expect:
        def urls = parser.parse("<a href=\"$href\">$text</a>" as String)
        urls.size() == 1
        urls.collect {it.toString()} == ["http://testrepo/directory1"]
        where:
        href                         | text           | urlType
        "directory1"                 | "directory1"   | "relative URLS"
        "/directory1"                | "directory1"   | "absolute URLS"
        "./directory1"               | "directory1"   | "absolute URLS"
        "http://testrepo/directory1" | "directory1"   | "complete URLS"
        "http://testrepo/directory1" | "directory1"   | "complete URLS"
        "http://testrepo/directory1" | "direct..&gt;" | "hrefs with truncated text"
    }
}
