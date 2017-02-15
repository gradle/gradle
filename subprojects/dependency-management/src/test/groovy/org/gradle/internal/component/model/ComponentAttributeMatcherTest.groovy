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

package org.gradle.internal.component.model

import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.AttributesSchema
import org.gradle.api.internal.attributes.DefaultMutableAttributeContainer
import org.gradle.api.internal.attributes.DefaultAttributesSchema
import org.gradle.api.internal.attributes.DefaultImmutableAttributesFactory
import org.gradle.api.internal.attributes.ImmutableAttributesFactory
import spock.lang.Specification

class ComponentAttributeMatcherTest extends Specification {

    AttributesSchema schema = new DefaultAttributesSchema(new ComponentAttributeMatcher())
    ImmutableAttributesFactory factory = new DefaultImmutableAttributesFactory()

    def "Matching two exactly similar attributes gives a full match" () {
        def key = Attribute.of(String)
        schema.attribute(key)

        given:
        def candidate = attributes()
        candidate.attribute(key, "value1")
        def requested = attributes()
        requested.attribute(key, "value1")

        when:
        def matches = new ComponentAttributeMatcher().match(schema, schema, [candidate], requested)

        then:
        matches == [candidate]
    }

    def "Matching two exactly similar attributes in presence of another one gives a partial match" () {
        def key1 = Attribute.of(String)
        def key2 = Attribute.of("a1", String)
        schema.attribute(key1)
        schema.attribute(key2) {
            it.compatibilityRules.assumeCompatibleWhenMissing()
        }

        given:
        def candidate = attributes()
        candidate.attribute(key1, "value1")
        def requested = attributes()
        requested.attribute(key1, "value1")
        requested.attribute(key2, "value2")

        when:
        def matches = new ComponentAttributeMatcher().match(schema, schema, [candidate], requested)

        then:
        matches == [candidate]
    }

    def "Matching two attributes with distinct types gives no match" () {
        def key1 = Attribute.of("a1", String)
        def key2 = Attribute.of("a2", String)
        schema.attribute(key1)
        schema.attribute(key2)

        given:
        def candidate = attributes()
        candidate.attribute(key1, "value1")
        def requested = attributes()
        requested.attribute(key2, "value1")

        when:
        def matches = new ComponentAttributeMatcher().match(schema, schema, [candidate], requested)

        then:
        matches == []
    }

    def "Matching two attributes with same type but different value gives no match" () {
        def key = Attribute.of(String)
        schema.attribute(key)

        given:
        def candidate = attributes()
        candidate.attribute(key, "value1")
        def requested = attributes()
        requested.attribute(key, "value2")

        when:
        def matches = new ComponentAttributeMatcher().match(schema, schema, [candidate], requested)

        then:
        matches == []
    }

    def "can ignore additional producer attributes" () {
        def key1 = Attribute.of("a1", String)
        def key2 = Attribute.of("a2", String)
        schema.attribute(key1)
        schema.attribute(key2)

        given:
        def candidate = attributes()
        candidate.attribute(key1, "value1")
        candidate.attribute(key2, "ignore me")
        def requested = attributes()
        requested.attribute(key1, "value1")

        expect:
        def matcher = new ComponentAttributeMatcher()

        def matches1 = matcher.match(schema, schema, [candidate], requested)
        matches1.empty

        def matches2 = matcher.ignoreAdditionalProducerAttributes().match(schema, schema, [candidate], requested)
        matches2 == [candidate]
    }

    def "disambiguates using ignored producer attributes" () {
        def key1 = Attribute.of("a1", String)
        def key2 = Attribute.of("a2", String)
        schema.attribute(key1)
        schema.attribute(key2).disambiguationRules.add { details -> if (details.candidateValues.contains("ignore2")) { details.closestMatch("ignore2") } }

        given:
        def candidate1 = attributes()
        candidate1.attribute(key1, "value1")
        candidate1.attribute(key2, "ignored1")
        def candidate2 = attributes()
        candidate2.attribute(key1, "value1")
        candidate2.attribute(key2, "ignore2")
        def requested = attributes()
        requested.attribute(key1, "value1")

        expect:
        def matcher = new ComponentAttributeMatcher()

        def matches = matcher.ignoreAdditionalProducerAttributes().match(schema, schema, [candidate1, candidate2], requested)
        matches == [candidate2]
    }

    def "can ignore additional consumer attributes" () {
        def key1 = Attribute.of("a1", String)
        def key2 = Attribute.of("a2", String)
        schema.attribute(key1)
        schema.attribute(key2)

        given:
        def candidate = attributes()
        candidate.attribute(key1, "value1")
        def requested = attributes()
        requested.attribute(key1, "value1")
        requested.attribute(key2, "ignore me")

        expect:
        def matcher = new ComponentAttributeMatcher()

        def matches1 = matcher.match(schema, schema, [candidate], requested)
        matches1.empty

        def matches2 = matcher.ignoreAdditionalConsumerAttributes().match(schema, schema, [candidate], requested)
        matches2 == [candidate]
    }

    private DefaultMutableAttributeContainer attributes() {
        new DefaultMutableAttributeContainer(factory)
    }
}
