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
import spock.lang.Unroll

import static org.gradle.util.AttributeTestUtil.attributes
import static org.gradle.util.TestUtil.objectFactory

class ComponentAttributeMatcherTest extends Specification {

    def schema = new TestSchema()
    def factory = AttributeTestUtil.attributesFactory()
    def explanationBuilder = Stub(AttributeMatchingExplanationBuilder)

    def "selects candidate with same set of attributes and whose values match"() {
        def attr = Attribute.of(String)
        def attr2 = Attribute.of('2', String)
        schema.attribute(attr)
        schema.attribute(attr2)

        given:
        def candidate1 = attrs()
        candidate1.attribute(attr, "value1")
        def candidate2 = attrs()
        candidate2.attribute(attr, "value2")
        def requested = attrs()
        requested.attribute(attr, "value1")

        def matcher = new ComponentAttributeMatcher()

        expect:
        matcher.match(schema, [candidate1, candidate2], requested, null, explanationBuilder) == [candidate1]
        matcher.match(schema, [candidate2], requested, null, explanationBuilder) == []

        matcher.isMatching(schema, candidate1, requested)
        !matcher.isMatching(schema, candidate2, requested)
    }

    def "selects candidate with subset of attributes and whose values match"() {
        def attr = Attribute.of(String)
        def attr2 = Attribute.of('2', String)
        schema.attribute(attr)
        schema.attribute(attr2)

        given:
        def candidate1 = attrs()
        candidate1.attribute(attr, "value1")
        def candidate2 = attrs()
        candidate2.attribute(attr, "no match")
        def candidate3 = attrs()
        candidate3.attribute(attr, "value1")
        candidate3.attribute(attr2, "no match")
        def candidate4 = attrs()
        def requested = attrs()
        requested.attribute(attr, "value1")
        requested.attribute(attr2, "value2")

        def matcher = new ComponentAttributeMatcher()

        expect:
        matcher.match(schema, [candidate1, candidate2, candidate3, candidate4], requested, null, explanationBuilder) == [candidate1]
        matcher.match(schema, [candidate2, candidate3, candidate4], requested, null, explanationBuilder) == [candidate4]

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
        def candidate1 = attrs()
        candidate1.attribute(attr, "value1")
        candidate1.attribute(attr2, "other")
        def candidate2 = attrs()
        candidate2.attribute(attr, "no match")
        def requested = attrs()
        requested.attribute(attr, "value1")

        def matcher = new ComponentAttributeMatcher()

        expect:
        matcher.match(schema, [candidate1, candidate2], requested, null, explanationBuilder) == [candidate1]
        matcher.match(schema, [candidate2], requested, null, explanationBuilder) == []

        matcher.isMatching(schema, candidate1, requested)
        !matcher.isMatching(schema, candidate2, requested)
    }

