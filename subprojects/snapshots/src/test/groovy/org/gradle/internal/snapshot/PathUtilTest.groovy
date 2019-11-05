/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.internal.snapshot

import spock.lang.Specification
import spock.lang.Unroll

import static org.gradle.internal.snapshot.PathUtil.compareChars
import static org.gradle.internal.snapshot.PathUtil.compareToChildOfOrThis
import static org.gradle.internal.snapshot.PathUtil.compareWithCommonPrefix
import static org.gradle.internal.snapshot.PathUtil.getFileName
import static org.gradle.internal.snapshot.PathUtil.sizeOfCommonPrefix

@Unroll
class PathUtilTest extends Specification {

    def "file name of '#path' is '#name'"() {
        expect:
        getFileName(path) == name

        where:
        path                                                      | name
        "/a/b/c"                                                  | "c"
        "/"                                                       | ""
        "C:${File.separator}some-name"                            | "some-name"
        ""                                                        | ""
        "C:${File.separator}Windows/system${File.separator}win32" | "win32"
    }

    def "common prefix of #prefix and #path at #offset is #result"() {
        expect:
        sizeOfCommonPrefix(prefix, path, offset) == result

        where:
        prefix       | path          | offset | result
        '/root'      | '/'           | 0      | 0
        '/root'      | '/root'       | 0      | 5
        '/root/some' | '/root/other' | 0      | 5
    }

    def "can compare size of common prefix"() {
        expect:
        Integer.signum(compareWithCommonPrefix(prefix, path, offset)) == result
        if (result) {
            assert Integer.signum(prefix <=> path.substring(offset)) == result
        }

        where:
        prefix              | path                    | offset | result
        "hello/world"       | "hello/other"           | 0      | 0
        "hello/world"       | "/var/hello/other"      | 5      | 0
        "hello/world"       | "/var/hello/world"      | 5      | 0
        "hello/world"       | "/var/hello\\world"      | 5      | 0
        "hello/world"       | "/var/hello/world/next" | 5      | 0
        "hello/world"       | "/var/hello1/other"     | 5      | -1
        "hello1/world"      | "/var/hello/other"      | 5      | 1
        "hello/world/some"  | "/var/hello/other"      | 5      | 0
        "hello/world"       | "/var/hello1/other"     | 5      | -1
        "bbc/some"          | "/var/abc/other"        | 5      | 1
        "/hello/world/some" | "/var/hello/other"      | 0      | -1
        "/hello/world/some" | "/var/hello/other"      | 4      | 0
    }

    def "size of common prefix of #prefix with #path at offset #offset is #result"() {
        expect:
        sizeOfCommonPrefix(prefix, path, offset) == result

        where:
        prefix              | path                    | offset | result
        "hello/world"       | "hello/other"           | 0      | 5
        "hello/world"       | "/var/hello/other"      | 5      | 5
        "hello/world"       | "/var/hello/world"      | 5      | 11
        "hello/world"       | "/var/hello\\world"     | 5      | 11
        "hello/world"       | "/var/hello/world/next" | 5      | 11
        "hello/world"       | "/var/hello1/other"     | 5      | 0
        "hello1/world"      | "/var/hello/other"      | 5      | 0
        "hello/world/some"  | "/var/hello/other"      | 5      | 5
        "hello/world"       | "/var/hello1/other"     | 5      | 0
        "bbc/some"          | "/var/abc/other"        | 5      | 0
        "/hello/world/some" | "/var/hello/other"      | 0      | 0
        "/hello/world/some" | "/var/hello/other"      | 4      | 6
    }

    def "#prefix is child of or this of #path at offset #offset: #result"() {
        expect:
        sizeOfCommonPrefix(prefix, path, offset)

        where:
        prefix         | path                       | offset | result
        "hello/world"  | "hello/world\\inside"      | 0      | true
        "hello/world"  | "/var/hello/world\\inside" | 5      | true
        "hello\\world" | "/var/hello/world\\inside" | 5      | true
    }

    def "separator is smaller than every other character"() {
        expect:
        Integer.signum(compareWithCommonPrefix(prefix, path, offset)) == result

        where:
        prefix              | path                    | offset | result
        "hello/world"       | "/var/hello-other"      | 5      | -1
    }

    def "can compare to child of this"() {
        expect:
        Integer.signum(compareToChildOfOrThis(prefix, path, offset)) == result
        if (result) {
            assert Integer.signum(prefix <=> path.substring(offset)) == result
        }

        where:
        prefix              | path                    | offset | result
        "hello/world"       | "hello/other"           | 0      | 1
        "hello/world"       | "/var/hello/other"      | 5      | 1
        "hello/world"       | "/var/hello/world"      | 5      | 0
        "hello/world"       | "/var/hello/world/next" | 5      | 0
        "hello/world"       | "/var/hello1/other"     | 5      | -1
        "hello1/world"      | "/var/hello/other"      | 5      | 1
        "hello/world/some"  | "/var/hello/other"      | 5      | 1
        "hello/world"       | "/var/hello/world1"     | 5      | -1
        "bbc/some"          | "/var/abc/other"        | 5      | 1
        "/hello/world/some" | "/var/hello/other"      | 0      | -1
        "/hello/world/some" | "/var/hello/other"      | 4      | 1
        "dir1"              | "some/dir12"            | 5      | -1
        "dir12"             | "some/dir1"             | 5      | 1
    }

    def "can compare path separator chars correctly"() {
        expect:
        compareChars(left as char, right as char) == result

        where:
        left | right | result
        'a'  | 'a'   | 0
        'a'  | 'b'   | -1
        'b'  | 'a'   | 1
        '/'  | 'a'   | -1
        'a'  | '/'   | 1
        '\\' | 'a'   | -1
        'a'  | '\\'  | 1
        '/'  | '/'   | 0
        '\\' | '\\'  | 0
        '/'  | '\\'  | 0
        '\\' | '/'   | 0
    }
}
