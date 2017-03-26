/*
 * Copyright 2016 the original author or authors.
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
import org.gradle.api.attributes.AttributeDisambiguationRule
import org.gradle.api.attributes.MultipleCandidatesDetails
import org.gradle.api.internal.attributes.DefaultAttributesSchema
import org.gradle.api.internal.attributes.DefaultImmutableAttributesFactory
import org.gradle.api.internal.attributes.DefaultMutableAttributeContainer
import org.gradle.util.TestUtil
import spock.lang.Specification

class ComponentAttributeMatcherTest extends Specification {

    def schema = new DefaultAttributesSchema(new ComponentAttributeMatcher(), TestUtil.instantiatorFactory())
    def factory = new DefaultImmutableAttributesFactory()

    def "selects candidate with same set of attributes and matching values"() {
        def attr = Attribute.of(String)
        def attr2 = Attribute.of('2', String)
        schema.attribute(attr)
        schema.attribute(attr2)

        given:
        def candidate1 = attributes()
        candidate1.attribute(attr, "value1")
        def candidate2 = attributes()
        candidate2.attribute(attr, "value2")
        def candidate3 = attributes()
        candidate3.attribute(attr, "value1")
        candidate3.attribute(attr2, "value2")
        def candidate4 = attributes()
        def requested = attributes()
        requested.attribute(attr, "value1")

        def matcher = new ComponentAttributeMatcher()

        expect:
        matcher.match(schema, [candidate1, candidate2, candidate3, candidate4], requested) == [candidate1]
        matcher.match(schema, [candidate2, candidate3, candidate4], requested) == []

        matcher.isMatching(schema, candidate1, requested)
        !matcher.isMatching(schema, candidate2, requested)
        !matcher.isMatching(schema, candidate3, requested)
        !matcher.isMatching(schema, candidate4, requested)
    }

    def "selects candidate with subset of attributes and matching values when missing attributes considered compatible"() {
        def attr = Attribute.of(String)
        def attr2 = Attribute.of('2', String)
        schema.attribute(attr).compatibilityRules.assumeCompatibleWhenMissing()
        schema.attribute(attr2).compatibilityRules.assumeCompatibleWhenMissing()

        given:
        def candidate1 = attributes()
        candidate1.attribute(attr, "value1")
        def candidate2 = attributes()
        candidate2.attribute(attr, "no match")
        def candidate3 = attributes()
        candidate3.attribute(attr, "value1")
        candidate3.attribute(attr2, "no match")
        def requested = attributes()
        requested.attribute(attr, "value1")
        requested.attribute(attr2, "value2")

        def matcher = new ComponentAttributeMatcher()

        expect:
        matcher.match(schema, [candidate1, candidate2, candidate3], requested) == [candidate1]
        matcher.match(schema, [candidate2, candidate3], requested) == []

        matcher.isMatching(schema, candidate1, requested)
        !matcher.isMatching(schema, candidate2, requested)
        !matcher.isMatching(schema, candidate3, requested)
    }

    def "selects multiple candidates with compatible values"() {
        def attr = Attribute.of(String)
        def attr2 = Attribute.of('2', String)
        schema.attribute(attr).compatibilityRules.assumeCompatibleWhenMissing()
        schema.attribute(attr2).compatibilityRules.assumeCompatibleWhenMissing()

        given:
        def candidate1 = attributes()
        candidate1.attribute(attr, "value1")
        def candidate2 = attributes()
        candidate2.attribute(attr, "no match")
        def candidate3 = attributes()
        candidate3.attribute(attr2, "value2")
        def candidate4 = attributes()
        def candidate5 = attributes()
        candidate5.attribute(attr, "value1")
        def requested = attributes()
        requested.attribute(attr, "value1")
        requested.attribute(attr2, "value2")

        def matcher = new ComponentAttributeMatcher()

        expect:
        matcher.match(schema, [candidate1, candidate2, candidate3, candidate4, candidate5], requested) == [candidate1, candidate3, candidate4, candidate5]
    }

    def "prefers match with superset of matching attributes"() {
        def attr = Attribute.of(String)
        def attr2 = Attribute.of('2', String)
        schema.attribute(attr).compatibilityRules.assumeCompatibleWhenMissing()
        schema.attribute(attr2).compatibilityRules.assumeCompatibleWhenMissing()

        given:
        def candidate1 = attributes()
        candidate1.attribute(attr, "value1")
        def candidate2 = attributes()
        candidate2.attribute(attr, "no match")
        def candidate3 = attributes()
        candidate3.attribute(attr2, "value2")
        def candidate4 = attributes()
        def candidate5 = attributes()
        candidate5.attribute(attr, "value1")
        candidate5.attribute(attr2, "value2")
        def candidate6 = attributes()
        candidate6.attribute(attr, "value1")
        def requested = attributes()
        requested.attribute(attr, "value1")
        requested.attribute(attr2, "value2")

        def matcher = new ComponentAttributeMatcher()

        expect:
        matcher.match(schema, [candidate1, candidate2, candidate3, candidate4, candidate5, candidate6], requested) == [candidate5]
        matcher.match(schema, [candidate1, candidate2, candidate3, candidate4, candidate6], requested) == [candidate1, candidate3, candidate4, candidate6]
        matcher.match(schema, [candidate1, candidate2, candidate4, candidate6], requested) == [candidate1, candidate4, candidate6]
        matcher.match(schema, [candidate2, candidate3, candidate4], requested) == [candidate3]
    }

    def "Matching two attributes with distinct types gives no match" () {
        def key1 = Attribute.of("a1", String)
        def key2 = Attribute.of("a2", String)
        schema.attribute(key1)
        schema.attribute(key2)

        given:
        def candidate = attributes()
        candidate.attribute(key1, "value1")
        def requested = attributes()
        requested.attribute(key2, "value1")

        when:
        def matches = new ComponentAttributeMatcher().match(schema, [candidate], requested)

        then:
        matches == []
    }

    def "can ignore additional producer attributes" () {
        def key1 = Attribute.of("a1", String)
        def key2 = Attribute.of("a2", String)
        schema.attribute(key1)
        schema.attribute(key2)

        given:
        def candidate = attributes()
        candidate.attribute(key1, "value1")
        candidate.attribute(key2, "ignore me")
        def requested = attributes()
        requested.attribute(key1, "value1")

        expect:
        def matcher = new ComponentAttributeMatcher()

        def matches1 = matcher.match(schema, [candidate], requested)
        matches1.empty

        def matches2 = matcher.ignoreAdditionalProducerAttributes().match(schema, [candidate], requested)
        matches2 == [candidate]
    }

    static class SelectValueRule implements AttributeDisambiguationRule<String> {
        @Override
        void execute(MultipleCandidatesDetails<String> details) {
            if (details.candidateValues.contains("ignore2")) {
                details.closestMatch("ignore2")
            }
        }
    }

    def "disambiguates using ignored producer attributes" () {
        def key1 = Attribute.of("a1", String)
        def key2 = Attribute.of("a2", String)
        schema.attribute(key1)
        schema.attribute(key2).disambiguationRules.add(SelectValueRule)

        given:
        def candidate1 = attributes()
        candidate1.attribute(key1, "value1")
        candidate1.attribute(key2, "ignored1")
        def candidate2 = attributes()
        candidate2.attribute(key1, "value1")
        candidate2.attribute(key2, "ignore2")
        def requested = attributes()
        requested.attribute(key1, "value1")

        expect:
        def matcher = new ComponentAttributeMatcher()

        def matches = matcher.ignoreAdditionalProducerAttributes().match(schema, [candidate1, candidate2], requested)
        matches == [candidate2]
    }

    def "can ignore additional consumer attributes" () {
        def key1 = Attribute.of("a1", String)
        def key2 = Attribute.of("a2", String)
        schema.attribute(key1)
        schema.attribute(key2)

        given:
        def candidate = attributes()
        candidate.attribute(key1, "value1")
        def requested = attributes()
        requested.attribute(key1, "value1")
        requested.attribute(key2, "ignore me")

        expect:
        def matcher = new ComponentAttributeMatcher()

        def matches1 = matcher.match(schema, [candidate], requested)
        matches1.empty

        def matches2 = matcher.ignoreAdditionalConsumerAttributes().match(schema, [candidate], requested)
        matches2 == [candidate]
    }

    def "empty producer attributes match empty consumer attributes"() {
        given:
        def key1 = Attribute.of("a1", String)
        schema.attribute(key1)

        def requested = attributes()
        def candidate = attributes()
        def candidate2 = attributes().attribute(key1, "1")

        expect:
        def matcher = new ComponentAttributeMatcher()

        matcher.match(schema, [candidate], requested) == [candidate]
        matcher.isMatching(schema, candidate, requested)

        matcher.ignoreAdditionalProducerAttributes().match(schema, [candidate], requested) == [candidate]
        matcher.ignoreAdditionalProducerAttributes().isMatching(schema, candidate, requested)

        matcher.match(schema, [candidate2], requested) == []
        !matcher.isMatching(schema, candidate2, requested)
    }

    def "non-empty producer attributes match empty consumer attributes when ignoring additional producer attributes"() {
        def key1 = Attribute.of("a1", String)
        def key2 = Attribute.of("a2", String)

        given:
        def requested = attributes()
        def candidate = attributes().attribute(key1, "1").attribute(key2, "2")

        expect:
        def matcher = new ComponentAttributeMatcher().ignoreAdditionalProducerAttributes()

        matcher.match(schema, [candidate], requested) == [candidate]
        matcher.isMatching(schema, candidate, requested)
    }

    def "non-empty producer attributes match empty consumer attributes when extra attributes are compatible when missing"() {
        def key1 = Attribute.of("a1", String)
        def key2 = Attribute.of("a2", String)

        given:
        schema.attribute(key1).compatibilityRules.assumeCompatibleWhenMissing()
        schema.attribute(key2)

        def requested = attributes()
        def candidate = attributes().attribute(key1, "1")
        def candidate2 = attributes().attribute(key1, "1").attribute(key2, "2")

        expect:
        def matcher = new ComponentAttributeMatcher()

        matcher.match(schema, [candidate], requested) == [candidate]
        matcher.isMatching(schema, candidate, requested)

        matcher.match(schema, [candidate2], requested) == []
        !matcher.isMatching(schema, candidate2, requested)
    }

    private DefaultMutableAttributeContainer attributes() {
        new DefaultMutableAttributeContainer(factory)
    }
}
