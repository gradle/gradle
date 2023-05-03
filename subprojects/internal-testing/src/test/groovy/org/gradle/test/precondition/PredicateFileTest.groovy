/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.test.precondition

import spock.lang.Specification

class PredicateFileTest extends Specification {

    Set<Set<String>> values = [
        // Value NOT shared between single and multi cases
        ["value1"] as Set,
        // Value shared between single and multi cases
        ["value2"] as Set,
        ["value2", "value3"] as Set,
    ] as Set

    def "accept single values"() {
        when:
        checkValidCombinations([value])

        then:
        noExceptionThrown()

        where:
        value << ["value1", "value2"]
    }

    def "accept multiple values"() {
        when:
        checkValidCombinations(combination)

        then:
        noExceptionThrown()

        where:
        combination << [["value2", "value3"], ["value3", "value2"]]
    }

    def "throws exception when single value not found"() {
        when:
        checkValidCombinations(["nonexistent"])

        then:
        final ex = thrown(IllegalArgumentException)
        ex.message.startsWith("Requested requirements [nonexistent] were not in the list of accepted combinations")
    }

    def "throws exception when single values are not found"() {
        when:
        checkValidCombinations(["nonexistent1", "nonexistent2"])

        then:
        final ex = thrown(IllegalArgumentException)
        ex.message.startsWith("Requested requirements [nonexistent1, nonexistent2] were not in the list of accepted combinations.")
    }

    def "standard implementation loads CSV correctly"() {
        when:
        PredicatesFile.checkValidNameCombinations(
            ["org.gradle.test.preconditions.UnitTestPreconditions\$Online"] as Set,
            PredicatesFile.DEFAULT_ACCEPTED_COMBINATIONS
        )

        then:
        noExceptionThrown()
    }

    private checkValidCombinations(List<String> combinations) {
        PredicatesFile.checkValidNameCombinations(combinations as Set, values)
    }

}
