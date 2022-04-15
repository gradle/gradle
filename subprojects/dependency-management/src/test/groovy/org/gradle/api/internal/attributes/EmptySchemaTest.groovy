/*
 * Copyright 2017 the original author or authors.
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
import spock.lang.Specification

class EmptySchemaTest extends Specification {
    def "has no attributes"() {
        expect:
        EmptySchema.INSTANCE.attributes.empty
        !EmptySchema.INSTANCE.hasAttribute(Attribute.of(String))
    }

    def "has no precedence"() {
        expect:
        EmptySchema.INSTANCE.attributeDisambiguationPrecedence.empty
    }

    def "cannot set precedence"() {
        when:
        EmptySchema.INSTANCE.attributeDisambiguationPrecedence(Attribute.of("attribute", String))
        then:
        thrown(UnsupportedOperationException)

        when:
        EmptySchema.INSTANCE.setAttributeDisambiguationPrecedence([Attribute.of("attribute", String)])
        then:
        thrown(UnsupportedOperationException)
    }
}
