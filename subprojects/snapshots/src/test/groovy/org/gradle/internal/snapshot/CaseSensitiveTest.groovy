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

class CaseSensitiveTest extends AbstractCaseSensitivyTest {

    def "finds right entry in sorted list with only case differences"() {
        def children = ["bAd", "BaD", "Bad"]
        children.sort(CaseSensitivity.CASE_SENSITIVE.pathComparator)
        expect:
        for (int i = 0; i < children.size(); i++) {
            def searchedChild = children[i]
            int foundIndex = SearchUtil.binarySearch(children) { child ->
                CaseSensitivity.CASE_SENSITIVE.compareToChildOfOrThis(child, searchedChild, 0)
            }
            assert foundIndex == i
        }
    }

    def "finds right entry in sorted list with only case differences in prefix"() {
        def children = ["bAd/aB", "BaD/Bb", "Bad/cC"]
        children.sort(CaseSensitivity.CASE_SENSITIVE.pathComparator)
        expect:
        for (int i = 0; i < children.size(); i++) {
            def searchedChild = children[i].substring(0, 3)
            int foundIndex = SearchUtil.binarySearch(children) { child ->
                CaseSensitivity.CASE_SENSITIVE.compareWithCommonPrefix(child, searchedChild, 0)
            }
            assert foundIndex == i
        }
    }

    @Override
    CaseSensitivity getCaseSensitivity() {
        return CaseSensitivity.CASE_SENSITIVE
    }
}
