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

import static org.gradle.internal.snapshot.CaseSensitivity.CASE_SENSITIVE
import static org.gradle.internal.snapshot.PathUtil.compareChars
import static org.gradle.internal.snapshot.PathUtil.compareCharsIgnoringCase
import static org.gradle.internal.snapshot.PathUtil.compareToPrefix
import static org.gradle.internal.snapshot.PathUtil.compareWithCommonPrefix
import static org.gradle.internal.snapshot.PathUtil.equalChars
import static org.gradle.internal.snapshot.PathUtil.getPathComparator
import static org.gradle.internal.snapshot.PathUtil.hasPrefix
import static org.gradle.internal.snapshot.PathUtil.lengthOfCommonPrefix

@Unroll
abstract class AbstractCasePathUtilTest extends Specification{

    abstract CaseSensitivity getCaseSensitivity()

    def "common prefix of #prefix and #path at #offset is #result"() {
        expect:
        lengthOfCommonPrefix(prefix, path, offset, caseSensitivity) == result

        where:
        prefix       | path          | offset | result
        '/root'      | '/'           | 0      | 0
        '/root'      | '/root'       | 0      | 5
        '/root/some' | '/root/other' | 0      | 5
    }

    def "can compare size of common prefix"() {
        expect:
        Integer.signum(compareWithCommonPrefix(prefix, path, offset, caseSensitivity)) == result
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
        lengthOfCommonPrefix(prefix, path, offset, caseSensitivity) == result

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
        hasPrefix(prefix, path, offset, caseSensitivity)
        lengthOfCommonPrefix(prefix, path, offset, caseSensitivity) == prefix.length()

        where:
        prefix         | path                       | offset | result
        "hello/world"  | "hello/world\\inside"      | 0      | true
        "hello/world"  | "/var/hello/world\\inside" | 5      | true
        "hello\\world" | "/var/hello/world\\inside" | 5      | true
    }

    def "separator is smaller than every other character"() {
        expect:
        Integer.signum(compareWithCommonPrefix(prefix, path, offset, caseSensitivity)) == result

        where:
        prefix              | path                    | offset | result
        "hello/world"       | "/var/hello-other"      | 5      | -1
    }

    def "can compare to child of this"() {
        expect:
        Integer.signum(compareToPrefix(prefix, path, offset, caseSensitivity)) == result
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
            assert compareCharsIgnoringCase(currentChar as char, currentChar as char) == 0
            assert compareChars(currentChar as char, currentChar as char) == 0
            assert equalChars(currentChar as char, currentChar as char, caseSensitivity)
        }
    }

    def "entry #spec.searchedPrefix is child of #spec.expectedIndex in #spec.children"() {
        def children = spec.children

        expect:
        SearchUtil.binarySearch(children) { child ->
            compareToPrefix(child, spec.searchedPrefix, 0, caseSensitivity)
        } == spec.expectedIndex

        where:
        spec << SAME_OR_CHILD
    }

    def "path separators are equal"() {
        def slash = '/' as char
        def backslash = '\\' as char

        expect:
        compareCharsIgnoringCase(slash, backslash) == 0
        compareCharsIgnoringCase(backslash, slash) == 0
        compareChars(slash, backslash) == 0
        compareChars(backslash, slash) == 0
        equalChars(slash, backslash, caseSensitivity)
        equalChars(backslash, slash, caseSensitivity)
    }

    def "can compare path separator chars correctly (#left - #right = #result)"() {
        def char1 = left as char
        def char2 = right as char

        expect:
        compareCharsIgnoringCase(char1, char2) == result
        compareCharsIgnoringCase(char2, char1) == -result
        compareChars(char1, char2) == result
        compareChars(char2, char1) == -result
        equalChars(char1, char2, caseSensitivity) == (result == 0)
        equalChars(char2, char1, caseSensitivity) == (result == 0)

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

    def "path #spec.searchedPrefix has common prefix with #spec.expectedIndex in #spec.children"() {
        expect:
        SearchUtil.binarySearch(spec.children) { child ->
            compareWithCommonPrefix(child, spec.searchedPrefix, 0, caseSensitivity)
        } == spec.expectedIndex
        spec.children.each { child ->
            def childIndex = spec.children.indexOf(child)
            def sizeOfCommonPrefix = lengthOfCommonPrefix(spec.searchedPrefix, child, 0, caseSensitivity)
            if (childIndex == spec.expectedIndex) {
                assert sizeOfCommonPrefix > 0
            } else {
                assert sizeOfCommonPrefix == 0
            }
        }

        where:
        spec << WITH_COMMON_PREFIX
    }

    static final List<List<String>> CHILDREN_LISTS = [
        ["bAdA"],
        ["bAdA", "BaDb"],
        ["bAdA", "BaDb", "Badc"],
        ["bAdA/something", "BaDb/other", "Badc/different"],
        ["bad/mine", "c/other", "ab/second"],
        ["Bad/mine", "c/other", "aB/second"],
        ["Bad/mine", "c/other", "AB/second"],
        ["Bad/mine", "cA/other", "AB/second"],
        ["c", "b/something", "a/very/long/suffix"]
    ]*.toSorted(getPathComparator(CASE_SENSITIVE))

    static final List<CaseSensitivityTestSpec> SAME_OR_CHILD = CHILDREN_LISTS.collectMany { children ->
        children.collectMany { child ->
            def childIndex = children.indexOf(child)
            [
                new CaseSensitivityTestSpec(children, child, childIndex),
                new CaseSensitivityTestSpec(children, "$child/a/something", childIndex),
                new CaseSensitivityTestSpec(children, "$child/A/something", childIndex),
                new CaseSensitivityTestSpec(children, "$child/e/something", childIndex),
                new CaseSensitivityTestSpec(children, "$child/E/other", childIndex),
            ]
        }
    }

    static final List<CaseSensitivityTestSpec> WITH_COMMON_PREFIX = CHILDREN_LISTS.collectMany { children ->
        children.collectMany { child ->
            def childIndex = children.indexOf(child)
            (child.split("/") as List).inits().tail().init().collect { it.join("/") }.collectMany { prefix ->
                [
                    new CaseSensitivityTestSpec(children, prefix, childIndex),
                    new CaseSensitivityTestSpec(children, "$prefix/a/something", childIndex),
                    new CaseSensitivityTestSpec(children, "$prefix/A/something", childIndex),
                    new CaseSensitivityTestSpec(children, "$prefix/e/something", childIndex),
                    new CaseSensitivityTestSpec(children, "$prefix/E/other", childIndex),
                ]
            }
        }
    }
}

class CaseSensitivityTestSpec {
    List<String> children
    String searchedPrefix
    int expectedIndex

    CaseSensitivityTestSpec(List<String> children, String searchedPrefix, int expectedIndex) {
        this.children = children
        this.searchedPrefix = searchedPrefix
        this.expectedIndex = expectedIndex
    }
}
