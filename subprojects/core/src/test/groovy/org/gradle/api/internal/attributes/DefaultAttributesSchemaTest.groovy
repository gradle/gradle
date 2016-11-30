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

import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.AttributeMatchingRules
import org.gradle.api.attributes.AttributeValue
import org.gradle.api.attributes.CompatibilityCheckDetails
import org.gradle.api.attributes.HasAttributes
import org.gradle.api.attributes.MultipleCandidatesDetails
import spock.lang.Specification

class DefaultAttributesSchemaTest extends Specification {
    def schema = new DefaultAttributesSchema()

    def "has a reasonable default matching strategy for String attributes"() {
        given:
        def strategy = schema.getMatchingStrategy(Attribute.of(String))
        def checkDetails = Mock(CompatibilityCheckDetails)
        def candidatesDetails = Mock(MultipleCandidatesDetails)
        def key = Mock(HasAttributes)

        when:
        checkDetails.getConsumerValue() >> AttributeValue.of('foo')
        checkDetails.getProducerValue() >> AttributeValue.of('foo')
        strategy.compatibilityRules.checkCompatibility(checkDetails)

        then:
        1 * checkDetails.compatible()
        0 * checkDetails.incompatible()

        when:
        checkDetails.getConsumerValue() >> AttributeValue.of('foo')
        checkDetails.getProducerValue() >> AttributeValue.of('bar')
        strategy.compatibilityRules.checkCompatibility(checkDetails)

        then:
        0 * checkDetails.compatible()
        1 * checkDetails.incompatible()

        when:
        candidatesDetails.consumerValue >> AttributeValue.of("foo")
        candidatesDetails.candidateValues >> [(key): AttributeValue.of("foo")]
        strategy.disambiguationRules.selectClosestMatch(candidatesDetails)

        then:
        1 * candidatesDetails.closestMatch(key)
        0 * candidatesDetails._
    }

    def "fails if no strategy is declared for custom type"() {
        when:
        schema.getMatchingStrategy(Attribute.of(Map))

        then:
        def e = thrown(IllegalArgumentException)
        e.message == 'Unable to find matching strategy for map'
    }

    def "is eventually incompatible by default"() {
        given:
        def strategy = schema.configureMatchingStrategy(Attribute.of(Map)) {}
        def details = Mock(CompatibilityCheckDetails)

        when:
        details.getConsumerValue() >> AttributeValue.of([a: 'foo', b: 'bar'])
        details.getProducerValue() >> AttributeValue.of([a: 'foo', b: 'bar'])
        strategy.compatibilityRules.checkCompatibility(details)

        then:
        1 * details.incompatible()
        0 * details._
    }

    def "can set eventually compatible by default"() {
        given:
        def strategy = schema.configureMatchingStrategy(Attribute.of(Map)) {
            it.compatibilityRules.eventuallyCompatible()
        }
        def details = Mock(CompatibilityCheckDetails)

        when:
        details.getConsumerValue() >> AttributeValue.of([a: 'foo', b: 'bar'])
        details.getProducerValue() >> AttributeValue.of([a: 'foo', b: 'baz'])
        strategy.compatibilityRules.checkCompatibility(details)

        then:
        1 * details.compatible()
        0 * details._
    }

    def "equality strategy takes precendence over default"() {
        given:
        def strategy = schema.configureMatchingStrategy(Attribute.of(Map)) {
            it.compatibilityRules.add(AttributeMatchingRules.equalityCompatibility())
            it.compatibilityRules.eventuallyCompatible()
        }
        def details = Mock(CompatibilityCheckDetails)

        when:
        details.getConsumerValue() >> AttributeValue.of([a: 'foo', b: 'bar'])
        details.getProducerValue() >> AttributeValue.of([a: 'foo', b: 'baz'])
        strategy.compatibilityRules.checkCompatibility(details)

        then:
        1 * details.incompatible()
        0 * details._
    }

    def "can set a basic equality match strategy"() {
        given:
        def strategy = schema.configureMatchingStrategy(Attribute.of(Map)) {
            it.compatibilityRules.add(AttributeMatchingRules.equalityCompatibility())
        }
        def details = Mock(CompatibilityCheckDetails)

        when:
        details.getConsumerValue() >> AttributeValue.of([a: 'foo', b: 'bar'])
        details.getProducerValue() >> AttributeValue.of([a: 'foo', b: 'bar'])
        strategy.compatibilityRules.checkCompatibility(details)

        then:
        1 * details.compatible()
        0 * details.incompatible()

        when:
        details.getConsumerValue() >> AttributeValue.of([a: 'foo', b: 'bar'])
        details.getProducerValue() >> AttributeValue.of([a: 'foo', b: 'baz'])
        strategy.compatibilityRules.checkCompatibility(details)

        then:
        0 * details.compatible()
        1 * details.incompatible()
    }

