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

import org.gradle.integtests.fixtures.logging.comparison.LineSearchFailures.InsufficientSizeLineListComparisonFailure
import org.gradle.integtests.fixtures.logging.comparison.LineSearchFailures.PotentialMatchesExistComparisonFailure
import spock.lang.Specification
import spock.lang.Subject

class ExhaustiveLinesSearcherTest extends Specification {
    @Subject
    def comparer = ExhaustiveLinesSearcher.useLcsDiff()

    // region: assertLinesContainedIn
    def "neither expected or actual lines can be empty"() {
        when:
        comparer.assertLinesContainedIn([], ["a"])
        then:
        thrown(AssertionError)

        when:
        comparer.assertLinesContainedIn(["a"], [])
        then:
        thrown(AssertionError)
    }

    def "successful lines contained in comparisons: #expectedLines vs #actualLines"() {
        expect:
        comparer.assertLinesContainedIn(expectedLines, actualLines)

        where:
        expectedLines           | actualLines
        ["a", "b", "c"]         | ["a", "b", "c"]
        ["a"]                   | ["a", "b"]
        ["a"]                   | ["b", "a"]
        ["a"]                   | ["b", "a", "c"]
        ["b", "c"]              | ["a", "b", "c", "d"]
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

    def "unified diff formatting of potential matches works: #expectedLines vs #actualLines"() {
        given:
        comparer = ExhaustiveLinesSearcher.useUnifiedDiff()
        def expectedLines = ["cat", "bird", "dog"]
        def actualLines = ["kangaroo", "cat", "llama", "dog", "turtle", "cat", "moose", "dog"]

        when:
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
