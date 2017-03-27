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

    def "is eventually incompatible by default"() {
        given:
        def attribute = Attribute.of(String)
        schema.attribute(attribute)
        def details = new DefaultCompatibilityCheckResult<String>(AttributeValue.of("a"), AttributeValue.of("b"))

        when:
        schema.mergeWith(EmptySchema.INSTANCE).matchValue(attribute, details)

        then:
        !details.isCompatible()
    }

    def "equality strategy takes precedence over default"() {
        given:
        def attribute = Attribute.of(String)
        schema.attribute(attribute)
        def details = new DefaultCompatibilityCheckResult<String>(AttributeValue.of("a"), AttributeValue.of("a"))

        when:
        schema.mergeWith(EmptySchema.INSTANCE).matchValue(attribute, details)

        then:
        details.isCompatible()
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
            if (producerValue.size() == consumerValue.size()) {
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

        def value1 = [a: 'foo', b: 'bar']
        def value2 = [c: 'foo', d: 'bar']

        def checkDetails = new DefaultCompatibilityCheckResult<Map>(AttributeValue.of(value1), AttributeValue.of(value2))

        when:
        schema.mergeWith(EmptySchema.INSTANCE).matchValue(attr, checkDetails)

        then:
        checkDetails.isCompatible()
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

    def "merges compatible-when-missing flags"() {
        def producer = new DefaultAttributesSchema(new ComponentAttributeMatcher(), TestUtil.instantiatorFactory())

        def attr1 = Attribute.of("a", String)
        def attr2 = Attribute.of("b", Integer)

        schema.attribute(attr1)
        schema.attribute(attr2).compatibilityRules.assumeCompatibleWhenMissing()

        producer.attribute(attr1)
        producer.attribute(attr2)

        expect:
        def merged = schema.mergeWith(producer)
        merged.hasAttribute(attr1)
        merged.hasAttribute(attr2)
        !merged.isCompatibleWhenMissing(attr1)
        merged.isCompatibleWhenMissing(attr2)

        producer.attribute(attr1).compatibilityRules.assumeCompatibleWhenMissing()

        def merged2 = schema.mergeWith(producer)
        merged2.isCompatibleWhenMissing(attr1)
        merged2.isCompatibleWhenMissing(attr2)
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
