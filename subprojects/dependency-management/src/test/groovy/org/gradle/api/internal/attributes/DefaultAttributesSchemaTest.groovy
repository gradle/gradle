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

package org.gradle.api.internal.attributes

import org.gradle.api.Named
import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.AttributeCompatibilityRule
import org.gradle.api.attributes.AttributeDisambiguationRule
import org.gradle.api.attributes.CompatibilityCheckDetails
import org.gradle.api.attributes.MultipleCandidatesDetails
import org.gradle.util.AttributeTestUtil
import org.gradle.util.SnapshotTestUtil
import org.gradle.util.TestUtil
import spock.lang.Specification

class DefaultAttributesSchemaTest extends Specification {
    def schema = new DefaultAttributesSchema(TestUtil.instantiatorFactory(), SnapshotTestUtil.isolatableFactory())

    def "can create an attribute of scalar type #type"() {
        when:
        Attribute.of('foo', type)

        then:
        noExceptionThrown()

        where:
        type << [
            String,
            Number,
            MyEnum,
            Flavor
        ]
    }

    def "can create an attribute of array type #type"() {
        when:
        Attribute.of('foo', type)

        then:
        noExceptionThrown()

        where:
        type << [
            String[].class,
            Number[].class,
            MyEnum[].class,
            Flavor[].class
        ]
    }

    def "fails if no strategy is declared for custom type"() {
        when:
        schema.getMatchingStrategy(Attribute.of('flavor', Flavor))

        then:
        def e = thrown(IllegalArgumentException)
        e.message == 'Unable to find matching strategy for flavor'
    }

    def "treats equal values as compatible when no rules defined"() {
        given:
        def attribute = Attribute.of(String)
        schema.attribute(attribute)

        expect:
        !schema.matcher().selectionSchema.matchValue(attribute, "a", "b")
        schema.matcher().selectionSchema.matchValue(attribute, "a", "a")

        !schema.matcher().isMatching(attribute, "a", "b")
        schema.matcher().isMatching(attribute, "a", "a")
    }

    static class DoNothingRule implements AttributeCompatibilityRule<String> {
        static int count

        @Override
        void execute(CompatibilityCheckDetails<String> stringCompatibilityCheckDetails) {
            count++
        }
    }

    def "treats equal values as compatible when no rule expresses an opinion"() {
        given:
        def attribute = Attribute.of(String)
        schema.attribute(attribute).compatibilityRules.add(DoNothingRule)

        expect:
        !schema.matcher().selectionSchema.matchValue(attribute, "a", "b")
        schema.matcher().selectionSchema.matchValue(attribute, "a", "a")

        !schema.matcher().isMatching(attribute, "a", "b")
        schema.matcher().isMatching(attribute, "a", "a")
    }

    static class BrokenRule implements AttributeCompatibilityRule<String> {
        @Override
        void execute(CompatibilityCheckDetails<String> stringCompatibilityCheckDetails) {
            throw new RuntimeException()
        }
    }

    def "short-circuits evaluation when values are equal"() {
        given:
        def attribute = Attribute.of(String)
        schema.attribute(attribute).compatibilityRules.add(BrokenRule)

        expect:
        schema.matcher().selectionSchema.matchValue(attribute, "a", "a")

        schema.matcher().isMatching(attribute, "a", "a")
    }

    def "strategy is per attribute"() {
        given:
        schema.attribute(Attribute.of('a', Flavor))

        when:
        schema.getMatchingStrategy(Attribute.of('someOther', Flavor))

        then:
        def e = thrown(IllegalArgumentException)
        e.message == 'Unable to find matching strategy for someOther'

        when:
        schema.getMatchingStrategy(Attribute.of('picard', Flavor))

        then:
        e = thrown(IllegalArgumentException)
        e.message == 'Unable to find matching strategy for picard'
    }

