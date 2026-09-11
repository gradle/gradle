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

import org.gradle.api.problems.ProblemGroup
import org.gradle.api.problems.ProblemId
import spock.lang.Specification

class ProblemGroupSupportTest extends Specification {

    def "accepts valid group name '#name'"() {
        expect:
        ProblemGroupSupport.validateGroupName(name) == name

        where:
        // "Compilation:Java" is allowed because identity is structural; the separator has no meaning in a name.
        // 50 rocket emoji are 100 UTF-16 units but 50 code points, so they are accepted: the limit counts code points.
        name << ["Java", "Build Cache", "C++", "KMP", "Ünïcödé", "a" * 50, "with-dash_and.dot", "Émoji 🚀", "Compilation:Java", "\uD83D\uDE80" * 50]
    }

    def "rejects invalid group name '#name'"() {
        when:
        ProblemGroupSupport.validateGroupName(name)

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
        "Ja\u200bva"  | "control or format characters"  // ZERO WIDTH SPACE, invisible, Unicode category Cf (Character.FORMAT)
        "Ja\u202eva"  | "control or format characters"  // RIGHT-TO-LEFT OVERRIDE, reverses the rendered text, category Cf
        "Ja\u2028va"  | "control or format characters"  // LINE SEPARATOR, category Zl, neither a control nor a format character
        "Ja\u2029va"  | "control or format characters"  // PARAGRAPH SEPARATOR, category Zp, same
    }

    def "accepts valid problem name '#name'"() {
        expect:
        ProblemGroupSupport.validateProblemName(name) == name

        where:
        // 2000 rocket emoji are 4000 UTF-16 units but 2000 code points, so they are accepted: the limit counts code points.
        name << ["Unused import", "Class 'Foo' is not serializable", "Compilation:Java failed", "a" * 2000, "Émoji 🚀", "\uD83D\uDE80" * 2000]
    }

    def "rejects invalid problem name '#name'"() {
        when:
        ProblemGroupSupport.validateProblemName(name)

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

    def "equality is structural on name and parent, across implementations"() {
        def root = ProblemGroup.create("Root", "Root display")
        def sameRoot = ProblemGroup.create("Root", "different display")
        def otherRoot = ProblemGroup.create("Other", "Root display")
        def child = ProblemGroup.create("Child", "Child", root)
        def sameChild = ProblemGroup.create("Child", "Child", sameRoot)
        def foreign = new ForeignGroup("Child", root)

        expect:
        root == sameRoot
        root.hashCode() == sameRoot.hashCode()
        root != otherRoot
        child == sameChild
        child.hashCode() == sameChild.hashCode()
        child != root
        // the same structural rules apply to other implementations that delegate to ProblemGroupSupport
        child == foreign
        ProblemGroupSupport.equals(foreign, child)
        ProblemGroupSupport.hashCode(foreign) == child.hashCode()
        !ProblemGroupSupport.equals(root, "Root")
        !ProblemGroupSupport.equals(root, null)
        !root.equals("Root")
    }

    def "renders the group chain from the root, and the id as name followed by its chain"() {
        def root = ProblemGroup.create("Root", "Root")
        def child = ProblemGroup.create("Child", "Child", root)
        def leaf = ProblemGroup.create("Leaf", "Leaf", child)

        expect:
        ProblemGroupSupport.render(root) == "Root"
        ProblemGroupSupport.render(child) == "Root > Child"
        ProblemGroupSupport.render(leaf) == "Root > Child > Leaf"
        ProblemGroupSupport.render(ProblemId.create("Unused import", "Unused import", child)) == "Unused import (in Root > Child)"
    }

    def "quotes group names that contain the separator or a quote so the rendering cannot be misread"() {
        def root = ProblemGroup.create("Root", "Root")
        def tricky = ProblemGroup.create("Java > Kotlin", "Java > Kotlin", root)
        def quoted = ProblemGroup.create('Say "hi"', 'Say "hi"', root)

        expect:
        ProblemGroupSupport.render(tricky) == 'Root > "Java > Kotlin"'
        ProblemGroupSupport.render(quoted) == 'Root > "Say \\"hi\\""'
        ProblemGroupSupport.render(ProblemId.create("a > b", "a > b", tricky)) == '"a > b" (in Root > "Java > Kotlin")'
    }

    def "problem names are quoted only when they contain the separator, since sentences legitimately contain quotes"() {
        def root = ProblemGroup.create("Root", "Root")

        expect:
        ProblemGroupSupport.render(ProblemId.create('Class "Foo" is bad', 'Class "Foo" is bad', root)) == 'Class "Foo" is bad (in Root)'
        ProblemGroupSupport.render(ProblemId.create("a > b (in c)", "a > b (in c)", root)) == '"a > b (in c)" (in Root)'
        ProblemGroupSupport.render(ProblemId.create('Say "hi" > there', 'Say "hi" > there', root)) == '"Say \\"hi\\" > there" (in Root)'
    }

    def "quoteIfNeeded('#name') renders as #expected"() {
        expect:
        ProblemGroupSupport.quoteIfNeeded(name) == expected

        where:
        name            | expected
        "plain"         | "plain"
        "has : colon"   | "has : colon"
        "has>no spaces" | '"has>no spaces"'
        'back\\slash'   | 'back\\slash'
        'back\\"quote'  | '"back\\\\\\"quote"'
        '"'             | '"\\""'
    }

    def "groups created through the legacy API have no description"() {
        expect:
        ((ProblemGroupInternal) ProblemGroup.create("Root", "Root")).description == null
    }

    private static class ForeignGroup extends ProblemGroup {
        private final String name
        private final ProblemGroup parent

        ForeignGroup(String name, ProblemGroup parent) {
            this.name = name
            this.parent = parent
        }

        String getName() { name }

        String getDisplayName() { name }

        ProblemGroup getParent() { parent }
    }
}
