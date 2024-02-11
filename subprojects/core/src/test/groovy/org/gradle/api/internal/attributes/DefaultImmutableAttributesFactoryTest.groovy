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
import org.gradle.api.internal.artifacts.JavaEcosystemSupport
import org.gradle.internal.snapshot.impl.CoercingStringValueSnapshot
import org.gradle.util.SnapshotTestUtil
import org.gradle.util.TestUtil
import spock.lang.Specification

class DefaultImmutableAttributesFactoryTest extends Specification {
    private static final Attribute<String> FOO = Attribute.of("foo", String)
    private static final Attribute<String> BAR = Attribute.of("bar", String)
    private static final Attribute<Object> OTHER_BAR = Attribute.of(BAR.name, Object.class)
    private static final Attribute<String> BAZ = Attribute.of("baz", String)

    def isolatableFactory = SnapshotTestUtil.isolatableFactory()
    def instantiator = TestUtil.objectInstantiator()

    def factory = new DefaultImmutableAttributesFactory(isolatableFactory, instantiator)

    def "can create empty set"() {
        when:
        def attributes = factory.root

        then:
        attributes.empty
        attributes.keySet() == [] as Set
    }

    def "can lookup entries in empty set"() {
        when:
        def attributes = factory.root

        then:
        attributes.getAttribute(FOO) == null
        !attributes.findEntry(FOO).isPresent()
        !attributes.findEntry("foo").isPresent()
    }

    def "can create a single entry immutable set"() {
        when:
        def attributes = factory.of(FOO, "foo")

        then:
        attributes.keySet() == [FOO] as Set

        and:
        attributes.getAttribute(FOO) == 'foo'
    }

    def "can lookup entries in a singleton set"() {
        when:
        def attributes = factory.of(FOO, "foo")

        then:
        attributes.getAttribute(FOO) == 'foo'
        attributes.findEntry(FOO).get() == "foo"
        attributes.findEntry("foo").get() == "foo"

        attributes.getAttribute(BAR) == null
        !attributes.findEntry(BAR).isPresent()
        !attributes.findEntry("bar").isPresent()
    }

    def "caches singleton sets"() {
        expect:
        def attributes = factory.of(FOO, "foo")
        factory.of(FOO, "foo").is(attributes)
        factory.of(FOO, "other") != attributes
        factory.of(BAR, "foo") != attributes
    }

    def "can concatenate immutable attributes sets"() {
        when:
        def set1 = factory.of(FOO, "foo")
        def set2 = factory.of(BAR, "bar")
        def union = factory.concat(set1, set2)

        then:
        union.keySet() == [FOO, BAR] as Set

        and:
        union.getAttribute(FOO) == 'foo'
        union.getAttribute(BAR) == 'bar'
    }

    def "can concatenate attribute to an empty set"() {
        when:
        def set = factory.concat(factory.root, FOO, "foo")

        then:
        set.keySet() == [FOO] as Set

        and:
        set.getAttribute(FOO) == 'foo'
    }

    def "can concatenate attribute to a singleton set"() {
        when:
        def set1 = factory.of(FOO, "foo")
        def set2 = factory.concat(set1, BAR, "bar")

        then:
        set2.keySet() == [FOO, BAR] as Set

        and:
        set2.getAttribute(FOO) == 'foo'
        set2.getAttribute(BAR) == 'bar'
    }

    def "can concatenate attribute to multiple value set"() {
        given:
        def attributes = factory.of(FOO, 'foo')
        attributes = factory.concat(attributes, BAR, 'bar')

        when:
        def set = factory.concat(attributes, BAZ, 'baz')

        then:
        set.keySet() == [FOO, BAR, BAZ] as Set
        set.getAttribute(FOO) == 'foo'
        set.getAttribute(BAR) == 'bar'
        set.getAttribute(BAZ) == 'baz'
    }

    def "can lookup entries in a multiple value set"() {
        when:
        def attributes = factory.concat(factory.of(FOO, 'foo'), BAR, 'bar')

        then:
        attributes.getAttribute(FOO) == "foo"
        attributes.findEntry(FOO).get() == "foo"
        attributes.findEntry("foo").get() == "foo"

        attributes.getAttribute(BAR) == "bar"
        attributes.findEntry(BAR).get() == "bar"
        attributes.findEntry("bar").get() == "bar"

        attributes.getAttribute(BAZ) == null
        !attributes.findEntry(BAZ).isPresent()
        !attributes.findEntry("baz").isPresent()
    }

