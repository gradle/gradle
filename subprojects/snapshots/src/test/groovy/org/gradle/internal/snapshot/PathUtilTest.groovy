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
import static org.gradle.internal.snapshot.PathUtil.equalChars
import static org.gradle.internal.snapshot.PathUtil.getFileName
import static org.gradle.internal.snapshot.PathUtil.isChildOfOrThis
import static org.gradle.internal.snapshot.PathUtil.sizeOfCommonPrefix

@Unroll
class PathUtilTest extends Specification {
    private static final List<Character> LETTERS = (('a'..'z') + ('A'..'Z')).collect { it.charAt(0) }
    private static final List<Character> NON_LETTER = (Character.MIN_VALUE..Character.MAX_VALUE).minus(LETTERS)

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
        sizeOfCommonPrefix(prefix, path, offset, true) == result
        sizeOfCommonPrefix(prefix, path, offset, false) == result

        where:
        prefix       | path          | offset | result
        '/root'      | '/'           | 0      | 0
        '/root'      | '/root'       | 0      | 5
        '/root/some' | '/root/other' | 0      | 5
    }

    def "can compare size of common prefix"() {
        expect:
        Integer.signum(compareWithCommonPrefix(prefix, path, offset, true)) == result
        Integer.signum(compareWithCommonPrefix(prefix, path, offset, false)) == result
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
        sizeOfCommonPrefix(prefix, path, offset, true) == result
        sizeOfCommonPrefix(prefix, path, offset, false) == result

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
        isChildOfOrThis(path, offset, prefix, true)
        isChildOfOrThis(path, offset, prefix, false)
        sizeOfCommonPrefix(prefix, path, offset, true) == prefix.length()
        sizeOfCommonPrefix(prefix, path, offset, false) == prefix.length()

        where:
        prefix         | path                       | offset | result
        "hello/world"  | "hello/world\\inside"      | 0      | true
        "hello/world"  | "/var/hello/world\\inside" | 5      | true
        "hello\\world" | "/var/hello/world\\inside" | 5      | true
    }

    def "separator is smaller than every other character"() {
        expect:
        Integer.signum(compareWithCommonPrefix(prefix, path, offset, true)) == result
        Integer.signum(compareWithCommonPrefix(prefix, path, offset, false)) == result

        where:
        prefix              | path                    | offset | result
        "hello/world"       | "/var/hello-other"      | 5      | -1
    }

    def "can compare to child of this"() {
        expect:
        Integer.signum(compareToChildOfOrThis(prefix, path, offset, true)) == result
        Integer.signum(compareToChildOfOrThis(prefix, path, offset, false)) == result
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
        (Character.MIN_VALUE..Character.MAX_VALUE).each {
            assert compareChars(it as char, it as char, true) == 0
            assert compareChars(it as char, it as char, false) == 0
            assert equalChars(it as char, it as char, true)
            assert equalChars(it as char, it as char, false)
        }
    }

    def "path separators are equal"() {
        def slash = '/' as char
        def backslash = '\\' as char
        expect:
        assert compareChars(slash, backslash, true) == 0
        assert compareChars(slash, backslash, false) == 0
        assert compareChars(backslash, slash, true) == 0
        assert compareChars(backslash, slash, false) == 0
        assert equalChars(slash, backslash, true)
        assert equalChars(slash, backslash, false)
        assert equalChars(backslash, slash, true)
        assert equalChars(backslash, slash, false)
    }

    def "can compare path separator chars correctly (#left - #right = #result)"() {
        def char1 = left as char
        def char2 = right as char
        expect:
        compareChars(char1, char2, true) == result
        compareChars(char1, char2, false) == result
        compareChars(char2, char1, true) == -result
        compareChars(char2, char1, false) == -result
        equalChars(char1, char2, true) == (result == 0)
        equalChars(char1, char2, false) == (result == 0)
        equalChars(char2, char1, true) == (result == 0)
        equalChars(char2, char1, false) == (result == 0)

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
        'a'  | 'A'   | 1
        'a'  | 'B'   | -1
        'A'  | 'B'   | -1
        'A'  | 'b'   | -1
        'a'  | 'b'   | -1
        'z'  | 'Z'   | 1
        'y'  | 'z'   | -1
        'Y'  | 'z'   | -1
        'Y'  | 'Z'   | -1
        'y'  | 'Z'   | -1
    }

    def "non-letters are smaller than letters (caseSensitive: #caseSensitive)"() {
        expect:
        for (char letter in LETTERS) {
            for (char nonLetter in NON_LETTER) {
                assert compareChars(letter, nonLetter, caseSensitive) > 0
                assert compareChars(nonLetter, letter, caseSensitive) < 0
                assert !equalChars(letter, nonLetter, caseSensitive)
                assert !equalChars(nonLetter, letter, caseSensitive)
            }
        }

        where:
        caseSensitive << [true, false]
    }
}
