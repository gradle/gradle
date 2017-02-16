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

class DefaultImmutableAttributesFactoryTest extends Specification {
    private static final Attribute<String> FOO = Attribute.of("foo", String)
    private static final Attribute<String> BAR = Attribute.of("bar", String)
    private static final Attribute<String> BAZ = Attribute.of("baz", String)

    ImmutableAttributesFactory factory = new DefaultImmutableAttributesFactory()

    def "can create empty attributes"() {
        when:
        def attributes = factory.builder().get()

        then:
        attributes.empty
    }

    def "can create immutable attributes"() {
        when:
        def attributes = factory.builder()
            .addAttribute(FOO, "foo")
            .get()

        then:
        attributes.keySet() == [FOO] as Set

        and:
        attributes.getAttribute(FOO) == 'foo'
    }

    def "can concatenate immutable attributes sets"() {
        when:
        def set1 = factory.builder()
            .addAttribute(FOO, "foo")
            .get()
        def set2 = factory.builder()
            .addAttribute(BAR, "bar")
            .get()
        def union = factory.concat(set1, set2)

        then:
        union.keySet() == [FOO, BAR] as Set

        and:
        union.getAttribute(FOO) == 'foo'
        union.getAttribute(BAR) == 'bar'
    }

    def "can concatenate an immutable attribute set with a new value"() {
        when:
        def set1 = factory.builder()
            .addAttribute(FOO, "foo")
            .get()
        def set2 = factory.concat(set1, BAR, "bar")
        def union = factory.concat(set1, set2)

        then:
        union.keySet() == [FOO, BAR] as Set

        and:
        union.getAttribute(FOO) == 'foo'
        union.getAttribute(BAR) == 'bar'
    }

    def "can create a single entry immutable set"() {
        when:
        def attributes = factory.of(FOO, "foo")

        then:
        attributes.keySet() == [FOO] as Set

        and:
        attributes.getAttribute(FOO) == 'foo'
    }

    def "can start a build chain from another set"() {
        given:
        def attributes = factory.of(FOO, 'foo')

        when:
        def set = factory.builder(attributes)
            .addAttribute(BAR, 'bar')
            .addAttribute(BAZ, 'baz')
            .get()

        then:
        set.keySet() == [FOO, BAR, BAZ] as Set
        set.getAttribute(FOO) == 'foo'
        set.getAttribute(BAR) == 'bar'
        set.getAttribute(BAZ) == 'baz'

    }

    def "order of entries is not significant in equality"() {
        when:
        def set1 = factory.builder()
            .addAttribute(FOO, "foo")
            .addAttribute(BAR, "bar")
            .get()
        def set2 = factory.builder()
            .addAttribute(BAR, "bar")
            .addAttribute(FOO, "foo")
            .get()

        then:
        set1 == set2
    }

    def "can compare attribute sets created by two different factories"() {
        given:
        def otherFactory = new DefaultImmutableAttributesFactory()

        when:
        def set1 = factory.builder()
            .addAttribute(FOO, "foo")
            .addAttribute(BAR, "bar")
            .get()
        def set2 = otherFactory.builder()
            .addAttribute(BAR, "bar")
            .addAttribute(FOO, "foo")
            .get()

        then:
        set1 == set2
    }

    def "can start a build chain from another set created with a different factory"() {
        given:
        def otherFactory = new DefaultImmutableAttributesFactory()
        def attributes = otherFactory.of(FOO, 'foo')

        when:
        def set = factory.builder(attributes)
            .addAttribute(BAR, 'bar')
            .addAttribute(BAZ, 'baz')
            .get()

        then:
        set.keySet() == [FOO, BAR, BAZ] as Set
        set.getAttribute(FOO) == 'foo'
        set.getAttribute(BAR) == 'bar'
        set.getAttribute(BAZ) == 'baz'

    }

    def "can concatenate immutable attributes sets from different factories"() {
        given:
        def otherFactory = new DefaultImmutableAttributesFactory()

        when:
        def set1 = factory.builder()
            .addAttribute(FOO, "foo")
            .get()
        def set2 = otherFactory.builder()
            .addAttribute(BAR, "bar")
            .get()
        def union = factory.concat(set1, set2)

        then:
        union.keySet() == [FOO, BAR] as Set

        and:
        union.getAttribute(FOO) == 'foo'
        union.getAttribute(BAR) == 'bar'
    }

    def "immutable attribute sets throw a default error when attempting modification"() {
        given:
        def attributes = factory.builder().get()

        when:
        attributes.attribute(FOO, "foo")

        then:
        UnsupportedOperationException t = thrown()
        t.message == "Mutation of attributes is not allowed"
    }
}
