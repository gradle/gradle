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
import static org.gradle.internal.snapshot.PathUtil.equalChars
import static org.gradle.internal.snapshot.PathUtil.getFileName
import static org.gradle.internal.snapshot.PathUtil.isChildOfOrThis
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
        CaseSensitivity.values().each {
            assert sizeOfCommonPrefix(prefix, path, offset, it) == result
        }

        where:
        prefix       | path          | offset | result
        '/root'      | '/'           | 0      | 0
        '/root'      | '/root'       | 0      | 5
        '/root/some' | '/root/other' | 0      | 5
    }

    def "can compare size of common prefix"() {
        expect:
        CaseSensitivity.values().each {
            assert Integer.signum(PathUtil.compareWithCommonPrefix(prefix, path, offset, it)) == result
        }
        if (result) {
            assert Integer.signum(prefix <=> path.substring(offset)) == result
        }

        where:
        prefix             | path                    | offset | result
        "hello/world"      | "hello/other"           | 0      | 0
        "hello/world"      | "/var/hello/other"      | 5      | 0
        "hello/world"      | "/var/hello/world"      | 5      | 0
        "hello/world"      | "/var/hello\\world"     | 5      | 0
        "hello/world"      | "/var/hello/world/next" | 5      | 0
        "hello/world"      | "/var/hello1/other"     | 5      | -1
        "hello1/world"     | "/var/hello/other"      | 5      | 1
        "hello/world/some" | "/var/hello/other"      | 5      | 0
        "hello/world"      | "/var/hello1/other"     | 5      | -1
        "bbc/some"         | "/var/abc/other"        | 5      | 1
        "hello/world/some" | "/var/hello/other"      | 1      | -1
        "hello/world/some" | "/var/hello/other"      | 5      | 0
    }

    def "size of common prefix of #prefix with #path at offset #offset is #result"() {
        expect:
        CaseSensitivity.values().each {
            assert sizeOfCommonPrefix(prefix, path, offset, it) == result
        }

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
        CaseSensitivity.values().each {
            assert isChildOfOrThis(path, offset, prefix, it)
            assert sizeOfCommonPrefix(prefix, path, offset, it) == prefix.length()
        }

        where:
        prefix         | path                       | offset | result
        "hello/world"  | "hello/world\\inside"      | 0      | true
        "hello/world"  | "/var/hello/world\\inside" | 5      | true
        "hello\\world" | "/var/hello/world\\inside" | 5      | true
    }

    def "separator is smaller than every other character"() {
        expect:
        CaseSensitivity.values().each {
            assert Integer.signum(PathUtil.compareWithCommonPrefix(prefix, path, offset, it)) == result
        }

        where:
        prefix              | path                    | offset | result
        "hello/world"       | "/var/hello-other"      | 5      | -1
    }

    def "can compare to child of this"() {
        expect:
        CaseSensitivity.values().each {
            assert Integer.signum(PathUtil.compareToChildOfOrThis(prefix, path, offset, it)) == result
        }
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

    def "equal chars are equal"() {
        expect:
        (Character.MIN_VALUE..Character.MAX_VALUE).each { currentChar ->
            CaseSensitivity.values().each {
                assert compareChars(currentChar as char, currentChar as char, it == CaseSensitivity.CASE_SENSITIVE) == 0
                assert equalChars(currentChar as char, currentChar as char, it == CaseSensitivity.CASE_SENSITIVE)
            }
        }
    }

    def "path separators are equal"() {
        def slash = '/' as char
        def backslash = '\\' as char
        expect:
        CaseSensitivity.values().each {
            def caseSensitive = it == CaseSensitivity.CASE_SENSITIVE
            assert compareChars(slash, backslash, caseSensitive) == 0
            assert compareChars(backslash, slash, caseSensitive) == 0
            assert equalChars(slash, backslash, caseSensitive)
            assert equalChars(backslash, slash, caseSensitive)
        }
    }

    def "can compare path separator chars correctly (#left - #right = #result)"() {
        def char1 = left as char
        def char2 = right as char
        expect:
        CaseSensitivity.values().each {
            def caseSensitive = it == CaseSensitivity.CASE_SENSITIVE
            assert compareChars(char1, char2, caseSensitive) == result
            assert compareChars(char2, char1, caseSensitive) == -result
            assert equalChars(char1, char2, caseSensitive) == (result == 0)
            assert equalChars(char2, char1, caseSensitive) == (result == 0)
        }

        where:
        left | right | result
        'a'  | 'a'   | 0
        'a'  | 'b'   | -1
        '/'  | 'a'   | -1
        '\\' | 'a'   | -1
        '/'  | '/'   | 0
        '\\' | '\\'  | 0
        '/'  | '\\'  | 0
        '/'  | 'A'   | -1
        '\\' | 'A'   | -1
    }

    def "can compare lower and upper case correctly (#left - #right = #result)"() {
        def char1 = left as char
        def char2 = right as char
        def caseInsensitiveResult = left.toUpperCase() == right.toUpperCase() ? 0 : result
        expect:
        compareChars(char1, char2, true) == result
        compareChars(char1, char2, false) == caseInsensitiveResult
        compareChars(char2, char1, true) == -result
        compareChars(char2, char1, false) == -caseInsensitiveResult
        !equalChars(char1, char2, true)
        equalChars(char1, char2, false) == (caseInsensitiveResult == 0)
        !equalChars(char2, char1, true)
        equalChars(char2, char1, false) == (caseInsensitiveResult == 0)

        where:
        left | right | result
        'a'  | 'A'   | 32
        'a'  | 'B'   | -1
        'A'  | 'B'   | -1
        'A'  | 'b'   | -1
        'a'  | 'b'   | -1
        'z'  | 'Z'   | 32
        'y'  | 'z'   | -1
        'Y'  | 'z'   | -1
        'Y'  | 'Z'   | -1
        'y'  | 'Z'   | -1
    }
}
