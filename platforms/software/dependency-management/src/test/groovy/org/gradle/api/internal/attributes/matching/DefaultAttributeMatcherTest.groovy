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

package org.gradle.api.internal.attributes.matching

import org.gradle.api.Named
import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.AttributeCompatibilityRule
import org.gradle.api.attributes.AttributeDisambiguationRule
import org.gradle.api.attributes.CompatibilityCheckDetails
import org.gradle.api.attributes.MultipleCandidatesDetails
import org.gradle.api.internal.attributes.DefaultAttributesSchema
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.api.internal.attributes.immutable.ImmutableAttributesSchema
import org.gradle.util.AttributeTestUtil
import org.gradle.util.SnapshotTestUtil
import org.gradle.util.TestUtil
import spock.lang.Specification

import javax.inject.Inject

import static org.gradle.util.AttributeTestUtil.attributes
import static org.gradle.util.AttributeTestUtil.attributesTyped
import static org.gradle.util.TestUtil.objectFactory

class DefaultAttributeMatcherTest extends Specification {

    def "selects candidate with same set of attributes and whose values match"() {
        given:
        def matcher = newMatcher {
            attribute(Attribute.of('usage', String))
        }

        def candidate1 = candidate(usage: "match")
        def candidate2 = candidate(usage: "no match")
        def requested = attributes(usage: "match")

        expect:
        matcher.matchMultipleCandidates([candidate1, candidate2], requested) == [candidate1]
        matcher.matchMultipleCandidates([candidate2], requested) == []

        matcher.isMatchingCandidate(candidate1.attributes, requested)
        !matcher.isMatchingCandidate(candidate2.attributes, requested)
    }

    def "selects candidate with subset of attributes and whose values match"() {
        given:
        def matcher = newMatcher {
            attribute(Attribute.of('usage', String))
            attribute(Attribute.of('other', String))
        }

        def candidate1 = candidate(usage: "match")
        def candidate2 = candidate(usage: "no match")
        def candidate3 = candidate(usage: "match", other: "no match")
        def candidate4 = candidate()

        def requested = attributes(usage: "match", other: "match")

        expect:
        matcher.matchMultipleCandidates([candidate1, candidate2, candidate3, candidate4], requested) == [candidate1]
        matcher.matchMultipleCandidates([candidate2, candidate3, candidate4], requested) == [candidate4]

        matcher.isMatchingCandidate(candidate1.attributes, requested)
        !matcher.isMatchingCandidate(candidate2.attributes, requested)
        !matcher.isMatchingCandidate(candidate3.attributes, requested)
        matcher.isMatchingCandidate(candidate4.attributes, requested)
    }

    def "selects candidate with additional attributes and whose values match"() {
        given:
        def matcher = newMatcher {
            attribute(Attribute.of('usage', String))
            attribute(Attribute.of('other', String))
        }

        def candidate1 = candidate(usage: "match", other: "dont care")
        def candidate2 = candidate(usage: "no match")
        def requested = attributes(usage: "match")

        expect:
        matcher.matchMultipleCandidates([candidate1, candidate2], requested) == [candidate1]
        matcher.matchMultipleCandidates([candidate2], requested) == []

        matcher.isMatchingCandidate(candidate1.attributes, requested)
        !matcher.isMatchingCandidate(candidate2.attributes, requested)
    }

    def "selects multiple candidates with compatible values"() {
        given:
        def matcher = newMatcher {
            attribute(Attribute.of('usage', String))
            attribute(Attribute.of('other', String))
        }

        def candidate1 = candidate(usage: "match")
        def candidate2 = candidate(usage: "no match")
        def candidate3 = candidate(other: "match")
        def candidate4 = candidate()
        def candidate5 = candidate(usage: "match")

        def requested = attributes(usage: "match", other: "match")

        expect:
        matcher.matchMultipleCandidates([candidate1, candidate2, candidate3, candidate4, candidate5], requested) == [candidate1, candidate3, candidate4, candidate5]
    }

