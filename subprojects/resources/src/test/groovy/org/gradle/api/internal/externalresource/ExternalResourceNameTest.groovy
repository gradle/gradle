/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.api.internal.externalresource

import org.gradle.util.Matchers
import spock.lang.Shared
import spock.lang.Specification

class ExternalResourceNameTest extends Specification {
    @Shared
    def root = File.listRoots()[0]
    @Shared
    def base = new File(root, "base")

    def "can construct a resource name from URI and path"() {
        expect:
        def name = new ExternalResourceName(URI.create(baseUri), path)
        name.uri == URI.create(expectedUri)
        name.path == expectedPath
        name.root.uri == URI.create(expectedRoot)
        name.root == name.root.root

        where:
        baseUri                 | path     | expectedUri                                | expectedRoot            | expectedPath
        "http://host/"          | "a/b/c"  | "http://host/a/b/c"                        | "http://host"           | "/a/b/c"
        "http://host/"          | "/a/b/c" | "http://host/a/b/c"                        | "http://host"           | "/a/b/c"
        "http://host:8008"      | "/a/b/c" | "http://host:8008/a/b/c"                   | "http://host:8008"      | "/a/b/c"
        "http://host/"          | "/"      | "http://host/"                             | "http://host"           | "/"
        "http://host/a/b/c"     | ""       | "http://host/a/b/c"                        | "http://host"           | "/a/b/c"
        "http://host/a/b/c"     | "[123]"  | "http://host/a/b/c/%5B123%5D"              | "http://host"           | "/a/b/c/[123]"
        base.toURI().toString() | "a/b/c"  | new File(base, "a/b/c").toURI().toString() | root.toURI().toString() | "/base/a/b/c"
    }

    def "can construct a resource name from a relative path"() {
        expect:
        def name = new ExternalResourceName(path)
        name.uri == URI.create(expectedUri)
        name.path == path
        name.root.uri == new URI(null, null, expectedRoot, null)
        name.root == name.root.root

        where:
        path     | expectedUri | expectedRoot
        "a/b/c"  | "a/b/c"     | ""
        "/a/b/c" | "/a/b/c"    | "/"
        "/a/b/c" | "/a/b/c"    | "/"
        "[123]"  | "%5B123%5D" | ""
        "/"      | "/"         | "/"
        ""       | ""          | ""
    }

    def "decoded name is base uri plus path"() {
        expect:
        def name = new ExternalResourceName(URI.create(uri))
        name.decoded == expectedDecoded

        where:
        uri                          | expectedDecoded
        "http://host:80/a/%5B123%5D" | "http://host:80/a/[123]"
        "http://host/a/b"            | "http://host/a/b"
        "http://host"                | "http://host"
        "a/b/c"                      | "a/b/c"
        "file:/a/b/c"                | "file:/a/b/c"
    }

    def "has equals and hashcode"() {
        def name = new ExternalResourceName(URI.create("http://host"), "a/b/c")
        def same = new ExternalResourceName(URI.create("http://host"), "a/b/c")
        def samePath = new ExternalResourceName(URI.create("http://host/a/b"), "c")
        def differentRoot = new ExternalResourceName(URI.create("http://other"), "a/b/c")
        def differentPath = new ExternalResourceName(URI.create("http://host"), "x/y/z")

        expect:
        name Matchers.strictlyEqual(same)
        name Matchers.strictlyEqual(samePath)
        name != differentPath
        name != differentRoot
    }

    def "can resolve an absolute path"() {
        expect:
        def name = new ExternalResourceName(URI.create(uri)).resolve(path)
        name.uri == URI.create(expectedUri)

        where:
        uri                      | path     | expectedUri
        "http://host/a/b/c"      | "/z"     | "http://host/z"
        "http://host:8080/a/b/c" | "/path"  | "http://host:8080/path"
        base.toURI().toString()  | "/z"     | new File(root, "z").toURI().toString()
        "/a/b/c"                 | "/z"     | "/z"
        "a/b/c"                  | "/z"     | "/z"
        "a/b/c"                  | "/"      | "/"
        "/"                      | "/a/b/c" | "/a/b/c"
        ""                       | "/a/b/c" | "/a/b/c"
    }

    def "can resolve an relative path"() {
        expect:
        def name = new ExternalResourceName(URI.create(uri)).resolve(path)
        name.uri == URI.create(expectedUri)

        where:
        uri                      | path    | expectedUri
        "http://host/a/b/c"      | "d"     | "http://host/a/b/c/d"
        "http://host:8080/a/b/c" | "d/e"   | "http://host:8080/a/b/c/d/e"
        base.toURI().toString()  | "a/b/c" | new File(base, "a/b/c").toURI().toString()
        "/a/b/c"                 | "z"     | "/a/b/c/z"
        "a/b/c"                  | "z"     | "a/b/c/z"
        "/"                      | "z"     | "/z"
        ""                       | "z"     | "z"
    }
}
