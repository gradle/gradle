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
 * Tests {@link HierarchicalAttributeContainer}.
 */
class HierarchicalAttributeContainerTest extends Specification {
    def attributesFactory = AttributeTestUtil.attributesFactory()

    def one = Attribute.of("one", String)
    def two = Attribute.of("two", String)

    private DefaultMutableAttributeContainer mutable() {
        return new DefaultMutableAttributeContainer(attributesFactory)
    }

    def "can override attributes from fallback"() {
        given:
        def fallback = mutable()
        def primary = mutable()
        def joined = new HierarchicalAttributeContainer(attributesFactory, fallback, primary)

        when:
        fallback.attribute(one, "fallback")
        fallback.attributeProvider(two, Providers.of("fallback"))
        primary.attribute(one, "primary")

        then:
        joined.getAttribute(one) == "primary"
        joined.getAttribute(two) == "fallback"
    }

    def "immutable containers are not modified when updating fallback or primary"() {
        given:
        def fallback = mutable()
        def primary = mutable()
        def joined = new HierarchicalAttributeContainer(attributesFactory, fallback, primary)

        fallback.attributeProvider(one, Providers.of("fallback"))
        fallback.attribute(two, "fallback")
        primary.attribute(two, "primary")

        when:
        def immutable1 = joined.asImmutable()

        then:
        immutable1 instanceof ImmutableAttributes
        immutable1.getAttribute(one) == "fallback"
        immutable1.getAttribute(two) == "primary"

        when:
        primary.attributeProvider(two, Providers.of("new primary"))
        fallback.attribute(one, "new fallback")
        fallback.attribute(two, "new fallback")

        then:
        joined.getAttribute(one) == "new fallback"
        joined.getAttribute(two) == "new primary"
        immutable1.getAttribute(one) == "fallback"
        immutable1.getAttribute(two) == "primary"

        when:
        def immutable2 = joined.asImmutable()

        then:
        immutable2.getAttribute(one) == "new fallback"
        immutable2.getAttribute(two) == "new primary"
    }

    def "keySet contains attributes from both fallback and primary"() {
        given:
        def fallback = mutable()
        def primary = mutable()
        def joined = new HierarchicalAttributeContainer(attributesFactory, fallback, primary)

        when:
        fallback.attribute(one, "fallback")

        then:
        joined.keySet() == [one] as Set

        when:
        primary.attributeProvider(one, Providers.of("primary"))

        then:
        joined.keySet() == [one] as Set

        when:
        fallback.attributeProvider(two, Providers.of("fallback"))

        then:
        joined.keySet() == [one, two] as Set

        when:
        primary.attribute(two, "primary")

        then:
        joined.keySet() == [one, two] as Set
    }

    def "mutations are passed to primary container"() {
        given:
        def fallback = mutable()
        def primary = mutable()
        def joined = new HierarchicalAttributeContainer(attributesFactory, fallback, primary)

        when:
        joined.attribute(one, "joined")

        then:
        joined.getAttribute(one) == "joined"
        primary.getAttribute(one) == "joined"
        fallback.getAttribute(one) == null

        when:
        joined.attributeProvider(two, Providers.of("joined-provider"))

        then:
        joined.getAttribute(two) == "joined-provider"
        primary.getAttribute(two) == "joined-provider"
        fallback.getAttribute(two) == null
    }

    def "can chain joined containers"() {
        given:
        def fallback = mutable()
        def middle = mutable()
        def primary = mutable()
        def chain = new HierarchicalAttributeContainer(attributesFactory, fallback,
            new HierarchicalAttributeContainer(attributesFactory, middle, primary))

        when:
        fallback.attribute(one, "fallback")

        then:
        chain.getAttribute(one) == "fallback"

        when:
        middle.attribute(one, "middle")

        then:
        chain.getAttribute(one) == "middle"

        when:
        primary.attributeProvider(one, Providers.of("primary"))

        then:
        chain.getAttribute(one) == "primary"
    }

