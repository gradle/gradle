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

import com.google.common.collect.LinkedListMultimap
import org.gradle.api.Named
import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.AttributeCompatibilityRule
import org.gradle.api.attributes.AttributeDisambiguationRule
import org.gradle.api.attributes.CompatibilityCheckDetails
import org.gradle.api.attributes.MultipleCandidatesDetails
import org.gradle.api.internal.changedetection.state.Scalars
import org.gradle.api.internal.model.NamedObjectInstantiator
import org.gradle.internal.component.model.ComponentAttributeMatcher
import org.gradle.internal.component.model.DefaultCandidateResult
import org.gradle.internal.component.model.DefaultCompatibilityCheckResult
import org.gradle.util.TestUtil
import spock.lang.Specification
import spock.lang.Unroll

class DefaultAttributesSchemaTest extends Specification {
    def schema = new DefaultAttributesSchema(new ComponentAttributeMatcher(), TestUtil.instantiatorFactory())
    def factory = new DefaultImmutableAttributesFactory()

    @Unroll
    def "can create an attribute of scalar type #type"() {
        when:
        Attribute.of('foo', type)

        then:
        noExceptionThrown()

        where:
        type << [
            *Scalars.TYPES,
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
            *Scalars.TYPES,
            MyEnum,
            Flavor
        ]
    }

    def "displays a reasonable error message if attribute type is unsupported"() {
        when:
        Attribute.of('attr', Date)

        then:
        def e = thrown(IllegalArgumentException)

        and:
        e.message == '''Cannot declare a attribute 'attr' with type class java.util.Date. Supported types are: 
   - primitive types (byte, boolean, char, short, int, long, float, double) and their wrapped types (Byte, ...)
   - an enum
   - an instance of String
   - an instance of File
   - an instance of BigInteger, BigDecimal, AtomicInteger, AtomicBoolean or AtomicLong
   - a Named instance
   - an array of the above
'''
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
        def details = new DefaultCompatibilityCheckResult<String>("a", "b")
        schema.mergeWith(EmptySchema.INSTANCE).matchValue(attribute, details)
        !details.isCompatible()

        def details2 = new DefaultCompatibilityCheckResult<String>("a", "a")
        schema.mergeWith(EmptySchema.INSTANCE).matchValue(attribute, details2)
        details2.isCompatible()

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
        def details = new DefaultCompatibilityCheckResult<String>("a", "b")
        schema.mergeWith(EmptySchema.INSTANCE).matchValue(attribute, details)
        !details.isCompatible()

        def details2 = new DefaultCompatibilityCheckResult<String>("a", "a")
        schema.mergeWith(EmptySchema.INSTANCE).matchValue(attribute, details2)
        details2.isCompatible()

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
        def details = new DefaultCompatibilityCheckResult<String>("a", "a")
        schema.mergeWith(EmptySchema.INSTANCE).matchValue(attribute, details)
        details.isCompatible()

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
        def details = new DefaultCompatibilityCheckResult<Flavor>(value1, value2)
        schema.mergeWith(EmptySchema.INSTANCE).matchValue(attr, details)
        details.isCompatible()

        def details2 = new DefaultCompatibilityCheckResult<Flavor>(value2, value1)
        schema.mergeWith(EmptySchema.INSTANCE).matchValue(attr, details2)
        !details2.isCompatible()

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
        def details = new DefaultCompatibilityCheckResult<String>("a", "b")
        schema.mergeWith(EmptySchema.INSTANCE).matchValue(attr, details)
        !details.isCompatible()

        !schema.matcher().isMatching(attr, "a", "b")
    }

    def "selects requested value when it is one of the candidate values and no rules defined"() {
        def attr = Attribute.of(String)

        given:
        schema.attribute(attr)

        def best = []
        def candidates = LinkedListMultimap.create()
        candidates.put("foo", "item1")
        candidates.put("bar", "item2")
        def candidateDetails = new DefaultCandidateResult(candidates, "bar", best)

        when:
        schema.mergeWith(EmptySchema.INSTANCE).disambiguate(attr, candidateDetails)

        then:
        best == ["item2"]
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

        def best = []
        def candidates = LinkedListMultimap.create()
        candidates.put("foo", "item1")
        candidates.put("bar", "item2")
        def candidateDetails = new DefaultCandidateResult(candidates, "bar", best)

        when:
        schema.mergeWith(EmptySchema.INSTANCE).disambiguate(attr, candidateDetails)

        then:
        best == ["item2"]
    }

