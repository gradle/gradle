/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.plugins.migration.model.compare.internal

import spock.lang.Specification
import org.gradle.api.plugins.migration.fixtures.outcome.StringBuildOutcomeComparator
import org.gradle.api.plugins.migration.fixtures.outcome.StringBuildOutcome
import org.gradle.api.plugins.migration.model.compare.BuildComparisonResult

import static org.apache.commons.lang.StringUtils.getLevenshteinDistance

class DefaultBuildComparatorTest extends Specification {

    def "do stuff"() {
        given:
        def outcomeComparatorFactory = new DefaultBuildOutcomeComparatorFactory()
        def buildComparator = new DefaultBuildComparator(outcomeComparatorFactory)
        def comparisonSpec = new DefaultBuildComparisonSpec()

        when:
        outcomeComparatorFactory.registerComparator(new StringBuildOutcomeComparator())

        associateStrings(comparisonSpec, "abc", "abd")
        associateStrings(comparisonSpec, "123", "123")

        BuildComparisonResult results = buildComparator.compareBuilds(comparisonSpec)

        then:
        results.comparisons.size() == 2
        results.comparisons[0].distance == getLevenshteinDistance("abc", "abd")
        results.comparisons[1].distance == 0
    }

    void associateStrings(DefaultBuildComparisonSpec spec, from, to) {
        spec.associate(toStringOutcome(from),  toStringOutcome(to), StringBuildOutcome)
    }

    StringBuildOutcome toStringOutcome(name, value = name) {
        new StringBuildOutcome(name, value)
    }
}
