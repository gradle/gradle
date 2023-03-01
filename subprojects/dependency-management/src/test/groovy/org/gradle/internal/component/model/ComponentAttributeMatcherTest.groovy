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
import org.gradle.api.Named
import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.AttributeDisambiguationRule
import org.gradle.api.attributes.MultipleCandidatesDetails
import org.gradle.api.internal.attributes.AttributeContainerInternal
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.util.AttributeTestUtil
import spock.lang.Specification

import java.util.stream.Collectors
import java.util.stream.IntStream

import static org.gradle.util.AttributeTestUtil.attributes
import static org.gradle.util.TestUtil.objectFactory

class ComponentAttributeMatcherTest extends Specification {

    def schema = new TestSchema()
    def factory = AttributeTestUtil.attributesFactory()
    def explanationBuilder = Stub(AttributeMatchingExplanationBuilder)

    def "selects candidate with same set of attributes and whose values match"() {
        given:
        def matcher = new ComponentAttributeMatcher(schema)

        def usage = Attribute.of('usage', String)
        schema.attribute(usage)

        def candidate1 = attributes(usage: "match")
        def candidate2 = attributes(usage: "no match")
        def requested = attributes(usage: "match")

        expect:
        matcher.matches([candidate1, candidate2], requested, null, explanationBuilder) == [candidate1]
        matcher.matches([candidate2], requested, null, explanationBuilder) == []

        matcher.isMatching(candidate1, requested)
        !matcher.isMatching(candidate2, requested)
    }

    def "selects candidate with subset of attributes and whose values match"() {
        given:
        def matcher = new ComponentAttributeMatcher(schema)

        def usage = Attribute.of('usage', String)
        schema.attribute(usage)
        def other = Attribute.of('other', String)
        schema.attribute(other)

        def candidate1 = attributes(usage: "match")
        def candidate2 = attributes(usage: "no match")
        def candidate3 = attributes(usage: "match", other: "no match")
        def candidate4 = attributes()

        def requested = attributes(usage: "match", other: "match")

        expect:
        matcher.matches([candidate1, candidate2, candidate3, candidate4], requested, null, explanationBuilder) == [candidate1]
        matcher.matches([candidate2, candidate3, candidate4], requested, null, explanationBuilder) == [candidate4]

        matcher.isMatching(candidate1, requested)
        !matcher.isMatching(candidate2, requested)
        !matcher.isMatching(candidate3, requested)
        matcher.isMatching(candidate4, requested)
    }

    def "selects candidate with additional attributes and whose values match"() {
        given:
        def matcher = new ComponentAttributeMatcher(schema)

        def usage = Attribute.of('usage', String)
        schema.attribute(usage)
        def other = Attribute.of('other', String)
        schema.attribute(other)

        def candidate1 = attributes(usage: "match", other: "dont care")
        def candidate2 = attributes(usage: "no match")
        def requested = attributes(usage: "match")

        expect:
        matcher.matches([candidate1, candidate2], requested, null, explanationBuilder) == [candidate1]
        matcher.matches([candidate2], requested, null, explanationBuilder) == []

        matcher.isMatching(candidate1, requested)
        !matcher.isMatching(candidate2, requested)
    }

    def "selects multiple candidates with compatible values"() {
        given:
        def matcher = new ComponentAttributeMatcher(schema)

        def usage = Attribute.of('usage', String)
        schema.attribute(usage)
        def other = Attribute.of('other', String)
        schema.attribute(other)

        def candidate1 = attributes(usage: "match")
        def candidate2 = attributes(usage: "no match")
        def candidate3 = attributes(other: "match")
        def candidate4 = attributes()
        def candidate5 = attributes(usage: "match")

        def requested = attributes(usage: "match", other: "match")

        expect:
        matcher.matches([candidate1, candidate2, candidate3, candidate4, candidate5], requested, null, explanationBuilder) == [candidate1, candidate3, candidate4, candidate5]
    }