    static class CustomCompatibilityRule implements AttributeCompatibilityRule<Flavor> {
        @Override
        void execute(CompatibilityCheckDetails<Flavor> details) {
            def producerValue = details.producerValue
            def consumerValue = details.consumerValue
            if (producerValue.name.length() > consumerValue.name.length()) {
                // arbitrary, just for testing purposes
                details.compatible()
            }
        }
    }

    static class CustomSelectionRule implements AttributeDisambiguationRule<Flavor> {
        @Override
        void execute(MultipleCandidatesDetails<Flavor> details) {
            details.closestMatch(details.candidateValues.first())
        }
    }

    def "compatibility rules can mark values as compatible"() {
        def attr = Attribute.of(Flavor)

        given:
        schema.attribute(attr).compatibilityRules.add(CustomCompatibilityRule)

        def value1 = flavor('value')
        def value2 = flavor('otherValue')

        expect:
        schema.matcher().selectionSchema.matchValue(attr, value1, value2)
        !schema.matcher().selectionSchema.matchValue(attr, value2, value1)

        schema.matcher().isMatching(attr, value2, value1)
        !schema.matcher().isMatching(attr, value1, value2)
    }

    static class IncompatibleStringsRule implements AttributeCompatibilityRule<String> {
        @Override
        void execute(CompatibilityCheckDetails<String> details) {
            details.incompatible()
        }
    }

    def "compatibility rules can mark values as incompatible and short-circuit evaluation"() {
        def attr = Attribute.of(String)

        given:
        schema.attribute(attr).compatibilityRules.add(IncompatibleStringsRule)
        schema.attribute(attr).compatibilityRules.add(BrokenRule)

        expect:
        !schema.matcher().selectionSchema.matchValue(attr, "a", "b")

        !schema.matcher().isMatching(attr, "a", "b")
    }

    def "selects requested value when it is one of the candidate values and no rules defined"() {
        def attr = Attribute.of(String)

        given:
        schema.attribute(attr)
        def candidates = ["foo", "bar"] as Set

        when:
        def best = schema.matcher().selectionSchema.disambiguate(attr, "bar", candidates)

        then:
        best == ["bar"] as Set
    }

    static class DoNothingSelectionRule implements AttributeDisambiguationRule<String> {
        @Override
        void execute(MultipleCandidatesDetails<String> stringMultipleCandidatesDetails) {
        }
    }

    def "selects requested value when it is one of the candidate values and no rule expresses an opinion"() {
        def attr = Attribute.of(String)

        given:
        schema.attribute(attr).disambiguationRules.add(DoNothingSelectionRule)
        def candidates = ["foo", "bar"] as Set

        when:
        def best = schema.matcher().selectionSchema.disambiguate(attr, "bar", candidates)

        then:
        best == ["bar"] as Set
    }

    def "returns null when no disambiguation rules and requested is not one of the candidate values"() {
        def attr = Attribute.of(String)

        given:
        schema.attribute(attr)
        def candidates = ["foo", "bar"] as Set

        when:
        def best = schema.matcher().selectionSchema.disambiguate(attr, "other", candidates)

        then:
        best == null
    }

    def "returns null when no rule expresses an opinion and requested is not one of the candidate values"() {
        def attr = Attribute.of(String)

        given:
        schema.attribute(attr).disambiguationRules.add(DoNothingSelectionRule)
        def candidates = ["foo", "bar"] as Set

        when:
        def best = schema.matcher().selectionSchema.disambiguate(attr, "other", candidates)

        then:
        best == null
    }

    def "custom rule can select best match"() {
        def attr = Attribute.of(Flavor)

        given:
        schema.attribute(attr).disambiguationRules.add(CustomSelectionRule)

        def value1 = flavor('value1')
        def value2 = flavor('value2')

        def candidates = [value1, value2] as Set

        when:
        def best = schema.matcher().selectionSchema.disambiguate(attr, flavor('requested'), candidates)

        then:
        best == [value1] as Set

        when:
        best = schema.matcher().selectionSchema.disambiguate(attr, value2, candidates)

        then:
        best == [value1] as Set
    }

