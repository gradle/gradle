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

package org.gradle.internal.component.model

import org.gradle.api.Named
import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.AttributeCompatibilityRule
import org.gradle.api.attributes.AttributeDisambiguationRule
import org.gradle.api.attributes.CompatibilityCheckDetails
import org.gradle.api.attributes.MultipleCandidatesDetails
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.api.internal.attributes.immutable.ImmutableAttributesSchema
import org.gradle.api.internal.attributes.matching.AttributeMatcher
import org.gradle.api.internal.attributes.matching.AttributeMatchingCandidate
import org.gradle.api.internal.attributes.matching.ImmutableAttributesBackedMatchingCandidate
import org.gradle.util.AttributeTestUtil
import spock.lang.Specification

class AttributePrecedenceSchemaAttributeMatcherTest extends Specification {

    def matcher = newMatcher()

    static Attribute<String> highest = Attribute.of("highest", String)
    static Attribute<String> middle = Attribute.of("middle", String)
    static Attribute<String> lowest = Attribute.of("lowest", String)
    // This has no precedence
    static Attribute<String> additional = Attribute.of("usage", String)

    static class CompatibilityRule implements AttributeCompatibilityRule<String> {
        @Override
        void execute(CompatibilityCheckDetails<String> details) {
            if (details.consumerValue == null) {
                details.compatible()
            } else {
                if (details.producerValue in ["best", "compatible"]) {
                    details.compatible()
                }
            }
        }
    }

    static class DisambiguationRule implements AttributeDisambiguationRule<String> {
        @Override
        void execute(MultipleCandidatesDetails<String> details) {
            if (details.consumerValue == null) {
                if (details.candidateValues.contains("best")) {
                    details.closestMatch("best")
                }
            } else {
                if (details.candidateValues.contains("best")) {
                    details.closestMatch("best")
                } else {
                    details.closestMatch(details.consumerValue)
                }
            }
        }
    }

    static AttributeMatcher newMatcher() {
        def schema = AttributeTestUtil.immutableSchema {
            attributeDisambiguationPrecedence(highest, middle, lowest)
            attribute(highest).compatibilityRules.add(CompatibilityRule)
            attribute(highest).disambiguationRules.add(DisambiguationRule)

            attribute(middle).compatibilityRules.add(CompatibilityRule)
            attribute(middle).disambiguationRules.add(DisambiguationRule)

            attribute(lowest).compatibilityRules.add(CompatibilityRule)
            attribute(lowest).disambiguationRules.add(DisambiguationRule)

            attribute(additional).compatibilityRules.add(CompatibilityRule)
            attribute(additional).disambiguationRules.add(DisambiguationRule)
        }

        return AttributeTestUtil.services().getMatcher(schema, ImmutableAttributesSchema.EMPTY)
    }

    def "when precedence is known, disambiguates by ordered elimination"() {
        def candidate1 = candidate("best", "best", "best")
        def candidate2 = candidate("best", "best", "compatible")
        def candidate3 = candidate("best", "compatible", "compatible")
        def candidate4 = candidate("compatible", "best", "best")
        def candidate5 = candidate("compatible", "compatible", "best")
        def candidate6 = candidate("compatible", "compatible", "compatible")
        def requested = requested("requested", "requested","requested")
        expect:
        matcher.matchMultipleCandidates([candidate1], requested) == [candidate1]
        matcher.matchMultipleCandidates([candidate1, candidate2, candidate3, candidate4, candidate5, candidate6], requested) == [candidate1]
        matcher.matchMultipleCandidates([candidate2, candidate3, candidate4, candidate5, candidate6], requested) == [candidate2]
        matcher.matchMultipleCandidates([candidate3, candidate4, candidate5, candidate6], requested) == [candidate3]
        matcher.matchMultipleCandidates([candidate4, candidate5, candidate6], requested) == [candidate4]
        matcher.matchMultipleCandidates([candidate5, candidate6], requested) == [candidate5]
        matcher.matchMultipleCandidates([candidate6], requested) == [candidate6]
    }

    def "disambiguates extra attributes in precedence order"() {
        def candidate1 = candidate("best", "compatible", "compatible")
        def candidate2 = candidate("compatible", "best", "compatible")
        def candidate3 = candidate("compatible", "compatible", "best")
        def candidate4 = candidate("compatible", "compatible", "compatible")
        def requested = AttributeTestUtil.attributes("unknown": "unknown")

        expect:
        matcher.matchMultipleCandidates([candidate1, candidate2, candidate3, candidate4], requested) == [candidate1]
        matcher.matchMultipleCandidates([candidate2, candidate3, candidate4], requested) == [candidate2]
        matcher.matchMultipleCandidates([candidate3, candidate4], requested) == [candidate3]
    }

    interface Flavor extends Named {}

    static class FlavorCompatibilityRule implements AttributeCompatibilityRule<Flavor> {
        @Override
        void execute(CompatibilityCheckDetails<Flavor> details) {
            details.compatible()
        }
    }

    static class FlavorDisambiguationRule implements AttributeDisambiguationRule<Flavor> {
        @Override
        void execute(MultipleCandidatesDetails<Flavor> details) {
            def best = details.candidateValues.find { it.name == "best" }
            if (best != null) {
                details.closestMatch(best)
            }
        }
    }

    def "disambiguates desugared requested attributes in the precedence order of their rich types"() {
        def highestFlavor = Attribute.of("highest", Flavor)
        def lowestFlavor = Attribute.of("lowest", Flavor)
        def schema = AttributeTestUtil.immutableSchema {
            attributeDisambiguationPrecedence(highestFlavor, lowestFlavor)
            attribute(highestFlavor).compatibilityRules.add(FlavorCompatibilityRule)
            attribute(highestFlavor).disambiguationRules.add(FlavorDisambiguationRule)
            attribute(lowestFlavor).compatibilityRules.add(FlavorCompatibilityRule)
            attribute(lowestFlavor).disambiguationRules.add(FlavorDisambiguationRule)
        }
        def matcher = AttributeTestUtil.services().getMatcher(schema, ImmutableAttributesSchema.EMPTY)

        // Desugared attributes, as read from published metadata
        def candidate1 = new ImmutableAttributesBackedMatchingCandidate(AttributeTestUtil.attributes([highest: "best", lowest: "other"]))
        def candidate2 = new ImmutableAttributesBackedMatchingCandidate(AttributeTestUtil.attributes([highest: "other", lowest: "best"]))
        def requested = AttributeTestUtil.attributes([highest: "requested", lowest: "requested"])

        expect:
        matcher.matchMultipleCandidates([candidate1, candidate2], requested) == [candidate1]
        matcher.matchMultipleCandidates([candidate2, candidate1], requested) == [candidate1]
    }

    private static ImmutableAttributes requested(String highestValue, String middleValue, String lowestValue) {
        return candidate(highestValue, middleValue, lowestValue).attributes
    }

    private static AttributeMatchingCandidate candidate(String highestValue, String middleValue, String lowestValue) {
        def attributes = AttributeTestUtil.attributes([highest: highestValue, middle: middleValue, lowest: lowestValue])
        new ImmutableAttributesBackedMatchingCandidate(attributes)
    }
}