    def "applies disambiguation rules and selects intersection of best matches for each attribute"() {
        given:
        def matcher = newMatcher {
            def usage = Attribute.of('usage', String)
            attribute(usage)
            accept(usage, "requested", "compatible")
            accept(usage, "requested", "best")
            prefer(usage, "best")

            def other = Attribute.of('other', String)
            attribute(other)
            accept(other, "requested", "compatible")
            accept(other, "requested", "best")
            prefer(other, "best")
        }

        def candidate1 = candidate(usage: "best", other: "compatible")
        def candidate2 = candidate(usage: "no match", other: "no match")
        def candidate3 = candidate(usage: "compatible", other: "best")
        def candidate4 = candidate()
        def candidate5 = candidate(usage: "best", other: "best")

        def requested = attributes(usage: "requested", other: "requested")

        expect:
        matcher.matchMultipleCandidates([candidate1, candidate2, candidate3, candidate4, candidate5], requested) == [candidate5]
        matcher.matchMultipleCandidates([candidate1, candidate2, candidate3, candidate4], requested) == [candidate1, candidate3, candidate4]
        matcher.matchMultipleCandidates([candidate1, candidate2, candidate4], requested) == [candidate1]
        matcher.matchMultipleCandidates([candidate2, candidate4], requested) == [candidate4]
    }

    static class DefaultToCompatible implements AttributeDisambiguationRule<String> {
        @Override
        void execute(MultipleCandidatesDetails<String> details) {
            if (details.consumerValue == null) {
                details.closestMatch("compatible")
            } else if (details.consumerValue == "requested") {
                details.closestMatch("best")
            }
        }
    }

    def "rule can disambiguate based on requested value"() {
        given:
        def usage = Attribute.of('usage', String)

        def matcher = newMatcher {
            attribute(usage).disambiguationRules.add(DefaultToCompatible)
            accept(usage, "requested", "compatible")
            accept(usage, "requested", "best")
        }

        def candidate1 = candidate(usage: "compatible")
        def candidate2 = candidate(usage: "no match")
        def candidate3 = candidate(usage: "best")
        def candidate4 = candidate()
        def requested1 = attributes(usage: "requested")
        def requested2 = ImmutableAttributes.EMPTY

        expect:
        matcher.matchMultipleCandidates([candidate1, candidate2, candidate3, candidate4], requested1) == [candidate3]
        matcher.matchMultipleCandidates([candidate1, candidate2, candidate3, candidate4], requested2) == [candidate1]
    }

    static class ChooseBest implements AttributeDisambiguationRule<String> {
        @Override
        void execute(MultipleCandidatesDetails<String> details) {
            if (details.consumerValue == "requested") {
                assert details.candidateValues == ["best", "compatible"] as Set
                details.closestMatch("best")
            }
        }
    }

    def "disambiguation rule is presented with all non-null candidate values"() {
        given:
        def usage = Attribute.of("usage", String)
        def matcher = newMatcher {
            attribute(usage).disambiguationRules.add(ChooseBest)
            accept(usage, "requested", "best")
            accept(usage, "requested", "compatible")
        }

        def candidate1 = candidate(usage: "best")
        def candidate2 = candidate(usage: "compatible")
        def candidate3 = candidate()
        def requested = attributes(usage: "requested")

        expect:
        matcher.matchMultipleCandidates([candidate1, candidate2, candidate3], requested) == [candidate1]
    }

    def "prefers match with superset of matching attributes"() {
        given:
        def matcher = newMatcher {
            attribute(Attribute.of('usage', String))
            attribute(Attribute.of('other', String))
        }

        def candidate1 = candidate(usage: "match")
        def candidate2 = candidate(usage: "no match")
        def candidate3 = candidate(other: "match")
        def candidate4 = candidate()
        def candidate5 = candidate(usage: "match", other: "match")
        def candidate6 = candidate(usage: "match")
        def requested = attributes(usage: "match", other: "match")

        expect:
        matcher.matchMultipleCandidates([candidate1, candidate2, candidate3, candidate4, candidate5, candidate6], requested) == [candidate5]
        matcher.matchMultipleCandidates([candidate1, candidate2, candidate3, candidate4, candidate6], requested) == [candidate1, candidate3, candidate4, candidate6]
        matcher.matchMultipleCandidates([candidate1, candidate2, candidate4, candidate6], requested) == [candidate1, candidate4, candidate6]
        matcher.matchMultipleCandidates([candidate2, candidate3, candidate4], requested) == [candidate3]
    }