    def "selects multiple candidates with compatible values"() {
        def attr = Attribute.of(String)
        def attr2 = Attribute.of('2', String)
        schema.attribute(attr)
        schema.attribute(attr2)

        given:
        def candidate1 = attrs()
        candidate1.attribute(attr, "value1")
        def candidate2 = attrs()
        candidate2.attribute(attr, "no match")
        def candidate3 = attrs()
        candidate3.attribute(attr2, "value2")
        def candidate4 = attrs()
        def candidate5 = attrs()
        candidate5.attribute(attr, "value1")
        def requested = attrs()
        requested.attribute(attr, "value1")
        requested.attribute(attr2, "value2")

        def matcher = new ComponentAttributeMatcher()

        expect:
        matcher.match(schema, [candidate1, candidate2, candidate3, candidate4, candidate5], requested, null, explanationBuilder) == [candidate1, candidate3, candidate4, candidate5]
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
        def candidate1 = attrs()
        candidate1.attribute(attr, "value1")
        candidate1.attribute(attr2, "value1")
        def candidate2 = attrs()
        candidate2.attribute(attr, "no match")
        candidate2.attribute(attr2, "no match")
        def candidate3 = attrs()
        candidate3.attribute(attr, "value2")
        candidate3.attribute(attr2, "value2")
        def candidate4 = attrs()
        def candidate5 = attrs()
        candidate5.attribute(attr, "value1")
        candidate5.attribute(attr2, "value2")
        def requested = attrs()
        requested.attribute(attr, "requested")
        requested.attribute(attr2, "requested")

        def matcher = new ComponentAttributeMatcher()

        expect:
        matcher.match(schema, [candidate1, candidate2, candidate3, candidate4, candidate5], requested, null, explanationBuilder) == [candidate5]
        matcher.match(schema, [candidate1, candidate2, candidate3, candidate4], requested, null, explanationBuilder) == [candidate1, candidate3, candidate4]
        matcher.match(schema, [candidate1, candidate2, candidate4], requested, null, explanationBuilder) == [candidate1]
        matcher.match(schema, [candidate2, candidate4], requested, null, explanationBuilder) == [candidate4]
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
        def candidate1 = attrs()
        candidate1.attribute(attr, "value1")
        def candidate2 = attrs()
        candidate2.attribute(attr, "no match")
        def candidate3 = attrs()
        candidate3.attribute(attr, "value2")
        def candidate4 = attrs()
        def requested1 = attrs()
        requested1.attribute(attr, "requested")
        def requested2 = attrs()

        def matcher = new ComponentAttributeMatcher()

        expect:
        matcher.match(schema, [candidate1, candidate2, candidate3, candidate4], requested1, null, explanationBuilder) == [candidate3]
        matcher.match(schema, [candidate1, candidate2, candidate3, candidate4], requested2, null, explanationBuilder) == [candidate1]
    }

    def "disambiguation rule is presented with all non-null candidate values"() {
        def rule = Mock(AttributeDisambiguationRule)
        def attr = Attribute.of(String)
        schema.attribute(attr)
        schema.accept(attr, "requested", "value1")
        schema.accept(attr, "requested", "value2")
        schema.select(attr, rule)

        rule.execute({ it.consumerValue == "requested" }) >> { MultipleCandidatesDetails details ->
            assert details.candidateValues == ["value1", "value2"] as Set
            details.closestMatch("value1")
        }

        given:
        def candidate1 = attrs()
        candidate1.attribute(attr, "value1")
        def candidate2 = attrs()
        candidate2.attribute(attr, "value2")
        def candidate3 = attrs()
        def requested1 = attrs()
        requested1.attribute(attr, "requested")

        def matcher = new ComponentAttributeMatcher()

        expect:
        matcher.match(schema, [candidate1, candidate2, candidate3], requested1, null, explanationBuilder) == [candidate1]
    }

    def "prefers match with superset of matching attributes"() {
        def attr = Attribute.of(String)
        def attr2 = Attribute.of('2', String)
        schema.attribute(attr)
        schema.attribute(attr2)

        given:
        def candidate1 = attrs()
        candidate1.attribute(attr, "value1")
        def candidate2 = attrs()
        candidate2.attribute(attr, "no match")
        def candidate3 = attrs()
        candidate3.attribute(attr2, "value2")
        def candidate4 = attrs()
        def candidate5 = attrs()
        candidate5.attribute(attr, "value1")
        candidate5.attribute(attr2, "value2")
        def candidate6 = attrs()
        candidate6.attribute(attr, "value1")
        def requested = attrs()
        requested.attribute(attr, "value1")
        requested.attribute(attr2, "value2")

        def matcher = new ComponentAttributeMatcher()

        expect:
        matcher.match(schema, [candidate1, candidate2, candidate3, candidate4, candidate5, candidate6], requested, null, explanationBuilder) == [candidate5]
        matcher.match(schema, [candidate1, candidate2, candidate3, candidate4, candidate6], requested, null, explanationBuilder) == [candidate1, candidate3, candidate4, candidate6]
        matcher.match(schema, [candidate1, candidate2, candidate4, candidate6], requested, null, explanationBuilder) == [candidate1, candidate4, candidate6]
        matcher.match(schema, [candidate2, candidate3, candidate4], requested, null, explanationBuilder) == [candidate3]
    }

