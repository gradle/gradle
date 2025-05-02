/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.api.internal.attributes.immutable

import org.gradle.api.Named
import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.AttributeCompatibilityRule
import org.gradle.api.attributes.AttributeDisambiguationRule
import org.gradle.api.attributes.CompatibilityCheckDetails
import org.gradle.api.attributes.MultipleCandidatesDetails
import org.gradle.api.internal.attributes.AttributesSchemaInternal
import org.gradle.api.internal.attributes.matching.DefaultAttributeSelectionSchema
import org.gradle.internal.component.external.model.PreferJavaRuntimeVariant
import org.gradle.util.AttributeTestUtil
import org.gradle.util.TestUtil
import spock.lang.Specification

import static org.gradle.util.AttributeTestUtil.mutableSchema

/**
 * Tests {@link ImmutableAttributesSchemaFactory}.
 */
class ImmutableAttributesSchemaFactoryTest extends Specification {

    ImmutableAttributesSchemaFactory factory = new ImmutableAttributesSchemaFactory(TestUtil.inMemoryCacheFactory())
    ImmutableAttributesSchema preferJavaRuntime = new PreferJavaRuntimeVariant(TestUtil.objectInstantiator(), factory).getSchema()

    def "empty concat with empty is empty"() {
        expect:
        factory.concat(ImmutableAttributesSchema.EMPTY, ImmutableAttributesSchema.EMPTY).is(ImmutableAttributesSchema.EMPTY)
    }

    def "empty concat with non-empty is non-empty"() {
        def schema = mutableSchema {
            attribute(Attribute.of("a", String))
        }

        def immutable = factory.create(schema)

        expect:
        factory.concat(ImmutableAttributesSchema.EMPTY, immutable).is(immutable)
        factory.concat(immutable, ImmutableAttributesSchema.EMPTY).is(immutable)
    }

    def "can concat with PreferJavaRuntimeVariant"() {
        def schema = mutableSchema {
            attribute(Attribute.of("a", String))
        }
        def immutable = factory.create(schema)

        expect:
        factory.concat(immutable, preferJavaRuntime).is(factory.concat(preferJavaRuntime, immutable))
        factory.concat(ImmutableAttributesSchema.EMPTY, preferJavaRuntime).is(preferJavaRuntime)
        factory.concat(preferJavaRuntime, ImmutableAttributesSchema.EMPTY).is(preferJavaRuntime)
    }

    def "merging creates schema with additional attributes defined by producer"() {
        def attr1 = Attribute.of("a", String)
        def attr2 = Attribute.of("b", Integer)
        def attr3 = Attribute.of("c", Boolean)

        def consumer = mutableSchema {
            attribute(attr1)
            attribute(attr2)
        }

        def producer = mutableSchema {
            attribute(attr2)
            attribute(attr3)
        }

        when:
        def merged = concat(consumer, producer)

        then:
        merged.attributes.contains(attr1)
        merged.attributes.contains(attr2)
        merged.attributes.contains(attr3)
    }

    def "precedence order is honored with merged schema"() {
        def x = Attribute.of("x", String)
        def producer = mutableSchema {
            attributeDisambiguationPrecedence(x, Attribute.of("a", String))
        }

        def a = Attribute.of("a", Flavor)
        def b = Attribute.of("b", String)
        def c = Attribute.of("c", ConcreteNamed)
        def consumer = mutableSchema {
            attributeDisambiguationPrecedence(a, b, c)
        }

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

        when:
        def merged = concat(consumer, producer)
        def result = new DefaultAttributeSelectionSchema(merged).orderByPrecedence(requested.keySet())

        then:
        result.sortedOrder == [2, 1, 0]
        result.unsortedOrder as List == [3]
    }