    def "disambiguates multiple matches using extra attributes from producer"() {
        given:
        def matcher = newMatcher {
            attribute(Attribute.of('usage', String))

            def other = Attribute.of('other', String)
            attribute(other)
            prefer(other, "best")
        }

        def candidate1 = candidate(usage: "match", other: "ignored")
        def candidate2 = candidate(usage: "match", other: "best")
        def requested = attributes(usage: "match")

        expect:
        def matches = matcher.matchMultipleCandidates([candidate1, candidate2], requested)
        matches == [candidate2]
    }

    def "ignores extra attributes if match is found after disambiguation requested attributes"() {
        given:
        def matcher = newMatcher {
            def usage = Attribute.of("usage", String)
            attribute(usage)
            accept(usage, "foo", "compatible")
            accept(usage, "foo", "best")
            prefer(usage, "best")

            def other = Attribute.of("other", String)
            attribute(other)
            prefer(other, "best")
        }

        def candidate1 = candidate(usage: "compatible", other: "ignored")
        def candidate2 = candidate(usage: "best", other: "best")
        def requested = attributes(usage: "foo")

        expect:
        def matches = matcher.matchMultipleCandidates([candidate1, candidate2], requested)
        matches == [candidate2]
    }

    def "empty consumer attributes match any producer attributes"() {
        given:
        def matcher = newMatcher {
            attribute(Attribute.of("usage", String))
        }

        def candidate1 = candidate()
        def candidate2 = candidate(usage: "ignored")
        def requested = ImmutableAttributes.EMPTY

        expect:
        matcher.matchMultipleCandidates([candidate1, candidate2], requested) == [candidate1, candidate2]

        matcher.matchMultipleCandidates([candidate1], requested) == [candidate1]
        matcher.isMatchingCandidate(candidate1.attributes, requested)

        matcher.matchMultipleCandidates([candidate2], requested) == [candidate2]
        matcher.isMatchingCandidate(candidate2.attributes, requested)
    }

    def "non-empty consumer attributes match empty producer attributes"() {
        given:
        def matcher = newMatcher()

        def candidate = candidate()
        def requested = attributes(usage: "dont care", other: "dont care")

        expect:
        matcher.matchMultipleCandidates([candidate], requested) == [candidate]
        matcher.isMatchingCandidate(candidate.attributes, requested)
    }

    def "can match when consumer uses more general type for attribute"() {
        given:
        def consumer = Attribute.of("a", Number)
        def producer = Attribute.of("a", Integer)

        def matcher = newMatcher {
            attribute(consumer)
        }

        def candidate1 = candidateTyped((producer): 1)
        def candidate2 = candidateTyped((producer): 2)
        def requested = attributesTyped((consumer): 1)

        expect:
        matcher.matchMultipleCandidates([candidate1, candidate2], requested) == [candidate1]
    }

    def "can match when producer uses desugared attribute of type Named"() {
        given:
        def consumer = Attribute.of("a", NamedTestAttribute)
        def producer = Attribute.of("a", String)

        def matcher = newMatcher {
            attribute(consumer)
        }

        def candidate1 = candidateTyped((producer): "name1")
        def candidate2 = candidateTyped((producer): "name2")
        def requested = attributesTyped((consumer): objectFactory().named(NamedTestAttribute, "name1"))

        expect:
        matcher.matchMultipleCandidates([candidate1, candidate2], requested) == [candidate1]
    }

    def "can match when consumer uses desugared attribute of type Named"() {
        given:
        def consumer = Attribute.of("a", String)
        def producer = Attribute.of("a", NamedTestAttribute)

        def matcher = newMatcher {
            attribute(consumer)
        }

        def candidate1 = candidateTyped((producer): objectFactory().named(NamedTestAttribute, "name1"))
        def candidate2 = candidateTyped((producer): objectFactory().named(NamedTestAttribute, "name2"))
        def requested = attributesTyped((consumer): "name1")

        expect:
        matcher.matchMultipleCandidates([candidate1, candidate2], requested) == [candidate1]
    }

    def "can match when producer uses desugared attribute of type Enum"() {
        given:
        def consumer = Attribute.of("a", EnumTestAttribute)
        def producer = Attribute.of("a", String)

        def matcher = newMatcher {
            attribute(consumer)
        }

        def candidate1 = candidateTyped((producer): "NAME1")
        def candidate2 = candidateTyped((producer): "NAME2")
        def requested = attributesTyped((consumer): EnumTestAttribute.NAME1)

        expect:
        matcher.matchMultipleCandidates([candidate1, candidate2], requested) == [candidate1]
    }

