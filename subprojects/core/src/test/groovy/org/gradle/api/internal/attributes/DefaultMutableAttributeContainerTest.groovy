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
import org.gradle.api.internal.provider.DefaultProperty
import org.gradle.api.internal.provider.DefaultProvider
import org.gradle.api.internal.provider.PropertyHost
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.util.AttributeTestUtil
import spock.lang.Specification

class DefaultMutableAttributeContainerTest extends Specification {
    def attributesFactory = AttributeTestUtil.attributesFactory()

    def "can override attributes from parent"() {
        def attr1 = Attribute.of("one", String)
        def attr2 = Attribute.of("two", String)

        given:
        def parent = new DefaultMutableAttributeContainer(attributesFactory)
        parent.attribute(attr1, "parent")
        parent.attribute(attr2, "parent")

        def child = new DefaultMutableAttributeContainer(attributesFactory, parent)
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

    def "adding mismatched attribute types fails fast"() {
        Property<Integer> testProperty = new DefaultProperty<>(Mock(PropertyHost), Integer).convention(1)
        def testAttribute = Attribute.of("test", String)
        def container = new DefaultMutableAttributeContainer(attributesFactory)

        when:
        container.attributeProvider(testAttribute, testProperty)
        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains("Unexpected type for attribute 'test' provided. Expected a value of type java.lang.String but found a value of type java.lang.Integer.")
    }

    def "adding mismatched attribute types fails when retrieving the key when the provider does not know the type"() {
        Provider<?> testProperty = new DefaultProvider<?>( { 1 })
        def testAttribute = Attribute.of("test", String)
        def container = new DefaultMutableAttributeContainer(attributesFactory)

        when:
        container.attributeProvider(testAttribute, testProperty)
        container.getAttribute(testAttribute)
        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains("Unexpected type for attribute 'test' provided. Expected a value of type java.lang.String but found a value of type java.lang.Integer.")
    }

    def "adding and retrieving lazy attribute works if attribute key already present in parent"() {
        given:
        def parent = new DefaultMutableAttributeContainer(attributesFactory)
        def testAttr = Attribute.of("test", String)
        parent.attribute(testAttr, "parent")

        Property<String> testProperty = new DefaultProperty<>(Mock(PropertyHost), String).convention("child")
        def child = new DefaultMutableAttributeContainer(attributesFactory, parent)

        when:
        child.attributeProvider(testAttr, testProperty)

        then:
        "child" == child.getAttribute(testAttr)
    }

    def "equals should return true for 2 containers with different provider instances that return the same value"() {
        Property<Integer> testProperty1 = new DefaultProperty<>(Mock(PropertyHost), String).convention("value")
        Property<Integer> testProperty2 = new DefaultProperty<>(Mock(PropertyHost), String).convention("value")
        def testAttribute = Attribute.of("test", String)
        def container1 = new DefaultMutableAttributeContainer(attributesFactory)
        def container2 = new DefaultMutableAttributeContainer(attributesFactory)

        when:
        container1.attributeProvider(testAttribute, testProperty1)
        container2.attributeProvider(testAttribute, testProperty2)

        then:
        container1 == container2
        container2 == container1
    }

    def "equals should return false for 2 containers with different provider instances that return different values"() {
        Property<Integer> testProperty1 = new DefaultProperty<>(Mock(PropertyHost), String).convention("value1")
        Property<Integer> testProperty2 = new DefaultProperty<>(Mock(PropertyHost), String).convention("value2")
        def testAttribute = Attribute.of("test", String)
        def container1 = new DefaultMutableAttributeContainer(attributesFactory)
        def container2 = new DefaultMutableAttributeContainer(attributesFactory)

        when:
        container1.attributeProvider(testAttribute, testProperty1)
        container2.attributeProvider(testAttribute, testProperty2)

        then:
        container1 != container2
        container2 != container1
    }

    def "hashCode should return the same result for 2 containers with different provider instances that return the same value"() {
        Property<Integer> testProperty1 = new DefaultProperty<>(Mock(PropertyHost), String).convention("value")
        Property<Integer> testProperty2 = new DefaultProperty<>(Mock(PropertyHost), String).convention("value")
        def testAttribute = Attribute.of("test", String)
        def container1 = new DefaultMutableAttributeContainer(attributesFactory)
        def container2 = new DefaultMutableAttributeContainer(attributesFactory)

        when:
        container1.attributeProvider(testAttribute, testProperty1)
        container2.attributeProvider(testAttribute, testProperty2)

        then:
        container1.hashCode() == container2.hashCode()
    }

    def "hashCode should return different result for 2 containers with different provider instances that return different values"() {
        Property<Integer> testProperty1 = new DefaultProperty<>(Mock(PropertyHost), String).convention("value1")
        Property<Integer> testProperty2 = new DefaultProperty<>(Mock(PropertyHost), String).convention("value2")
        def testAttribute = Attribute.of("test", String)
        def container1 = new DefaultMutableAttributeContainer(attributesFactory)
        def container2 = new DefaultMutableAttributeContainer(attributesFactory)

        when:
        container1.attributeProvider(testAttribute, testProperty1)
        container2.attributeProvider(testAttribute, testProperty2)

        then:
        container1.hashCode() != container2.hashCode()
    }
}
