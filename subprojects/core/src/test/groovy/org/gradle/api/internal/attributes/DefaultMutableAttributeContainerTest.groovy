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
import org.gradle.api.attributes.Usage
import org.gradle.util.TestUtil
import spock.lang.Specification

class DefaultMutableAttributeContainerTest extends Specification {
    def attributesFactory = TestUtil.attributesFactory()

    def "can override attributes from parent"() {
        def attr1 = Attribute.of("one", String)
        def attr2 = Attribute.of("two", String)

        given:
        def parent = new DefaultMutableAttributeContainer(attributesFactory, TestUtil.objectInstantiator())
        parent.attribute(attr1, "parent")
        parent.attribute(attr2, "parent")

        def child = new DefaultMutableAttributeContainer(attributesFactory, parent, TestUtil.objectInstantiator())
        child.attribute(attr1, "child")

        expect:
        child.getAttribute(attr1) == "child"
        child.getAttribute(attr2) == "parent"

        def immutable1 = child.asImmutable()
        immutable1.getAttribute(attr1) == "child"
        immutable1.getAttribute(attr2) == "parent"

        parent.attribute(attr2, "new parent")

        child.getAttribute(attr1) == "child"
        child.getAttribute(attr2) == "new parent"

        immutable1.getAttribute(attr1) == "child"
        immutable1.getAttribute(attr2) == "parent"

        def immutable2 = child.asImmutable()
        immutable2.getAttribute(attr1) == "child"
        immutable2.getAttribute(attr2) == "new parent"
    }

    def "can use coercing attribute method"() {
        given:
        def container = new DefaultMutableAttributeContainer(attributesFactory, TestUtil.objectInstantiator())

        when:
        container.attribute(Usage.NAME, 'special')

        then:
        //calling ony getAttribute can not do coercing
        container.getAttribute(Usage.USAGE_ATTRIBUTE) == null
        container.asImmutable().getAttribute(Usage.USAGE_ATTRIBUTE) == null
        !container.asImmutable().findEntry(Usage.USAGE_ATTRIBUTE).isPresent()

        def entry = container.asImmutable().findEntry(Usage.NAME)
        entry.isPresent()
        entry.get() == 'special'
        entry.coerce(Usage.USAGE_ATTRIBUTE) instanceof Usage
        entry.coerce(Usage.USAGE_ATTRIBUTE).name == 'special'
    }

    def "can use coercing attribute method for String attributes"() {
        given:
        def container = new DefaultMutableAttributeContainer(attributesFactory, TestUtil.objectInstantiator())
        def attribute = Attribute.of('a', String)

        when:
        container.attribute(attribute.name, "value")

        then:
        container.getAttribute(attribute) == 'value'
        container.getAttribute(attribute).class == String
        container.asImmutable().getAttribute(attribute) == 'value'
    }
}
