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

import static org.gradle.internal.snapshot.CaseSensitivity.CASE_SENSITIVE
import static org.gradle.internal.snapshot.PathUtil.getPathComparator

class CaseSensitiveVfsRelativePathTest extends AbstractCaseVfsRelativePathTest {

    def "finds right entry in sorted list with only case differences"() {
        def children = ["bAd", "BaD", "Bad"]
        children.sort(getPathComparator(CASE_SENSITIVE))
        expect:
        for (int i = 0; i < children.size(); i++) {
            def searchedChild = children[i]
            int foundIndex = SearchUtil.binarySearch(children) { child ->
                VfsRelativePath.of(searchedChild).compareToFirstSegment(child, CASE_SENSITIVE)
            }
            assert foundIndex == i
        }
    }

    def "finds right entry in sorted list with only case differences in prefix"() {
        def children = ["bAd/aB", "BaD/Bb", "Bad/cC"]
        children.sort(getPathComparator(CASE_SENSITIVE))
        expect:
        for (int i = 0; i < children.size(); i++) {
            def searchedChild = children[i].substring(0, 3)
            int foundIndex = SearchUtil.binarySearch(children) { child ->
                VfsRelativePath.of(searchedChild).compareToFirstSegment(child, CASE_SENSITIVE)
            }
            assert foundIndex == i
        }
    }

    def "children #children are sorted the same with path sensitive and path insensitive compare"() {
        def caseInsensitiveSorted = children.toSorted(getPathComparator(CaseSensitivity.CASE_INSENSITIVE))
        def caseSensitiveSorted = children.toSorted(getPathComparator(CASE_SENSITIVE))
        expect:
        caseInsensitiveSorted == caseSensitiveSorted

        where:
        children << CHILDREN_LISTS
    }

    def "children names #children are sorted the same with path sensitive and name only comparison"() {
        def nameSorted = children.toSorted(PathUtil.&compareFileNames)
        def caseSensitiveSorted = children.toSorted(getPathComparator(CASE_SENSITIVE))

        expect:
        nameSorted == caseSensitiveSorted

        where:
        children << [
            ["bAdA", "BaDb"],
            ["bAdA", "BaDb", "Badc"],
            ["bad", "c", "ab"],
            ["Bad", "c", "aB"],
            ["Bad", "c", "AB"],
            ["Bad", "cA", "AB"],
            ["cpuid.h", "cross-stdarg.h", "emmintrin.h", "f16cintrin.h", "float.h", "iso646.h", "ISO_Fortran_binding.h", "lwpintrin.h", "lzcntintrin.h", "mm3dnow.h", "mm_malloc.h", "mmintrin.h", "movdirintrin.h", "mwaitxintrin.h", "nmmintrin.h", "omp.h", "openacc.h", "pconfigintrin.h", "stddef.h", "stdfix.h", "stdint-gcc.h", "stdint.h", "stdnoreturn.h", "tbmintrin.h", "tgmath.h", "tmmintrin.h", "unwind.h"],
            ["SS", "ß", "ßa", "ss", "sa", "sb", "st", "ßß"]

        ]
    }

    @Override
    CaseSensitivity getCaseSensitivity() {
        return CASE_SENSITIVE
    }
}
