/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.api.internal.attributes.immutable

import org.gradle.api.attributes.Usage
import org.gradle.api.internal.artifacts.JavaEcosystemSupport
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.internal.snapshot.impl.CoercingStringValueSnapshot
import spock.lang.Specification

/**
 * Unit tests for {@link ImmutableAttributes}.
 */
class ImmutableAttributesTest extends Specification implements TestsImmutableAttributes {
    def "empty set is empty"() {
        when:
        def attributes = ImmutableAttributes.EMPTY

        then:
        attributes.empty
        attributes.keySet() == [] as Set
    }

    def "can lookup entries in empty set"() {
        when:
        def attributes = ImmutableAttributes.EMPTY

        then:
        attributes.getAttribute(FOO) == null
        !attributes.findEntry(FOO).isPresent()
        !attributes.findEntry("foo").isPresent()
    }

    def "immutable attribute sets throw a default error when attempting modification"() {
        when:
        attributes.attribute(FOO, "foo")

        then:
        UnsupportedOperationException t = thrown()
        t.message == "Mutation of attributes is not allowed"

        where:
        attributes << [ImmutableAttributes.EMPTY, factory.of(FOO, "other"), factory.of(BAR, "other")]
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

    def "order of entries is not significant in equality"() {
        when:
        def set1 = factory.concat(factory.of(FOO, "foo"), BAR, "bar")
        def set2 = factory.concat(factory.of(BAR, "bar"), FOO, "foo")

        then:
        set1 == set2
    }

    def "translates deprecated usage values"() {
        def result = factory.of(Usage.USAGE_ATTRIBUTE, instantiator.named(Usage, JavaEcosystemSupport.DEPRECATED_JAVA_API_JARS))

        expect:
        result.findEntry(Usage.USAGE_ATTRIBUTE).get().name == "java-api"
    }

    @SuppressWarnings('GroovyAssignabilityCheck')
    def "translates deprecated usage values as Isolatable"() {
        def result = factory.of(Usage.USAGE_ATTRIBUTE, new CoercingStringValueSnapshot(JavaEcosystemSupport.DEPRECATED_JAVA_RUNTIME_JARS, instantiator))

        expect:
        result.findEntry(Usage.USAGE_ATTRIBUTE).get().toString() == "java-runtime"
    }
}