    def "selects all candidates when no disambiguation rules and requested is not one of the candidate values"() {
        def attr = Attribute.of(String)

        given:
        schema.attribute(attr)

        def best = []
        def candidates = LinkedListMultimap.create()
        candidates.put("foo", "item1")
        candidates.put("bar", "item2")
        def candidateDetails = new DefaultCandidateResult(candidates, "other", best)

        when:
        schema.mergeWith(EmptySchema.INSTANCE).disambiguate(attr, candidateDetails)

        then:
        best == ["item1", "item2"]
    }

    def "selects all candidates when no rule expresses an opinion and requested is not one of the candidate values"() {
        def attr = Attribute.of(String)

        given:
        schema.attribute(attr).disambiguationRules.add(DoNothingSelectionRule)

        def best = []
        def candidates = LinkedListMultimap.create()
        candidates.put("foo", "item1")
        candidates.put("bar", "item2")
        def candidateDetails = new DefaultCandidateResult(candidates, "other", best)

        when:
        schema.mergeWith(EmptySchema.INSTANCE).disambiguate(attr, candidateDetails)

        then:
        best == ["item1", "item2"]
    }

    def "custom rule can select best match"() {
        def attr = Attribute.of(Flavor)

        given:
        schema.attribute(attr).disambiguationRules.add(CustomSelectionRule)

        def value1 = flavor('value1')
        def value2 = flavor('value2')

        def candidates = LinkedListMultimap.create()
        candidates.put(value1, "item1")
        candidates.put(value2, "item2")

        when:
        def best = []
        def candidateDetails = new DefaultCandidateResult(candidates, flavor('requested'), best)

        schema.mergeWith(EmptySchema.INSTANCE).disambiguate(attr, candidateDetails)

        then:
        best == ["item1"]

        when:
        best = []
        candidateDetails = new DefaultCandidateResult(candidates, value2, best)

        schema.mergeWith(EmptySchema.INSTANCE).disambiguate(attr, candidateDetails)

        then:
        best == ["item1"]
    }

    def "merging creates schema with additional attributes defined by producer"() {
        def producer = new DefaultAttributesSchema(new ComponentAttributeMatcher(), TestUtil.instantiatorFactory())

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
        def producer = new DefaultAttributesSchema(new ComponentAttributeMatcher(), TestUtil.instantiatorFactory())

        def attr = Attribute.of("a", Flavor)

        schema.attribute(attr)
        producer.attribute(attr).compatibilityRules.add(CustomCompatibilityRule)

        expect:
        def merged = schema.mergeWith(producer)
        def result = new DefaultCompatibilityCheckResult<Object>(flavor('value'), flavor('otherValue'))
        merged.matchValue(attr, result)
        result.compatible
    }

    def "uses the producers selection rules when the consumer does not express an opinion"() {
        def producer = new DefaultAttributesSchema(new ComponentAttributeMatcher(), TestUtil.instantiatorFactory())

        def attr = Attribute.of("a", Flavor)

        schema.attribute(attr)
        producer.attribute(attr).disambiguationRules.add(CustomSelectionRule)

        def value1 = flavor('value')
        def value2 = flavor('otherValue')

        def candidates = LinkedListMultimap.create()
        candidates.put(value1, "item1")
        candidates.put(value2, "item2")

        when:
        def best = []
        def candidateDetails = new DefaultCandidateResult(candidates, flavor('requested'), best)

        schema.mergeWith(producer).disambiguate(attr, candidateDetails)

        then:
        best == ["item1"]
    }

    interface Flavor extends Named {}

    enum MyEnum {
        FOO,
        BAR
    }

    static Flavor flavor(String name) {
        NamedObjectInstantiator.INSTANCE.named(Flavor, name)
    }

}
