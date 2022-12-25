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

import static org.gradle.internal.snapshot.CaseSensitivity.CASE_INSENSITIVE
import static org.gradle.internal.snapshot.CaseSensitivity.CASE_SENSITIVE
import static org.gradle.internal.snapshot.PathUtil.compareChars
import static org.gradle.internal.snapshot.PathUtil.compareCharsIgnoringCase
import static org.gradle.internal.snapshot.PathUtil.equalChars

class CaseInsensitiveVfsRelativePathTest extends AbstractCaseVfsRelativePathTest {

    def "#left and #right are equal ignoring case"() {
        char char1 = left as char
        char char2 = right as char
        expect:
        equalChars(char2, char1, CASE_INSENSITIVE)
        equalChars(char1, char2, CASE_INSENSITIVE)
        compareCharsIgnoringCase(char1, char2) == 0
        compareCharsIgnoringCase(char2, char1) == 0

        where:
        left     | right
        '\u1e9e' | 'ß'
        '\u03a3' | '\u03c2'
        '\u03a3' | '\u03c3'
    }

    def "can compare lower and upper case correctly (#left - #right = #result)"() {
        def char1 = left as char
        def char2 = right as char
        def caseInsensitiveResult = left.toUpperCase() == right.toUpperCase() ? 0 : result

        expect:
        compareCharsIgnoringCase(char1, char2) == caseInsensitiveResult
        compareCharsIgnoringCase(char2, char1) == -caseInsensitiveResult
        compareChars(char1, char2) == Character.compare(char1, char2)
        compareChars(char2, char1) == Character.compare(char2, char1)
        !equalChars(char1, char2, CASE_SENSITIVE)
        equalChars(char1, char2, CASE_INSENSITIVE) == (caseInsensitiveResult == 0)
        !equalChars(char2, char1, CASE_SENSITIVE)
        equalChars(char2, char1, CASE_INSENSITIVE) == (caseInsensitiveResult == 0)

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

    @Override
    CaseSensitivity getCaseSensitivity() {
        return CASE_INSENSITIVE
    }
}