    def "strategy is per attribute"() {
        given:
        schema.configureMatchingStrategy(Attribute.of('a', Map)) {
            it.compatibilityRules.add(AttributeMatchingRules.equalityCompatibility())
        }

        when:
        schema.getMatchingStrategy(Attribute.of('someOther', Map))

        then:
        def e = thrown(IllegalArgumentException)
        e.message == 'Unable to find matching strategy for someOther'

        when:
        schema.getMatchingStrategy(Attribute.of(Map))

        then:
        e = thrown(IllegalArgumentException)
        e.message == 'Unable to find matching strategy for map'
    }

    def "can set a custom matching strategy"() {
        def attr = Attribute.of(Map)
        def key1 = Mock(HasAttributes)
        def key2 = Mock(HasAttributes)
        def key3 = Mock(HasAttributes)

        given:
        schema.configureMatchingStrategy(attr) {
            it.compatibilityRules.add { CompatibilityCheckDetails<Map> details ->
                def producerValue = details.producerValue
                def consumerValue = details.consumerValue
                if (producerValue.present && consumerValue.present) {
                    if (producerValue.get().size() == consumerValue.get().size()) {
                        // arbitrary, just for testing purposes
                        details.compatible()
                    }
                }
            }
            it.disambiguationRules.add { MultipleCandidatesDetails<Map> details ->
                def optionalAttribute = details.consumerValue
                def candidateValues = details.candidateValues
                if (optionalAttribute.present) {
                    candidateValues.findAll { it.value == optionalAttribute }*.key.each {
                        details.closestMatch(it)
                    }
                } else if (optionalAttribute.missing) {
                    details.closestMatch(candidateValues.keySet().first())
                } else {
                    candidateValues.keySet().each { details.closestMatch(it) }
                }
            }
        }
        def strategy = schema.getMatchingStrategy(attr)
        def checkDetails = Mock(CompatibilityCheckDetails)
        def candidateDetails = Mock(MultipleCandidatesDetails)

        when:
        checkDetails.getConsumerValue() >> AttributeValue.of([a: 'foo', b: 'bar'])
        checkDetails.getProducerValue() >> AttributeValue.of([a: 'foo', b: 'bar'])
        strategy.compatibilityRules.checkCompatibility(checkDetails)

        then:
        1 * checkDetails.compatible()
        0 * checkDetails.incompatible()

        when:
        checkDetails.getConsumerValue() >> AttributeValue.of([a: 'foo', b: 'bar'])
        checkDetails.getProducerValue() >> AttributeValue.of([c: 'foo', d: 'bar'])
        strategy.compatibilityRules.checkCompatibility(checkDetails)

        then:
        1 * checkDetails.compatible()
        0 * checkDetails.incompatible()

        when:
        candidateDetails.consumerValue >> AttributeValue.of([a: 'foo', b: 'bar'])
        candidateDetails.candidateValues >> [(key1): AttributeValue.of([a: 'foo', b: 'bar']), (key2): AttributeValue.of([c: 'foo', d: 'bar']), (key3): AttributeValue.of([a: 'foo', b: 'bar'])]
        strategy.disambiguationRules.selectClosestMatch(candidateDetails)

        then:
        1 * candidateDetails.closestMatch(key1)
        1 * candidateDetails.closestMatch(key3)
        0 * candidateDetails._

        when:
        candidateDetails.consumerValue >> AttributeValue.missing()
        candidateDetails.candidateValues >> [(key1): AttributeValue.of([a: 'foo', b: 'bar']), (key2): AttributeValue.of([c: 'foo', d: 'bar']), (key3): AttributeValue.of([a: 'foo', b: 'bar'])]
        strategy.disambiguationRules.selectClosestMatch(candidateDetails)

        then:
        1 * candidateDetails.closestMatch(key1)
        0 * candidateDetails._

        when:
        candidateDetails.consumerValue >> AttributeValue.unknown()
        candidateDetails.candidateValues >> [(key1): AttributeValue.of([a: 'foo', b: 'bar']), (key2): AttributeValue.of([c: 'foo', d: 'bar']), (key3): AttributeValue.of([a: 'foo', b: 'bar'])]
        strategy.disambiguationRules.selectClosestMatch(candidateDetails)

        then:
        1 * candidateDetails.closestMatch(key1)
        1 * candidateDetails.closestMatch(key2)
        1 * candidateDetails.closestMatch(key3)
        0 * candidateDetails._
    }
}
