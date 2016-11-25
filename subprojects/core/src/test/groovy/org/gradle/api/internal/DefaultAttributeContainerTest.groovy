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

package org.gradle.api.internal

import org.gradle.api.Attribute
import org.gradle.api.HasAttributes
import spock.lang.Specification

class DefaultAttributeContainerTest extends Specification {

    def "A copy of an attribute container contains the same attributes and the same values as the original"() {
        given:
        def container = new DefaultAttributeContainer()
        container.attribute(Attribute.of("a1", Integer), 1)
        container.attribute(Attribute.of("a2", String), "2")

        when:
        def copy = container.copy()

        then:
        copy.keySet().size() == 2
        copy.getAttribute(Attribute.of("a1", Integer)) == 1
        copy.getAttribute(Attribute.of("a2", String)) == "2"
    }

    def "A copy of an immutable attribute container is mutable and contains the same attributes and the same values as the original"() {
        given:
        AttributeContainerInternal container = new DefaultAttributeContainer()
        container.attribute(Attribute.of("a1", Integer), 1)
        container.attribute(Attribute.of("a2", String), "2")
        container = container.asImmutable()

        when:
        AttributeContainerInternal copy = container.copy()
        copy.attribute(Attribute.of("a3", String), "3")

        then:
        copy.keySet().size() == 3
        copy.getAttribute(Attribute.of("a1", Integer)) == 1
        copy.getAttribute(Attribute.of("a2", String)) == "2"
        copy.getAttribute(Attribute.of("a3", String)) == "3"
        noExceptionThrown()
    }

    def "A copy of an empty attribute container is a modifiable container which is empty"() {
        when:
        def copy = AttributeContainerInternal.EMPTY.copy()
        copy.attribute(Attribute.of("a1", Integer), 1)

        then:
        copy.keySet().size() == 1
        copy.getAttribute(Attribute.of("a1", Integer)) == 1
    }

    def "An attribute container can provide the attributes through the HasAttributes interface"() {
        given:
        def container = new DefaultAttributeContainer()
        container.attribute(Attribute.of("a1", Integer), 1)

        when:
        HasAttributes access = container

        then:
        access.attributes.getAttribute(Attribute.of("a1", Integer)) == 1
        access.attributes == access
    }
}
