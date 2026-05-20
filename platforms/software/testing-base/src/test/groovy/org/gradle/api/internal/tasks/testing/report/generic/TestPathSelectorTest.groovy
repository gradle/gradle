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

import com.google.common.collect.ImmutableList
import org.gradle.util.Path
import spock.lang.Specification

import static org.gradle.api.internal.tasks.testing.report.generic.TestPathSelector.classMarker
import static org.gradle.api.internal.tasks.testing.report.generic.TestPathSelector.furtherLeafNodes
import static org.gradle.api.internal.tasks.testing.report.generic.TestPathSelector.literal
import static org.gradle.api.internal.tasks.testing.report.generic.TestPathSelector.regex

class TestPathSelectorTest extends Specification {

    def "of() creates selector from varargs"() {
        expect:
        TestPathSelector.of(
            literal("a"),
            literal("b"),
        ).segments() == ImmutableList.of(
            literal("a"),
            literal("b")
        )
    }

    def "ClassMarker cannot be nested"() {
        when:
        classMarker(classMarker(literal("a")))

        then:
        thrown(IllegalArgumentException)
    }

    def "only one ClassMarker is allowed"() {
        when:
        TestPathSelector.of(classMarker(literal("a")), classMarker(literal("b")))

        then:
        thrown(IllegalArgumentException)
    }

    def "FurtherLeafNodes segment must be last: #name"() {
        when:
        TestPathSelector.of(*segments)

        then:
        thrown(IllegalArgumentException)

        where:
        name                                           | segments
        "FurtherLeafNodes before literal"              | [furtherLeafNodes(), literal("a")]
        "ClassMarker(FurtherLeafNodes) before literal" | [classMarker(furtherLeafNodes()), literal("a")]
    }

    def "toString() for #name is #expected"() {
        expect:
        selector.toString() == expected

        where:
        name                      | selector                                              | expected
        "root"                    | TestPathSelector.of()                                 | ":"
        "single segment"          | TestPathSelector.of(literal("a"))                     | ":a"
        "multi segment"           | TestPathSelector.of(literal("a"), literal("b"))       | ":a:b"
        "regex segment"           | TestPathSelector.of(regex(".*Test"))                  | ":Regex(.*Test)"
        "further leaf nodes"      | TestPathSelector.of(literal("a"), furtherLeafNodes()) | ":a:FurtherLeafNodes"
        "class marker"            | TestPathSelector.of(classMarker(literal("b")))        | ":ClassMarker(b)"
        "further leaf nodes only" | TestPathSelector.of(furtherLeafNodes())               | ":FurtherLeafNodes"
    }

    def "hasFurtherLeafNodes() is #expected for #name"() {
        expect:
        selector.hasFurtherLeafNodes() == expected

        where:
        name                            | selector                                                           | expected
        "empty"                         | TestPathSelector.of()                                              | false
        "literal only"                  | TestPathSelector.of(literal("a"))                                  | false
        "trailing further leaf nodes"   | TestPathSelector.of(literal("a"), furtherLeafNodes())              | true
        "further leaf nodes only"       | TestPathSelector.of(furtherLeafNodes())                            | true
        "ClassMarker(FurtherLeafNodes)" | TestPathSelector.of(literal("a"), classMarker(furtherLeafNodes())) | true
    }

    def "selector matches path: #name"() {
        expect:
        selector.matches(Path.path(path)).isMatch()

        where:
        name                                   | selector                                                                    | path
        "exact literal match"                  | TestPathSelector.of(literal("com"), literal("example"))                     | ":com:example"
        "root matches root"                    | TestPathSelector.of()                                                       | ":"
        "regex match"                          | TestPathSelector.of(literal("com"), regex(".*Test"))                        | ":com:MyTest"
        "further leaf nodes matches"           | TestPathSelector.of(literal("com"), literal("example"), furtherLeafNodes()) | ":com:example"
        "further leaf nodes only matches root" | TestPathSelector.of(furtherLeafNodes())                                     | ":"
        "class marker matches"                 | TestPathSelector.of(literal("a"), classMarker(literal("b")), literal("c"))  | ":a:b:c"
        "class marker regex"                   | TestPathSelector.of(literal("a"), classMarker(regex(".*")), literal("c"))   | ":a:foo:c"
    }

    def "selector does not match path: #name"() {
        expect:
        !selector.matches(Path.path(path)).isMatch()

        where:
        name                              | selector                                                                    | path
        "root vs non-empty"               | TestPathSelector.of()                                                       | ":foo"
        "segment count mismatch"          | TestPathSelector.of(literal("a"), literal("b"), literal("c"))               | ":a:b"
        "literal mismatch"                | TestPathSelector.of(literal("a"), literal("b"))                             | ":a:c"
        "regex mismatch"                  | TestPathSelector.of(literal("com"), regex(".*Test"))                        | ":com:TestCase"
        "leaf with same segment count"    | TestPathSelector.of(literal("com"), literal("example"), furtherLeafNodes()) | ":com:example:foo"
        "leaf with smaller segment count" | TestPathSelector.of(literal("a"), furtherLeafNodes())                       | ":a:b:c"
    }

    def "matched result is the Matched singleton"() {
        when:
        def result = TestPathSelector.of(literal("a")).matches(Path.path(":a"))

        then:
        result == TestPathSelector.MatchResult.Matched.INSTANCE
    }

