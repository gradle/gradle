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

class PotentialMatchTest extends Specification {
    def "potential match renders helpful context"() {
        when:
        def pm = new PotentialMatch(expectedLines, actualLines, matchBegins)
        def expectedContextMsg = PotentialMatch.HEADER + '\n' + contextBody.join('\n')

        then:
        def actualContextMsg = pm.buildContext(2)
        actualContextMsg.readLines() == expectedContextMsg.readLines()

        where:
        expectedLines           | actualLines                               | matchBegins    || contextBody

        // Single Mismatches
        ["a", "b", "c"]         | ["a", "b", "d"]                           | 0              || [" [     1: a",
                                                                                                 "       2: b",
                                                                                                 "   X ] 3: d",
                                                                                                 PotentialMatch.buildComparison("c", "d", 1)]

        ["b", "c"]              | ["a", "b", "d"]                           | 1              || ["       1: a",
                                                                                                 " [     2: b",
                                                                                                 "   X ] 3: d",
                                                                                                 PotentialMatch.buildComparison("c", "d", 1)]

        ["b", "c"]              | ["a", "b", "d", "e"]                      | 1              || ["       1: a",
                                                                                                 " [     2: b",
                                                                                                 "   X ] 3: d",
                                                                                                 PotentialMatch.buildComparison("c", "d", 1),
                                                                                                 "       4: e"]

        ["b", "c"]              | ["y", "z", "a", "b", "d", "e", "y", "z"]  | 3              || ["       2: z",
                                                                                                 "       3: a",
                                                                                                 " [     4: b",
                                                                                                 "   X ] 5: d",
                                                                                                 PotentialMatch.buildComparison("c", "d", 1),
                                                                                                 "       6: e",
                                                                                                 "       7: y"]

        ["e", "f", "g"]         | ["a", "b", "c", "d", "e", "f", "z"]       | 4              || ["       3: c",
                                                                                                 "       4: d",
                                                                                                 " [     5: e",
                                                                                                 "       6: f",
                                                                                                 "   X ] 7: z",
                                                                                                 PotentialMatch.buildComparison("g", "z", 1)]

        // Multiple Mismatches

        ["b", "c", "d"]         | ["a", "b", "q", "q", "f"]                 | 1              || ["       1: a",
                                                                                                 " [     2: b",
                                                                                                 "   X   3: q",
                                                                                                 PotentialMatch.buildComparison("c", "q", 1),
                                                                                                 "   X ] 4: q",
                                                                                                 PotentialMatch.buildComparison("d", "q", 1),
                                                                                                 "       5: f"]

        // Padding
        ["b", "c", "d"]         | ["a", "b", "q", "q", "f",
                                   "z", "b", "q", "d", "x",
                                   "y", "z"]                                | 6              || ["        5: f",
                                                                                                 "        6: z",
                                                                                                 " [      7: b",
                                                                                                 "   X    8: q",
                                                                                                 PotentialMatch.buildComparison("c", "q", 1),
                                                                                                 "     ]  9: d",
                                                                                                 "       10: x",
                                                                                                 "       11: y"]
    }
}
