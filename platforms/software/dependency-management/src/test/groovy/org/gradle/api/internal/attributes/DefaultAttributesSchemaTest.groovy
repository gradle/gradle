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
import org.gradle.api.internal.attributes.matching.DefaultAttributeSelectionSchema
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

    static class FailingCompatibilityRule implements AttributeCompatibilityRule<Flavor> {
        @Override
        void execute(CompatibilityCheckDetails<Flavor> details) {
            throw new RuntimeException()
        }
    }

    static class FailingSelectionRule implements AttributeDisambiguationRule<Flavor> {
        @Override
        void execute(MultipleCandidatesDetails<Flavor> details) {
            throw new RuntimeException()
        }
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
        new DefaultAttributeSelectionSchema(schema, producer).hasAttribute(attr1)
        new DefaultAttributeSelectionSchema(schema, producer).hasAttribute(attr2)
        new DefaultAttributeSelectionSchema(schema, producer).hasAttribute(attr3)
    }

    def "uses the producers compatibility rules when the consumer does not express an opinion"() {
        def producer = new DefaultAttributesSchema(TestUtil.instantiatorFactory(), SnapshotTestUtil.isolatableFactory())

        def attr = Attribute.of("a", Flavor)

        schema.attribute(attr)
        producer.attribute(attr).compatibilityRules.add(CustomCompatibilityRule)

        expect:
        schema.withProducer(producer).isMatchingValue(attr, flavor('otherValue'), flavor('value'))
    }

    def "uses the producers disambiguation rules when the consumer does not express an opinion"() {
        def producer = new DefaultAttributesSchema(TestUtil.instantiatorFactory(), SnapshotTestUtil.isolatableFactory())

        def attr = Attribute.of("a", Flavor)

        schema.attribute(attr)
        producer.attribute(attr).disambiguationRules.add(CustomSelectionRule)

        def value1 = flavor('value')
        def value2 = flavor('otherValue')

        def candidates = [value1, value2] as Set

        when:
        def best = new DefaultAttributeSelectionSchema(schema, producer).disambiguate(attr, flavor('requested'), candidates)

        then:
        best == [value1] as Set
    }

    def "uses the consumer's compatibility rules when both the consumer and producer express an opinion"() {
        def producer = new DefaultAttributesSchema(TestUtil.instantiatorFactory(), SnapshotTestUtil.isolatableFactory())

        def attr = Attribute.of("a", Flavor)

        schema.attribute(attr).compatibilityRules.add(CustomCompatibilityRule)
        producer.attribute(attr).compatibilityRules.add(FailingCompatibilityRule)

        expect:
        schema.withProducer(producer).isMatchingValue(attr, flavor('otherValue'), flavor('value'))
    }

    def "uses the consumer's disambiguation rules when both the consumer and producer express an opinion"() {
        def producer = new DefaultAttributesSchema(TestUtil.instantiatorFactory(), SnapshotTestUtil.isolatableFactory())

        def attr = Attribute.of("a", Flavor)

        schema.attribute(attr).disambiguationRules.add(CustomSelectionRule)
        producer.attribute(attr).disambiguationRules.add(FailingSelectionRule)

        def value1 = flavor('value')
        def value2 = flavor('otherValue')

        def candidates = [value1, value2] as Set

        when:
        def best = new DefaultAttributeSelectionSchema(schema, producer).disambiguate(attr, flavor('requested'), candidates)

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
        def result = new DefaultAttributeSelectionSchema(schema, producer).orderByPrecedence(requested.keySet())
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
        def result = new DefaultAttributeSelectionSchema(schema, producer).orderByPrecedence(requested.keySet())
        result.sortedOrder == [1, 0, 2]
        result.unsortedOrder as List == [3]
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