    def "mismatch results have descriptive display names"() {
        when:
        def result = selector.matches(Path.path(path))

        then:
        result.displayName == expectedMessage

        where:
        selector                                                      | path   | expectedMessage
        TestPathSelector.of(literal("a"), literal("b"), literal("c")) | ":a:b" | "segment count mismatch (expected: 3, actual: 2)"
        TestPathSelector.of(literal("a"), literal("b"))               | ":a:c" | "segment 1 mismatch (expected 'b', got 'c')"
        TestPathSelector.of(literal("a"), furtherLeafNodes())         | ":a:b" | "segment count mismatch (expected: 1, actual: 2)"
    }

    def "regex mismatch display name includes pattern"() {
        when:
        def result = TestPathSelector.of(literal("com"), regex(".*Test")).matches(Path.path(":com:TestCase"))

        then:
        result.displayName.contains("Regex(.*Test)")
    }

    def "returns NotAbsolute for relative path"() {
        when:
        def result = TestPathSelector.of(literal("a")).matches(Path.path("relative"))

        then:
        result == TestPathSelector.MatchResult.NotAbsolute.INSTANCE
    }

    def "ancestors of #name"() {
        when:
        def ancestors = selector.ancestors().collect { it.toString() }

        then:
        ancestors == expectedAncestors

        where:
        name         | selector                                                      | expectedAncestors
        "3 segments" | TestPathSelector.of(literal("a"), literal("b"), literal("c")) | [":a:b:FurtherLeafNodes", ":a:FurtherLeafNodes", ":FurtherLeafNodes"]
        "1 segment"  | TestPathSelector.of(literal("a"))                             | [":FurtherLeafNodes"]
        "root"       | TestPathSelector.of()                                         | []
    }

    def "ancestors of further leaf nodes selectors"() {
        when:
        def ancestors = selector.ancestors().collect { it.toString() }

        then:
        ancestors == expectedAncestors

        where:
        name        | selector                                                            | expectedAncestors
        "a:b:leaf"  | TestPathSelector.of(literal("a"), literal("b"), furtherLeafNodes()) | [":a:FurtherLeafNodes", ":FurtherLeafNodes"]
        "a:leaf"    | TestPathSelector.of(literal("a"), furtherLeafNodes())               | [":FurtherLeafNodes"]
        "leaf only" | TestPathSelector.of(furtherLeafNodes())                             | []
    }

    def "ancestors propagate ClassMarker to leaf position when trimmed: #name"() {
        when:
        def ancestors = selector.ancestors().collect { it.toString() }

        then:
        ancestors == expectedAncestors

        where:
        name               | selector                                                                   | expectedAncestors
        "marker on middle" | TestPathSelector.of(literal("a"), classMarker(literal("b")), literal("c")) | [":a:ClassMarker(b):FurtherLeafNodes", ":a:ClassMarker(FurtherLeafNodes)", ":ClassMarker(FurtherLeafNodes)"]
        "marker on first"  | TestPathSelector.of(classMarker(literal("a")), literal("b"), literal("c")) | [":ClassMarker(a):b:FurtherLeafNodes", ":ClassMarker(a):FurtherLeafNodes", ":ClassMarker(FurtherLeafNodes)"]
        "marker on last"   | TestPathSelector.of(literal("a"), literal("b"), classMarker(literal("c"))) | [":a:b:ClassMarker(FurtherLeafNodes)", ":a:ClassMarker(FurtherLeafNodes)", ":ClassMarker(FurtherLeafNodes)"]
        "marker regex"     | TestPathSelector.of(literal("a"), classMarker(regex(".*")), literal("c"))  | [":a:ClassMarker(Regex(.*)):FurtherLeafNodes", ":a:ClassMarker(FurtherLeafNodes)", ":ClassMarker(FurtherLeafNodes)"]
        "marker on leaf"   | TestPathSelector.of(literal("a"), classMarker(furtherLeafNodes()))         | [":ClassMarker(FurtherLeafNodes)"]
        "no marker"        | TestPathSelector.of(literal("a"), literal("b"), literal("c"))              | [":a:b:FurtherLeafNodes", ":a:FurtherLeafNodes", ":FurtherLeafNodes"]
    }

    def "selectors with same segments are equal: #name"() {
        expect:
        first == second
        first.hashCode() == second.hashCode()

        where:
        name                 | first                                                 | second
        "literal"            | TestPathSelector.of(literal("a"), literal("b"))       | TestPathSelector.of(literal("a"), literal("b"))
        "regex"              | TestPathSelector.of(regex(".*Test"))                  | TestPathSelector.of(regex(".*Test"))
        "further leaf nodes" | TestPathSelector.of(literal("a"), furtherLeafNodes()) | TestPathSelector.of(literal("a"), furtherLeafNodes())
        "class marker"       | TestPathSelector.of(classMarker(literal("b")))        | TestPathSelector.of(classMarker(literal("b")))
    }

    def "different selectors are not equal: #name"() {
        expect:
        first != second

        where:
        name                   | first                                           | second
        "different literal"    | TestPathSelector.of(literal("a"), literal("b")) | TestPathSelector.of(literal("a"), literal("c"))
        "different length"     | TestPathSelector.of(literal("a"), literal("b")) | TestPathSelector.of(literal("a"))
        "different regex"      | TestPathSelector.of(regex(".*Test"))            | TestPathSelector.of(regex(".*Spec"))
        "missing class marker" | TestPathSelector.of(classMarker(literal("b")))  | TestPathSelector.of(literal("b"))
    }
}
