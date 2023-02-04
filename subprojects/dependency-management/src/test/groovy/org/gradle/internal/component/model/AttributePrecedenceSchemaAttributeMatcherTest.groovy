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

import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.AttributeCompatibilityRule
import org.gradle.api.attributes.AttributeDisambiguationRule
import org.gradle.api.attributes.CompatibilityCheckDetails
import org.gradle.api.attributes.MultipleCandidatesDetails
import org.gradle.api.internal.attributes.AttributeContainerInternal
import org.gradle.api.internal.attributes.DefaultAttributesSchema
import org.gradle.internal.isolation.TestIsolatableFactory
import org.gradle.util.AttributeTestUtil
import org.gradle.util.TestUtil
import spock.lang.Specification

class AttributePrecedenceSchemaAttributeMatcherTest extends Specification {

    def schema = new DefaultAttributesSchema(TestUtil.instantiatorFactory(), new TestIsolatableFactory())
    def explanationBuilder = Stub(AttributeMatchingExplanationBuilder)

    def highest = Attribute.of("highest", String)
    def middle = Attribute.of("middle", String)
    def lowest = Attribute.of("lowest", String)
    // This has no precedence
    def additional = Attribute.of("usage", String)

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

    def setup() {
        schema.attributeDisambiguationPrecedence(highest, middle, lowest)
        schema.attribute(highest).with {
            compatibilityRules.add(CompatibilityRule)
            disambiguationRules.add(DisambiguationRule)
        }
        schema.attribute(middle).with {
            compatibilityRules.add(CompatibilityRule)
            disambiguationRules.add(DisambiguationRule)
        }
        schema.attribute(lowest).with {
            compatibilityRules.add(CompatibilityRule)
            disambiguationRules.add(DisambiguationRule)
        }
        schema.attribute(additional).with {
            compatibilityRules.add(CompatibilityRule)
            disambiguationRules.add(DisambiguationRule)
        }
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
        schema.matcher().matches([candidate1], requested, null, explanationBuilder) == [candidate1]
        schema.matcher().matches([candidate1, candidate2, candidate3, candidate4, candidate5, candidate6], requested, null, explanationBuilder) == [candidate1]
        schema.matcher().matches([candidate2, candidate3, candidate4, candidate5, candidate6], requested, null, explanationBuilder) == [candidate2]
        schema.matcher().matches([candidate3, candidate4, candidate5, candidate6], requested, null, explanationBuilder) == [candidate3]
        schema.matcher().matches([candidate4, candidate5, candidate6], requested, null, explanationBuilder) == [candidate4]
        schema.matcher().matches([candidate5, candidate6], requested, null, explanationBuilder) == [candidate5]
        schema.matcher().matches([candidate6], requested, null, explanationBuilder) == [candidate6]
    }

    def "disambiguates extra attributes in precedence order"() {
        def candidate1 = candidate("best", "compatible", "compatible")
        def candidate2 = candidate("compatible", "best", "compatible")
        def candidate3 = candidate("compatible", "compatible", "best")
        def candidate4 = candidate("compatible", "compatible", "compatible")
        def requested = AttributeTestUtil.attributes("unknown": "unknown")

        expect:
        schema.matcher().matches([candidate1, candidate2, candidate3, candidate4], requested, null, explanationBuilder) == [candidate1]
        schema.matcher().matches([candidate2, candidate3, candidate4], requested, null, explanationBuilder) == [candidate2]
        schema.matcher().matches([candidate3, candidate4], requested, null, explanationBuilder) == [candidate3]
    }

    private static AttributeContainerInternal requested(String highestValue, String middleValue, String lowestValue) {
        return candidate(highestValue, middleValue, lowestValue)
    }
    private static AttributeContainerInternal candidate(String highestValue, String middleValue, String lowestValue) {
        return AttributeTestUtil.attributes([highest: highestValue, middle: middleValue, lowest: lowestValue])
    }
}
