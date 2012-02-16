/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api

import spock.lang.Specification

/**
 * Contract test for dynamic extension implementations.
 *
 * @param <T> The concrete implementaion type
 */
abstract class DynamicExtensionTest<T extends DynamicExtension> extends Specification {
    
    T extension

    def setup() {
        extension = createExtension()
    }

    abstract T createExtension()

    def "can register properties"() {
        given:
        extension.add("foo", "bar")

        expect:
        extension.get("foo") == "bar"

        when:
        extension.set("foo", "baz")

        then:
        extension.get("foo") == "baz"
    }

    def "cannot get or set properties that have not been added"() {
        when:
        extension.get("foo")

        then:
        thrown(DynamicExtension.UnknownPropertyException)

        when:
        extension.set("foo", "bar")

        then:
        thrown(DynamicExtension.UnknownPropertyException)
    }

    def "can read/write properties using groovy notation"() {
        given:
        extension.add("foo", null)

        expect:
        extension.foo == null

        when:
        extension.foo = "bar"

        then:
        extension.foo == "bar"
    }

    def "cannot read or write unregistered property using groovy syntax"() {
        when:
        extension.foo

        then:
        thrown(MissingPropertyException)

        when:
        extension.foo = "bar"

        then:
        thrown(MissingPropertyException)
    }

    def "adding an already added property is like setting"() {
        when:
        extension.add("foo", "bar")

        and:
        extension.add("foo", "baz")

        then:
        extension.foo == "baz"
        extension.get("foo") == "baz"
    }

}
