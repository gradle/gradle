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
import org.gradle.api.attributes.HasAttributes
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.attributes.Usage
import org.gradle.api.internal.artifacts.JavaEcosystemSupport
import org.gradle.api.internal.provider.DefaultProperty
import org.gradle.api.internal.provider.DefaultProvider
import org.gradle.api.internal.provider.DefaultProviderWithValue
import org.gradle.api.internal.provider.PropertyHost
import org.gradle.api.internal.provider.Providers
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.internal.evaluation.CircularEvaluationException
import org.gradle.util.AttributeTestUtil
import org.gradle.util.TestUtil

/**
 * Unit tests for the {@link DefaultMutableAttributeContainer} class.
 */
final class DefaultMutableAttributeContainerTest extends BaseAttributeContainerTest {

    @Override
    protected DefaultMutableAttributeContainer createContainer(Map<Attribute<?>, ?> attributes = [:], Map<Attribute<?>, ?> moreAttributes = [:]) {
        DefaultMutableAttributeContainer container = new DefaultMutableAttributeContainer(attributesFactory, AttributeTestUtil.attributeValueIsolator(), TestUtil.propertyFactory())
        attributes.forEach {key, value ->
            container.attribute(key, value)
        }
        moreAttributes.forEach {key, value ->
            container.attribute(key, value)
        }
        return container
    }

    def "lazy attributes are evaluated in insertion order"() {
        def container = createContainer()
        def actual = []
        def expected = []
        (1..100).each { idx ->
            def testAttribute = Attribute.of("test"+idx, String)
            expected << idx
            container.attributeProvider(testAttribute, Providers.<String>changing {
                actual << idx
                "value " + idx
            })
        }
        expect:
        container.asImmutable().keySet().size() == 100
        actual == expected
    }

    def "cannot query container while realizing the value of lazy attributes"() {
        def container = createContainer()
        def firstAttribute = Attribute.of("first", String)
        def secondAttribute = Attribute.of("second", String)
        container.attributeProvider(firstAttribute, Providers.<String>changing {
            // side effect is to evaluate the secondAttribute's value and prevent
            // it from changing by removing it from the list of "lazy attributes"
            container.getAttribute(secondAttribute)
            "first"
        })
        container.attributeProvider(secondAttribute, Providers.of("second"))

        when:
        container.asImmutable()

        then:
        thrown(CircularEvaluationException)
    }

    def "realizing the value of lazy attributes cannot add new attributes to the container"() {
        def container = createContainer()
        def firstAttribute = Attribute.of("first", String)
        def secondAttribute = Attribute.of("second", String)
        container.attributeProvider(firstAttribute, Providers.<String>changing {
            container.attribute(secondAttribute, "second" )
            "first"
        })

        when:
        container.asImmutable()

        then:
        def e = thrown(IllegalStateException)
        e.message == "Cannot add new attribute 'second' while realizing all attributes of the container."
    }

    def "realizing the value of lazy attributes cannot add new lazy attributes to the container"() {
        def container = createContainer()
        def firstAttribute = Attribute.of("first", String)
        def secondAttribute = Attribute.of("second", String)
        container.attributeProvider(firstAttribute, Providers.<String>changing {
            container.attributeProvider(secondAttribute, Providers.of("second"))
            "first"
        })

        when:
        container.asImmutable()

        then:
        def e = thrown(IllegalStateException)
        e.message == "Cannot add new attribute 'second' while realizing all attributes of the container."
    }

    def "adding mismatched attribute types fails fast"() {
        Property<Integer> testProperty = new DefaultProperty<>(Mock(PropertyHost), Integer).convention(1)
        def testAttribute = Attribute.of("test", String)
        def container = createContainer()

        when:
        //noinspection GroovyAssignabilityCheck - meant to fail
        container.attributeProvider(testAttribute, testProperty)
        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains("Unexpected type for attribute 'test' provided. Expected a value of type java.lang.String but found a value of type java.lang.Integer.")
    }

    def "adding mismatched attribute types fails when retrieving the key when the provider does not know the type"() {
        Provider<?> testProperty = new DefaultProvider<?>( { 1 })
        def testAttribute = Attribute.of("test", String)
        def container = createContainer()

        when:
        //noinspection GroovyAssignabilityCheck - meant to fail
        container.attributeProvider(testAttribute, testProperty)
        container.getAttribute(testAttribute)
        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains("Unexpected type for attribute 'test' provided. Expected a value of type java.lang.String but found a value of type java.lang.Integer.")
    }

