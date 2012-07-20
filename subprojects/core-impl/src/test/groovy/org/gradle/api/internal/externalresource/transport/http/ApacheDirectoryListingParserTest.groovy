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

import org.gradle.util.Resources
import spock.lang.Specification
import spock.lang.Unroll

import static junit.framework.Assert.assertNotNull
import static org.junit.Assert.assertNull
import org.junit.Rule

class ApacheDirectoryListingParserTest extends Specification {
    @Rule public final Resources resources = new Resources();

    private URL baseUrl = new URL("http://testrepo/")
    private ApacheDirectoryListingParser parser = new ApacheDirectoryListingParser(baseUrl);

    def "parse returns empty List if no link can be found"() {
        expect:
        List urls = parser.parse("<html>no link here</html>".bytes, null)
        assertNotNull(urls)
        urls.isEmpty()
    }

    def "parse handles multiple listed links"() {
        def html = """
        <a href="directory1">directory1</a>
        <a href="directory2">directory2</a>
        <a href="directory3">directory3</a>
        <a href="directory4">directory4</a>"""
        expect:
        List urls = parser.parse(html.bytes, null)
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

    @Unroll
    def "parse is compatible with #repoType"() {
        setup:
        def byte[] content =  resources.getResource("${repoType}_dirlisting.html").bytes
        expect:
        List<URL> urls = new ApacheDirectoryListingParser(new URL(artifactRootURL)).parse(content, null)
        urls.collect {it.toString()} as Set == ["${artifactRootURL}3.7/",
                "${artifactRootURL}3.8/",
                "${artifactRootURL}3.8.1/",
                "${artifactRootURL}3.8.2/",
                "${artifactRootURL}4.0/",
                "${artifactRootURL}4.1/",
                "${artifactRootURL}4.10/",
                "${artifactRootURL}4.2/",
                "${artifactRootURL}4.3/",
                "${artifactRootURL}4.3.1/",
                "${artifactRootURL}4.4/",
                "${artifactRootURL}4.5/",
                "${artifactRootURL}4.6/",
                "${artifactRootURL}4.7/",
                "${artifactRootURL}4.8/",
                "${artifactRootURL}4.8.1/",
                "${artifactRootURL}4.8.2/",
                "${artifactRootURL}4.9/",
                "${artifactRootURL}maven-metadata.xml",
                "${artifactRootURL}maven-metadata.xml.md5",
                "${artifactRootURL}maven-metadata.xml.sha1"] as Set
        where:
        artifactRootURL                                                         | repoType
        "http://localhost:8081/artifactory/repo1/junit/junit/"                  | "artifactory"
        "http://repo1.maven.org/maven2/junit/junit/"                            | "mavencentral"
        "http://localhost:8081/nexus/content/repositories/central/junit/junit/" | "nexus"
    }
}
