/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.integtests.fixtures.logging.comparison

import spock.lang.Specification
import spock.lang.Subject
import org.gradle.integtests.fixtures.logging.comparison.LineSearchFailures.DifferentSizesLineListComparisonFailure
import org.gradle.integtests.fixtures.logging.comparison.LineSearchFailures.InsufficientSizeLineListComparisonFailure
import org.gradle.integtests.fixtures.logging.comparison.LineSearchFailures.NoMatchingLinesExistComparisonFailure
import org.gradle.integtests.fixtures.logging.comparison.LineSearchFailures.PotentialMatchesExistComparisonFailure

class ExhaustiveLinesSearcherTest extends Specification {
    @Subject
    def comparer = new ExhaustiveLinesSearcher()

    // region: assertSameLines
    def "successful same lines comparisons: #expectedLines vs #actualLines"() {
        expect:
        comparer.assertSameLines(expectedLines, actualLines)

        where:
        expectedLines           | actualLines
        ["a", "b", "c"]         | ["a", "b", "c"]
        []                      | []
    }

    def "failing same lines comparison due to different line counts: #expectedLines vs #actualLines"() {
        when:
        comparer.assertSameLines(expectedLines, actualLines)

        then:
        def e = thrown(exception)
        e.getMessage().startsWith(message)

        where:
        expectedLines           | actualLines           || exception                                || message
        ["a", "b", "c"]         | ["d"]                 || DifferentSizesLineListComparisonFailure  || String.format(DifferentSizesLineListComparisonFailure.HEADER_TEMPLATE, 3, 1)
        ["a", "b", "c"]         | ["d", "e", "f", "g"]  || DifferentSizesLineListComparisonFailure  || String.format(DifferentSizesLineListComparisonFailure.HEADER_TEMPLATE, 3, 4)
        ["a", "b", "c"]         | ["a", "b", "c", "d"]  || DifferentSizesLineListComparisonFailure  || String.format(DifferentSizesLineListComparisonFailure.HEADER_TEMPLATE, 3, 4)
        ["a", "b", "c"]         | []                    || DifferentSizesLineListComparisonFailure  || String.format(DifferentSizesLineListComparisonFailure.HEADER_TEMPLATE, 3, 0)
        []                      | ["a", "b", "c"]       || DifferentSizesLineListComparisonFailure  || String.format(DifferentSizesLineListComparisonFailure.HEADER_TEMPLATE, 0, 3)
    }

    def "failing same lines comparison due to mismatched lines: #expectedLines vs #actualLines"() {
        when:
        comparer.assertSameLines(expectedLines, actualLines)

        then:
        def e = thrown(exception)
        e.getMessage().startsWith(message)

        where:
        expectedLines           | actualLines           || exception                                || message
        ["a", "b", "c"]         | ["d", "e", "g"]       || NoMatchingLinesExistComparisonFailure    || NoMatchingLinesExistComparisonFailure.HEADER
        ["a", "b", "c"]         | ["a", "e", "g"]       || PotentialMatchesExistComparisonFailure   || PotentialMatchesExistComparisonFailure.HEADER
        ["a", "b", "c"]         | ["a", "b", "g"]       || PotentialMatchesExistComparisonFailure   || PotentialMatchesExistComparisonFailure.HEADER
    }
    // endregion assertSameLines

    // region: assertLinesContainedIn
    def "successful lines contained in comparisons: #expectedLines vs #actualLines"() {
        expect:
        comparer.assertLinesContainedIn(expectedLines, actualLines)

        where:
        expectedLines           | actualLines
        ["a", "b", "c"]         | ["a", "b", "c"]
        []                      | []
        ["a"]                   | ["a", "b"]
        ["a"]                   | ["b", "a"]
        ["a"]                   | ["b", "a", "c"]
        ["b", "c"]              | ["a", "b", "c", "d"]
        []                      | ["a", "b", "c", "d"]
        ["b", "c"]              | ["a", "b", "c", "d", "b", "c", "e"]
    }

    def "failing lines contained in comparison due to too small actual line count: #expectedLines vs #actualLines"() {
        when:
        comparer.assertLinesContainedIn(expectedLines, actualLines)

        then:
        def e = thrown(exception)
        e.getMessage().startsWith(message)

        where:
        expectedLines           | actualLines           || exception                                    || message
        ["a", "b", "c"]         | ["d"]                 || InsufficientSizeLineListComparisonFailure    || String.format(InsufficientSizeLineListComparisonFailure.HEADER_TEMPLATE, 3, 1);
        ["a", "b", "c"]         | []                    || InsufficientSizeLineListComparisonFailure    || String.format(InsufficientSizeLineListComparisonFailure.HEADER_TEMPLATE, 3, 0);
    }

