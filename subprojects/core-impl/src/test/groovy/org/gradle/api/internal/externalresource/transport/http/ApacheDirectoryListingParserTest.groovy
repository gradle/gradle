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
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Unroll

import static org.junit.Assert.assertNotNull

class ApacheDirectoryListingParserTest extends Specification {
    @Rule public final Resources resources = new Resources();

    private URI baseUrl = URI.create("http://testrepo/")
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
        def uris = parser.parse(html.bytes, null)
        assertNotNull(uris)
        uris.collect {it.toString()} == ["http://testrepo/directory1", "http://testrepo/directory2", "http://testrepo/directory3", "http://testrepo/directory4"]
    }

    @Unroll
    def "parse ignores #descr"() {
        expect:
        parser.parse("<a href=\"${href}\">link</a>".toString().bytes, null).isEmpty()
        where:
        href                                                | descr
        "http://anothertestrepo/"                           | "URLs which aren't children of base URL"
        "../"                                               | "links to parent URLs of base URL"
        "http://[2h:23:3]"                                  | "invalid URLs"
        "dir1/subdir1"                                      | "links to nested subdirectories"
        "<![CDATA[<a href=\"directory2\">directory2</a>]]>" | "links in CDATA blocks"
        "#achor"                                            | "anchor links"
        "<a name=\"anchorname\">headline</a>"               | "anchor definitions"
    }


    @Unroll
    def "parseLink handles #urlDescr"() {
        expect:
        def foundURIs = parser.parse("<a href=\"${href}\">link</a>".toString().bytes, null)
        !foundURIs.isEmpty()
        foundURIs.collect {it.toString()} == ["http://testrepo/directory1"]
        where:
        href                         | text           | urlDescr
        "directory1"                 | "directory1"   | "relative URLS"
        "/directory1"                | "directory1"   | "absolute URLS"
        "./directory1"               | "directory1"   | "explicit relative URLS"
        "http://testrepo/directory1" | "directory1"   | "complete URLS"
        "http://testrepo/directory1" | "direct..&gt;" | "hrefs with truncated text"
    }

    @Unroll
    def "parse is compatible with #repoType"() {
        setup:
        def byte[] content =  resources.getResource("${repoType}_dirlisting.html").bytes
        expect:
        List<URI> urls = new ApacheDirectoryListingParser(new URI(artifactRootURI)).parse(content, null)
        urls.collect {it.toString()} as Set == ["${artifactRootURI}3.7/",
                "${artifactRootURI}3.8/",
                "${artifactRootURI}3.8.1/",
                "${artifactRootURI}3.8.2/",
                "${artifactRootURI}4.0/",
                "${artifactRootURI}4.1/",
                "${artifactRootURI}4.10/",
                "${artifactRootURI}4.2/",
                "${artifactRootURI}4.3/",
                "${artifactRootURI}4.3.1/",
                "${artifactRootURI}4.4/",
                "${artifactRootURI}4.5/",
                "${artifactRootURI}4.6/",
                "${artifactRootURI}4.7/",
                "${artifactRootURI}4.8/",
                "${artifactRootURI}4.8.1/",
                "${artifactRootURI}4.8.2/",
                "${artifactRootURI}4.9/",
                "${artifactRootURI}maven-metadata.xml",
                "${artifactRootURI}maven-metadata.xml.md5",
                "${artifactRootURI}maven-metadata.xml.sha1"] as Set
        where:
        artifactRootURI                                                         | repoType
        "http://localhost:8081/artifactory/repo1/junit/junit/"                  | "artifactory"
        "http://repo1.maven.org/maven2/junit/junit/"                            | "mavencentral"
        "http://localhost:8081/nexus/content/repositories/central/junit/junit/" | "nexus"
    }
}