    def "applies disambiguation rules and selects intersection of best matches for each attribute"() {
        given:
        def matcher = new ComponentAttributeMatcher(schema)

        def usage = Attribute.of('usage', String)
        schema.attribute(usage)
        schema.accept(usage, "requested", "compatible")
        schema.accept(usage, "requested", "best")
        schema.prefer(usage, "best")
        def other = Attribute.of('other', String)
        schema.attribute(other)
        schema.accept(other, "requested", "compatible")
        schema.accept(other, "requested", "best")
        schema.prefer(other, "best")

        def candidate1 = attributes(usage: "best", other: "compatible")
        def candidate2 = attributes(usage: "no match", other: "no match")
        def candidate3 = attributes(usage: "compatible", other: "best")
        def candidate4 = attributes()
        def candidate5 = attributes(usage: "best", other: "best")

        def requested = attributes(usage: "requested", other: "requested")

        expect:
        matcher.matches([candidate1, candidate2, candidate3, candidate4, candidate5], requested, null, explanationBuilder) == [candidate5]
        matcher.matches([candidate1, candidate2, candidate3, candidate4], requested, null, explanationBuilder) == [candidate1, candidate3, candidate4]
        matcher.matches([candidate1, candidate2, candidate4], requested, null, explanationBuilder) == [candidate1]
        matcher.matches([candidate2, candidate4], requested, null, explanationBuilder) == [candidate4]
    }

    def "rule can disambiguate based on requested value"() {
        given:
        def matcher = new ComponentAttributeMatcher(schema)

        def usage = Attribute.of('usage', String)
        def rule = new AttributeDisambiguationRule<String>() {
            @Override
            void execute(MultipleCandidatesDetails details) {
                if (details.consumerValue == null) {
                    details.closestMatch("compatible")
                } else if (details.consumerValue == "requested") {
                    details.closestMatch("best")
                }
            }
        }
        schema.attribute(usage)
        schema.accept(usage, "requested", "compatible")
        schema.accept(usage, "requested", "best")
        schema.select(usage, rule)

        def candidate1 = attributes(usage: "compatible")
        def candidate2 = attributes(usage: "no match")
        def candidate3 = attributes(usage: "best")
        def candidate4 = attributes()
        def requested1 = attributes(usage: "requested")
        def requested2 = attributes()


        expect:
        matcher.matches([candidate1, candidate2, candidate3, candidate4], requested1, null, explanationBuilder) == [candidate3]
        matcher.matches([candidate1, candidate2, candidate3, candidate4], requested2, null, explanationBuilder) == [candidate1]
    }

    def "disambiguation rule is presented with all non-null candidate values"() {
        given:
        def matcher = new ComponentAttributeMatcher(schema)
        def usage = Attribute.of("usage", String)
        def rule = new AttributeDisambiguationRule<String>() {
            @Override
            void execute(MultipleCandidatesDetails details) {
                if (details.consumerValue == "requested") {
                    assert details.candidateValues == ["best", "compatible"] as Set
                    details.closestMatch("best")
                }
            }
        }
        schema.attribute(usage)
        schema.accept(usage, "requested", "best")
        schema.accept(usage, "requested", "compatible")
        schema.select(usage, rule)

        def candidate1 = attributes(usage: "best")
        def candidate2 = attributes(usage: "compatible")
        def candidate3 = attributes()
        def requested = attributes(usage: "requested")

        expect:
        matcher.matches([candidate1, candidate2, candidate3], requested, null, explanationBuilder) == [candidate1]
    }

    def "prefers match with superset of matching attributes"() {
        given:
        def matcher = new ComponentAttributeMatcher(schema)

        def usage = Attribute.of('usage', String)
        schema.attribute(usage)
        def other = Attribute.of('other', String)
        schema.attribute(other)

        def candidate1 = attributes(usage: "match")
        def candidate2 = attributes(usage: "no match")
        def candidate3 = attributes(other: "match")
        def candidate4 = attributes()
        def candidate5 = attributes(usage: "match", other: "match")
        def candidate6 = attributes(usage: "match")
        def requested = attributes(usage: "match", other: "match")

        expect:
        matcher.matches([candidate1, candidate2, candidate3, candidate4, candidate5, candidate6], requested, null, explanationBuilder) == [candidate5]
        matcher.matches([candidate1, candidate2, candidate3, candidate4, candidate6], requested, null, explanationBuilder) == [candidate1, candidate3, candidate4, candidate6]
        matcher.matches([candidate1, candidate2, candidate4, candidate6], requested, null, explanationBuilder) == [candidate1, candidate4, candidate6]
        matcher.matches([candidate2, candidate3, candidate4], requested, null, explanationBuilder) == [candidate3]
    }

    def "disambiguates multiple matches using extra attributes from producer"() {
        given:
        def matcher = new ComponentAttributeMatcher(schema)
        def usage = Attribute.of("usage", String)
        def other = Attribute.of("other", String)
        schema.attribute(usage)
        schema.attribute(other)
        schema.prefer(other, "best")

        def candidate1 = attributes(usage: "match", other: "ignored")
        def candidate2 = attributes(usage: "match", other: "best")
        def requested = attributes(usage: "match")

        expect:
        def matches = matcher.matches([candidate1, candidate2], requested, null, explanationBuilder)
        matches == [candidate2]
    }