    def "failing lines contained in comparison due to mismatched lines with single potential match: #expectedLines vs #actualLines"() {
        when:
        comparer.assertLinesContainedIn(expectedLines, actualLines)

        then:
        PotentialMatchesExistComparisonFailure e = thrown(exception)
        e.getNumPotentialMatches() == 1

        where:
        expectedLines           | actualLines                       || exception
        ["a", "b", "c"]         | ["a", "b", "d", "e", "g"]         || PotentialMatchesExistComparisonFailure
        ["b", "c", "d"]         | ["a", "b", "e", "d", "f"]         || PotentialMatchesExistComparisonFailure
    }

    def "failing lines contained in comparison due to mismatched lines with multiple potential matches: #expectedLines vs #actualLines"() {
        when:
        comparer.assertLinesContainedIn(expectedLines, actualLines)

        then:
        PotentialMatchesExistComparisonFailure e = thrown(exception)
        e.getNumPotentialMatches() == numPotentialMatches

        where:
        expectedLines           | actualLines                       || exception                                || numPotentialMatches
        ["b", "c"]              | ["a", "b", "d", "c", "g"]         || PotentialMatchesExistComparisonFailure   || 2
    }

    def "if match would extend beyond last line of actual, it is not a potential match: #expectedLines vs #actualLines"() {
        when:
        comparer.assertLinesContainedIn(expectedLines, actualLines)

        then:
        PotentialMatchesExistComparisonFailure e = thrown(exception)
        e.getNumPotentialMatches() == numPotentialMatches

        where:
        expectedLines           | actualLines                       || exception                                || numPotentialMatches
        ["b", "c"]              | ["a", "b"]                        || PotentialMatchesExistComparisonFailure   || 0
    }

    def "by default, matching blank lines should NOT cause a potential match: #expectedLines vs #actualLines"() {
        when:
        comparer.assertLinesContainedIn(expectedLines, actualLines)

        then:
        PotentialMatchesExistComparisonFailure e = thrown(exception)
        e.getNumPotentialMatches() == numPotentialMatches

        where:
        expectedLines                  | actualLines                            || exception                                || numPotentialMatches
        ["b", "c", ""]                 | ["a", "b", "c", "x", "y", "z", ""]     || PotentialMatchesExistComparisonFailure   || 1
    }

    def "if requested, matching blank lines should cause a potential match: #expectedLines vs #actualLines"() {
        when:
        comparer.matchesBlankLines()
        comparer.assertLinesContainedIn(expectedLines, actualLines)

        then:
        PotentialMatchesExistComparisonFailure e = thrown(exception)
        e.getNumPotentialMatches() == numPotentialMatches

        where:
        expectedLines           | actualLines                                   || exception                                || numPotentialMatches
        ["b", "c", ""]          | ["a", "b", "c", "x", "b", "z", ""]            || PotentialMatchesExistComparisonFailure   || 2
    }

    def "by default, potential matches should only include those with a minimum number of mismatches: #expectedLines vs #actualLines"() {
        when:
        comparer.assertLinesContainedIn(expectedLines, actualLines)

        then:
        PotentialMatchesExistComparisonFailure e = thrown(exception)
        e.getNumPotentialMatches() == numPotentialMatches

        where:
        expectedLines           | actualLines                                   || exception                                || numPotentialMatches
        ["a", "b", "d"]         | ["a", "b", "c", "a", "q", "f", "a", "x", "d"] || PotentialMatchesExistComparisonFailure   || 2
        ["a", "b", "d"]         | ["a", "a", "d", "a", "a", "d", "q", "b", "d"] || PotentialMatchesExistComparisonFailure   || 3
    }

    def "if requested, potential matches should include those where any line matches: #expectedLines vs #actualLines"() {
        when:
        comparer.showLessLikelyMatches()
        comparer.assertLinesContainedIn(expectedLines, actualLines)

        then:
        PotentialMatchesExistComparisonFailure e = thrown(exception)
        e.getNumPotentialMatches() == numPotentialMatches

        where:
        expectedLines           | actualLines                                   || exception                                || numPotentialMatches
        ["a", "b", "d"]         | ["a", "b", "c", "a", "q", "f", "a", "x", "d"] || PotentialMatchesExistComparisonFailure   || 3
        ["a", "b", "d"]         | ["a", "a", "d", "a", "a", "d", "d", "b", "d"] || PotentialMatchesExistComparisonFailure   || 5
    }

    def "alternate formatting of potential matches works: #expectedLines vs #actualLines"() {
        given:
        def expectedLines = ["cat", "bird", "dog"]
        def actualLines = ["kangaroo", "cat", "llama", "dog", "turtle", "cat", "moose", "dog"]

        when:
        comparer.useUnifiedDiff()
        comparer.assertLinesContainedIn(expectedLines, actualLines)

        then:
        PotentialMatchesExistComparisonFailure e = thrown(PotentialMatchesExistComparisonFailure)
        e.getMessage() == """|${PotentialMatchesExistComparisonFailure.HEADER}
           |
           |@@ -1,3 +1,8 @@
           |+kangaroo
           | cat
           |-bird
           |+llama
           | dog
           |+turtle
           |+cat
           |+moose
           |+dog""".stripMargin()
    }
    // endregion assertLinesContainedIn
}
