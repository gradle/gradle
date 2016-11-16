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

package org.gradle.api.internal.project

import org.gradle.api.Attribute
import org.gradle.api.AttributeMatchingStrategy
import org.gradle.api.AttributeValue
import spock.lang.Specification

class DefaultConfigurationAttributesSchemaTest extends Specification {
    def schema = new DefaultConfigurationAttributesSchema()

    def "has a reasonable default matching strategy for String attributes"() {
        when:
        def strategy = schema.getMatchingStrategy(Attribute.of(String))

        then:
        strategy.isCompatible("foo", "foo")
        !strategy.isCompatible("foo", "bar")

        and:
        strategy.selectClosestMatch(AttributeValue.of("foo"), [a: "foo"]) == ['a']
    }

    def "fails if no strategy is declared for custom type"() {
        when:
        schema.getMatchingStrategy(Attribute.of(Map))

        then:
        def e = thrown(IllegalArgumentException)
        e.message == 'Unable to find matching strategy for map'
    }

    def "can set a basic equality match strategy"() {
        given:
        schema.matchStrictly(Attribute.of(Map))

        when:
        def strategy= schema.getMatchingStrategy(Attribute.of(Map))

        then:
        strategy.isCompatible([a: 'foo', b: 'bar'], [a: 'foo', b: 'bar'])
        !strategy.isCompatible([a: 'foo', b: 'bar'], [a: 'foo', b: 'baz'])
    }

    def "strategy is per attribute"() {
        given:
        schema.matchStrictly(Attribute.of('a', Map))

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

        given:
        schema.setMatchingStrategy(attr, new AttributeMatchingStrategy<Map>() {
            @Override
            boolean isCompatible(Map requestedValue, Map candidateValue) {
                requestedValue.size() == candidateValue.size() // arbitrary, just for testing purposes
            }

            @Override
            def <K> List<K> selectClosestMatch(AttributeValue<Map> optionalAttribute, Map<K, Map> candidateValues) {
                optionalAttribute.whenPresent { requestedValue ->
                    candidateValues.findAll { it.value == requestedValue }*.getKey()
                } whenMissing {
                    [candidateValues.keySet().first()]
                } getOrElse { candidateValues.keySet().toList() }
            }
        })

        when:
        def strategy = schema.getMatchingStrategy(attr)

        then:
        strategy.isCompatible([a: 'foo', b: 'bar'], [a: 'foo', b: 'bar'])
        strategy.isCompatible([a: 'foo', b: 'bar'], [c: 'foo', d: 'bar'])

        and:
        strategy.selectClosestMatch(AttributeValue.of([a: 'foo', b: 'bar']), [1: [a: 'foo', b: 'bar'], 2: [c: 'foo', d: 'bar'], 3: [a: 'foo', b: 'bar']]) == [1, 3]
        strategy.selectClosestMatch(AttributeValue.missing(), [1: [a: 'foo', b: 'bar'], 2: [c: 'foo', d: 'bar'], 3: [a: 'foo', b: 'bar']]) == [1]
        strategy.selectClosestMatch(AttributeValue.unknown(), [1: [a: 'foo', b: 'bar'], 2: [c: 'foo', d: 'bar'], 3: [a: 'foo', b: 'bar']]) == [1, 2, 3]
    }
}