    def "disambiguates using ignored producer attributes"() {
        def key1 = Attribute.of("a1", String)
        def key2 = Attribute.of("a2", String)
        schema.attribute(key1)
        schema.attribute(key2)
        schema.prefer(key2, "ignore2")

        given:
        def candidate1 = attrs()
        candidate1.attribute(key1, "value1")
        candidate1.attribute(key2, "ignored1")
        def candidate2 = attrs()
        candidate2.attribute(key1, "value1")
        candidate2.attribute(key2, "ignore2")
        def requested = attrs()
        requested.attribute(key1, "value1")

        expect:
        def matcher = new ComponentAttributeMatcher()

        def matches = matcher.match(schema, [candidate1, candidate2], requested, null, explanationBuilder)
        matches == [candidate2]
    }

    def "ignores extra attributes if match is found after disambiguation requested attributes"() {
        def key1 = Attribute.of("a1", String)
        schema.attribute(key1)
        schema.accept(key1, "foo", "bar")
        schema.accept(key1, "foo", "baz")
        schema.prefer(key1, "baz")

        def key2 = Attribute.of("a2", String)
        schema.attribute(key2)
        schema.prefer(key2, "ignored1")

        given:
        def candidate1 = attrs()
        candidate1.attribute(key1, "bar")
        candidate1.attribute(key2, "ignored1")

        def candidate2 = attrs()
        candidate2.attribute(key1, "baz")
        candidate2.attribute(key2, "ignored2")

        def requested = attrs()
        requested.attribute(key1, "foo")

        expect:
        def matcher = new ComponentAttributeMatcher()

        def matches = matcher.match(schema, [candidate1, candidate2], requested, null, explanationBuilder)
        matches == [candidate2]
    }

    def "empty producer attributes match any consumer attributes"() {
        given:
        def key1 = Attribute.of("a1", String)
        schema.attribute(key1)

        def requested = attrs()
        def candidate = attrs()
        def candidate2 = attrs().attribute(key1, "1")

        expect:
        def matcher = new ComponentAttributeMatcher()

        matcher.match(schema, [candidate], requested, null, explanationBuilder) == [candidate]
        matcher.isMatching(schema, candidate, requested)

        matcher.match(schema, [candidate], requested, null, explanationBuilder) == [candidate]
        matcher.isMatching(schema, candidate, requested)

        matcher.match(schema, [candidate2], requested, null, explanationBuilder) == [candidate2]
        matcher.isMatching(schema, candidate2, requested)
    }

    def "non-empty producer attributes match empty consumer attributes"() {
        def key1 = Attribute.of("a1", String)
        def key2 = Attribute.of("a2", String)

        given:
        def candidate = attrs()
        def requested = attrs().attribute(key1, "1").attribute(key2, "2")

        expect:
        def matcher = new ComponentAttributeMatcher()

        matcher.match(schema, [candidate], requested, null, explanationBuilder) == [candidate]
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
        def candidate1 = attrs().attribute(key1, "other")
        def candidate2 = attrs().attribute(key2, "other")
        def fallback1 = attrs()
        def fallback2 = attrs().attribute(key1, "1")
        def fallback3 = attrs().attribute(key3, "3")
        def fallback4 = attrs().attribute(key1, "other")
        def requested = attrs().attribute(key1, "1").attribute(key2, "2")

        expect:
        def matcher = new ComponentAttributeMatcher()

        // No candidates, fallback matches
        matcher.match(schema, [], requested, fallback1, explanationBuilder) == [fallback1]
        matcher.match(schema, [], requested, fallback2, explanationBuilder) == [fallback2]
        matcher.match(schema, [], requested, fallback3, explanationBuilder) == [fallback3]

        // Fallback does not match
        matcher.match(schema, [], requested, fallback4, explanationBuilder) == []

        // Candidates, fallback matches
        matcher.match(schema, [candidate1, candidate2], requested, fallback1, explanationBuilder) == []
        matcher.match(schema, [candidate1, candidate2], requested, fallback2, explanationBuilder) == []
        matcher.match(schema, [candidate1, candidate2], requested, fallback3, explanationBuilder) == []

        // No fallback
        matcher.match(schema, [candidate1, candidate2], requested, null, explanationBuilder) == []
        matcher.match(schema, [], requested, null, explanationBuilder) == []

        // Fallback also a candidate
        matcher.match(schema, [candidate1, fallback4], requested, fallback4, explanationBuilder) == []
        matcher.match(schema, [candidate1, candidate2, fallback1], requested, fallback1, explanationBuilder) == [fallback1]
    }