    def "merging creates schema with additional attributes defined by producer"() {
        def producer = new DefaultAttributesSchema(TestUtil.instantiatorFactory(), SnapshotTestUtil.isolatableFactory())

        def attr1 = Attribute.of("a", String)
        def attr2 = Attribute.of("b", Integer)
        def attr3 = Attribute.of("c", Boolean)

        schema.attribute(attr1)
        schema.attribute(attr2)
        producer.attribute(attr2)
        producer.attribute(attr3)

        expect:
        schema.withProducer(producer).selectionSchema.hasAttribute(attr1)
        schema.withProducer(producer).selectionSchema.hasAttribute(attr2)
        schema.withProducer(producer).selectionSchema.hasAttribute(attr3)
    }

    def "uses the producers compatibility rules when the consumer does not express an opinion"() {
        def producer = new DefaultAttributesSchema(TestUtil.instantiatorFactory(), SnapshotTestUtil.isolatableFactory())

        def attr = Attribute.of("a", Flavor)

        schema.attribute(attr)
        producer.attribute(attr).compatibilityRules.add(CustomCompatibilityRule)

        expect:
        schema.withProducer(producer).selectionSchema.matchValue(attr, flavor('value'), flavor('otherValue'))
    }

    def "uses the producers selection rules when the consumer does not express an opinion"() {
        def producer = new DefaultAttributesSchema(TestUtil.instantiatorFactory(), SnapshotTestUtil.isolatableFactory())

        def attr = Attribute.of("a", Flavor)

        schema.attribute(attr)
        producer.attribute(attr).disambiguationRules.add(CustomSelectionRule)

        def value1 = flavor('value')
        def value2 = flavor('otherValue')

        def candidates = [value1, value2] as Set

        when:
        def best = schema.withProducer(producer).selectionSchema.disambiguate(attr, flavor('requested'), candidates)

        then:
        best == [value1] as Set
    }

    def "precedence order can be set"() {
        when:
        schema.attributeDisambiguationPrecedence(Attribute.of("a", Flavor), Attribute.of("b", String), Attribute.of("c", ConcreteNamed))
        then:
        schema.attributeDisambiguationPrecedence*.name == [ "a", "b", "c" ]
        when:
        schema.attributeDisambiguationPrecedence = [Attribute.of("c", ConcreteNamed)]
        then:
        schema.attributeDisambiguationPrecedence*.name == [ "c" ]
        when:
        schema.attributeDisambiguationPrecedence(Attribute.of("a", Flavor))
        then:
        schema.attributeDisambiguationPrecedence*.name == [ "c", "a" ]
    }

    def "precedence order cannot be changed for the same attribute"() {
        when:
        schema.attributeDisambiguationPrecedence(Attribute.of("a", Flavor), Attribute.of("b", String), Attribute.of("c", ConcreteNamed))
        schema.attributeDisambiguationPrecedence(Attribute.of("a", Flavor))
        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Attribute 'a' precedence has already been set."
    }

    def "precedence order is honored with merged schema"() {
        def producer = new DefaultAttributesSchema(TestUtil.instantiatorFactory(), SnapshotTestUtil.isolatableFactory())
        when:
        def x = Attribute.of("x", String)
        producer.attributeDisambiguationPrecedence(x, Attribute.of("a", String))

        def a = Attribute.of("a", Flavor)
        def b = Attribute.of("b", String)
        def c = Attribute.of("c", ConcreteNamed)
        schema.attributeDisambiguationPrecedence(a, b, c)

        def requested = AttributeTestUtil.attributesTyped(
                // attribute that doesn't have a precedence in consumer
                (x): "x",
                // attribute that has a lower precedence than the next one
                (c): AttributeTestUtil.named(ConcreteNamed, "c"),
                // attribute with the highest precedence
                (a): flavor("a"),
                // attribute that doesn't have a precedence
                (Attribute.of("z", String)): "z"
        )

        then:
        def result = schema.withProducer(producer).selectionSchema.orderByPrecedence(requested.keySet())
        result.sortedOrder == [2, 1, 0]
        result.unsortedOrder as List == [3]
    }