    def "equals should return true for 2 containers with different provider instances that return the same value"() {
        Property<String> testProperty1 = new DefaultProperty<>(Mock(PropertyHost), String).convention("value")
        Property<String> testProperty2 = new DefaultProperty<>(Mock(PropertyHost), String).convention("value")
        def testAttribute = Attribute.of("test", String)
        def container1 = createContainer()
        def container2 = createContainer()

        when:
        container1.attributeProvider(testAttribute, testProperty1)
        container2.attributeProvider(testAttribute, testProperty2)

        then:
        container1 == container2
        container2 == container1
    }

    def "equals should return false for 2 containers with different provider instances that return different values"() {
        Property<String> testProperty1 = new DefaultProperty<>(Mock(PropertyHost), String).convention("value1")
        Property<String> testProperty2 = new DefaultProperty<>(Mock(PropertyHost), String).convention("value2")
        def testAttribute = Attribute.of("test", String)
        def container1 = createContainer()
        def container2 = createContainer()

        when:
        container1.attributeProvider(testAttribute, testProperty1)
        container2.attributeProvider(testAttribute, testProperty2)

        then:
        container1 != container2
        container2 != container1
    }

    def "hashCode should return the same result for 2 containers with different provider instances that return the same value"() {
        Property<String> testProperty1 = new DefaultProperty<>(Mock(PropertyHost), String).convention("value")
        Property<String> testProperty2 = new DefaultProperty<>(Mock(PropertyHost), String).convention("value")
        def testAttribute = Attribute.of("test", String)
        def container1 = createContainer()
        def container2 = createContainer()

        when:
        container1.attributeProvider(testAttribute, testProperty1)
        container2.attributeProvider(testAttribute, testProperty2)

        then:
        container1.hashCode() == container2.hashCode()
    }

    def "hashCode should return different result for 2 containers with different provider instances that return different values"() {
        Property<String> testProperty1 = new DefaultProperty<>(Mock(PropertyHost), String).convention("value1")
        Property<String> testProperty2 = new DefaultProperty<>(Mock(PropertyHost), String).convention("value2")
        def testAttribute = Attribute.of("test", String)
        def container1 = createContainer()
        def container2 = createContainer()

        when:
        container1.attributeProvider(testAttribute, testProperty1)
        container2.attributeProvider(testAttribute, testProperty2)

        then:
        container1.hashCode() != container2.hashCode()
    }

    def "adding attribute should override replace existing lazy attribute"() {
        given: "a container with testAttr set to a provider"
        def testAttr = Attribute.of("test", String)
        def container = createContainer()
        Property<String> testProvider = new DefaultProperty<>(Mock(PropertyHost), String).convention("lazy value")
        container.attributeProvider(testAttr, testProvider)

        when: "adding a set value testAttr"
        container.attribute(testAttr, "set value")

        then: "the set value should be retrievable"
        "set value" == container.getAttribute(testAttr)
    }

    def "adding lazy attribute should override replace existing attribute"() {
        given: "a container with testAttr set to a fixed value"
        def testAttr = Attribute.of("test", String)
        def container = createContainer()
        container.attribute(testAttr, "set value")

        when: "adding a lazy testAttr"
        Property<String> testProvider = new DefaultProperty<>(Mock(PropertyHost), String).convention("lazy value")
        container.attributeProvider(testAttr, testProvider)

        then: "the lazy provider should be retrievable"
        "lazy value" == container.getAttribute(testAttr)
    }

    def "can query contents of container"() {
        def thing = Attribute.of("thing", String)
        def thing2 = Attribute.of("thing2", String)

        when:
        def container = createContainer()

        then:
        container.empty
        container.keySet().empty
        !container.contains(thing)
        container.getAttribute(thing) == null

        when:
        container.attribute(thing, "thing")

        then:
        !container.empty
        container.keySet() == [thing] as Set
        container.contains(thing)
        container.getAttribute(thing) == "thing"

        when:
        container.attributeProvider(thing2, Providers.of("value"))

        then:
        !container.empty
        container.keySet() == [thing, thing2] as Set
        container.contains(thing2)
        container.getAttribute(thing2) == "value"

    }

    def "A copy of an attribute container contains the same attributes and the same values as the original"() {
        given:
        def container = createContainer()
        container.attribute(Attribute.of("a1", Integer), 1)
        container.attribute(Attribute.of("a2", String), "2")
        container.attributeProvider(Attribute.of("a3", String), Providers.of("3"))

        when:
        def copy = container.asImmutable()

        then:
        copy.keySet().size() == 3
        copy.getAttribute(Attribute.of("a1", Integer)) == 1
        copy.getAttribute(Attribute.of("a2", String)) == "2"
        copy.getAttribute(Attribute.of("a3", String)) == "3"
    }

