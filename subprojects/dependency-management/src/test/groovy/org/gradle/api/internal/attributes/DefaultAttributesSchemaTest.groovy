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
import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.AttributeCompatibilityRule
import org.gradle.api.attributes.AttributeDisambiguationRule
import org.gradle.api.attributes.CompatibilityCheckDetails
import org.gradle.api.attributes.MultipleCandidatesDetails
import org.gradle.internal.component.model.ComponentAttributeMatcher
import org.gradle.internal.component.model.DefaultCandidateResult
import org.gradle.internal.component.model.DefaultCompatibilityCheckResult
import org.gradle.util.TestUtil
import spock.lang.Specification

class DefaultAttributesSchemaTest extends Specification {
    def schema = new DefaultAttributesSchema(new ComponentAttributeMatcher(), TestUtil.instantiatorFactory())
    def factory = new DefaultImmutableAttributesFactory()

    def "fails if no strategy is declared for custom type"() {
        when:
        schema.getMatchingStrategy(Attribute.of('map', Map))

        then:
        def e = thrown(IllegalArgumentException)
        e.message == 'Unable to find matching strategy for map'
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

    def "is eventually incompatible by default when no rule expresses an opinion"() {
        given:
        def attribute = Attribute.of(String)
        schema.attribute(attribute).compatibilityRules.add(DoNothingRule)

        expect:
        def details = new DefaultCompatibilityCheckResult<String>("a", "b")
        schema.mergeWith(EmptySchema.INSTANCE).matchValue(attribute, details)
        !details.isCompatible()

        !schema.matcher().isMatching(attribute, "a", "b")

        DoNothingRule.count == 2
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
        schema.attribute(Attribute.of('a', Map))

        when:
        schema.getMatchingStrategy(Attribute.of('someOther', Map))

        then:
        def e = thrown(IllegalArgumentException)
        e.message == 'Unable to find matching strategy for someOther'

        when:
        schema.getMatchingStrategy(Attribute.of('map', Map))

        then:
        e = thrown(IllegalArgumentException)
        e.message == 'Unable to find matching strategy for map'
    }

    static class CustomCompatibilityRule implements AttributeCompatibilityRule<Map> {
        @Override
        void execute(CompatibilityCheckDetails<Map> details) {
            def producerValue = details.producerValue
            def consumerValue = details.consumerValue
            if (producerValue.size() > consumerValue.size()) {
                // arbitrary, just for testing purposes
                details.compatible()
            }
        }
    }

    static class CustomSelectionRule implements AttributeDisambiguationRule<Map> {
        @Override
        void execute(MultipleCandidatesDetails<Map> details) {
            details.closestMatch(details.candidateValues.first())
        }
    }

    def "can set a custom compatibility rule"() {
        def attr = Attribute.of(Map)

        given:
        schema.attribute(attr) {
            it.compatibilityRules.add(CustomCompatibilityRule)
        }

        def consumerValue = [a: 'foo', b: 'bar']
        def producerValue = [c: 'foo', d: 'bar', e: 'nothing']

        expect:
        def checkDetails = new DefaultCompatibilityCheckResult<Map>(consumerValue, producerValue)
        schema.mergeWith(EmptySchema.INSTANCE).matchValue(attr, checkDetails)
        checkDetails.isCompatible()

        schema.matcher().isMatching(attr, producerValue, consumerValue)
    }

    def "selects all candidates when no disambiguation rules"() {
        def attr = Attribute.of(Map)

        given:
        schema.attribute(attr)

        def value1 = [a: 'foo', b: 'bar']
        def value2 = [c: 'foo', d: 'bar']

        def best = []
        def candidates = LinkedListMultimap.create()
        candidates.put(value1, "item1")
        candidates.put(value2, "item2")
        def candidateDetails = new DefaultCandidateResult(candidates, best)

        when:
        schema.mergeWith(EmptySchema.INSTANCE).disambiguate(attr, candidateDetails)

        then:
        best == ["item1", "item2"]
    }

    def "can set a custom disambiguation rule"() {
        def attr = Attribute.of(Map)

        given:
        schema.attribute(attr) {
            it.disambiguationRules.add(CustomSelectionRule)
        }

        def value1 = [a: 'foo', b: 'bar']
        def value2 = [c: 'foo', d: 'bar']

        def best = []
        def candidates = LinkedListMultimap.create()
        candidates.put(value1, "item1")
        candidates.put(value2, "item2")
        def candidateDetails = new DefaultCandidateResult(candidates, best)

        when:
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
}
