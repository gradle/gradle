/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.api.internal.tasks.testing.report.generic

import spock.lang.Specification

class RecentCharsTest extends Specification {

    def "ends with '#text' after adding '#added' with capacity #capacity is #expected"() {
        given:
        def chars = new RecentChars(capacity)

        when:
        added.each { chars.add(it as char) }

        then:
        chars.endsWithIgnoreCase(text) == expected

        where:
        capacity | added            | text    | expected
        // Not filled yet
        4        | ""               | ""      | true
        4        | ""               | "a"     | false
        4        | "ab"             | "ab"    | true
        4        | "ab"             | "b"     | true
        4        | "ab"             | "a"     | false
        4        | "ab"             | "xab"   | false
        // Exactly filled
        4        | "abcd"           | "abcd"  | true
        4        | "abcd"           | "cd"    | true
        // Wrapped around, possibly multiple times
        4        | "abcdef"         | "cdef"  | true
        4        | "abcdef"         | "ef"    | true
        4        | "abcdef"         | "bcde"  | false
        4        | "abcdefghijklm"  | "jklm"  | true
        4        | "abcdefghijklm"  | "ijkl"  | false
        // Ignores case of the added characters
        4        | "xABc"           | "abc"   | true
        // Capacity of one
        1        | "abc"            | "c"     | true
        1        | "abc"            | "b"     | false
    }

    def "rejects text longer than the capacity"() {
        given:
        def chars = new RecentChars(2)

        when:
        chars.endsWithIgnoreCase("abc")

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Text 'abc' is longer than the capacity 2."
    }

    def "rejects capacity of #capacity"() {
        when:
        new RecentChars(capacity)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Capacity must be positive, but was ${capacity}."

        where:
        capacity << [0, -1]
    }
}
