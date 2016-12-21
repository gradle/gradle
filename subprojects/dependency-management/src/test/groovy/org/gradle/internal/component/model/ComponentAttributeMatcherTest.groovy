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
import org.gradle.api.internal.attributes.DefaultAttributeContainer
import org.gradle.api.internal.attributes.DefaultAttributesSchema
import spock.lang.Specification

class ComponentAttributeMatcherTest extends Specification {

    AttributesSchema schema = new DefaultAttributesSchema()

    def "Matching two exactly similar attributes gives a full match" () {
        def key = Attribute.of(String)
        schema.attribute(key)

        given:
        def candidate = new DefaultAttributeContainer()
        candidate.attribute(key, "value1")
        def requested = new DefaultAttributeContainer()
        requested.attribute(key, "value1")

        when:
        def matcher = new ComponentAttributeMatcher(schema, schema, Collections.singleton(candidate), requested, null)

        then:
        matcher.matchs == [candidate]
    }

    def "Matching two exactly similar attributes in presence of another one gives a partial match" () {
        def key1 = Attribute.of(String)
        def key2 = Attribute.of("a1", String)
        schema.attribute(key1)
        schema.attribute(key2) {
            it.compatibilityRules.assumeCompatibleWhenMissing()
        }

        given:
        def candidate = new DefaultAttributeContainer()
        candidate.attribute(key1, "value1")
        def requested = new DefaultAttributeContainer()
        requested.attribute(key1, "value1")
        requested.attribute(key2, "value2")

        when:
        def matcher = new ComponentAttributeMatcher(schema, schema, Collections.singleton(candidate), requested, null)

        then:
        matcher.matchs == [candidate]
    }

    def "Matching two attributes with distinct types gives no match and also no failure" () {
        def key1 = Attribute.of("a1", String)
        def key2 = Attribute.of("a2", String)
        schema.attribute(key1)
        schema.attribute(key2)

        given:
        def candidate = new DefaultAttributeContainer()
        candidate.attribute(key1, "value1")
        def requested = new DefaultAttributeContainer()
        requested.attribute(key2, "value1")

        when:
        def matcher = new ComponentAttributeMatcher(schema, schema, Collections.singleton(candidate), requested, null)

        then:
        matcher.matchs == []
    }

    def "Matching two attributes with same type but different value gives no match but a failure" () {
        def key = Attribute.of(String)
        schema.attribute(key)

        given:
        def candidate = new DefaultAttributeContainer()
        candidate.attribute(key, "value1")
        def requested = new DefaultAttributeContainer()
        requested.attribute(key, "value2")

        when:
        def matcher = new ComponentAttributeMatcher(schema, schema, Collections.singleton(candidate), requested, null)

        then:
        matcher.matchs == []
    }
}
