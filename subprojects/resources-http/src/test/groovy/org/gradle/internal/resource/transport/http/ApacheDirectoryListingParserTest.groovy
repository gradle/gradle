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

import org.gradle.api.resources.ResourceException
import org.gradle.util.internal.Resources
import org.junit.Rule
import spock.lang.Specification

import static org.junit.Assert.assertNotNull

class ApacheDirectoryListingParserTest extends Specification {
    @Rule
    public final Resources resources = new Resources();

    private static final CONTENT_TYPE = "text/html;charset=utf-8";
    private URI baseUrl = URI.create("http://testrepo/")
    private ApacheDirectoryListingParser parser = new ApacheDirectoryListingParser();

    def "parse returns empty List if no link can be found"() {
        expect:
        List urls = parser.parse(baseUrl, new ByteArrayInputStream("<html>no link here</html>".bytes), CONTENT_TYPE)
        assertNotNull(urls)
        urls.isEmpty()
    }

    def "addTrailingSlashes adds trailing slashes on relative URL if not exist"() {
        expect:
        new URI(resultingURI) == parser.addTrailingSlashes(new URI(inputURI))
        where:
        inputURI                     | resultingURI
        "http://testrepo"            | "http://testrepo/"
        "http://testrepo/"           | "http://testrepo/"
        "http://testrepo/index.html" | "http://testrepo/index.html"
    }

    def "parse handles multiple listed links"() {
        def html = """
        <a href="directory1">directory1</a>
        <a href="directory2">directory2</a>
        <a href="directory3">directory3</a>
        <a href="directory4"/>"""
        expect:
        def uris = parser.parse(baseUrl, new ByteArrayInputStream(html.bytes), CONTENT_TYPE)
        assertNotNull(uris)
        uris.collect { it.toString() } == ["directory1", "directory2", "directory3", "directory4"]
    }

    def "only text/html content type is supported"() {
        def html = """
        <a href="directory1">directory1</a>
        <a href="directory2">directory2</a>"""
        when:
        parser.parse(baseUrl, new ByteArrayInputStream(html.bytes), contentType)
        then:
        thrown(ResourceException)
        where:
        contentType << ["text/plain", "application/octetstream"]
    }

    def "uses charset specified in content type"() {
        def html = """
        <a href="\u00c1\u00d2">directory1</a>
        """
        def encodedHtml = html.getBytes('ISO-8859-1')
        assert !Arrays.equals(encodedHtml, html.getBytes("utf-8"))

        expect:
        def uris = parser.parse(baseUrl, new ByteArrayInputStream(encodedHtml), 'text/html;charset=ISO-8859-1')
        uris.collect { it.toString() } == ["\u00c1\u00d2"]
    }

    def "defaults to utf-8 when no charset specified"() {
        def html = """
        <a href="\u0321\u0322">directory1</a>
        """
        def encodedHtml = html.getBytes('utf-8')

        expect:
        def uris = parser.parse(baseUrl, new ByteArrayInputStream(encodedHtml), 'text/html')
        uris.collect { it.toString() } == ["\u0321\u0322"]
    }

    def "parse ignores #descr"() {
        expect:
        parser.parse(baseUrl, new ByteArrayInputStream(href.bytes), CONTENT_TYPE).isEmpty()

        where:
        href                                                | descr
        "<a href=\"http://anothertestrepo\">link</a>/"      | "URLs which aren't children of base URL"
        "<a href=\"../\">link</a>"                          | "links to parent URLs of base URL"
        "<a href=\"http://[2h:23:3]\">link</a>"             | "invalid URLs"
        "<a href=\"dir1/subdir1\">link</a>"                 | "links to nested subdirectories"
        "<![CDATA[<a href=\"directory2\">directory2</a>]]>" | "links in CDATA blocks"
        "<a href=\"#anchor\">link</a>"                      | "anchor links"
        "<a name=\"anchorname\">headline</a>"               | "anchor definitions"
    }

    def "parseLink handles #urlDescr"() {
        def listingParser = new ApacheDirectoryListingParser()
        expect:
        def foundURIs = listingParser.parse(URI.create(baseUri), new ByteArrayInputStream("<a href=\"${href}\">link</a>".toString().bytes), CONTENT_TYPE)
        !foundURIs.isEmpty()
        foundURIs.collect { it.toString() } == ["directory1"]
        where:
        baseUri                | href                         | urlDescr
        "http://testrepo"      | "directory1"                 | "relative URLS"
        "http://testrepo"      | "/directory1"                | "absolute URLS"
        "http://testrepo"      | "./directory1"               | "explicit relative URLS"
        "http://testrepo"      | "directory1/"                | "trailing slash"
        "http://testrepo"      | "./directory1/"              | "relative URL with trailing slash"
        "http://testrepo"      | "http://testrepo/directory1" | "complete URLS"
        "http://testrepo"      | "http://testrepo/directory1" | "hrefs with truncated text"
        "http://testrepo"      | "http://testrepo/directory1" | "hrefs with truncated text"
        "http://[2001:db8::7]" | "directory1"                 | "ipv6 host with relative URLS"
        "http://[2001:db8::7]" | "./directory1"               | "ipv6 host with explicit relative URLS"
        "http://192.0.0.10"    | "directory1"                 | "ipv4 host with relative URLS"
        "http://192.0.0.10"    | "./directory1"               | "ipv4 host with relative URLS"
    }

    def "parse is compatible with #repoType"() {
        setup:
        def byte[] content = resources.getResource("${repoType}_dirlisting.html").bytes
        expect:
        List<String> urls = new ApacheDirectoryListingParser().parse(new URI(artifactRootURI), new ByteArrayInputStream(content), CONTENT_TYPE)
        urls as Set == ["3.7",
                        "3.8",
                        "3.8.1",
                        "3.8.2",
                        "4.0",
                        "4.1",
                        "4.10",
                        "4.2",
                        "4.3",
                        "4.3.1",
                        "4.4",
                        "4.5",
                        "4.6",
                        "4.7",
                        "4.8",
                        "4.8.1",
                        "4.8.2",
                        "4.9",
                        "maven-metadata.xml",
                        "maven-metadata.xml.md5",
                        "maven-metadata.xml.sha1"] as Set
        where:
        artifactRootURI                                                         | repoType
        "http://localhost:8081/artifactory/repo1/junit/junit/"                  | "artifactory"
        "https://repo.maven.apache.org/maven2/junit/junit/"                     | "mavencentral"
        "http://localhost:8081/nexus/content/repositories/central/junit/junit/" | "nexus"
    }

}
