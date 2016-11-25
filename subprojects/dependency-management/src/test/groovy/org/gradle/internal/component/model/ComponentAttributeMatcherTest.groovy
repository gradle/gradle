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

import org.gradle.api.Attribute
import org.gradle.api.AttributesSchema
import org.gradle.api.internal.DefaultAttributeContainer
import org.gradle.api.internal.project.DefaultConfigurationAttributesSchema
import spock.lang.Specification

class ComponentAttributeMatcherTest extends Specification {

    AttributesSchema schema = new DefaultConfigurationAttributesSchema()

    def "Matching two exactly similar attributes gives a full match" () {
        given:
        def candidate = new DefaultAttributeContainer()
        candidate.attribute(Attribute.of(String), "value1")
        def requested = new DefaultAttributeContainer()
        requested.attribute(Attribute.of(String), "value1")

        when:
        def matcher = new ComponentAttributeMatcher(schema, null, Collections.singleton(candidate), requested);

        then:
        matcher.fullMatchs == [candidate]
        matcher.partialMatchs == []
        !matcher.hasFailingMatches()
    }

    def "Matching two exactly similar attributes in presence of another one gives a partial match" () {
        given:
        def candidate = new DefaultAttributeContainer()
        candidate.attribute(Attribute.of(String), "value1")
        def requested = new DefaultAttributeContainer()
        requested.attribute(Attribute.of(String), "value1")
        requested.attribute(Attribute.of("a1", String), "value2")

        when:
        def matcher = new ComponentAttributeMatcher(schema, null, Collections.singleton(candidate), requested);

        then:
        matcher.fullMatchs == []
        matcher.partialMatchs == [candidate]
        !matcher.hasFailingMatches()
    }

    def "Matching two attributes with distinct types gives no match and also no failure" () {
        given:
        def candidate = new DefaultAttributeContainer()
        candidate.attribute(Attribute.of("a1", String), "value1")
        def requested = new DefaultAttributeContainer()
        requested.attribute(Attribute.of("a2", String), "value1")

        when:
        def matcher = new ComponentAttributeMatcher(schema, null, Collections.singleton(candidate), requested);

        then:
        matcher.fullMatchs == []
        matcher.partialMatchs == []
        !matcher.hasFailingMatches()
    }

    def "Matching two attributes with same type but different value gives no match but a failure" () {
        given:
        def candidate = new DefaultAttributeContainer()
        candidate.attribute(Attribute.of(String), "value1")
        def requested = new DefaultAttributeContainer()
        requested.attribute(Attribute.of(String), "value2")

        when:
        def matcher = new ComponentAttributeMatcher(schema, null, Collections.singleton(candidate), requested);

        then:
        matcher.fullMatchs == []
        matcher.partialMatchs == []
        matcher.hasFailingMatches()
    }
}