    def "can match when consumer uses more general type for attribute"() {
        def matcher = new ComponentAttributeMatcher()
        def key1 = Attribute.of("a", Number)
        def key2 = Attribute.of("a", Integer)
        schema.attribute(key1)

        def requested = attrs().attribute(key1, 1)
        def c1 = attrs().attribute(key2, 1)
        def c2 = attrs().attribute(key2, 2)

        expect:
        matcher.match(schema, [c1, c2], requested, null, explanationBuilder) == [c1]
    }

    def "can match when producer uses desugared attribute of type Named"() {
        def matcher = new ComponentAttributeMatcher()
        def key1 = Attribute.of("a", NamedTestAttribute)
        def key2 = Attribute.of("a", String)
        schema.attribute(key1)

        def requested = attrs().attribute(key1, objectFactory().named(NamedTestAttribute, "name1"))
        def c1 = attrs().attribute(key2, "name1")
        def c2 = attrs().attribute(key2, "name2")

        expect:
        matcher.match(schema, [c1, c2], requested, null, explanationBuilder) == [c1]
    }

    def "can match when consumer uses desugared attribute of type Named"() {
        def matcher = new ComponentAttributeMatcher()
        def key1 = Attribute.of("a", String)
        def key2 = Attribute.of("a", NamedTestAttribute)
        schema.attribute(key1)

        def requested = attrs().attribute(key1, "name1")
        def c1 = attrs().attribute(key2, objectFactory().named(NamedTestAttribute, "name1"))
        def c2 = attrs().attribute(key2, objectFactory().named(NamedTestAttribute, "name2"))

        expect:
        matcher.match(schema, [c1, c2], requested, null, explanationBuilder) == [c1]
    }

    def "can match when producer uses desugared attribute of type Enum"() {
        def matcher = new ComponentAttributeMatcher()
        def key1 = Attribute.of("a", EnumTestAttribute)
        def key2 = Attribute.of("a", String)
        schema.attribute(key1)

        def requested = attrs().attribute(key1, EnumTestAttribute.NAME1)
        def c1 = attrs().attribute(key2, "NAME1")
        def c2 = attrs().attribute(key2, "NAME2")

        expect:
        matcher.match(schema, [c1, c2], requested, null, explanationBuilder) == [c1]
    }

    def "can match when consumer uses desugared attribute of type Enum"() {
        def matcher = new ComponentAttributeMatcher()
        def key1 = Attribute.of("a", String)
        def key2 = Attribute.of("a", EnumTestAttribute)
        schema.attribute(key1)

        def requested = attrs().attribute(key1, "NAME1")
        def c1 = attrs().attribute(key2, EnumTestAttribute.NAME1)
        def c2 = attrs().attribute(key2, EnumTestAttribute.NAME2)

        expect:
        matcher.match(schema, [c1, c2], requested, null, explanationBuilder) == [c1]
    }

    def "cannot match when producer uses desugared attribute of unsupported type"() {
        def matcher = new ComponentAttributeMatcher()
        def key1 = Attribute.of("a", NotSerializableInGradleMetadataAttribute)
        def key2 = Attribute.of("a", String)
        schema.attribute(key1)

        def requested = attrs().attribute(key1, new NotSerializableInGradleMetadataAttribute("name1"))
        def c1 = attrs().attribute(key2, "name1")
        def c2 = attrs().attribute(key2, "name2")

        when:
        matcher.match(schema, [c1, c2], requested, null, explanationBuilder)

        then:
        IllegalArgumentException e = thrown()
        e.message == "Unexpected type for attribute 'a' provided. Expected a value of type org.gradle.internal.component.model.ComponentAttributeMatcherTest${'$'}NotSerializableInGradleMetadataAttribute but found a value of type java.lang.String."
    }