    def "can match when consumer uses desugared attribute of type Enum"() {
        given:
        def consumer = Attribute.of("a", String)
        def producer = Attribute.of("a", EnumTestAttribute)

        def matcher = newMatcher {
            attribute(consumer)
        }

        def candidate1 = candidateTyped((producer): EnumTestAttribute.NAME1)
        def candidate2 = candidateTyped((producer): EnumTestAttribute.NAME2)
        def requested = attributesTyped((consumer): "NAME1")

        expect:
        matcher.matchMultipleCandidates([candidate1, candidate2], requested) == [candidate1]
    }

    def "cannot match when producer uses desugared attribute of unsupported type"() {
        given:
        def consumer = Attribute.of("a", NotSerializableInGradleMetadataAttribute)
        def producer = Attribute.of("a", String)

        def matcher = newMatcher {
            attribute(consumer)
        }

        def candidate1 = candidateTyped((producer): "name1")
        def candidate2 = candidateTyped((producer): "name2")
        def requested = attributesTyped((consumer): new NotSerializableInGradleMetadataAttribute("name1"))

        when:
        matcher.matchMultipleCandidates([candidate1, candidate2], requested)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Unexpected type for attribute 'a' provided. Expected a value of type org.gradle.api.internal.attributes.matching.DefaultAttributeMatcherTest\$NotSerializableInGradleMetadataAttribute but found a value of type java.lang.String."
    }

    def "cannot match when consumer uses desugared attribute of unsupported type"() {
        given:
        def consumer = Attribute.of("a", String)
        def producer = Attribute.of("a", NotSerializableInGradleMetadataAttribute)

        def matcher = newMatcher {
            attribute(consumer)
        }

        def candidate1 = candidateTyped((producer): new NotSerializableInGradleMetadataAttribute("name1"))
        def candidate2 = candidateTyped((producer): new NotSerializableInGradleMetadataAttribute("name2"))
        def requested = attributesTyped((consumer): "name1")

        when:
        matcher.matchMultipleCandidates([candidate1, candidate2], requested)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Unexpected type for attribute 'a' provided. Expected a value of type java.lang.String but found a value of type org.gradle.api.internal.attributes.matching.DefaultAttributeMatcherTest\$NotSerializableInGradleMetadataAttribute."
    }

    def "matching fails when attribute has incompatible types in consumer and producer"() {
        given:
        def consumer = Attribute.of("a", String)
        def producer = Attribute.of("a", Number)

        def matcher = newMatcher {
            attribute(consumer)
        }

        def candidate = candidateTyped((producer): 1)
        def requested = attributesTyped((consumer): "1")

        when:
        matcher.matchMultipleCandidates([candidate], requested)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Unexpected type for attribute 'a' provided. Expected a value of type java.lang.String but found a value of type java.lang.Integer."
    }

    def "prefers a strict match with requested values"() {
        given:
        def matcher = newMatcher {
            attribute(Attribute.of("usage", String))
            attribute(Attribute.of("other", String))
        }

        def candidate1 = candidate(usage: 'match')
        def candidate2 = candidate(usage: 'match', other: 'foo')
        def requested = attributes(usage: 'match')

        expect:
        def matches = matcher.matchMultipleCandidates([candidate1, candidate2], requested)
        matches == [candidate1]
    }

