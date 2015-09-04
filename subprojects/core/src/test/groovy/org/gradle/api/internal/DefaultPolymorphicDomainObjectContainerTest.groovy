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

import com.google.common.collect.Sets
import org.gradle.api.Action
import org.gradle.api.GradleException
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

    def container = new DefaultPolymorphicDomainObjectContainer<Person>(Person, DirectInstantiator.INSTANCE)

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

    interface UnnamedPerson {} // doesn't implement Named

    static class DefaultUnnamedPerson {}

    interface CtorNamedPerson extends Person {}

    static class DefaultCtorNamedPerson extends DefaultPerson implements CtorNamedPerson {
        DefaultCtorNamedPerson(String name) {
            this.name = name
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
        container.createableTypes == Collections.singleton(Person)
    }

    def "maybe create elements without specifying type"() {
        container.registerDefaultFactory({ new DefaultPerson(name: it) } as NamedDomainObjectFactory )

        when:
        def first = container.maybeCreate("fred")
        def second = container.maybeCreate("fred")

        then:
        container.size() == 1
        container.findByName("fred") == fred
        first == second
    }

    def "throws meaningful exception if it doesn't support creating domain objects without specifying a type"() {
        container = new DefaultPolymorphicDomainObjectContainer<Person>(Person, DirectInstantiator.INSTANCE)

        when:
        container.create("fred")

        then:
        InvalidUserDataException e = thrown()
        e.message == "Cannot create a Person named 'fred' because this container does not support creating " +
                "elements by name alone. Please specify which subtype of Person to create. Known subtypes are: (None)"
    }

    def "create elements with specified type based on NamedDomainObjectFactory"() {
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

    def "create elements with specified type based on closure-based factory"() {
        container.registerFactory(Person, { new DefaultPerson(name: it) })
        container.registerFactory(AgeAwarePerson, { new DefaultAgeAwarePerson(name: it, age: 42) })

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

    def "create elements with specified type based on type binding"() {
        container = new DefaultPolymorphicDomainObjectContainer<?>(Object, DirectInstantiator.INSTANCE,
                { it instanceof Named ? it.name : "unknown" } as Named.Namer)

        container.registerBinding(UnnamedPerson, DefaultUnnamedPerson)
        container.registerBinding(CtorNamedPerson, DefaultCtorNamedPerson)

        when:
        container.create("fred", UnnamedPerson)
        container.create("barney", CtorNamedPerson)

        then:
        container.size() == 2
        !container.findByName("fred")
        with(container.findByName("unknown")) {
            it.getClass() == DefaultUnnamedPerson
        }
        with(container.findByName("barney")) {
            it.getClass() == DefaultCtorNamedPerson
            name == "barney"
        }
        container.createableTypes == Sets.newHashSet(UnnamedPerson, CtorNamedPerson)
    }

    def "maybe create elements with specified type"() {
        container.registerFactory(Person, { new DefaultPerson(name: it) } as NamedDomainObjectFactory)

        when:
        def first = container.maybeCreate("fred", Person)
        def second = container.maybeCreate("fred", Person)

        then:
        container.size() == 1
        container.findByName("fred") == fred
        first == second
        container.createableTypes == Sets.newHashSet(Person)
    }

    def "throws meaningful exception if element with same name exists with incompatible type"() {
        container.registerFactory(Person, { new DefaultPerson(name: it) } as NamedDomainObjectFactory)
        container.create("fred", Person)

        when:
        container.maybeCreate("fred", AgeAwarePerson)

        then:
        ClassCastException e = thrown()
        e.message == "Failed to cast object fred of type ${DefaultPerson.class.name} to target type ${AgeAwarePerson.class.name}"
    }

    def "throws meaningful exception if it doesn't support creating domain objects with the specified type"() {
        container = new DefaultPolymorphicDomainObjectContainer<Person>(Person, DirectInstantiator.INSTANCE)

        when:
        container.create("fred", Person)

        then:
        InvalidUserDataException e = thrown()
        e.message == "Cannot create a Person because this type is not known to this container. Known types are: (None)"
    }

    def "throws meaningful exception if factory element type is not a subtype of container element type"() {
        when:
        container.registerFactory(String, {} as NamedDomainObjectFactory)

        then:
        IllegalArgumentException e = thrown()
        e.message == "Cannot register a factory for type String because it is not a subtype of " +
                "container element type Person."
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

    def "cannot register factory for already registered type"() {
        given:
        container.registerFactory(Person, { new DefaultPerson(name: it) })
        when:
        container.registerFactory(Person, { new DefaultPerson(name: it) })

        then:
        def e = thrown(GradleException)
        e.message == "Cannot register a factory for type Person because a factory for this type is already registered."
    }
}