    def "ignores extra attributes if match is found after disambiguation requested attributes"() {
        given:
        def matcher = new ComponentAttributeMatcher(schema)

        def usage = Attribute.of("usage", String)
        schema.attribute(usage)
        schema.accept(usage, "foo", "compatible")
        schema.accept(usage, "foo", "best")
        schema.prefer(usage, "best")

        def other = Attribute.of("other", String)
        schema.attribute(other)
        schema.prefer(other, "best")

        def candidate1 = attributes(usage: "compatible", other: "ignored")
        def candidate2 = attributes(usage: "best", other: "best")
        def requested = attributes(usage: "foo")

        expect:
        def matches = matcher.matches([candidate1, candidate2], requested, null, explanationBuilder)
        matches == [candidate2]
    }

    def "empty consumer attributes match any producer attributes"() {
        given:
        def matcher = new ComponentAttributeMatcher(schema)

        def usage = Attribute.of("usage", String)
        schema.attribute(usage)

        def candidate1 = attributes()
        def candidate2 = attributes(usage: "ignored")
        def requested = attributes()

        expect:
        matcher.matches([candidate1, candidate2], requested, null, explanationBuilder) == [candidate1, candidate2]

        matcher.matches([candidate1], requested, null, explanationBuilder) == [candidate1]
        matcher.isMatching(candidate1, requested)

        matcher.matches([candidate2], requested, null, explanationBuilder) == [candidate2]
        matcher.isMatching(candidate2, requested)
    }

    def "non-empty consumer attributes match empty producer attributes"() {
        given:
        def matcher = new ComponentAttributeMatcher(schema)

        def candidate = attributes()
        def requested = attributes(usage: "dont care", other: "dont care")

        expect:
        matcher.matches([candidate], requested, null, explanationBuilder) == [candidate]
        matcher.isMatching(candidate, requested)
    }

    def "selects fallback when it matches requested and there are no candidates"() {
        given:
        def matcher = new ComponentAttributeMatcher(schema)

        def usage = Attribute.of("usage", String)
        def other = Attribute.of("other", String)
        def another = Attribute.of("another", String)
        schema.attribute(usage)
        schema.attribute(other)
        schema.attribute(another)

        def candidate1 = attributes(usage: "no match")
        def candidate2 = attributes(other: "no match")
        def fallback1 = attributes()
        def fallback2 = attributes(usage: "match")
        def fallback3 = attributes(another: "dont care")
        def fallback4 = attributes(usage: "other")
        def requested = attributes(usage: "match", other: "match")

        expect:
        // No candidates, fallback matches
        matcher.matches([], requested, fallback1, explanationBuilder) == [fallback1]
        matcher.matches([], requested, fallback2, explanationBuilder) == [fallback2]
        matcher.matches([], requested, fallback3, explanationBuilder) == [fallback3]

        // Fallback does not match
        matcher.matches([], requested, fallback4, explanationBuilder) == []

        // Candidates, fallback matches
        matcher.matches([candidate1, candidate2], requested, fallback1, explanationBuilder) == []
        matcher.matches([candidate1, candidate2], requested, fallback2, explanationBuilder) == []
        matcher.matches([candidate1, candidate2], requested, fallback3, explanationBuilder) == []

        // No fallback
        matcher.matches([candidate1, candidate2], requested, null, explanationBuilder) == []
        matcher.matches([], requested, null, explanationBuilder) == []

        // Fallback also a candidate
        matcher.matches([candidate1, fallback4], requested, fallback4, explanationBuilder) == []
        matcher.matches([candidate1, candidate2, fallback1], requested, fallback1, explanationBuilder) == [fallback1]
    }

    def "can match when consumer uses more general type for attribute"() {
        given:
        def matcher = new ComponentAttributeMatcher(schema)

        def consumer = Attribute.of("a", Number)
        def producer = Attribute.of("a", Integer)
        schema.attribute(consumer)

        def candidate1 = attributes().attribute(producer, 1)
        def candidate2 = attributes().attribute(producer, 2)
        def requested = attributes().attribute(consumer, 1)

        expect:
        matcher.matches([candidate1, candidate2], requested, null, explanationBuilder) == [candidate1]
    }