    def "changes to attribute container are not seen by immutable copy"() {
        given:
        AttributeContainerInternal container = createContainer()
        container.attribute(Attribute.of("a1", Integer), 1)
        container.attribute(Attribute.of("a2", String), "2")
        container.attributeProvider(Attribute.of("a3", String), Providers.of("3"))
        def immutable = container.asImmutable()

        when:
        container.attribute(Attribute.of("a1", Integer), 2)
        container.attribute(Attribute.of("a3", String), "3")
        container.attributeProvider(Attribute.of("a3", String), Providers.of("4"))

        then:
        immutable.keySet().size() == 3
        immutable.getAttribute(Attribute.of("a1", Integer)) == 1
        immutable.getAttribute(Attribute.of("a2", String)) == "2"
        immutable.getAttribute(Attribute.of("a3", String)) == "3"
    }

    def "An attribute container can provide the attributes through the HasAttributes interface"() {
        given:
        def container = createContainer()
        container.attribute(Attribute.of("a1", Integer), 1)
        container.attributeProvider(Attribute.of("a2", String), Providers.of("2"))

        when:
        HasAttributes access = container

        then:
        access.attributes.getAttribute(Attribute.of("a1", Integer)) == 1
        access.attributes.getAttribute(Attribute.of("a2", String)) == "2"
        access.attributes == access
    }

    def "has useful string representation"() {
        def a = Attribute.of("a", String)
        def b = Attribute.of("b", String)
        def c = Attribute.of("c", String)

        when:
        def container = createContainer()

        then:
        container.toString() == "{}"
        container.asImmutable().toString() == "{}"

        when:
        container.attribute(b, "b")
        container.attribute(c, "c")
        container.attributeProvider(a, Providers.of("a"))

        then:
        container.toString() == "{a=a, b=b, c=c}"
        container.asImmutable().toString() == "{a=a, b=b, c=c}"
    }

    def "can access lazy elements while iterating over keySet"() {
        def container = createContainer()

        when:
        container.attributeProvider(Attribute.of("a", String), Providers.of("foo"))
        container.attributeProvider(Attribute.of("b", String), Providers.of("foo"))

        then:
        for (Attribute<Object> attribute : container.keySet()) {
            container.getAttribute(attribute)
        }
    }

    def "can add deprecated usage then add libraryelements and convert to immutable"() {
        def container = createContainer()

        when:
        container.attribute(Usage.USAGE_ATTRIBUTE, TestUtil.objectInstantiator().named(Usage, JavaEcosystemSupport.DEPRECATED_JAVA_API_JARS))
        container.attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, TestUtil.objectInstantiator().named(LibraryElements, "aar"))

        then:
        def immutable = container.asImmutable()
        immutable.keySet().size() == 2
        immutable.getAttribute(Usage.USAGE_ATTRIBUTE).name == Usage.JAVA_API
        immutable.getAttribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE).name == "aar"
    }

    def "can add libraryelements then add deprecated usage and convert to immutable"() {
        def container = createContainer()

        when:
        container.attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, TestUtil.objectInstantiator().named(LibraryElements, "aar"))
        container.attribute(Usage.USAGE_ATTRIBUTE, TestUtil.objectInstantiator().named(Usage, JavaEcosystemSupport.DEPRECATED_JAVA_API_JARS))

        then:
        def immutable = container.asImmutable()
        immutable.keySet().size() == 2
        immutable.getAttribute(Usage.USAGE_ATTRIBUTE).name == Usage.JAVA_API
        immutable.getAttribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE).name == "jar"
    }

    def "can add 2 identically named attributes with the same type, resulting in a single entry and no exception thrown"() {
        def container = createContainer()

        when:
        container.attribute(Attribute.of("test", String), "a")
        container.attribute(Attribute.of("test", String), "b")

        then:
        container.asMap().with {
            assert it.size() == 1
            assert it[Attribute.of("test", String)] == "b" // Second attribute to be added remains
        }
    }

    def "cannot define two attributes with the same name but different types"() {
        def container = createContainer()

        given:
        container.attribute(Attribute.of('flavor', Boolean), true)
        container.attribute(Attribute.of('flavor', String.class), 'paid')

        when:
        container.asImmutable()

        then:
        def e = thrown(IllegalStateException)
        e.message == "Cannot have two attributes with the same name but different types. This container has an attribute named 'flavor' of type 'java.lang.Boolean' and another attribute of type 'java.lang.String'"
    }

    def "calling keySet does not realize lazy attributes when they are guaranteed to have a value"() {
        def container = createContainer()
        def testAttribute = Attribute.of("test", String)
        container.attributeProvider(testAttribute, new DefaultProviderWithValue<>(String.class, () -> {
            throw new RuntimeException("Foooooooo")
        }))

        when:
        def keys = container.keySet()

        then:
        keys.size() == 1
        keys.contains(testAttribute)

        when:
        container.getAttribute(testAttribute)

        then:
        def e = thrown(RuntimeException)
        e.message == "Foooooooo"
    }
}
