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

import com.google.common.collect.LinkedListMultimap
import com.google.common.collect.Multimap
import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.AttributeDisambiguationRule
import org.gradle.api.attributes.MultipleCandidatesDetails
import org.gradle.api.internal.attributes.AttributeContainerInternal
import org.gradle.util.TestUtil
import spock.lang.Specification

class ComponentAttributeMatcherTest extends Specification {

    def schema = new TestSchema()
    def factory = TestUtil.attributesFactory()

    def "selects candidate with same set of attributes and whose values match"() {
        def attr = Attribute.of(String)
        def attr2 = Attribute.of('2', String)
        schema.attribute(attr)
        schema.attribute(attr2)

        given:
        def candidate1 = attributes()
        candidate1.attribute(attr, "value1")
        def candidate2 = attributes()
        candidate2.attribute(attr, "value2")
        def requested = attributes()
        requested.attribute(attr, "value1")

        def matcher = new ComponentAttributeMatcher()

        expect:
        matcher.match(schema, [candidate1, candidate2], requested, null) == [candidate1]
        matcher.match(schema, [candidate2], requested, null) == []

        matcher.isMatching(schema, candidate1, requested)
        !matcher.isMatching(schema, candidate2, requested)
    }

    def "selects candidate with subset of attributes and whose values match"() {
        def attr = Attribute.of(String)
        def attr2 = Attribute.of('2', String)
        schema.attribute(attr)
        schema.attribute(attr2)

        given:
        def candidate1 = attributes()
        candidate1.attribute(attr, "value1")
        def candidate2 = attributes()
        candidate2.attribute(attr, "no match")
        def candidate3 = attributes()
        candidate3.attribute(attr, "value1")
        candidate3.attribute(attr2, "no match")
        def candidate4 = attributes()
        def requested = attributes()
        requested.attribute(attr, "value1")
        requested.attribute(attr2, "value2")

        def matcher = new ComponentAttributeMatcher()

        expect:
        matcher.match(schema, [candidate1, candidate2, candidate3, candidate4], requested, null) == [candidate1]
        matcher.match(schema, [candidate2, candidate3, candidate4], requested, null) == [candidate4]

        matcher.isMatching(schema, candidate1, requested)
        !matcher.isMatching(schema, candidate2, requested)
        !matcher.isMatching(schema, candidate3, requested)
        matcher.isMatching(schema, candidate4, requested)
    }

    def "selects candidate with additional attributes and whose values match"() {
        def attr = Attribute.of(String)
        def attr2 = Attribute.of('2', String)
        schema.attribute(attr)
        schema.attribute(attr2)

        given:
        def candidate1 = attributes()
        candidate1.attribute(attr, "value1")
        candidate1.attribute(attr2, "other")
        def candidate2 = attributes()
        candidate2.attribute(attr, "no match")
        def requested = attributes()
        requested.attribute(attr, "value1")

        def matcher = new ComponentAttributeMatcher()

        expect:
        matcher.match(schema, [candidate1, candidate2], requested, null) == [candidate1]
        matcher.match(schema, [candidate2], requested, null) == []

        matcher.isMatching(schema, candidate1, requested)
        !matcher.isMatching(schema, candidate2, requested)
    }

    def "selects multiple candidates with compatible values"() {
        def attr = Attribute.of(String)
        def attr2 = Attribute.of('2', String)
        schema.attribute(attr)
        schema.attribute(attr2)

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
        matcher.match(schema, [candidate1, candidate2, candidate3, candidate4, candidate5], requested, null) == [candidate1, candidate3, candidate4, candidate5]
    }

    def "applies disambiguation rules and selects intersection of best matches for each attribute"() {
        def attr = Attribute.of(String)
        def attr2 = Attribute.of('2', String)
        schema.attribute(attr)
        schema.accept(attr, "requested", "value1")
        schema.accept(attr, "requested", "value2")
        schema.prefer(attr, "value1")
        schema.attribute(attr2)
        schema.accept(attr2, "requested", "value1")
        schema.accept(attr2, "requested", "value2")
        schema.prefer(attr2, "value2")

        given:
        def candidate1 = attributes()
        candidate1.attribute(attr, "value1")
        candidate1.attribute(attr2, "value1")
        def candidate2 = attributes()
        candidate2.attribute(attr, "no match")
        candidate2.attribute(attr2, "no match")
        def candidate3 = attributes()
        candidate3.attribute(attr, "value2")
        candidate3.attribute(attr2, "value2")
        def candidate4 = attributes()
        def candidate5 = attributes()
        candidate5.attribute(attr, "value1")
        candidate5.attribute(attr2, "value2")
        def requested = attributes()
        requested.attribute(attr, "requested")
        requested.attribute(attr2, "requested")

        def matcher = new ComponentAttributeMatcher()

        expect:
        matcher.match(schema, [candidate1, candidate2, candidate3, candidate4, candidate5], requested, null) == [candidate5]
        matcher.match(schema, [candidate1, candidate2, candidate3, candidate4], requested, null) == [candidate1, candidate3, candidate4]
        matcher.match(schema, [candidate1, candidate2, candidate4], requested, null) == [candidate1]
        matcher.match(schema, [candidate2, candidate4], requested, null) == [candidate4]
    }

