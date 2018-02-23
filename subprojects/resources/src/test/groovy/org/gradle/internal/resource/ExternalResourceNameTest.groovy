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

package org.gradle.internal.resource

import org.gradle.util.Matchers
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import spock.lang.Shared
import spock.lang.Specification

class ExternalResourceNameTest extends Specification {
    @Shared
    def root = File.listRoots()[0]
    @Shared
    def base = new File(root, "base")

    def "can construct a resource name from URI and path"() {
        expect:
        def base = URI.create(baseUri)
        def name = new ExternalResourceName(base, path)
        name.uri.toASCIIString() == new URI(base.scheme, null, base.host, base.port, expectedPath, null, null).toASCIIString()
        name.path == expectedPath
        name.uri.path == expectedPath
        name.root.uri == URI.create(expectedRoot)
        name.root == name.root.root

        where:
        baseUri                      | path                                               | expectedRoot        | expectedPath
        "http://host/"               | "a/b/c.html"                                       | "http://host/"      | "/a/b/c.html"
        "http://host/"               | "/a/b/c"                                           | "http://host/"      | "/a/b/c"
        "http://host:8008"           | "/a/b/c"                                           | "http://host:8008/" | "/a/b/c"
        "http://host/"               | "/"                                                | "http://host/"      | "/"
        "http://host/a/b/c"          | ""                                                 | "http://host/"      | "/a/b/c"
        "http://host/a/b/c"          | "[123]"                                            | "http://host/"      | "/a/b/c/[123]"
        "http://host"                | "\u007b\u007f\u0080\u03b1\u07ff\u0800\u30b1\ufffe" | "http://host/"      | "/\u007b\u007f\u0080\u03b1\u07ff\u0800\u30b1\ufffe"
        "http://host"                | ":?#-.~_@"                                         | "http://host/"      | "/:?#-.~_@"
        this.base.toURI().toString() | "a/b/c"                                            | "file:/"            | this.base.toURI().path + "/a/b/c"
    }

    def "can construct a resource name from a file URI with host and a path"() {
        expect:
        def base = URI.create("file:////host/")
        def name = new ExternalResourceName(base, "/a/b/c")
        name.uri.toASCIIString() == new URI(base.scheme, null, base.host, base.port, "////host/a/b/c", null, null).toASCIIString()
        name.path == "/a/b/c"
        name.uri.path == "//host/a/b/c"
        name.root.uri == URI.create("file:////host/" )
        name.root == name.root.root
    }