    def "precedence order is honored with merged schema when producer has attributes with the same name"() {
        def producer = new DefaultAttributesSchema(TestUtil.instantiatorFactory(), SnapshotTestUtil.isolatableFactory())
        when:
        def producerA = Attribute.of("a", String)
        def x = Attribute.of("x", String)
        producer.attributeDisambiguationPrecedence(x, producerA)

        def a = Attribute.of("a", Flavor)
        def b = Attribute.of("b", String)
        def c = Attribute.of("c", ConcreteNamed)
        schema.attributeDisambiguationPrecedence(a, b, c)

        def requested = AttributeTestUtil.attributesTyped(
                // attribute that doesn't have a precedence in consumer
                (x): "x",
                // attribute that has a precedence in consumer
                (c): AttributeTestUtil.named(ConcreteNamed, "c"),
                // attribute with the highest precedence in consumer, but lowest in producer
                (producerA): "a",
                // attribute that doesn't have a precedence
                (Attribute.of("z", String)): "z"
        )

        then:
        def result = schema.withProducer(producer).selectionSchema.orderByPrecedence(requested.keySet())
        result.sortedOrder == [1, 0, 2]
        result.unsortedOrder as List == [3]
    }

    def "precedence order is honored"() {
        def x = Attribute.of("x", String)
        def a = Attribute.of("a", Flavor)
        def b = Attribute.of("b", String)
        def c = Attribute.of("c", ConcreteNamed)
        schema.attributeDisambiguationPrecedence(a, b, c)

        def requested = AttributeTestUtil.attributesTyped(
                // attribute that doesn't have a precedence
                (x): "x",
                // attribute that has a lower precedence than the next one
                (c): AttributeTestUtil.named(ConcreteNamed, "c"),
                // attribute with the highest precedence
                (a): flavor("a"),
                // attribute that doesn't have a precedence
                (Attribute.of("z", String)): "z"
        )
        expect:
        def result = schema.matcher().selectionSchema.orderByPrecedence(requested.keySet())
        result.sortedOrder == [2, 1]
        result.unsortedOrder as List == [0, 3]
    }

    def "requested attributes are not sorted when there is no attribute precedence"() {
        def x = Attribute.of("x", String)
        def a = Attribute.of("a", Flavor)
        def c = Attribute.of("c", ConcreteNamed)

        def requested = AttributeTestUtil.attributesTyped(
                (x): "x",
                (c): AttributeTestUtil.named(ConcreteNamed, "c"),
                (a): flavor("a"),
                (Attribute.of("z", String)): "z"
        )
        expect:
        def result = schema.matcher().selectionSchema.orderByPrecedence(requested.keySet())
        result.sortedOrder == []
        result.unsortedOrder as List == [0, 1, 2, 3]
    }

    def "requested attributes are not sorted when there is a different set of attributes used for precedence"() {
        def x = Attribute.of("x", String)
        def a = Attribute.of("a", Flavor)
        def c = Attribute.of("c", ConcreteNamed)

        schema.attributeDisambiguationPrecedence(Attribute.of("notA", Flavor), Attribute.of("notB", String), Attribute.of("notC", ConcreteNamed))

        def requested = AttributeTestUtil.attributesTyped(
                (x): "x",
                (c): AttributeTestUtil.named(ConcreteNamed, "c"),
                (a): flavor("a"),
                (Attribute.of("z", String)): "z"
        )
        expect:
        def result = schema.matcher().selectionSchema.orderByPrecedence(requested.keySet())
        result.sortedOrder == []
        result.unsortedOrder as List == [0, 1, 2, 3]
    }

    static interface Flavor extends Named {}

    enum MyEnum {
        FOO,
        BAR
    }

    static Flavor flavor(String name) {
        TestUtil.objectInstantiator().named(Flavor, name)
    }

    static abstract class ConcreteNamed implements Named {
    }

}
