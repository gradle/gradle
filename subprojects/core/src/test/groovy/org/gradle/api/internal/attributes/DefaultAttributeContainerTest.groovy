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
import org.gradle.api.attributes.HasAttributes
import spock.lang.Specification

import static org.gradle.api.internal.attributes.AttributeContainerInternal.EMPTY

class DefaultAttributeContainerTest extends Specification {
    def "can query contents of container"() {
        def thing = Attribute.of("thing", String)

        expect:
        def container = new DefaultAttributeContainer()

        container.empty
        container.keySet().empty
        !container.contains(thing)
        container.getAttribute(thing) == null

        container.attribute(thing, "thing")

        !container.empty
        container.keySet() == [thing] as Set
        container.contains(thing)
        container.getAttribute(thing) == "thing"
    }

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

    def "changes to attribute container are not seen by mutable copy"() {
        given:
        def container = new DefaultAttributeContainer()
        container.attribute(Attribute.of("a1", Integer), 1)
        container.attribute(Attribute.of("a2", String), "2")

        when:
        def copy = container.copy()
        container.attribute(Attribute.of("a1", Integer), 2)
        container.attribute(Attribute.of("a3", Number), 12L)

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
    }

    def "changes to attribute container are not seen by immutable copy"() {
        given:
        AttributeContainerInternal container = new DefaultAttributeContainer()
        container.attribute(Attribute.of("a1", Integer), 1)
        container.attribute(Attribute.of("a2", String), "2")
        def copy = container.asImmutable()

        when:
        container.attribute(Attribute.of("a1", Integer), 2)
        container.attribute(Attribute.of("a3", String), "3")

        then:
        copy.keySet().size() == 2
        copy.getAttribute(Attribute.of("a1", Integer)) == 1
        copy.getAttribute(Attribute.of("a2", String)) == "2"
    }

    def "A copy of an empty attribute container is a modifiable container which is empty"() {
        when:
        def copy = EMPTY.copy()
        copy.attribute(Attribute.of("a1", Integer), 1)

        then:
        copy.keySet().size() == 1
        copy.getAttribute(Attribute.of("a1", Integer)) == 1
    }

    def "immutable copy of empty attribute container is EMPTY"() {
        expect:
        new DefaultAttributeContainer().asImmutable().is(EMPTY)
        EMPTY.asImmutable().is(EMPTY)
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

    def "can inherit attributes from parent container"() {
        def thing = Attribute.of("thing", String)
        def other = Attribute.of("other", String)

        expect:
        def parent = new DefaultAttributeContainer()
        def child = new DefaultAttributeContainer(parent)

        child.empty
        child.keySet().empty
        child.asImmutable().is(EMPTY)
        child.copy().empty
        !child.contains(thing)
        child.getAttribute(thing) == null

        parent.attribute(thing, "parent")

        !child.empty
        child.keySet() == [thing] as Set
        child.contains(thing)
        child.getAttribute(thing) == "parent"
        child.asImmutable().keySet() == [thing] as Set
        child.copy().keySet() == [thing] as Set

        child.attribute(thing, "child")

        !child.empty
        child.keySet() == [thing] as Set
        child.contains(thing)
        child.getAttribute(thing) == "child"
        child.asImmutable().keySet() == [thing] as Set
        child.copy().keySet() == [thing] as Set

        child.attribute(other, "other")
        child.keySet() == [thing, other] as Set
        child.getAttribute(other) == "other"
        child.asImmutable().keySet() == [thing, other] as Set
        child.copy().keySet() == [thing, other] as Set

        def child2 = new DefaultAttributeContainer(new DefaultAttributeContainer())
        child2.attribute(thing, "child")

        !child2.empty
        child2.keySet() == [thing] as Set
        child2.contains(thing)
        child2.getAttribute(thing) == "child"
        child2.asImmutable().keySet() == [thing] as Set
        child2.copy().keySet() == [thing] as Set
    }

    def "has useful string representation"() {
        def a = Attribute.of("a", String)
        def b = Attribute.of("b", String)
        def c = Attribute.of("c", String)

        expect:
        def container = new DefaultAttributeContainer()
        container.toString() == "{}"
        container.asImmutable().toString() == "{}"

        container.attribute(b, "b")
        container.attribute(c, "c")
        container.attribute(a, "a")
        container.toString() == "{a=a, b=b, c=c}"
        container.asImmutable().toString() == "{a=a, b=b, c=c}"
    }

}
