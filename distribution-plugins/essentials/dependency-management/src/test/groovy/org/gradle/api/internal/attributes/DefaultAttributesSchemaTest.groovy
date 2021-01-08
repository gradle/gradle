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
import org.gradle.internal.component.model.ComponentAttributeMatcher
import org.gradle.util.AttributeTestUtil
import org.gradle.util.SnapshotTestUtil
import org.gradle.util.TestUtil
import spock.lang.Specification
import spock.lang.Unroll

class DefaultAttributesSchemaTest extends Specification {
    def schema = new DefaultAttributesSchema(new ComponentAttributeMatcher(), TestUtil.instantiatorFactory(), SnapshotTestUtil.valueSnapshotter())
    def factory = AttributeTestUtil.attributesFactory()

    @Unroll
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

    @Unroll
    def "can create an attribute of scalar type #type.name[]"() {
        when:
        Attribute.of('foo', Eval.me("${type.name}[]"))

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
        !schema.mergeWith(EmptySchema.INSTANCE).matchValue(attribute, "a", "b")
        schema.mergeWith(EmptySchema.INSTANCE).matchValue(attribute, "a", "a")

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
        !schema.mergeWith(EmptySchema.INSTANCE).matchValue(attribute, "a", "b")
        schema.mergeWith(EmptySchema.INSTANCE).matchValue(attribute, "a", "a")

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
        schema.mergeWith(EmptySchema.INSTANCE).matchValue(attribute, "a", "a")

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
        schema.mergeWith(EmptySchema.INSTANCE).matchValue(attr, value1, value2)
        !schema.mergeWith(EmptySchema.INSTANCE).matchValue(attr, value2, value1)

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
        !schema.mergeWith(EmptySchema.INSTANCE).matchValue(attr, "a", "b")

        !schema.matcher().isMatching(attr, "a", "b")
    }

    def "selects requested value when it is one of the candidate values and no rules defined"() {
        def attr = Attribute.of(String)

        given:
        schema.attribute(attr)
        def candidates = ["foo", "bar"] as Set

        when:
        def best = schema.mergeWith(EmptySchema.INSTANCE).disambiguate(attr, "bar", candidates)

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
        def best = schema.mergeWith(EmptySchema.INSTANCE).disambiguate(attr, "bar", candidates)

        then:
        best == ["bar"] as Set
    }

    def "selects all candidates when no disambiguation rules and requested is not one of the candidate values"() {
        def attr = Attribute.of(String)

        given:
        schema.attribute(attr)
        def candidates = ["foo", "bar"] as Set

        when:
        def best = schema.mergeWith(EmptySchema.INSTANCE).disambiguate(attr, "other", candidates)

        then:
        best == candidates
    }

    def "selects all candidates when no rule expresses an opinion and requested is not one of the candidate values"() {
        def attr = Attribute.of(String)

        given:
        schema.attribute(attr).disambiguationRules.add(DoNothingSelectionRule)
        def candidates = ["foo", "bar"] as Set

        when:
        def best = schema.mergeWith(EmptySchema.INSTANCE).disambiguate(attr, "other", candidates)

        then:
        best == candidates
    }

    def "custom rule can select best match"() {
        def attr = Attribute.of(Flavor)

        given:
        schema.attribute(attr).disambiguationRules.add(CustomSelectionRule)

        def value1 = flavor('value1')
        def value2 = flavor('value2')

        def candidates = [value1, value2] as Set

        when:
        def best= schema.mergeWith(EmptySchema.INSTANCE).disambiguate(attr, flavor('requested'), candidates)

        then:
        best == [value1] as Set

        when:
        best = schema.mergeWith(EmptySchema.INSTANCE).disambiguate(attr, value2, candidates)

        then:
        best == [value1] as Set
    }

    def "merging creates schema with additional attributes defined by producer"() {
        def producer = new DefaultAttributesSchema(new ComponentAttributeMatcher(), TestUtil.instantiatorFactory(), SnapshotTestUtil.valueSnapshotter())

        def attr1 = Attribute.of("a", String)
        def attr2 = Attribute.of("b", Integer)
        def attr3 = Attribute.of("c", Boolean)

        schema.attribute(attr1)
        schema.attribute(attr2)
        producer.attribute(attr2)
        producer.attribute(attr3)

        expect:
        def merged = schema.mergeWith(producer)
        merged.hasAttribute(attr1)
        merged.hasAttribute(attr2)
        merged.hasAttribute(attr3)
    }

    def "uses the producers compatibility rules when the consumer does not express an opinion"() {
        def producer = new DefaultAttributesSchema(new ComponentAttributeMatcher(), TestUtil.instantiatorFactory(), SnapshotTestUtil.valueSnapshotter())

        def attr = Attribute.of("a", Flavor)

        schema.attribute(attr)
        producer.attribute(attr).compatibilityRules.add(CustomCompatibilityRule)

        expect:
        def merged = schema.mergeWith(producer)
        merged.matchValue(attr, flavor('value'), flavor('otherValue'))
    }

    def "uses the producers selection rules when the consumer does not express an opinion"() {
        def producer = new DefaultAttributesSchema(new ComponentAttributeMatcher(), TestUtil.instantiatorFactory(), SnapshotTestUtil.valueSnapshotter())

        def attr = Attribute.of("a", Flavor)

        schema.attribute(attr)
        producer.attribute(attr).disambiguationRules.add(CustomSelectionRule)

        def value1 = flavor('value')
        def value2 = flavor('otherValue')

        def candidates = [value1, value2] as Set

        when:
        def best = schema.mergeWith(producer).disambiguate(attr, flavor('requested'), candidates)

        then:
        best == [value1] as Set
    }

    interface Flavor extends Named {}

    enum MyEnum {
        FOO,
        BAR
    }

    static Flavor flavor(String name) {
        TestUtil.objectInstantiator().named(Flavor, name)
    }

    static class ConcreteNamed implements Named {
        String name
    }

}