    def "cannot match when consumer uses desugared attribute of unsupported type"() {
        def matcher = new ComponentAttributeMatcher()
        def key1 = Attribute.of("a", String)
        def key2 = Attribute.of("a", NotSerializableInGradleMetadataAttribute)
        schema.attribute(key1)

        def requested = attrs().attribute(key1, "name1")
        def c1 = attrs().attribute(key2, new NotSerializableInGradleMetadataAttribute("name1"))
        def c2 = attrs().attribute(key2, new NotSerializableInGradleMetadataAttribute("name2"))

        when:
        matcher.match(schema, [c1, c2], requested, null, explanationBuilder)

        then:
        IllegalArgumentException e = thrown()
        e.message == "Unexpected type for attribute 'a' provided. Expected a value of type java.lang.String but found a value of type org.gradle.internal.component.model.ComponentAttributeMatcherTest${'$'}NotSerializableInGradleMetadataAttribute."
    }

    def "matching fails when attribute has incompatible types in consumer and producer"() {
        def matcher = new ComponentAttributeMatcher()
        def key1 = Attribute.of("a", String)
        def key2 = Attribute.of("a", Number)
        schema.attribute(key1)

        def requested = attrs().attribute(key1, "1")
        def c1 = attrs().attribute(key2, 1)

        when:
        matcher.match(schema, [c1], requested, null, explanationBuilder)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Unexpected type for attribute 'a' provided. Expected a value of type java.lang.String but found a value of type java.lang.Integer."
    }

    def "prefers a strict match with requested values"() {
        def matcher = new ComponentAttributeMatcher()
        def key1 = Attribute.of("a", String)
        def key2 = Attribute.of("b", String)
        schema.attribute(key1)
        schema.attribute(key2)

        def requested = attributes(a: 'val')
        def candidate1 = attributes(a: 'val')
        def candidate2 = attributes(a: 'val', b: 'foo')

        when:
        def result = matcher.match(schema, [candidate1, candidate2], requested, null, explanationBuilder)

        then:
        result == [candidate1]
    }

    @Unroll
    def "prefers a shorter match with compatible requested values and more than one extra attribute (type: #type)"() {
        def matcher = new ComponentAttributeMatcher()
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

        def requested = attributes(usage: 'java-api')
        def candidate1 = attributes(usage: 'java-api-extra', status: 'integration')
        def candidate2 = attributes(usage: 'java-runtime-extra', status: 'integration')
        def candidate3 = attributes(usage: 'java-api-extra', status: 'integration', bundling: value1)
        def candidate4 = attributes(usage: 'java-runtime-extra', status: 'integration', bundling: value2)

        when:
        def result = matcher.match(schema, [candidate1, candidate2, candidate3, candidate4], requested, null, explanationBuilder)

        then:
        result == [candidate1]

        when: // check with a different attribute order
        candidate1 = attributes(usage: 'java-api-extra', status: 'integration')
        candidate2 = attributes(usage: 'java-runtime-extra', status: 'integration')
        candidate3 = attributes(usage: 'java-api-extra', bundling: value2, status: 'integration')
        candidate4 = attributes(usage: 'java-runtime-extra', bundling: value1, status: 'integration')

        result = matcher.match(schema, [candidate1, candidate2, candidate3, candidate4], requested, null, explanationBuilder)

        then:
        result == [candidate1]

        when: // yet another attribute order
        candidate1 = attributes(status: 'integration', usage: 'java-api-extra')
        candidate2 = attributes(usage: 'java-runtime-extra', status: 'integration')
        candidate3 = attributes(bundling: value1, status: 'integration', usage: 'java-api-extra')
        candidate4 = attributes(status: 'integration', usage: 'java-runtime-extra', bundling: value2)

        result = matcher.match(schema, [candidate1, candidate2, candidate3, candidate4], requested, null, explanationBuilder)

        then:
        result == [candidate1]

        where:
        type                | value1        | value2
        String              | "embedded"    | "embedded"
        EnumTestAttribute   | "NAME1"       | "NAME2"
        NamedTestAttribute  | "foo"         | "bar"
    }

    private AttributeContainerInternal attrs() {
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

            candidates
        }

        @Override
        Attribute<?>[] collectExtraAttributes(ImmutableAttributes[] candidates, ImmutableAttributes requested) {
            AttributeSelectionUtils.collectExtraAttributes(this, candidates, requested)
        }
    }
}