    def "caches instances of multiple value sets"() {
        given:
        def attributes = factory.concat(factory.of(FOO, 'foo'), BAR, 'bar')

        expect:
        def a2 = factory.concat(factory.of(FOO, 'foo'), BAR, 'bar')
        a2.is(attributes)

        def a3 = factory.concat(factory.of(FOO, 'foo'), BAR, 'other')
        a3 != attributes
    }

    def "order of entries is not significant in equality"() {
        when:
        def set1 = factory.concat(factory.of(FOO, "foo"), BAR, "bar")
        def set2 = factory.concat(factory.of(BAR, "bar"), FOO, "foo")

        then:
        set1 == set2
    }

    def "can compare attribute sets created by two different factories"() {
        given:
        def otherFactory = new DefaultImmutableAttributesFactory(isolatableFactory, instantiator)

        when:
        def set1 = factory.concat(factory.of(FOO, "foo"), BAR, "bar")
        def set2 = otherFactory.concat(otherFactory.of(BAR, "bar"), FOO, "foo")

        then:
        set1 == set2
    }

    def "can append to a set created with a different factory"() {
        given:
        def otherFactory = new DefaultImmutableAttributesFactory(isolatableFactory, instantiator)
        def attributes = otherFactory.of(FOO, 'foo')

        when:
        def set = factory.concat(attributes, BAR, 'bar')

        then:
        set.keySet() == [FOO, BAR] as Set
        set.getAttribute(FOO) == 'foo'
        set.getAttribute(BAR) == 'bar'
    }

    def "immutable attribute sets throw a default error when attempting modification"() {
        given:
        def attributes = factory.root

        when:
        attributes.attribute(FOO, "foo")

        then:
        UnsupportedOperationException t = thrown()
        t.message == "Mutation of attributes is not allowed"
    }

    def "can override values"() {
        given:
        def set1 = factory.concat(factory.of(FOO, "foo1"), factory.of(BAR, "bar1"))
        def set2 = factory.of(BAR, "bar2")

        when:
        def concat = factory.concat(set1, set2)

        then:
        concat.keySet() == [FOO, BAR] as Set
        concat.getAttribute(FOO) == "foo1"
        concat.getAttribute(BAR) == "bar2"
    }

    def "can replace attribute with same name and different type"() {
        given:
        def set1 = factory.concat(factory.of(FOO, "foo1"), factory.of(OTHER_BAR, "bar1"))
        def set2 = factory.of(BAR, "bar2")

        when:
        def concat = factory.concat(set1, set2)

        then:
        concat.keySet() == [FOO, BAR] as Set
        concat.getAttribute(FOO) == "foo1"
        concat.getAttribute(BAR) == "bar2"
    }

    def "can detect incompatible values when merging"() {
        given:
        def set1 = factory.concat(factory.of(FOO, "foo1"), factory.of(BAR, "bar1"))
        def set2 = factory.concat(factory.of(FOO, "foo1"), factory.of(BAR, "bar2"))

        when:
        factory.safeConcat(set1, set2)

        then:
        AttributeMergingException e = thrown()
        e.attribute == BAR
        e.leftValue == "bar1"
        e.rightValue == "bar2"
    }

    def "can detect incompatible attributes with different types when merging"() {
        given:
        def set1 = factory.concat(factory.of(FOO, "foo1"), factory.of(OTHER_BAR, "bar1"))
        def set2 = factory.concat(factory.of(FOO, "foo1"), factory.of(BAR, "bar2"))

        when:
        factory.safeConcat(set1, set2)

        then:
        AttributeMergingException e = thrown()
        e.attribute == OTHER_BAR
        e.leftValue == "bar1"
        e.rightValue == "bar2"
    }

    def "translates deprecated usage values"() {
        def result = factory.concat(factory.of(FOO, "foo"), Usage.USAGE_ATTRIBUTE, instantiator.named(Usage, JavaEcosystemSupport.DEPRECATED_JAVA_API_JARS))

        expect:
        result.findEntry(Usage.USAGE_ATTRIBUTE).get().name == "java-api"
    }

    def "translates deprecated usage values as Isolatable"() {
        def result = factory.concat(factory.of(FOO, "foo"), Usage.USAGE_ATTRIBUTE, new CoercingStringValueSnapshot(JavaEcosystemSupport.DEPRECATED_JAVA_RUNTIME_JARS, instantiator))

        expect:
        result.findEntry(Usage.USAGE_ATTRIBUTE).get().toString() == "java-runtime"
    }
}
