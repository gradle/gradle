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
import org.gradle.api.internal.attributes.CompatibilityCheckResult
import org.gradle.api.internal.attributes.DefaultImmutableAttributesFactory
import org.gradle.api.internal.attributes.DefaultMutableAttributeContainer
import org.gradle.api.internal.attributes.MultipleCandidatesResult
import spock.lang.Specification

class ComponentAttributeMatcherTest extends Specification {

    def schema = new TestSchema()
    def factory = new DefaultImmutableAttributesFactory()

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
        matcher.match(schema, [candidate1, candidate2], requested) == [candidate1]
        matcher.match(schema, [candidate2], requested) == []

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
        matcher.match(schema, [candidate1, candidate2, candidate3, candidate4], requested) == [candidate1]
        matcher.match(schema, [candidate2, candidate3, candidate4], requested) == [candidate4]

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
        matcher.match(schema, [candidate1, candidate2], requested) == [candidate1]
        matcher.match(schema, [candidate2], requested) == []

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
        matcher.match(schema, [candidate1, candidate2, candidate3, candidate4, candidate5], requested) == [candidate1, candidate3, candidate4, candidate5]
    }

    def "applies disambiguation rules"() {
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
        matcher.match(schema, [candidate1, candidate2, candidate3, candidate4, candidate5], requested) == [candidate5]
        matcher.match(schema, [candidate1, candidate2, candidate3, candidate4], requested) == [candidate1, candidate3, candidate4]
        matcher.match(schema, [candidate1, candidate2, candidate4], requested) == [candidate1]
        matcher.match(schema, [candidate2, candidate4], requested) == [candidate4]
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
        matcher.match(schema, [candidate1, candidate2, candidate3, candidate4, candidate5, candidate6], requested) == [candidate5]
        matcher.match(schema, [candidate1, candidate2, candidate3, candidate4, candidate6], requested) == [candidate1, candidate3, candidate4, candidate6]
        matcher.match(schema, [candidate1, candidate2, candidate4, candidate6], requested) == [candidate1, candidate4, candidate6]
        matcher.match(schema, [candidate2, candidate3, candidate4], requested) == [candidate3]
    }

    def "disambiguates using ignored producer attributes" () {
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

        def matches = matcher.match(schema, [candidate1, candidate2], requested)
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

        matcher.match(schema, [candidate], requested) == [candidate]
        matcher.isMatching(schema, candidate, requested)

        matcher.match(schema, [candidate], requested) == [candidate]
        matcher.isMatching(schema, candidate, requested)

        matcher.match(schema, [candidate2], requested) == [candidate2]
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

        matcher.match(schema, [candidate], requested) == [candidate]
        matcher.isMatching(schema, candidate, requested)
    }

    private DefaultMutableAttributeContainer attributes() {
        new DefaultMutableAttributeContainer(factory)
    }

    private static class TestSchema implements AttributeSelectionSchema {
        Set<Attribute<?>> attributes = []
        Map<Attribute<?>, Object> preferredValue = [:]
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

        void prefer(Attribute<?> attribute, Object value) {
            preferredValue.put(attribute, value)
        }

        @Override
        boolean hasAttribute(Attribute<?> attribute) {
            return attributes.contains(attribute)
        }

        @Override
        void matchValue(Attribute<?> attribute, CompatibilityCheckResult<Object> result) {
            if (attributes.contains(attribute)) {
                if (compatibleValues.containsKey(attribute)) {
                    if (compatibleValues.get(attribute).get(result.consumerValue).contains(result.producerValue)) {
                        result.compatible()
                        return
                    }
                }
                if (result.consumerValue == result.producerValue) {
                    result.compatible()
                    return
                }
            }

            result.incompatible()
        }

        @Override
        void disambiguate(Attribute<?> attribute, MultipleCandidatesResult<Object> result) {
            def preferred = preferredValue.get(attribute)
            if (preferred != null && result.candidateValues.contains(preferred)) {
                result.closestMatch(preferred)
                return
            }

            for (Object match : result.candidateValues) {
                result.closestMatch(match)
            }
        }
    }
}