    def "can construct a resource name from a path"() {
        expect:
        def name = new ExternalResourceName(path)
        name.uri.toASCIIString() == URI.create(expectedUri).toASCIIString()
        name.path == expectedPath
        name.root.uri == URI.create(expectedRoot)
        name.root == name.root.root

        where:
        path     | expectedUri | expectedRoot | expectedPath
        "a/b/c"  | "a/b/c"     | ""           | "a/b/c"
        "/a/b/c" | "/a/b/c"    | "/"          | "/a/b/c"
        "/"      | "/"         | "/"          | "/"
        "a:b"    | "a%3Ab"     | ""           | "a:b"
        "a%:b"   | "a%25%3Ab"  | ""           | "a%:b"
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

    def "short display name is last element of the path if present"() {
        expect:
        def name = new ExternalResourceName(URI.create(uri))
        name.shortDisplayName == expectedDisplayName

        where:
        uri               | expectedDisplayName
        "http://host:80"  | "http://host:80"
        "http://host/a/b" | "b"
        "http://host"     | "http://host"
        "a/b/c"           | "c"
        "file:/a/b/c"     | "c"
    }

    @Requires(TestPrecondition.WINDOWS)
    def "can handle UNC paths"() {
        expect:
        def name = new ExternalResourceName(uri)
        name.decoded == expectedDecoded
        name.resolve("").decoded == expectedDecoded

        where:
        uri                              | expectedDecoded
        URI.create("file:////ms/dist")   | "file:////ms/dist"
        new File('\\\\ms\\dist').toURI() | "file:////ms/dist"
    }

    def "has equals and hashcode"() {
        def name = new ExternalResourceName(URI.create("http://host"), "a/b/c")
        def same = new ExternalResourceName(URI.create("http://host"), "a/b/c")
        def samePath = new ExternalResourceName(URI.create("http://host/a/b"), "c")
        def differentRoot = new ExternalResourceName(URI.create("http://other"), "a/b/c")
        def differentPath = new ExternalResourceName(URI.create("http://host"), "x/y/z")
        def relative = new ExternalResourceName("a/b/c")
        def sameRelative = new ExternalResourceName("a/b/c")

        expect:
        name Matchers.strictlyEqual(same)
        name Matchers.strictlyEqual(samePath)
        relative Matchers.strictlyEqual(sameRelative)
        name != differentPath
        name != differentRoot
        name != relative
    }

    def "can resolve an absolute path"() {
        expect:
        def name = new ExternalResourceName(URI.create(uri)).resolve(path)
        name.uri == URI.create(expectedUri)

        where:
        uri                      | path           | expectedUri
        "http://host/a/b/c"      | "/z"           | "http://host/z"
        "http://host:8080/a/b/c" | "/path"        | "http://host:8080/path"
        "http://host:8080/a/b/c" | "/path/"       | "http://host:8080/path/"
        "http://host:8080/a/b/c" | "/a/./../path" | "http://host:8080/path"
        "http://host:8080/a/b/c" | "/a/b/../path" | "http://host:8080/a/path"
        base.toURI().toString()  | "/z"           | "file:/z"
        "/a/b/c"                 | "/z"           | "/z"
        "a/b/c"                  | "/z"           | "/z"
        "a/b/c"                  | "/"            | "/"
        "/"                      | "/a/b/c"       | "/a/b/c"
        ""                       | "/a/b/c"       | "/a/b/c"
        "//host"                 | "/a/b//c"      | "//host/a/b/c"
        "//host/a/b/c"           | "/z"           | "//host/z"
        "file:////host"          | "/a/b//c"      | "file:////host/a/b/c"
        "file:////host/a/b/c"    | "/z"           | "file:////host/z"
    }

    def "can resolve a relative path"() {
        expect:
        def name = new ExternalResourceName(URI.create(uri)).resolve(path)
        name.uri == URI.create(expectedUri)

        where:
        uri                      | path                  | expectedUri
        "http://host/a/b/c"      | "d"                   | "http://host/a/b/c/d"
        "http://host:8080/a/b/c" | "d/e"                 | "http://host:8080/a/b/c/d/e"
        "http://host:8080/a/b/c" | "d/e/"                | "http://host:8080/a/b/c/d/e/"
        "http://host:8080/a/b/c" | "."                   | "http://host:8080/a/b/c"
        "http://host:8080/a/b/c" | ".."                  | "http://host:8080/a/b"
        "http://host:8080/a/b/c" | "../../.."            | "http://host:8080/"
        "http://host:8080/a/b/c" | ".././.././z/../abc"  | "http://host:8080/a/abc"
        "http://host:8080/a/b/c" | "z/././//./../z/../d" | "http://host:8080/a/b/c/d"
        "http://host:8080/"      | "z/././//./../z/../d" | "http://host:8080/d"
        base.toURI().toString()  | "a/b/c"               | new File(base, "a/b/c").toURI().toString()
        "/a/b/c"                 | "z"                   | "/a/b/c/z"
        "/a//b/c"                | "z"                   | "/a/b/c/z"
        "a/b/c"                  | "z"                   | "a/b/c/z"
        "/"                      | "z"                   | "/z"
        ""                       | "z"                   | "z"
        "//host/a/b"             | "z"                   | "//host/a/b/z"
        "//host/a//b"            | "z"                   | "//host/a/b/z"
        "file:////host/a/b"      | "z"                   | "file:////host/a/b/z"
        "file:////host//a//b"    | "z"                   | "file:////host/a/b/z"
    }
}
