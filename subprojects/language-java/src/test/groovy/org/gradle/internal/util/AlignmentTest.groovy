/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.internal.util

import spock.lang.Specification
import spock.lang.Unroll

import static org.gradle.internal.util.Alignment.Kind.identical

@Unroll
class AlignmentTest extends Specification {
    def "sequences #left and #right are identical"() {
        given:
        def alignment = align(left, right)

        expect:
        alignment.every { it.kind == identical }

        where:
        left  | right
        ''    | ''
        'A'   | 'A'
        'B'   | 'B'
        'AB'  | 'AB'
        'ABC' | 'ABC'
        'CAB' | 'CAB'
    }

    def "detects insertion #current and #previous"() {
        given:
        def alignment = align(current, previous)

        expect:
        isAligned(alignment, expectedAlignment)

        where:
        current | previous | expectedAlignment
        'A'     | ''       | ['+A']
        'AA'    | 'A'      | ['+A', 'A']
        'BA'    | 'B'      | ['B', '+A']
        'AB'    | 'A'      | ['A', '+B']
        'ABC'   | 'AC'     | ['A', '+B', 'C']
        'CAB'   | 'AB'     | ['+C', 'A', 'B']
    }

    def "detects removal #current and #previous"() {
        given:
        def alignment = align(current, previous)

        expect:
        isAligned(alignment, expectedAlignment)

        where:
        current | previous | expectedAlignment
        ''      | 'A'      | ['-A']
        ''      | 'AB'     | ['-A', '-B']
        'A'     | 'AA'     | ['-A', 'A']
        'A'     | 'BA'     | ['-B', 'A']
        'A'     | 'AB'     | ['A', '-B']
        'AC'    | 'ABC'    | ['A', '-B', 'C']
        'AB'    | 'CAB'    | ['-C', 'A', 'B']
    }

    def "detects reorder #current and #previous"() {
        given:
        def alignment = align(current, previous)

        expect:
        isAligned(alignment, expectedAlignment)

        where:
        current | previous | expectedAlignment
        'AB'    | 'BA'     | ['B -> A', 'A -> B']
        'ABC'   | 'BAC'    | ['B -> A', 'A -> B', 'C']
        'ABC'   | 'CBA'    | ['C -> A', 'B', 'A -> C']
    }

    def "detects mix of addition, removals and mutations #current and #previous"() {
        given:
        def alignment = align(current, previous)

        expect:
        isAligned(alignment, expectedAlignment)

        where:
        current | previous | expectedAlignment
        'AB'    | 'CD'     | ['C -> A', 'D -> B']
        'AB'    | 'XADY'   | ['-X', 'A', '-D', 'Y -> B']
        'EABC'  | 'ABCD'   | ['+E', 'A', 'B', 'C', '-D']
        'ABC'   | 'BA'     | ['+A', 'B', 'A -> C']
        'EABC'  | 'ACBD'   | ['A -> E', 'C -> A', 'B', 'D -> C']
    }

    private static <T> void isAligned(List<Alignment<T>> result, List<?> expectation) {
        if (expectation.size() != result.size()) {
            throw new AssertionError("Unexpected alignment. Expected $expectation, was: $result")
        }
        for (int i = 0; i < expectation.size(); i++) {
            def expected = expectation[i]
            def actual = result[i].toString()
            assert actual == expected: """Unexpected alignment. Expected $expectation, was: $result
At index $i, found $actual. Expected $expected"""
        }
    }

    private static List<Alignment<Character>> align(String a, String b) {
        Alignment.align(a as Character[], b as Character[])
    }
}
