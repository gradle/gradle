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

@Unroll
abstract class AbstractCaseSensitivityTest extends Specification{

    abstract CaseSensitivity getCaseSensitivity()

    def "entry #spec.searchedPrefix is child of #spec.expectedIndex in #spec.children"() {
        def children = spec.children
        expect:
        SearchUtil.binarySearch(children) { child ->
            PathUtil.compareToChildOfOrThis(child, spec.searchedPrefix, 0, caseSensitivity)
        } == spec.expectedIndex

        where:
        spec << SAME_OR_CHILD
    }

    def "path #spec.searchedPrefix has common prefix with #spec.expectedIndex in #spec.children"() {
        expect:
        SearchUtil.binarySearch(spec.children) { child ->
            PathUtil.compareWithCommonPrefix(child, spec.searchedPrefix, 0, caseSensitivity)
        } == spec.expectedIndex
        spec.children.each { child ->
            def childIndex = spec.children.indexOf(child)
            def sizeOfCommonPrefix = PathUtil.sizeOfCommonPrefix(spec.searchedPrefix, child, 0, caseSensitivity)
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
    ]*.toSorted(PathUtil.getPathComparator(CaseSensitivity.CASE_SENSITIVE))

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