    def "rule can disambiguate based on requested value"() {
        def rule = Mock(AttributeDisambiguationRule)
        def attr = Attribute.of(String)
        schema.attribute(attr)
        schema.accept(attr, "requested", "value1")
        schema.accept(attr, "requested", "value2")
        schema.select(attr, rule)

        rule.execute({ it.consumerValue == "requested" }) >> { MultipleCandidatesDetails details -> details.closestMatch("value2") }
        rule.execute({ it.consumerValue == null }) >> { MultipleCandidatesDetails details -> details.closestMatch("value1") }

        given:
        def candidate1 = attributes()
        candidate1.attribute(attr, "value1")
        def candidate2 = attributes()
        candidate2.attribute(attr, "no match")
        def candidate3 = attributes()
        candidate3.attribute(attr, "value2")
        def candidate4 = attributes()
        def requested1 = attributes()
        requested1.attribute(attr, "requested")
        def requested2 = attributes()

        def matcher = new ComponentAttributeMatcher()

        expect:
        matcher.match(schema, [candidate1, candidate2, candidate3, candidate4], requested1, null) == [candidate3]
        matcher.match(schema, [candidate1, candidate2, candidate3, candidate4], requested2, null) == [candidate1]
    }

    def "prefers match with superset of matching attributes"() {
        def attr = Attribute.of(String)
        def attr2 = Attribute.of('2', String)
        schema.attribute(attr)
        schema.attribute(attr2)

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
        matcher.match(schema, [candidate1, candidate2, candidate3, candidate4, candidate5, candidate6], requested, null) == [candidate5]
        matcher.match(schema, [candidate1, candidate2, candidate3, candidate4, candidate6], requested, null) == [candidate1, candidate3, candidate4, candidate6]
        matcher.match(schema, [candidate1, candidate2, candidate4, candidate6], requested, null) == [candidate1, candidate4, candidate6]
        matcher.match(schema, [candidate2, candidate3, candidate4], requested, null) == [candidate3]
    }

    def "disambiguates using ignored producer attributes"() {
        def key1 = Attribute.of("a1", String)
        def key2 = Attribute.of("a2", String)
        schema.attribute(key1)
        schema.attribute(key2)
        schema.prefer(key2, "ignore2")

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

        def matches = matcher.match(schema, [candidate1, candidate2], requested, null)
        matches == [candidate2]
    }

    def "empty producer attributes match any consumer attributes"() {
        given:
        def key1 = Attribute.of("a1", String)
        schema.attribute(key1)

        def requested = attributes()
        def candidate = attributes()
        def candidate2 = attributes().attribute(key1, "1")

        expect:
        def matcher = new ComponentAttributeMatcher()

        matcher.match(schema, [candidate], requested, null) == [candidate]
        matcher.isMatching(schema, candidate, requested)

        matcher.match(schema, [candidate], requested, null) == [candidate]
        matcher.isMatching(schema, candidate, requested)

        matcher.match(schema, [candidate2], requested, null) == [candidate2]
        matcher.isMatching(schema, candidate2, requested)
    }

    def "non-empty producer attributes match empty consumer attributes"() {
        def key1 = Attribute.of("a1", String)
        def key2 = Attribute.of("a2", String)

        given:
        def candidate = attributes()
        def requested = attributes().attribute(key1, "1").attribute(key2, "2")

        expect:
        def matcher = new ComponentAttributeMatcher()

        matcher.match(schema, [candidate], requested, null) == [candidate]
        matcher.isMatching(schema, candidate, requested)
    }