    def "joined containers are equal if their fallbacks and primaryren are equal"() {
        given:
        def hasNone = mutable()
        def hasOne = mutable()
        hasOne.attribute(one, "one")
        def hasTwo = mutable()
        hasTwo.attribute(two, "two")
        def hasBoth = mutable()
        hasBoth.attribute(one, "one").attribute(two, "two")

        expect:
        new HierarchicalAttributeContainer(attributesFactory, hasOne, hasTwo) == new HierarchicalAttributeContainer(attributesFactory, hasOne, hasTwo)
        new HierarchicalAttributeContainer(attributesFactory, hasBoth, hasNone) != new HierarchicalAttributeContainer(attributesFactory, hasNone, hasBoth)
        new HierarchicalAttributeContainer(attributesFactory, hasBoth, hasNone) != new HierarchicalAttributeContainer(attributesFactory, hasBoth, hasOne)
        new HierarchicalAttributeContainer(attributesFactory, hasNone, hasBoth) != new HierarchicalAttributeContainer(attributesFactory, hasOne, hasBoth)

        // Same as above, but checking hash code
        new HierarchicalAttributeContainer(attributesFactory, hasOne, hasTwo).hashCode() == new HierarchicalAttributeContainer(attributesFactory, hasOne, hasTwo).hashCode()
        new HierarchicalAttributeContainer(attributesFactory, hasBoth, hasNone).hashCode() != new HierarchicalAttributeContainer(attributesFactory, hasNone, hasBoth).hashCode()
        new HierarchicalAttributeContainer(attributesFactory, hasBoth, hasNone).hashCode() != new HierarchicalAttributeContainer(attributesFactory, hasBoth, hasOne).hashCode()
        new HierarchicalAttributeContainer(attributesFactory, hasNone, hasBoth).hashCode() != new HierarchicalAttributeContainer(attributesFactory, hasOne, hasBoth).hashCode()
    }

    def "has useful toString"() {
        given:
        def fallback = mutable()
        def primary = mutable()
        def joined = new HierarchicalAttributeContainer(attributesFactory, fallback, primary)

        when:
        fallback.attribute(one, "fallback")
        fallback.attribute(two, "fallback")
        primary.attributeProvider(two, Providers.of("primary"))

        then:
        joined.toString() == "{one=fallback, two=primary}"
    }

    def "can inherit attributes from fallback container"() {
        given:
        def fallback = mutable()
        def primary = mutable()
        def joined = new HierarchicalAttributeContainer(attributesFactory, fallback, primary)

        expect:
        joined.empty
        joined.keySet().empty
        joined.asImmutable() == ImmutableAttributes.EMPTY
        !joined.contains(one)
        joined.getAttribute(one) == null

        when:
        fallback.attribute(one, "fallback")

        then:
        !joined.empty
        joined.keySet() == [one] as Set
        joined.contains(one)
        joined.getAttribute(one) == "fallback"
        joined.asImmutable().keySet() == [one] as Set

        when:
        primary.attribute(one, "primary")

        then:
        !joined.empty
        joined.keySet() == [one] as Set
        joined.contains(one)
        joined.getAttribute(one) == "primary"
        joined.asImmutable().keySet() == [one] as Set

        when:
        primary.attributeProvider(two, Providers.of("primary"))

        then:
        joined.keySet() == [one, two] as Set
        joined.getAttribute(two) == "primary"
        joined.asImmutable().keySet() == [one, two] as Set

        when:
        def primary2 = mutable()
        def joined2 = new HierarchicalAttributeContainer(attributesFactory, mutable(), primary2)
        primary2.attribute(one, "primary")

        then:
        !joined2.empty
        joined2.keySet() == [one] as Set
        joined2.contains(one)
        joined2.getAttribute(one) == "primary"
        joined2.asImmutable().keySet() == [one] as Set
    }
}