    def "prefers a shorter match with compatible requested values and more than one extra attribute (type: #type)"() {
        given:
        def matcher = newMatcher {
            def usage = Attribute.of("usage", String)
            def bundling = Attribute.of("bundling", type)
            def status = Attribute.of("status", String)

            attribute(usage)
            attribute(bundling)
            attribute(status)

            accept(usage, 'java-api', 'java-api-extra')
            accept(usage, 'java-api', 'java-runtime-extra')
            prefer(usage, 'java-api-extra')
        }

        def candidate1 = candidate(usage: 'java-api-extra', status: 'integration')
        def candidate2 = candidate(usage: 'java-runtime-extra', status: 'integration')
        def candidate3 = candidate(usage: 'java-api-extra', status: 'integration', bundling: value1)
        def candidate4 = candidate(usage: 'java-runtime-extra', status: 'integration', bundling: value2)
        def requested = attributes(usage: 'java-api')

        when:
        def result = matcher.matchMultipleCandidates([candidate1, candidate2, candidate3, candidate4], requested)
        then:
        result == [candidate1]

        when: // check with a different attribute order
        candidate1 = candidate(usage: 'java-api-extra', status: 'integration')
        candidate2 = candidate(usage: 'java-runtime-extra', status: 'integration')
        candidate3 = candidate(usage: 'java-api-extra', bundling: value2, status: 'integration')
        candidate4 = candidate(usage: 'java-runtime-extra', bundling: value1, status: 'integration')

        result = matcher.matchMultipleCandidates([candidate1, candidate2, candidate3, candidate4], requested)

        then:
        result == [candidate1]

        when: // yet another attribute order
        candidate1 = candidate(status: 'integration', usage: 'java-api-extra')
        candidate2 = candidate(usage: 'java-runtime-extra', status: 'integration')
        candidate3 = candidate(bundling: value1, status: 'integration', usage: 'java-api-extra')
        candidate4 = candidate(status: 'integration', usage: 'java-runtime-extra', bundling: value2)

        result = matcher.matchMultipleCandidates([candidate1, candidate2, candidate3, candidate4], requested)

        then:
        result == [candidate1]

        where:
        type                | value1        | value2
        String              | "embedded"    | "embedded"
        EnumTestAttribute   | "NAME1"       | "NAME2"
        NamedTestAttribute  | "foo"         | "bar"
    }

    private static AttributeMatchingCandidate candidate() {
        new ImmutableAttributesBackedMatchingCandidate(ImmutableAttributes.EMPTY)
    }

    private static AttributeMatchingCandidate candidate(Map<String, String> attributes) {
        new ImmutableAttributesBackedMatchingCandidate(AttributeTestUtil.attributes(attributes))
    }

    private static AttributeMatchingCandidate candidateTyped(Map<Attribute<?>, Object> attributes) {
        new ImmutableAttributesBackedMatchingCandidate(attributesTyped(attributes))
    }

    interface NamedTestAttribute extends Named { }
    enum EnumTestAttribute { NAME1, NAME2 }
    static class NotSerializableInGradleMetadataAttribute implements Serializable {
        String name

        NotSerializableInGradleMetadataAttribute(String name) {
            this.name = name
        }
    }

    private AttributeMatcher newMatcher(@DelegatesTo(TestSchema) Closure<?> action = {}) {
        def mutable = new TestSchema()

        action.delegate = mutable
        action(mutable)

        def services = AttributeTestUtil.services()
        def immutable = services.getSchemaFactory().create(mutable)
        services.getMatcher(immutable, ImmutableAttributesSchema.EMPTY)
    }

    private class TestSchema extends DefaultAttributesSchema {
        TestSchema() {
            super(TestUtil.instantiatorFactory(), SnapshotTestUtil.isolatableFactory())
        }

        void accept(Attribute<?> attribute, Object consumer, Object producer) {
            this.attribute(attribute).compatibilityRules.add(AcceptingCompatibilityRule) {
                params(consumer, producer)
            }
        }

        void prefer(Attribute<?> attribute, Object value) {
            this.attribute(attribute).disambiguationRules.add(PreferredDisambiguationRule) {
                params(value)
            }
        }

        static class AcceptingCompatibilityRule implements AttributeCompatibilityRule<Object> {
            Object consumer
            Object producer

            @Inject
            AcceptingCompatibilityRule(Object consumer, Object producer) {
                this.consumer = consumer
                this.producer = producer
            }

            @Override
            void execute(CompatibilityCheckDetails<Object> details) {
                if (details.consumerValue == consumer && details.producerValue == producer) {
                    details.compatible()
                }
            }
        }

        static class PreferredDisambiguationRule implements AttributeDisambiguationRule<Object> {
            Object value

            @Inject
            PreferredDisambiguationRule(Object value) {
                this.value = value
            }

            @Override
            void execute(MultipleCandidatesDetails<Object> details) {
                if (details.candidateValues.contains(value)) {
                    details.closestMatch(value)
                }
            }
        }
    }
}
