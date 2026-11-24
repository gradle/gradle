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

package org.gradle.internal.uri

import spock.lang.Specification

class UriDisplayTest extends Specification {

    def "masks the user info of '#uri'"() {
        expect:
        UriDisplay.maskUserInfo(new URI(uri)) == new URI(expected)
        UriDisplay.maskUserInfo(uri) == expected

        where:
        uri                                           | expected
        "http://user:secret@host/a/b"                 | "http://***@host/a/b"
        "https://user:secret@host:8443/a/b?q=1#frag"  | "https://***@host:8443/a/b?q=1#frag"
        "http://user@host/a/b"                        | "http://***@host/a/b"
        "http://:secret@host/a/b"                     | "http://***@host/a/b"
        "http://user:se%40cr%2Fet@host/a/b"           | "http://***@host/a/b"
        "http://user:se:cr;et@host/a%20b"             | "http://***@host/a%20b"
        "http://user:secret@host"                     | "http://***@host"
        "http://user:secret@host?q=a@b"               | "http://***@host?q=a@b"
        "http://user:secret@[::1]:8080/a"             | "http://***@[::1]:8080/a"
        "http://user:secret@host_name/a"              | "http://***@host_name/a"
        "sftp://user:secret@host:22/a"                | "sftp://***@host:22/a"
        "//user:secret@host/a"                        | "//***@host/a"
    }

    def "masks the user info of the URI nested in '#uri'"() {
        expect:
        UriDisplay.maskUserInfo(new URI(uri)) == new URI(expected)
        UriDisplay.maskUserInfo(uri) == expected

        where:
        uri                                         | expected
        "jar:https://user:secret@host/a.jar!/b"     | "jar:https://***@host/a.jar!/b"
        "jar:https://user:secret@host/a.jar!/b#c@d" | "jar:https://***@host/a.jar!/b#c@d"
        "jar:https://host/a.jar!/b@c"               | "jar:https://host/a.jar!/b@c"
        "jar:file:/a/b@c.jar!/d"                    | "jar:file:/a/b@c.jar!/d"
        "mailto:user@host"                          | "mailto:user@host"
    }

    def "leaves '#uri' unchanged"() {
        expect:
        UriDisplay.maskUserInfo(new URI(uri)) == new URI(uri)
        UriDisplay.maskUserInfo(uri) == uri

        where:
        uri << [
            "http://host/a/b",
            "http://host:80/a/b?q=1#frag",
            "http://host/a@b/c",
            "http://host/a/b?user=a@b",
            "http://host#a@b",
            "http://[::1]:8080/a",
            "file:/a/b@c",
            "file:///a/b@c",
            "file:////host/share/a@b",
            "file:/a//b@c/d",
            "a/b@c",
            "/a//b@c",
            "mailto:user@host",
            "urn:a//b@c",
            ""
        ]
    }

    def "returns a URI with the user info masked"() {
        expect:
        UriDisplay.maskUserInfo(new URI("http://user:se%40cret@host/a%20b?q=1")) == new URI("http://***@host/a%20b?q=1")

        and:
        def unchanged = new URI("http://host/a%20b?q=1")
        UriDisplay.maskUserInfo(unchanged).is(unchanged)

        and: "an authority made of user info only"
        UriDisplay.maskUserInfo(new URI("http://user:secret@")) == new URI("http://***@")

        and: "an already masked URI"
        def masked = new URI("http://***@host/a")
        UriDisplay.maskUserInfo(masked).is(masked)
    }

    def "masks the user info of a URI followed by a pattern"() {
        expect:
        UriDisplay.maskUserInfo("http://user:secret@host/repo/[organisation]/[module]@[revision]") == "http://***@host/repo/[organisation]/[module]@[revision]"
    }
}
