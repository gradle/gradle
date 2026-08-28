/*
 * Copyright 2026 the original author or authors.
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

import spock.lang.Specification
import spock.lang.Timeout

class HtmlAnchorHrefScannerTest extends Specification {

    private HtmlAnchorHrefScanner scanner = new HtmlAnchorHrefScanner()

    def "collects hrefs in document order"() {
        expect:
        scanner.scan('<a href="one">1</a><p><a href="two">2</a></p><a href="three">3</a>') == ["one", "two", "three"]
    }

    def "finds anchors written as #descr"() {
        expect:
        scanner.scan(html) == ["directory1"]

        where:
        html                                              | descr
        '<a href="directory1">'                           | "double quoted values"
        "<a href='directory1'>"                           | "single quoted values"
        '<a href=directory1>'                             | "unquoted values"
        '<a href = "directory1" >'                        | "spaces around the equals sign"
        '<A HREF="directory1">'                           | "upper case names"
        '<a href="directory1"/>'                          | "self closing tags"
        '<a class="link" href="directory1" rel="nofollow">' | "other attributes"
        '<a download href="directory1">'                  | "valueless attributes"
        '<a\nhref="directory1">'                          | "newlines between attributes"
        '<a href="directory1"'                            | "an unterminated tag"
    }

    def "ignores #descr"() {
        expect:
        scanner.scan(html).isEmpty()

        where:
        html                                                    | descr
        '<a name="anchorname">headline</a>'                     | "anchors without an href"
        '<p href="directory1">'                                 | "hrefs on other elements"
        '<!-- <a href="directory1"> -->'                        | "anchors inside comments"
        '<![CDATA[<a href="directory1">]]>'                     | "anchors inside CDATA sections"
        '<script>var l = \'<a href="directory1">\';</script>'   | "anchors inside script"
        '<style>/* <a href="directory1"> */</style>'            | "anchors inside style"
        '<textarea><a href="directory1"></textarea>'            | "anchors inside textarea"
        '<title><a href="directory1"></title>'                  | "anchors inside title"
        'a < b and <a> without an href'                         | "a bare less-than sign"
        ''                                                      | "empty content"
    }

    def "keeps scanning after a raw text element"() {
        expect:
        scanner.scan('<script>ignored <a href="no"></script><a href="yes">') == ["yes"]
        scanner.scan('<title>Index of /repo</title><a href="yes">') == ["yes"]
    }

    def "recovers the remaining links when a raw text element is never closed"() {
        expect:
        scanner.scan('<script><a href="directory1">') == ["directory1"]
        scanner.scan('<title>Index of <a href="directory1">') == ["directory1"]
    }

    def "reads a valueless href as an empty href"() {
        expect:
        scanner.scan(html) == [""]

        where:
        html << ['<a href>', '<a href >', '<a href=>', '<a href="">']
    }

    def "resolves #descr in href values"() {
        expect:
        scanner.scan("<a href=\"$href\">") == [resolved]

        where:
        href              | resolved     | descr
        'a&amp;b'         | 'a&b'        | "named references"
        'a&#38;b'         | 'a&b'        | "decimal references"
        'a&#x26;b'        | 'a&b'        | "hexadecimal references"
        'a&quot;b'        | 'a"b'        | "quote references"
        'a&b=c'           | 'a&b=c'      | "an unescaped ampersand"
        'a&unknown;b'     | 'a&unknown;b'| "unrecognised references"
    }

    def "uses the first href when an anchor repeats the attribute"() {
        expect:
        scanner.scan('<a href="first" href="second">') == ["first"]
    }

    /**
     * Rescanning the rest of the document for each unclosed tag would take minutes here.
     */
    @Timeout(30)
    def "does not rescan the document for each unclosed raw text element"() {
        given:
        def html = '<script>' * 200000 + '<a href="directory1">'

        expect:
        scanner.scan(html) == ["directory1"]
    }
}
