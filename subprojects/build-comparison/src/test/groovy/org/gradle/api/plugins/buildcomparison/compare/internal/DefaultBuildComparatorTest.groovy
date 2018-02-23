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

package org.gradle.api.plugins.buildcomparison.compare.internal

import org.gradle.api.plugins.buildcomparison.outcome.string.StringBuildOutcome
import org.gradle.api.plugins.buildcomparison.outcome.string.StringBuildOutcomeComparator

import spock.lang.Specification

import static org.apache.commons.lang.StringUtils.getLevenshteinDistance

class DefaultBuildComparatorTest extends Specification {

    def outcomeComparatorFactory = new DefaultBuildOutcomeComparatorFactory()
    def buildComparator = new DefaultBuildComparator(outcomeComparatorFactory)
    def specBuilder = new DefaultBuildComparisonSpecBuilder()

    def setup() {
        outcomeComparatorFactory.registerComparator(new StringBuildOutcomeComparator())
    }

    def "simple comparison"() {
        when:
        associateStrings("abc", "abd")
        associateStrings("123", "123")

        then:
        def results = compareBuilds()
        results.comparisons.size() == 2
        results.comparisons[0].distance == distance("abc", "abd")
        results.comparisons[1].distance == 0
    }

    def "comparison with unassociateds"() {
        when:
        def uncomparedFrom = toStringOutcome("a")
        def uncomparedTo = toStringOutcome("a")
        specBuilder.addUnassociatedFrom(uncomparedFrom)
        specBuilder.addUnassociatedTo(uncomparedTo)
        associateStrings("c", "c")

        then:
        def results = compareBuilds()
        results.comparisons.first().distance == 0
        results.uncomparedSourceOutcomes == [uncomparedFrom] as Set
        results.uncomparedTargetOutcomes == [uncomparedTo] as Set
    }

    def "empty spec is ok"() {
        expect:
        def result = compareBuilds()
        result.comparisons.empty
        result.uncomparedSourceOutcomes.empty
        result.uncomparedTargetOutcomes.empty
    }

    protected int distance(String from, String to) {
        getLevenshteinDistance(from, to)
    }

    protected BuildComparisonResult compareBuilds() {
        buildComparator.compareBuilds(specBuilder.build())
    }

    void associateStrings(BuildComparisonSpecBuilder builder = specBuilder, from, to) {
        builder.associate(toStringOutcome(from), toStringOutcome(to), StringBuildOutcome)
    }

    StringBuildOutcome toStringOutcome(name, value = name) {
        new StringBuildOutcome(name, value)
    }
}
