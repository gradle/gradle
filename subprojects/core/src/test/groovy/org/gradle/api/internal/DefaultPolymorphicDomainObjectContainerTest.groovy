/*
 * Copyright 2011 the original author or authors.
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

import org.gradle.api.Action
import org.gradle.api.InvalidUserDataException
import org.gradle.api.Named
import org.gradle.api.NamedDomainObjectFactory
import org.gradle.internal.reflect.DirectInstantiator

import spock.lang.Specification

class DefaultPolymorphicDomainObjectContainerTest extends Specification {
    def fred = new DefaultPerson(name: "fred")
    def barney = new DefaultPerson(name: "barney")
    def agedFred = new DefaultAgeAwarePerson(name: "fred", age: 42)
    def agedBarney = new DefaultAgeAwarePerson(name: "barney", age: 42)

    def container = new DefaultPolymorphicDomainObjectContainer<Person>(Person, new DirectInstantiator())

    interface Person extends Named {}

    static class DefaultPerson implements Person {
        String name
        String toString() { name }


        boolean equals(DefaultPerson other) {
            name == other.name
        }

        int hashCode() {
            name.hashCode()
        }
    }

    interface AgeAwarePerson extends Person {
        int getAge()
    }

    static class DefaultAgeAwarePerson extends DefaultPerson implements AgeAwarePerson {
        int age

        boolean equals(DefaultAgeAwarePerson other) {
            name == other.name && age == other.age
        }

        int hashCode() {
            name.hashCode() * 31 + age
        }
    }

    def "add elements"() {
        when:
        container.add(fred)
        container.add(barney)

        then:
        container == [fred, barney] as Set
    }

    def "create elements without specifying type"() {
        container.registerDefaultFactory({ new DefaultPerson(name: it) } as NamedDomainObjectFactory )

        when:
        container.create("fred")
        container.create("barney")

        then:
        container.size() == 2
        container.findByName("fred") == fred
        container.findByName("barney") == barney
        container.asDynamicObject.getProperty("fred") == fred
        container.asDynamicObject.getProperty("barney") == barney
    }

    def "throws meaningful exception if it doesn't support creating domain objects without specifying a type"() {
        container = new DefaultPolymorphicDomainObjectContainer<Person>(Person, new DirectInstantiator())

        when:
        container.create("fred")

        then:
        InvalidUserDataException e = thrown()
        e.message == "This container does not support creating domain objects without specifying a type."
    }

    def "create elements with specified type"() {
        container.registerFactory(Person, { new DefaultPerson(name: it) } as NamedDomainObjectFactory)
        container.registerFactory(AgeAwarePerson, { new DefaultAgeAwarePerson(name: it, age: 42) } as NamedDomainObjectFactory)

        when:
        container.create("fred", Person)
        container.create("barney", AgeAwarePerson)

        then:
        container.size() == 2
        container.findByName("fred") == fred
        container.findByName("barney") == agedBarney
        container.asDynamicObject.getProperty("fred") == fred
        container.asDynamicObject.getProperty("barney") == agedBarney
    }

    def "throws meaningful exception if it doesn't support creating domain objects with the specified type"() {
        container = new DefaultPolymorphicDomainObjectContainer<Person>(Person, new DirectInstantiator())

        when:
        container.create("fred", Person)

        then:
        InvalidUserDataException e = thrown()
        e.message == "This container does not support creating domain objects of type " +
                "'org.gradle.api.internal.DefaultPolymorphicDomainObjectContainerTest\$Person'."
    }

    def "throws meaningful exception if factory element type is not a subtype of container element type"() {
        when:
        container.registerFactory(String, {} as NamedDomainObjectFactory)

        then:
        IllegalArgumentException e = thrown()
        e.message == "Factory element type 'java.lang.String' is not a subtype of container element type " +
                "'org.gradle.api.internal.DefaultPolymorphicDomainObjectContainerTest\$Person'"
    }

    def "fires events when elements are added"() {
        Action<Person> action = Mock()

        given:
        container.all(action)

        when:
        container.addAll([fred, barney])

        then:
        1 * action.execute(fred)
        1 * action.execute(barney)
        0 * action._
    }

    def "can find all elements that match closure"() {
        given:
        container.addAll([fred, barney])

        expect:
        container.findAll { it != fred } == [barney] as Set
    }
}
