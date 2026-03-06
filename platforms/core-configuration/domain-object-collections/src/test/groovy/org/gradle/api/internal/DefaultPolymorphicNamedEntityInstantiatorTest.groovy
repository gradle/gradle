/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.api.internal

import org.gradle.api.GradleException
import org.gradle.api.InvalidUserDataException
import spock.lang.Specification
import spock.lang.Subject

class DefaultPolymorphicNamedEntityInstantiatorTest extends Specification {

    @Subject
    def instantiator = new DefaultPolymorphicNamedEntityInstantiator<Base>(Base, "this container")

    class Base {
        String value
    }

    class TestType extends Base {}

    class AnotherTestType extends Base {}

    def "trying to create an entity for which there is no factory registered results in an exception"() {
        given:
        instantiator.registerFactory(TestType, {})

        when:
        instantiator.create("foo", Integer)

        then:
        InvalidUserDataException e = thrown()
        e.message == "Cannot create a Integer because this type is not known to this container. Known types are: TestType"
        e.cause instanceof NoFactoryRegisteredForTypeException
    }

    def "can retrieve all creatable types and supported type names"() {
        when:
        types.each { instantiator.registerFactory(it, {}) }

        then:
        instantiator.creatableTypes == types as Set
        instantiator.supportedTypeNames == names

        where:
        types                       | names
        []                          | "(None)"
        [TestType]                  | "TestType"
        [TestType, AnotherTestType] | "AnotherTestType, TestType"
    }

    def "can create a type for which a factory has been registered"() {
        given:
        instantiator.registerFactory(TestType, { new TestType(value: it) })

        expect:
        instantiator.create("foo", TestType).value == "foo"
    }

    def "registering an incompatible type results in an exception"() {
        when:
        instantiator.registerFactory(String, {})

        then:
        IllegalArgumentException e = thrown()
        e.message == "Cannot register a factory for type String because it is not a subtype of container element type Base."
    }

    def "registering factory for the same type more than once results in an exception"() {
        given:
        instantiator.registerFactory(TestType, {})

        when:
        instantiator.registerFactory(TestType, {})

        then:
        GradleException e = thrown()
        e.message == "Cannot register a factory for type TestType because a factory for this type is already registered."
    }
}
