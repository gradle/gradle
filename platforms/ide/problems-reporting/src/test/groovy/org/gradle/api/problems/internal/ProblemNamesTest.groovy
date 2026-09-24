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

package org.gradle.api.problems.internal

import spock.lang.Specification

class ProblemNamesTest extends Specification {

    def "accepts valid group name '#name'"() {
        expect:
        ProblemNames.validateGroupName(name) == name

        where:
        // "Compilation:Java" is allowed because identity is structural; the separator has no meaning in a name.
        // 50 rocket emoji are 100 UTF-16 units but 50 code points, so they are accepted: the limit counts code points.
        // "می\u200Cخواهم" (Persian) needs the zero width non-joiner, the family emoji is joined with zero width joiners: both allowed
        name << ["Java", "Build Cache", "C++", "KMP", "Ünïcödé", "a" * 50, "with-dash_and.dot", "Émoji 🚀", "Compilation:Java", "\uD83D\uDE80" * 50, "می\u200Cخواهم", "👨\u200D👩\u200D👧"]
    }

    def "rejects invalid group name '#name'"() {
        when:
        ProblemNames.validateGroupName(name)

        then:
        def e = thrown(IllegalArgumentException)
        e.message.startsWith("Problem group name")
        e.message.contains(reason)

        where:
        name          | reason
        null          | "must not be null"
        ""            | "must not be empty"
        "   "         | "must not be blank"
        " Java"       | "must not start with whitespace"
        "Java "       | "must not end with whitespace"
        "Ja\tva"      | "control or format characters"  // tab
        "Ja\nva"      | "control or format characters"  // line feed
        "Ja\u0007va"  | "control or format characters"  // BEL, an ASCII C0 control character (Character.isISOControl)
        "Ja\u009fva"  | "control or format characters"  // APPLICATION PROGRAM COMMAND, a C1 control character (Character.isISOControl)
        "a" * 51      | "longer than 50 characters, but was 51 characters long"
        "\uD83D\uDE80" * 51 | "longer than 50 characters, but was 51 characters long"  // 102 UTF-16 units; the message counts code points
        "\u00a0Java"  | "must not start with whitespace"  // NO-BREAK SPACE (Character.isSpaceChar, not isWhitespace)
        "Java\u3000"  | "must not end with whitespace"    // IDEOGRAPHIC SPACE
        "\u2003"      | "must not be blank"               // EM SPACE
        "Ja\u200bva"  | "control or format characters"  // ZERO WIDTH SPACE, invisible, Unicode category Cf (Character.FORMAT); unlike U+200C/U+200D it has no spelling role
        "Ja\u202eva"  | "control or format characters"  // RIGHT-TO-LEFT OVERRIDE, reverses the rendered text, category Cf
        "Ja\u2028va"  | "control or format characters"  // LINE SEPARATOR, category Zl, neither a control nor a format character
        "Ja\u2029va"  | "control or format characters"  // PARAGRAPH SEPARATOR, category Zp, same
    }

    def "accepts valid problem name '#name'"() {
        expect:
        ProblemNames.validateProblemName(name) == name

        where:
        // 2000 rocket emoji are 4000 UTF-16 units but 2000 code points, so they are accepted: the limit counts code points.
        name << ["Unused import", "Class 'Foo' is not serializable", "Compilation:Java failed", "a" * 2000, "Émoji 🚀", "\uD83D\uDE80" * 2000, "می\u200Cخواهم", "👨\u200D👩\u200D👧"]
    }

    def "rejects invalid problem name '#name'"() {
        when:
        ProblemNames.validateProblemName(name)

        then:
        def e = thrown(IllegalArgumentException)
        e.message.startsWith("Problem name")
        e.message.contains(reason)

        where:
        name               | reason
        null               | "must not be null"
        ""                 | "must not be empty"
        "   "              | "must not be blank"
        "\u2003"           | "must not be blank"                     // EM SPACE
        " Unused import"   | "must not start with whitespace"
        "Unused import "   | "must not end with whitespace"
        " Unused import "  | "must not start or end with whitespace"
        "\u00a0Unused"     | "must not start with whitespace"        // NO-BREAK SPACE
        "Unused\u3000"     | "must not end with whitespace"          // IDEOGRAPHIC SPACE
        "line\nbreak"      | "control or format characters"
        "tab\tbed"         | "control or format characters"
        "bell\u0007"       | "control or format characters"          // BEL, ASCII C0 control character
        "zero\u200bwidth"  | "control or format characters"          // ZERO WIDTH SPACE, format character (Cf)
        "bidi\u202eflip"   | "control or format characters"          // RIGHT-TO-LEFT OVERRIDE, format character (Cf)
        "line\u2028sep"    | "control or format characters"          // LINE SEPARATOR (Zl)
        "para\u2029sep"    | "control or format characters"          // PARAGRAPH SEPARATOR (Zp)
        "a" * 2001         | "longer than 2000 characters, but was 2001 characters long"
        "\uD83D\uDE80" * 2001 | "longer than 2000 characters, but was 2001 characters long"  // 4002 UTF-16 units; the message counts code points
    }
}