    def "precedence order is honored with merged schema when producer has attributes with the same name"() {
        def producerA = Attribute.of("a", String)
        def x = Attribute.of("x", String)
        def producer = mutableSchema {
            attributeDisambiguationPrecedence(x, producerA)
        }

        def a = Attribute.of("a", Flavor)
        def b = Attribute.of("b", String)
        def c = Attribute.of("c", ConcreteNamed)

        def consumer = mutableSchema {
            attributeDisambiguationPrecedence(a, b, c)
        }

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

        when:
        def merged = concat(consumer, producer)
        def result = new DefaultAttributeSelectionSchema(merged).orderByPrecedence(requested.keySet())

        then:
        result.sortedOrder == [1, 0, 2]
        result.unsortedOrder as List == [3]
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

    def "uses the producers compatibility rules when the consumer does not express an opinion"() {
        def attr = Attribute.of("a", Flavor)

        def producer = mutableSchema {
            attribute(attr).compatibilityRules.add(CustomCompatibilityRule)
        }
        def consumer = mutableSchema {
            attribute(attr)
        }

        when:
        def merged = concat(consumer, producer)
        def schema = new DefaultAttributeSelectionSchema(merged)

        then:
        schema.matchValue(attr, flavor('value'), flavor('otherValue'))
    }

    static class CustomSelectionRule implements AttributeDisambiguationRule<Flavor> {
        @Override
        void execute(MultipleCandidatesDetails<Flavor> details) {
            details.closestMatch(details.candidateValues.first())
        }
    }

    def "uses the producers disambiguation rules when the consumer does not express an opinion"() {
        def attr = Attribute.of("a", Flavor)

        def producer = mutableSchema {
            attribute(attr).disambiguationRules.add(CustomSelectionRule)
        }
        def consumer = mutableSchema {
            attribute(attr)
        }

        def value1 = flavor('value')
        def value2 = flavor('otherValue')

        when:
        def merged = concat(consumer, producer)
        def schema = new DefaultAttributeSelectionSchema(merged)

        then:
        schema.disambiguate(attr, flavor('requested'), [value1, value2] as Set) == [value1] as Set
    }

    static class FailingCompatibilityRule implements AttributeCompatibilityRule<Flavor> {
        @Override
        void execute(CompatibilityCheckDetails<Flavor> details) {
            throw new RuntimeException()
        }
    }

    def "uses the consumer's compatibility rules when both the consumer and producer express an opinion"() {
        def attr = Attribute.of("a", Flavor)

        def producer = mutableSchema {
            attribute(attr).compatibilityRules.add(FailingCompatibilityRule)
        }
        def consumer = mutableSchema {
            attribute(attr).compatibilityRules.add(CustomCompatibilityRule)
        }

        when:
        def merged = concat(consumer, producer)
        def schema = new DefaultAttributeSelectionSchema(merged)

        then:
        schema.matchValue(attr, flavor('value'), flavor('otherValue'))
    }

    static class FailingSelectionRule implements AttributeDisambiguationRule<Flavor> {
        @Override
        void execute(MultipleCandidatesDetails<Flavor> details) {
            throw new RuntimeException()
        }
    }

    def "uses the consumer's disambiguation rules when both the consumer and producer express an opinion"() {
        def attr = Attribute.of("a", Flavor)

        def producer = mutableSchema {
            attribute(attr).disambiguationRules.add(FailingSelectionRule)
        }
        def consumer = mutableSchema {
            attribute(attr).disambiguationRules.add(CustomSelectionRule)
        }

        def value1 = flavor('value')
        def value2 = flavor('otherValue')

        when:
        def merged = concat(consumer, producer)
        def schema = new DefaultAttributeSelectionSchema(merged)

        then:
        schema.disambiguate(attr, flavor('requested'), [value1, value2] as Set) == [value1] as Set
    }

    ImmutableAttributesSchema concat(AttributesSchemaInternal consumer, AttributesSchemaInternal producer) {
        return factory.concat(
            factory.create(consumer),
            factory.create(producer)
        )
    }

    static interface Flavor extends Named {}

    static Flavor flavor(String name) {
        TestUtil.objectInstantiator().named(Flavor, name)
    }

    static abstract class ConcreteNamed implements Named {
    }

}
