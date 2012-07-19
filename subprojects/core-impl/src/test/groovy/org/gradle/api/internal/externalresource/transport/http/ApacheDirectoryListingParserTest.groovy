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

    @Unroll
    def "parse is compatible with #repoType"(){
        expect:
        List<URL> urls = new ApacheDirectoryListingParser(new URL(artifactRootURL)).parse(capture)
        //(urls.collect{it.toString()})
        urls.collect{it.toString()} as Set == ["${artifactRootURL}3.7/",
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
        capture                        | artifactRootURL                                                            |repoType
        capturedListingFromArtifactory | "http://localhost:8081/artifactory/repo1/junit/junit/"                   |"artifactory"
        capturedListingFromMavenCentral| "http://repo1.maven.org/maven2/junit/junit/"                             |"maven central"
        captureListingFromNexus        | "http://localhost:8081/nexus/content/repositories/central/junit/junit/"  |"nexus"
    }

    static String capturedListingFromMavenCentral ="""<html>
    <head><title>Index of /maven2/junit/junit/</title></head>
    <body bgcolor="white">
    <h1>Index of /maven2/junit/junit/</h1><hr><pre><a href="../">../</a>
    <a href="3.7/">3.7/</a>                                               07-Dec-2010 15:34                   -
    <a href="3.8/">3.8/</a>                                               07-Dec-2010 15:34                   -
    <a href="3.8.1/">3.8.1/</a>                                             07-Dec-2010 15:34                   -
    <a href="3.8.2/">3.8.2/</a>                                             07-Dec-2010 15:34                   -
    <a href="4.0/">4.0/</a>                                               07-Dec-2010 15:34                   -
    <a href="4.1/">4.1/</a>                                               07-Dec-2010 15:34                   -
    <a href="4.10/">4.10/</a>                                              29-Sep-2011 19:19                   -
    <a href="4.2/">4.2/</a>                                               07-Dec-2010 15:34                   -
    <a href="4.3/">4.3/</a>                                               07-Dec-2010 15:34                   -
    <a href="4.3.1/">4.3.1/</a>                                             07-Dec-2010 15:34                   -
    <a href="4.4/">4.4/</a>                                               07-Dec-2010 15:34                   -
    <a href="4.5/">4.5/</a>                                               07-Dec-2010 15:34                   -
    <a href="4.6/">4.6/</a>                                               07-Dec-2010 15:34                   -
    <a href="4.7/">4.7/</a>                                               07-Dec-2010 15:34                   -
    <a href="4.8/">4.8/</a>                                               07-Dec-2010 15:34                   -
    <a href="4.8.1/">4.8.1/</a>                                             07-Dec-2010 15:34                   -
    <a href="4.8.2/">4.8.2/</a>                                             07-Dec-2010 15:34                   -
    <a href="4.9/">4.9/</a>                                               24-Aug-2011 11:32                   -
    <a href="maven-metadata.xml">maven-metadata.xml</a>                                 29-Sep-2011 19:19                 817
    <a href="maven-metadata.xml.md5">maven-metadata.xml.md5</a>                             29-Sep-2011 19:19                  32
    <a href="maven-metadata.xml.sha1">maven-metadata.xml.sha1</a>                            29-Sep-2011 19:19                  40
    </pre><hr></body>
    </html>"""

    static String capturedListingFromArtifactory = """<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 3.2 Final//EN">
<html>
<head>
<title>Index of repo1/junit/junit</title>
</head>
<body>
<h1>Index of repo1/junit/junit</h1>
<pre>Name                     Last modified      Size</pre>
<hr/>
<pre><a href="../">../</a>
<a href="3.7/"">3.7/</a>->                        -    -
<a href="3.8/"">3.8/</a>->                        -    -
<a href="3.8.1/"">3.8.1/</a>                    02-Jul-2012 13:40    -
<a href="3.8.2/"">3.8.2/</a>->                      -    -
<a href="4.0/"">4.0/</a>->                        -    -
<a href="4.1/"">4.1/</a>->                        -    -
<a href="4.10/"">4.10/</a>                     19-Jul-2012 09:46    -
<a href="4.2/"">4.2/</a>->                        -    -
<a href="4.3/"">4.3/</a>->                        -    -
<a href="4.3.1/"">4.3.1/</a>->                      -    -
<a href="4.4/"">4.4/</a>->                        -    -
<a href="4.5/"">4.5/</a>->                        -    -
<a href="4.6/"">4.6/</a>->                        -    -
<a href="4.7/"">4.7/</a>                      05-Jul-2012 09:00    -
<a href="4.8/"">4.8/</a>->                        -    -
<a href="4.8.1/"">4.8.1/</a>->                      -    -
<a href="4.8.2/"">4.8.2/</a>->                      -    -
<a href="4.9/"">4.9/</a>->                        -    -
<a href="maven-metadata.xml">maven-metadata.xml</a>->         -    -
<a href="maven-metadata.xml.md5">maven-metadata.xml.md5</a>->     -    -
<a href="maven-metadata.xml.sha1">maven-metadata.xml.sha1</a>->    -    -
</pre>
<hr/>
<address style="font-size:small;">Artifactory/2.6.1 Server at localhost Port 8081</address>
</body>
</html>"""
    static String captureListingFromNexus = """<!--

    Sonatype Nexus (TM) Open Source Version
    Copyright (c) 2007-2012 Sonatype, Inc.
    All rights reserved. Includes the third-party code listed at http://links.sonatype.com/products/nexus/oss/attributions.

    This program and the accompanying materials are made available under the terms of the Eclipse Public License Version 1.0,
    which accompanies this distribution and is available at http://www.eclipse.org/legal/epl-v10.html.

    Sonatype Nexus (TM) Professional Version is available from Sonatype, Inc. "Sonatype" and "Sonatype Nexus" are trademarks
    of Sonatype, Inc. Apache Maven is a trademark of the Apache Software Foundation. M2eclipse is a trademark of the
    Eclipse Foundation. All other trademarks are the property of their respective owners.

-->
<html>
  <head>
    <title>Index of /nexus/content/repositories/central/junit/junit/</title>
    <meta http-equiv="Content-Type" content="text/html; charset=utf-8"/>
    <link rel="stylesheet" href="http://localhost:8081/nexus//style/Sonatype-content.css?2.0.6" type="text/css" media="screen" title="no title" charset="utf-8">
  </head>
  <body>
    <h1>Index of /nexus/content/repositories/central/junit/junit/</h1>
    <table cellspacing="10">
      <tr>
        <th align="left">Name</th>
        <th>Last Modified</th>
        <th>Size</th>
        <th>Description</th>
      </tr>
      <tr>
        <td>
          <a href="../">Parent Directory</a>
        </td>
      </tr>
                  <tr>
            <td>
                              <a href="http://localhost:8081/nexus/content/repositories/central/junit/junit/maven-metadata.xml">maven-metadata.xml</a>
                          </td>
            <td>
              Thu Sep 29 21:19:50 CEST 2011
            </td>
            <td align="right">
                              817
                          </td>
            <td>
              &nbsp;
            </td>
          </tr>
                  <tr>
            <td>
                              <a href="http://localhost:8081/nexus/content/repositories/central/junit/junit/maven-metadata.xml.sha1">maven-metadata.xml.sha1</a>
                          </td>
            <td>
              Thu Sep 29 21:19:50 CEST 2011
            </td>
            <td align="right">
                              40
                          </td>
            <td>
              &nbsp;
            </td>
          </tr>
                  <tr>
            <td>
                              <a href="http://localhost:8081/nexus/content/repositories/central/junit/junit/maven-metadata.xml.md5">maven-metadata.xml.md5</a>
                          </td>
            <td>
              Thu Sep 29 21:19:50 CEST 2011
            </td>
            <td align="right">
                              40
                          </td>
            <td>
              &nbsp;
            </td>
          </tr>
                  <tr>
            <td>
                              <a href="http://localhost:8081/nexus/content/repositories/central/junit/junit/3.7/">3.7/</a>
                          </td>
            <td>
              Thu Jul 19 10:51:57 CEST 2012
            </td>
            <td align="right">
                              &nbsp;
                          </td>
            <td>
              &nbsp;
            </td>
          </tr>
                  <tr>
            <td>
                              <a href="http://localhost:8081/nexus/content/repositories/central/junit/junit/3.8/">3.8/</a>
                          </td>
            <td>
              Thu Jul 19 10:51:57 CEST 2012
            </td>
            <td align="right">
                              &nbsp;
                          </td>
            <td>
              &nbsp;
            </td>
          </tr>
                  <tr>
            <td>
                              <a href="http://localhost:8081/nexus/content/repositories/central/junit/junit/3.8.1/">3.8.1/</a>
                          </td>
            <td>
              Thu Jul 19 10:51:58 CEST 2012
            </td>
            <td align="right">
                              &nbsp;
                          </td>
            <td>
              &nbsp;
            </td>
          </tr>
                  <tr>
            <td>
                              <a href="http://localhost:8081/nexus/content/repositories/central/junit/junit/3.8.2/">3.8.2/</a>
                          </td>
            <td>
              Thu Jul 19 10:51:58 CEST 2012
            </td>
            <td align="right">
                              &nbsp;
                          </td>
            <td>
              &nbsp;
            </td>
          </tr>
                  <tr>
            <td>
                              <a href="http://localhost:8081/nexus/content/repositories/central/junit/junit/4.0/">4.0/</a>
                          </td>
            <td>
              Thu Jul 19 10:51:59 CEST 2012
            </td>
            <td align="right">
                              &nbsp;
                          </td>
            <td>
              &nbsp;
            </td>
          </tr>
                  <tr>
            <td>
                              <a href="http://localhost:8081/nexus/content/repositories/central/junit/junit/4.1/">4.1/</a>
                          </td>
            <td>
              Thu Jul 19 10:51:59 CEST 2012
            </td>
            <td align="right">
                              &nbsp;
                          </td>
            <td>
              &nbsp;
            </td>
          </tr>
                  <tr>
            <td>
                              <a href="http://localhost:8081/nexus/content/repositories/central/junit/junit/4.10/">4.10/</a>
                          </td>
            <td>
              Thu Jul 19 10:25:49 CEST 2012
            </td>
            <td align="right">
                              &nbsp;
                          </td>
            <td>
              &nbsp;
            </td>
          </tr>
                  <tr>
            <td>
                              <a href="http://localhost:8081/nexus/content/repositories/central/junit/junit/4.2/">4.2/</a>
                          </td>
            <td>
              Thu Jul 19 10:52:00 CEST 2012
            </td>
            <td align="right">
                              &nbsp;
                          </td>
            <td>
              &nbsp;
            </td>
          </tr>
                  <tr>
            <td>
                              <a href="http://localhost:8081/nexus/content/repositories/central/junit/junit/4.3/">4.3/</a>
                          </td>
            <td>
              Thu Jul 19 10:52:01 CEST 2012
            </td>
            <td align="right">
                              &nbsp;
                          </td>
            <td>
              &nbsp;
            </td>
          </tr>
                  <tr>
            <td>
                              <a href="http://localhost:8081/nexus/content/repositories/central/junit/junit/4.3.1/">4.3.1/</a>
                          </td>
            <td>
              Thu Jul 19 10:52:01 CEST 2012
            </td>
            <td align="right">
                              &nbsp;
                          </td>
            <td>
              &nbsp;
            </td>
          </tr>
                  <tr>
            <td>
                              <a href="http://localhost:8081/nexus/content/repositories/central/junit/junit/4.4/">4.4/</a>
                          </td>
            <td>
              Thu Jul 19 10:52:02 CEST 2012
            </td>
            <td align="right">
                              &nbsp;
                          </td>
            <td>
              &nbsp;
            </td>
          </tr>
                  <tr>
            <td>
                              <a href="http://localhost:8081/nexus/content/repositories/central/junit/junit/4.5/">4.5/</a>
                          </td>
            <td>
              Thu Jul 19 10:52:02 CEST 2012
            </td>
            <td align="right">
                              &nbsp;
                          </td>
            <td>
              &nbsp;
            </td>
          </tr>
                  <tr>
            <td>
                              <a href="http://localhost:8081/nexus/content/repositories/central/junit/junit/4.6/">4.6/</a>
                          </td>
            <td>
              Thu Jul 19 10:52:03 CEST 2012
            </td>
            <td align="right">
                              &nbsp;
                          </td>
            <td>
              &nbsp;
            </td>
          </tr>
                  <tr>
            <td>
                              <a href="http://localhost:8081/nexus/content/repositories/central/junit/junit/4.7/">4.7/</a>
                          </td>
            <td>
              Thu Jul 19 10:52:03 CEST 2012
            </td>
            <td align="right">
                              &nbsp;
                          </td>
            <td>
              &nbsp;
            </td>
          </tr>
                  <tr>
            <td>
                              <a href="http://localhost:8081/nexus/content/repositories/central/junit/junit/4.8/">4.8/</a>
                          </td>
            <td>
              Thu Jul 19 10:43:51 CEST 2012
            </td>
            <td align="right">
                              &nbsp;
                          </td>
            <td>
              &nbsp;
            </td>
          </tr>
                  <tr>
            <td>
                              <a href="http://localhost:8081/nexus/content/repositories/central/junit/junit/4.8.1/">4.8.1/</a>
                          </td>
            <td>
              Thu Jul 19 10:45:17 CEST 2012
            </td>
            <td align="right">
                              &nbsp;
                          </td>
            <td>
              &nbsp;
            </td>
          </tr>
                  <tr>
            <td>
                              <a href="http://localhost:8081/nexus/content/repositories/central/junit/junit/4.8.2/">4.8.2/</a>
                          </td>
            <td>
              Thu Jul 19 10:45:17 CEST 2012
            </td>
            <td align="right">
                              &nbsp;
                          </td>
            <td>
              &nbsp;
            </td>
          </tr>
                  <tr>
            <td>
                              <a href="http://localhost:8081/nexus/content/repositories/central/junit/junit/4.9/">4.9/</a>
                          </td>
            <td>
              Thu Jul 19 10:43:51 CEST 2012
            </td>
            <td align="right">
                              &nbsp;
                          </td>
            <td>
              &nbsp;
            </td>
          </tr>
            </table>
  </body>
</html>"""
}
