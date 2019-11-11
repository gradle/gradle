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

    def "finds right entry in sorted list"() {
        def children = ["bAda", "BaDb", "Badc"]
        children.sort(CaseSensitivity.CASE_SENSITIVE.pathComparator)
        expect:
        for (int i = 0; i < children.size(); i++) {
            def searchedChild = children[i]
            int foundIndex = SearchUtil.binarySearch(children) { child ->
                caseSensitivity.compareToChildOfOrThis(child, searchedChild, 0)
            }
            assert foundIndex == i
        }
    }

    def "finds child with common prefix"() {
        def children = ["bada/first", "BaDb/second", "BadC/third"]
        children.sort(CaseSensitivity.CASE_SENSITIVE.pathComparator)
        expect:
        for (int i = 0; i < children.size(); i++) {
            def searchedChild = children[i].substring(0, children[i].indexOf('/'))
            int foundIndex = SearchUtil.binarySearch(children) { child ->
                caseSensitivity.compareWithCommonPrefix(child, searchedChild, 0)
            }
            assert foundIndex == i
        }
    }
}
