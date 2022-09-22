/*
 * Copyright 2022 the original author or authors.
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
import org.gradle.api.internal.provider.Providers
import org.gradle.util.AttributeTestUtil
import spock.lang.Specification

/**
 * Tests {@link JoinedAttributeContainer}.
 */
class JoinedAttributeContainerTest extends Specification {
    def attributesFactory = AttributeTestUtil.attributesFactory()

    def one = Attribute.of("one", String)
    def two = Attribute.of("two", String)

    private DefaultMutableAttributeContainer mutable() {
        return new DefaultMutableAttributeContainer(attributesFactory)
    }

    def "can override attributes from parent"() {
        given:
        def parent = mutable()
        def child = mutable()
        def joined = new JoinedAttributeContainer(attributesFactory, parent, child)

        when:
        parent.attribute(one, "parent")
        parent.attribute(two, "parent")
        child.attribute(one, "child")

        then:
        joined.getAttribute(one) == "child"
        joined.getAttribute(two) == "parent"
    }

    def "immutable containers are not modified when updating parent or child"() {
        given:
        def parent = mutable()
        def child = mutable()
        def joined = new JoinedAttributeContainer(attributesFactory, parent, child)

        parent.attribute(one, "parent")
        parent.attribute(two, "parent")
        child.attribute(two, "child")

        when:
        def immutable1 = joined.asImmutable()
        immutable1 instanceof ImmutableAttributes
        immutable1.getAttribute(one) == "child"
        immutable1.getAttribute(two) == "parent"
        child.attribute(two, "new child")
        parent.attribute(one, "new parent")
        parent.attribute(two, "new parent")

        then:
        joined.getAttribute(one) == "new parent"
        joined.getAttribute(two) == "new child"
        immutable1.getAttribute(one) == "parent"
        immutable1.getAttribute(two) == "child"

        when:
        def immutable2 = joined.asImmutable()

        then:
        immutable2.getAttribute(one) == "new parent"
        immutable2.getAttribute(two) == "new child"
    }

    def "keySet contains attributes from both parent and child"() {
        given:
        def parent = mutable()
        def child = mutable()
        def joined = new JoinedAttributeContainer(attributesFactory, parent, child)

        when:
        parent.attribute(one, "parent")

        then:
        joined.keySet() == [one] as Set

        when:
        child.attribute(one, "child")

        then:
        joined.keySet() == [one] as Set

        when:
        parent.attribute(two, "parent")

        then:
        joined.keySet() == [one, two] as Set

        when:
        child.attribute(two, "child")

        then:
        joined.keySet() == [one, two] as Set
    }

    def "mutations are passed to child container"() {
        given:
        def parent = mutable()
        def child = mutable()
        def joined = new JoinedAttributeContainer(attributesFactory, parent, child)

        when:
        joined.attribute(one, "joined")

        then:
        joined.getAttribute(one) == "joined"
        child.getAttribute(one) == "joined"
        parent.getAttribute(one) == null

        when:
        joined.attributeProvider(two, Providers.of("joined-provider"))

        then:
        joined.getAttribute(two) == "joined-provider"
        child.getAttribute(two) == "joined-provider"
        parent.getAttribute(two) == null
    }

    def "can chain joined containers"() {
        given:
        def parent = mutable()
        def middle = mutable()
        def child = mutable()
        def chain = new JoinedAttributeContainer(attributesFactory, parent,
            new JoinedAttributeContainer(attributesFactory, middle, child))

        when:
        parent.attribute(one, "parent")

        then:
        chain.getAttribute(one) == "parent"

        when:
        middle.attribute(one, "middle")

        then:
        chain.getAttribute(one) == "middle"

        when:
        child.attribute(one, "child")

        then:
        chain.getAttribute(one) == "child"
    }

    def "joined containers are equal if their parents and children are equal"() {
        given:
        def hasNone = mutable()
        def hasOne = mutable()
        hasOne.attribute(one, "one")
        def hasTwo = mutable()
        hasTwo.attribute(two, "two")
        def hasBoth = mutable()
        hasBoth.attribute(one, "one").attribute(two, "two")

        expect:
        new JoinedAttributeContainer(attributesFactory, hasOne, hasTwo) == new JoinedAttributeContainer(attributesFactory, hasOne, hasTwo)
        new JoinedAttributeContainer(attributesFactory, hasBoth, hasNone) != new JoinedAttributeContainer(attributesFactory, hasNone, hasBoth)
        new JoinedAttributeContainer(attributesFactory, hasBoth, hasNone) != new JoinedAttributeContainer(attributesFactory, hasBoth, hasOne)
        new JoinedAttributeContainer(attributesFactory, hasNone, hasBoth) != new JoinedAttributeContainer(attributesFactory, hasOne, hasBoth)

        // Same as above, but checking hash code
        new JoinedAttributeContainer(attributesFactory, hasOne, hasTwo).hashCode() == new JoinedAttributeContainer(attributesFactory, hasOne, hasTwo).hashCode()
        new JoinedAttributeContainer(attributesFactory, hasBoth, hasNone).hashCode() != new JoinedAttributeContainer(attributesFactory, hasNone, hasBoth).hashCode()
        new JoinedAttributeContainer(attributesFactory, hasBoth, hasNone).hashCode() != new JoinedAttributeContainer(attributesFactory, hasBoth, hasOne).hashCode()
        new JoinedAttributeContainer(attributesFactory, hasNone, hasBoth).hashCode() != new JoinedAttributeContainer(attributesFactory, hasOne, hasBoth).hashCode()
    }

    def "has useful toString"() {
        given:
        def parent = mutable()
        def child = mutable()
        def joined = new JoinedAttributeContainer(attributesFactory, parent, child)

        when:
        parent.attribute(one, "parent")
        parent.attribute(two, "parent")
        child.attribute(two, "child")

        then:
        joined.toString() == "{one=parent, two=child}"
    }

    def "can inherit attributes from parent container"() {
        given:
        def parent = mutable()
        def child = mutable()
        def joined = new JoinedAttributeContainer(attributesFactory, parent, child)

        expect:
        joined.empty
        joined.keySet().empty
        joined.asImmutable() == ImmutableAttributes.EMPTY
        !joined.contains(one)
        joined.getAttribute(one) == null

        when:
        parent.attribute(one, "parent")

        then:
        !joined.empty
        joined.keySet() == [one] as Set
        joined.contains(one)
        joined.getAttribute(one) == "parent"
        joined.asImmutable().keySet() == [one] as Set

        when:
        child.attribute(one, "child")

        then:
        !joined.empty
        joined.keySet() == [one] as Set
        joined.contains(one)
        joined.getAttribute(one) == "child"
        joined.asImmutable().keySet() == [one] as Set

        when:
        child.attribute(two, "child")

        then:
        joined.keySet() == [one, two] as Set
        joined.getAttribute(two) == "child"
        joined.asImmutable().keySet() == [one, two] as Set

        when:
        def child2 = mutable()
        def joined2 = new JoinedAttributeContainer(attributesFactory, mutable(), child2)
        child2.attribute(one, "child")

        then:
        !joined2.empty
        joined2.keySet() == [one] as Set
        joined2.contains(one)
        joined2.getAttribute(one) == "child"
        joined2.asImmutable().keySet() == [one] as Set
    }
}