    def "can match when producer uses desugared attribute of type Named"() {
        given:
        def matcher = new ComponentAttributeMatcher(schema)

        def consumer = Attribute.of("a", NamedTestAttribute)
        def producer = Attribute.of("a", String)
        schema.attribute(consumer)

        def candidate1 = attributes().attribute(producer, "name1")
        def candidate2 = attributes().attribute(producer, "name2")
        def requested = attributes().attribute(consumer, objectFactory().named(NamedTestAttribute, "name1"))

        expect:
        matcher.matches([candidate1, candidate2], requested, null, explanationBuilder) == [candidate1]
    }

    def "can match when consumer uses desugared attribute of type Named"() {
        given:
        def matcher = new ComponentAttributeMatcher(schema)

        def consumer = Attribute.of("a", String)
        def producer = Attribute.of("a", NamedTestAttribute)
        schema.attribute(consumer)

        def candidate1 = attributes().attribute(producer, objectFactory().named(NamedTestAttribute, "name1"))
        def candidate2 = attributes().attribute(producer, objectFactory().named(NamedTestAttribute, "name2"))
        def requested = attributes().attribute(consumer, "name1")

        expect:
        matcher.matches([candidate1, candidate2], requested, null, explanationBuilder) == [candidate1]
    }

    def "can match when producer uses desugared attribute of type Enum"() {
        given:
        def matcher = new ComponentAttributeMatcher(schema)

        def consumer = Attribute.of("a", EnumTestAttribute)
        def producer = Attribute.of("a", String)
        schema.attribute(consumer)

        def candidate1 = attributes().attribute(producer, "NAME1")
        def candidate2 = attributes().attribute(producer, "NAME2")
        def requested = attributes().attribute(consumer, EnumTestAttribute.NAME1)

        expect:
        matcher.matches([candidate1, candidate2], requested, null, explanationBuilder) == [candidate1]
    }

    def "can match when consumer uses desugared attribute of type Enum"() {
        given:
        def matcher = new ComponentAttributeMatcher(schema)

        def consumer = Attribute.of("a", String)
        def producer = Attribute.of("a", EnumTestAttribute)
        schema.attribute(consumer)

        def candidate1 = attributes().attribute(producer, EnumTestAttribute.NAME1)
        def candidate2 = attributes().attribute(producer, EnumTestAttribute.NAME2)
        def requested = attributes().attribute(consumer, "NAME1")

        expect:
        matcher.matches([candidate1, candidate2], requested, null, explanationBuilder) == [candidate1]
    }

    def "cannot match when producer uses desugared attribute of unsupported type"() {
        given:
        def matcher = new ComponentAttributeMatcher(schema)

        def consumer = Attribute.of("a", NotSerializableInGradleMetadataAttribute)
        def producer = Attribute.of("a", String)
        schema.attribute(consumer)

        def candidate1 = attributes().attribute(producer, "name1")
        def candidate2 = attributes().attribute(producer, "name2")
        def requested = attributes().attribute(consumer, new NotSerializableInGradleMetadataAttribute("name1"))

        when:
        matcher.matches([candidate1, candidate2], requested, null, explanationBuilder)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Unexpected type for attribute 'a' provided. Expected a value of type org.gradle.internal.component.model.ComponentAttributeMatcherTest${'$'}NotSerializableInGradleMetadataAttribute but found a value of type java.lang.String."
    }

    def "cannot match when consumer uses desugared attribute of unsupported type"() {
        given:
        def matcher = new ComponentAttributeMatcher(schema)

        def consumer = Attribute.of("a", String)
        def producer = Attribute.of("a", NotSerializableInGradleMetadataAttribute)
        schema.attribute(consumer)

        def candidate1 = attributes().attribute(producer, new NotSerializableInGradleMetadataAttribute("name1"))
        def candidate2 = attributes().attribute(producer, new NotSerializableInGradleMetadataAttribute("name2"))
        def requested = attributes().attribute(consumer, "name1")

        when:
        matcher.matches([candidate1, candidate2], requested, null, explanationBuilder)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Unexpected type for attribute 'a' provided. Expected a value of type java.lang.String but found a value of type org.gradle.internal.component.model.ComponentAttributeMatcherTest${'$'}NotSerializableInGradleMetadataAttribute."
    }

    def "matching fails when attribute has incompatible types in consumer and producer"() {
        given:
        def matcher = new ComponentAttributeMatcher(schema)

        def consumer = Attribute.of("a", String)
        def producer = Attribute.of("a", Number)
        schema.attribute(consumer)

        def candidate = attributes().attribute(producer, 1)
        def requested = attributes().attribute(consumer, "1")

        when:
        matcher.matches([candidate], requested, null, explanationBuilder)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Unexpected type for attribute 'a' provided. Expected a value of type java.lang.String but found a value of type java.lang.Integer."
    }

