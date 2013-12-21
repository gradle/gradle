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

import org.gradle.api.internal.resource.ResourceException;
import org.gradle.util.Resources
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Unroll

import static org.junit.Assert.assertNotNull

class ApacheDirectoryListingParserTest extends Specification {
    @Rule public final Resources resources = new Resources();

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
        uris.collect {it.toString()} == ["http://testrepo/directory1", "http://testrepo/directory2", "http://testrepo/directory3", "http://testrepo/directory4"]
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
        uris.collect {it.toString()} == ["http://testrepo/\u00c1\u00d2"]
    }

    def "defaults to utf-8 when no charset specified"() {
        def html = """
        <a href="\u0321\u0322">directory1</a>
        """
        def encodedHtml = html.getBytes('utf-8')

        expect:
        def uris = parser.parse(baseUrl, new ByteArrayInputStream(encodedHtml), 'text/html')
        uris.collect {it.toString()} == ["http://testrepo/\u0321\u0322"]
    }

    @Unroll
    def "parse ignores #descr"() {
        expect:
        parser.parse(baseUrl, new ByteArrayInputStream("<a href=\"${href}\">link</a>".toString().bytes), CONTENT_TYPE).isEmpty()
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
        def listingParser = new ApacheDirectoryListingParser()
        expect:
        def foundURIs = listingParser.parse(URI.create(baseUri), new ByteArrayInputStream("<a href=\"${href}\">link</a>".toString().bytes), CONTENT_TYPE)
        !foundURIs.isEmpty()
        foundURIs.collect {it.toString()} == ["${baseUri}/directory1"]
        where:
        baseUri                | href                         | urlDescr
        "http://testrepo"      | "directory1"                 | "relative URLS"
        "http://testrepo"      | "/directory1"                | "absolute URLS"
        "http://testrepo"      | "./directory1"               | "explicit relative URLS"
        "http://testrepo"      | "http://testrepo/directory1" | "complete URLS"
        "http://testrepo"      | "http://testrepo/directory1" | "hrefs with truncated text"
        "http://testrepo"      | "http://testrepo/directory1" | "hrefs with truncated text"
        "http://[2001:db8::7]" | "directory1"                 | "ipv6 host with relative URLS"
        "http://[2001:db8::7]" | "./directory1"               | "ipv6 host with explicit relative URLS"
        "http://192.0.0.10"    | "directory1"                 | "ipv4 host with relative URLS"
        "http://192.0.0.10"    | "./directory1"               | "ipv4 host with relative URLS"
    }

    @Unroll
    def "parse is compatible with #repoType"() {
        setup:
        def byte[] content = resources.getResource("${repoType}_dirlisting.html").bytes
        expect:
        List<URI> urls = new ApacheDirectoryListingParser().parse(new URI(artifactRootURI), new ByteArrayInputStream(content), CONTENT_TYPE)
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
