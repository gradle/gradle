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
import org.gradle.api.attributes.CompatibilityCheckDetails
import org.gradle.api.attributes.MultipleCandidatesDetails
import spock.lang.Specification

class DefaultAttributesSchemaTest extends Specification {
    def schema = new DefaultAttributesSchema()

    def "fails if no strategy is declared for custom type"() {
        when:
        schema.getMatchingStrategy(Attribute.of(Map))

        then:
        def e = thrown(IllegalArgumentException)
        e.message == 'Unable to find matching strategy for map'
    }

    def "is eventually incompatible by default"() {
        given:
        def strategy = schema.attribute(Attribute.of(Map)) {}
        def details = Mock(CompatibilityCheckDetails)

        when:
        details.getConsumerValue() >> AttributeValue.of([a: 'foo', b: 'bar'])
        details.getProducerValue() >> AttributeValue.missing()
        strategy.compatibilityRules.execute(details)

        then:
        1 * details.incompatible()
        0 * details._
    }

    def "equality strategy takes precendence over default"() {
        given:
        def strategy = schema.attribute(Attribute.of(Map))
        def details = Mock(CompatibilityCheckDetails)

        when:
        details.getConsumerValue() >> AttributeValue.of([a: 'foo', b: 'bar'])
        details.getProducerValue() >> AttributeValue.of([a: 'foo', b: 'baz'])
        strategy.compatibilityRules.execute(details)

        then:
        1 * details.incompatible()
        0 * details._
    }

    def "can set a basic equality match strategy"() {
        given:
        def strategy = schema.attribute(Attribute.of(Map))
        def details = Mock(CompatibilityCheckDetails)

        when:
        details.getConsumerValue() >> AttributeValue.of([a: 'foo', b: 'bar'])
        details.getProducerValue() >> AttributeValue.of([a: 'foo', b: 'bar'])
        strategy.compatibilityRules.execute(details)

        then:
        1 * details.compatible()
        0 * details.incompatible()

        when:
        details.getConsumerValue() >> AttributeValue.of([a: 'foo', b: 'bar'])
        details.getProducerValue() >> AttributeValue.of([a: 'foo', b: 'baz'])
        strategy.compatibilityRules.execute(details)

        then:
        0 * details.compatible()
        1 * details.incompatible()
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
        schema.getMatchingStrategy(Attribute.of(Map))

        then:
        e = thrown(IllegalArgumentException)
        e.message == 'Unable to find matching strategy for map'
    }

    @SuppressWarnings('VariableName')
    def "can set a custom matching strategy"() {
        def attr = Attribute.of(Map)

        given:
        schema.attribute(attr) {
            it.compatibilityRules.add { CompatibilityCheckDetails<Map> details ->
                def producerValue = details.producerValue
                def consumerValue = details.consumerValue
                if (producerValue.size() == consumerValue.size()) {
                    // arbitrary, just for testing purposes
                    details.compatible()
                }
            }
            it.disambiguationRules.add { details ->
                details.closestMatch(details.candidateValues.first())
            }
        }
        def strategy = schema.getMatchingStrategy(attr)
        def checkDetails = Mock(CompatibilityCheckDetails)
        def candidateDetails = Mock(MultipleCandidatesDetails)

        def aFoo_bBar = [a: 'foo', b: 'bar']
        def cFoo_dBar = [c: 'foo', d: 'bar']

        when:
        checkDetails.getConsumerValue() >> aFoo_bBar
        checkDetails.getProducerValue() >> aFoo_bBar
        strategy.compatibilityRules.execute(checkDetails)

        then:
        1 * checkDetails.compatible()
        0 * checkDetails.incompatible()

        when:
        checkDetails.getConsumerValue() >> aFoo_bBar
        checkDetails.getProducerValue() >> cFoo_dBar
        strategy.compatibilityRules.execute(checkDetails)

        then:
        1 * checkDetails.compatible()
        0 * checkDetails.incompatible()

        when:
        candidateDetails.candidateValues >> [aFoo_bBar, cFoo_dBar]
        strategy.disambiguationRules.execute(candidateDetails)

        then:
        1 * candidateDetails.closestMatch(aFoo_bBar)
        0 * candidateDetails._

    }
}