    def "prefers a strict match with requested values"() {
        given:
        def matcher = new ComponentAttributeMatcher(schema)

        def usage = Attribute.of("usage", String)
        def other = Attribute.of("other", String)
        schema.attribute(usage)
        schema.attribute(other)

        def candidate1 = attributes(usage: 'match')
        def candidate2 = attributes(usage: 'match', other: 'foo')
        def requested = attributes(usage: 'match')

        expect:
        def matches = matcher.matches([candidate1, candidate2], requested, null, explanationBuilder)
        matches == [candidate1]
    }

    def "prefers a shorter match with compatible requested values and more than one extra attribute (type: #type)"() {
        given:
        def matcher = new ComponentAttributeMatcher(schema)

        def usage = Attribute.of("usage", String)
        def bundling = Attribute.of("bundling", type)
        def status = Attribute.of("status", String)

        schema.with {
            attribute(usage)
            attribute(bundling)
            attribute(status)
            accept(usage, 'java-api', 'java-api-extra')
            accept(usage, 'java-api', 'java-runtime-extra')
            prefer(usage, 'java-api-extra')
        }

        def candidate1 = attributes(usage: 'java-api-extra', status: 'integration')
        def candidate2 = attributes(usage: 'java-runtime-extra', status: 'integration')
        def candidate3 = attributes(usage: 'java-api-extra', status: 'integration', bundling: value1)
        def candidate4 = attributes(usage: 'java-runtime-extra', status: 'integration', bundling: value2)
        def requested = attributes(usage: 'java-api')

        when:
        def result = matcher.matches([candidate1, candidate2, candidate3, candidate4], requested, null, explanationBuilder)
        then:
        result == [candidate1]

        when: // check with a different attribute order
        candidate1 = attributes(usage: 'java-api-extra', status: 'integration')
        candidate2 = attributes(usage: 'java-runtime-extra', status: 'integration')
        candidate3 = attributes(usage: 'java-api-extra', bundling: value2, status: 'integration')
        candidate4 = attributes(usage: 'java-runtime-extra', bundling: value1, status: 'integration')

        result = matcher.matches([candidate1, candidate2, candidate3, candidate4], requested, null, explanationBuilder)

        then:
        result == [candidate1]

        when: // yet another attribute order
        candidate1 = attributes(status: 'integration', usage: 'java-api-extra')
        candidate2 = attributes(usage: 'java-runtime-extra', status: 'integration')
        candidate3 = attributes(bundling: value1, status: 'integration', usage: 'java-api-extra')
        candidate4 = attributes(status: 'integration', usage: 'java-runtime-extra', bundling: value2)

        result = matcher.matches([candidate1, candidate2, candidate3, candidate4], requested, null, explanationBuilder)

        then:
        result == [candidate1]

        where:
        type                | value1        | value2
        String              | "embedded"    | "embedded"
        EnumTestAttribute   | "NAME1"       | "NAME2"
        NamedTestAttribute  | "foo"         | "bar"
    }

    private AttributeContainerInternal attributes() {
        factory.mutable()
    }

    interface NamedTestAttribute extends Named { }
    enum EnumTestAttribute { NAME1, NAME2 }
    static class NotSerializableInGradleMetadataAttribute implements Serializable {
        String name

        NotSerializableInGradleMetadataAttribute(String name) {
            this.name = name
        }
    }

    private static class TestSchema implements AttributeSelectionSchema {
        Set<Attribute<?>> attributes = []
        Map<String, Attribute<?>> attributesByName = [:]
        Map<Attribute<?>, Object> preferredValue = [:]
        Map<Attribute<?>, AttributeDisambiguationRule> rules = [:]
        Map<Attribute<?>, Multimap<Object, Object>> compatibleValues = [:]

        void attribute(Attribute<?> attribute) {
            attributes.add(attribute)
            attributesByName.put(attribute.getName(), attribute)
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
        Attribute<?> getAttribute(String name) {
            return attributesByName.get(name)
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

            null
        }

        @Override
        Attribute<?>[] collectExtraAttributes(ImmutableAttributes[] candidates, ImmutableAttributes requested) {
            AttributeSelectionUtils.collectExtraAttributes(this, candidates, requested)
        }

        @Override
        PrecedenceResult orderByPrecedence(Collection<Attribute<?>> requested) {
            return new PrecedenceResult(IntStream.range(0, requested.size()).boxed().collect(Collectors.toList()))
        }
    }
}