    def "selects fallback when it matches requested and there are no candidates"() {
        def key1 = Attribute.of("a1", String)
        def key2 = Attribute.of("a2", String)
        def key3 = Attribute.of("a3", String)
        schema.attribute(key1)
        schema.attribute(key2)
        schema.attribute(key3)

        given:
        def candidate1 = attributes().attribute(key1, "other")
        def candidate2 = attributes().attribute(key2, "other")
        def fallback1 = attributes()
        def fallback2 = attributes().attribute(key1, "1")
        def fallback3 = attributes().attribute(key3, "3")
        def fallback4 = attributes().attribute(key1, "other")
        def requested = attributes().attribute(key1, "1").attribute(key2, "2")

        expect:
        def matcher = new ComponentAttributeMatcher()

        // No candidates, fallback matches
        matcher.match(schema, [], requested, fallback1) == [fallback1]
        matcher.match(schema, [], requested, fallback2) == [fallback2]
        matcher.match(schema, [], requested, fallback3) == [fallback3]

        // Fallback does not match
        matcher.match(schema, [], requested, fallback4) == []

        // Candidates, fallback matches
        matcher.match(schema, [candidate1, candidate2], requested, fallback1) == []
        matcher.match(schema, [candidate1, candidate2], requested, fallback2) == []
        matcher.match(schema, [candidate1, candidate2], requested, fallback3) == []

        // No fallback
        matcher.match(schema, [candidate1, candidate2], requested, null) == []
        matcher.match(schema, [], requested, null) == []

        // Fallback also a candidate
        matcher.match(schema, [candidate1, fallback4], requested, fallback4) == []
        matcher.match(schema, [candidate1, candidate2, fallback1], requested, fallback1) == [fallback1]
    }

    def "can match when consumer uses more general type for attribute"() {
        def matcher = new ComponentAttributeMatcher()
        def key1 = Attribute.of("a", Number)
        def key2 = Attribute.of("a", Integer)
        schema.attribute(key1)

        def requested = attributes().attribute(key1, 1)
        def c1 = attributes().attribute(key2, 1)
        def c2 = attributes().attribute(key2, 2)

        expect:
        matcher.match(schema, [c1, c2], requested, null) == [c1]
    }

    def "matching fails when attribute has incompatible types in consumer and producer"() {
        def matcher = new ComponentAttributeMatcher()
        def key1 = Attribute.of("a", String)
        def key2 = Attribute.of("a", Number)
        schema.attribute(key1)

        def requested = attributes().attribute(key1, "1")
        def c1 = attributes().attribute(key2, 1)

        when:
        matcher.match(schema, [c1], requested, null)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Unexpected type for attribute 'a' provided. Expected a value of type java.lang.String but found a value of type java.lang.Integer."
    }

    private AttributeContainerInternal attributes() {
        factory.mutable()
    }

    private static class TestSchema implements AttributeSelectionSchema {
        Set<Attribute<?>> attributes = []
        Map<Attribute<?>, Object> preferredValue = [:]
        Map<Attribute<?>, AttributeDisambiguationRule> rules = [:]
        Map<Attribute<?>, Multimap<Object, Object>> compatibleValues = [:]

        void attribute(Attribute<?> attribute) {
            attributes.add(attribute)
        }

        void accept(Attribute<?> attribute, Object consumer, Object producer) {
            if (!compatibleValues.containsKey(attribute)) {
                compatibleValues.put(attribute, LinkedListMultimap.create())
            }
            compatibleValues.get(attribute).put(consumer, producer)
        }

        void select(Attribute<?> attribute, AttributeDisambiguationRule rule) {
            rules.put(attribute, rule)
        }

        void prefer(Attribute<?> attribute, Object value) {
            preferredValue.put(attribute, value)
        }

        @Override
        boolean hasAttribute(Attribute<?> attribute) {
            return attributes.contains(attribute)
        }

        @Override
        boolean matchValue(Attribute<?> attribute, Object requested, Object candidate) {
            if (attributes.contains(attribute)) {
                if (compatibleValues.containsKey(attribute)) {
                    if (compatibleValues.get(attribute).get(requested).contains(candidate)) {
                        return true
                    }
                }
                if (requested == candidate) {
                    return true
                }
            }

            return false
        }

        @Override
        Set<Object> disambiguate(Attribute<?> attribute, Object requested, Set<Object> candidates) {
            def result = new DefaultMultipleCandidateResult(requested, candidates)

            def rule = rules.get(attribute)
            if (rule != null) {
                rule.execute(result)
                return result.matches
            }

            def preferred = preferredValue.get(attribute)
            if (preferred != null && candidates.contains(preferred)) {
                return [preferred]
            }

            candidates
        }
    }
}
