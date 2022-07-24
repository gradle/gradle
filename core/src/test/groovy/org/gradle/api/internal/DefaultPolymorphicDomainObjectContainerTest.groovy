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
import org.gradle.api.PolymorphicDomainObjectContainer
import org.gradle.util.TestUtil

class DefaultPolymorphicDomainObjectContainerTest extends AbstractPolymorphicDomainObjectContainerSpec<Person> {
    def fred = new DefaultPerson(name: "fred")
    def barney = new DefaultPerson(name: "barney")
    def agedBarney = new DefaultAgeAwarePerson(name: "barney", age: 42)

    def container = createContainer()

    private DefaultPolymorphicDomainObjectContainer<Person> createContainer() {
        new DefaultPolymorphicDomainObjectContainer<Person>(Person, TestUtil.instantiatorFactory().decorateLenient(), TestUtil.instantiatorFactory().decorateLenient(), callbackActionDecorator)
    }

    boolean supportsBuildOperations = true

    @Override
    final PolymorphicDomainObjectContainer<Person> getContainer() {
        return container
    }

    Person a = new DefaultPerson(name: "a")
    Person b = new DefaultPerson(name: "b")
    Person c = new DefaultPerson(name: "c")
    Person d = new DefaultCtorNamedPerson("d")
    boolean externalProviderAllowed = true
    boolean directElementAdditionAllowed = true
    boolean elementRemovalAllowed = true

    @Override
    void setupContainerDefaults() {
        container.registerDefaultFactory({ new DefaultPerson(name: it) } as NamedDomainObjectFactory)
    }

    @Override
    List<Person> iterationOrder(Person... elements) {
        return elements.sort { it.name }
    }

    interface Person extends Named {}

    static abstract class AbstractPerson implements Person {
        String name

        String toString() { name }


        boolean equals(DefaultPerson other) {
            name == other.name
        }

        int hashCode() {
            name.hashCode()
        }
    }

    static class DefaultPerson extends AbstractPerson {
    }

    interface AgeAwarePerson extends Person {
        int getAge()
    }

    static class DefaultAgeAwarePerson extends AbstractPerson implements AgeAwarePerson {
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

    static class DefaultCtorNamedPerson extends AbstractPerson implements CtorNamedPerson {
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
        container.registerDefaultFactory({ new DefaultPerson(name: it) } as NamedDomainObjectFactory)

        when:
        container.create("fred")
        container.create("barney")

        then:
        container.size() == 2
        container.findByName("fred") == fred
        container.findByName("barney") == barney
        container.elementsAsDynamicObject.getProperty("fred") == fred
        container.elementsAsDynamicObject.getProperty("barney") == barney
        container.createableTypes == Collections.singleton(Person)
    }

    def "maybe create elements without specifying type"() {
        container.registerDefaultFactory({ new DefaultPerson(name: it) } as NamedDomainObjectFactory)

        when:
        def first = container.maybeCreate("fred")
        def second = container.maybeCreate("fred")

        then:
        container.size() == 1
        container.findByName("fred") == fred
        first == second
    }

    def "throws meaningful exception if it doesn't support creating domain objects without specifying a type"() {
        container = createContainer()

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
        container.elementsAsDynamicObject.getProperty("fred") == fred
        container.elementsAsDynamicObject.getProperty("barney") == agedBarney
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
        container.elementsAsDynamicObject.getProperty("fred") == fred
        container.elementsAsDynamicObject.getProperty("barney") == agedBarney
    }

    def "create elements with specified type based on type binding"() {
        container = new DefaultPolymorphicDomainObjectContainer<?>(Object, TestUtil.instantiatorFactory().decorateLenient(),
            { it instanceof Named ? it.name : "unknown" } as Named.Namer, CollectionCallbackActionDecorator.NOOP)

        container.registerBinding(UnnamedPerson, DefaultUnnamedPerson)
        container.registerBinding(CtorNamedPerson, DefaultCtorNamedPerson)

        when:
        container.create("fred", UnnamedPerson)
        container.create("barney", CtorNamedPerson)

        then:
        container.size() == 2
        !container.findByName("fred")
        with(container.findByName("unknown")) {
            DefaultUnnamedPerson.isInstance(it)
        }
        with(container.findByName("barney")) {
            DefaultCtorNamedPerson.isInstance(it)
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
        container = createContainer()

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

    def "can register objects"() {
        container.registerFactory(Person, { new DefaultPerson(name: it) } as NamedDomainObjectFactory)
        container.registerFactory(AgeAwarePerson, { new DefaultAgeAwarePerson(name: it) } as NamedDomainObjectFactory)
        when:
        def fred = container.register("fred", Person)
        def bob = container.register("bob", AgeAwarePerson) {
            it.age = 50
        }
        then:
        fred.present
        fred.get().name == "fred"
        bob.present
        bob.get().age == 50
    }

    def "can look up objects by name"() {
        container.registerFactory(Person, { new DefaultPerson(name: it) } as NamedDomainObjectFactory)
        when:
        container.register("fred", Person)
        container.create("bob", Person)
        def fred = container.named("fred")
        def bob = container.named("bob")

        then:
        fred.present
        fred.get().name == "fred"
        bob.present
        bob.get().name == "bob"
    }

    def "can configure objects via provider"() {
        given:
        container.registerFactory(AgeAwarePerson, { new DefaultAgeAwarePerson(name: it) } as NamedDomainObjectFactory)

        when:
        container.register("fred", AgeAwarePerson).configure {
            it.age = 50
        }
        container.create("bob", AgeAwarePerson)

        def fred = container.named("fred")
        def bob = container.named("bob")
        bob.configure {
            it.age = 50
        }
        then:
        fred.present
        fred.get().age == 50
        bob.present
        bob.get().age == 50
    }

    def "can extract schema from container that mixes register, create and add"() {
        given:
        container.registerFactory(Person, { new DefaultPerson(name: it) } as NamedDomainObjectFactory)
        container.registerFactory(AgeAwarePerson, { new DefaultAgeAwarePerson(name: it) } as NamedDomainObjectFactory)

        def expectedSchema = [
            mike: "DefaultPolymorphicDomainObjectContainerTest.Person",
            fred: "DefaultPolymorphicDomainObjectContainerTest.Person",
            alice: "DefaultPolymorphicDomainObjectContainerTest.Person", // TODO: Should be AgeAwarePerson
            kate: "DefaultPolymorphicDomainObjectContainerTest.Person",
            bob: "DefaultPolymorphicDomainObjectContainerTest.Person",
            mary: "DefaultPolymorphicDomainObjectContainerTest.Person", // TODO should be AgeAwarePerson
            john: "DefaultPolymorphicDomainObjectContainerTest.Person",
            janis: "DefaultPolymorphicDomainObjectContainerTest.Person", // TODO could be AgeAwarePerson
            robert: "DefaultPolymorphicDomainObjectContainerTest.Person" // TODO could be DefaultCtorNamedPerson
        ]

        when:
        container.register("mike")
        container.register("fred", Person)
        container.register("alice", AgeAwarePerson)
        container.create("kate")
        container.create("bob", Person)
        container.create("mary", AgeAwarePerson)
        container.add(new DefaultPerson(name: "john"))
        container.add(new DefaultAgeAwarePerson(name: "janis"))
        container.add(new DefaultCtorNamedPerson("robert"))

        then:
        assertSchemaIs(expectedSchema)

        when: "realizing pending elements"
        container.getByName("mike")
        container.getByName("fred")
        container.getByName("alice")
        then: "schema is the same"
        assertSchemaIs(expectedSchema)
    }

    def "can find elements added by rules"() {
        given:
        container.registerFactory(Person, { new DefaultPerson(name: it) } as NamedDomainObjectFactory)
        container.registerFactory(AgeAwarePerson, { new DefaultAgeAwarePerson(name: it) } as NamedDomainObjectFactory)

        container.addRule("adds people") { elementName ->
            if (elementName == "fred") {
                container.register("fred", Person)
            } else if (elementName == "bob") {
                container.create("bob", AgeAwarePerson)
            }
        }
        when:
        def fred = container.named("fred")
        def bob = container.named("bob")
        bob.configure {
            it.age = 50
        }
        then:
        fred.present
        fred.get().name == "fred"
        bob.present
        bob.get().age == 50
    }

    def "can find and configure objects by name and type"() {
        container.registerFactory(Person, { new DefaultPerson(name: it) } as NamedDomainObjectFactory)
        container.registerFactory(AgeAwarePerson, { new DefaultAgeAwarePerson(name: it) } as NamedDomainObjectFactory)
        container.register("fred", Person)
        container.register("bob", AgeAwarePerson)
        when:
        def fred = container.named("fred", Person)
        def bob = container.named("bob", AgeAwarePerson) {
            it.age = 50
        }
        then:
        fred.present
        fred.get().name == "fred"
        bob.present
        bob.get().age == 50

        when:
        container.named("bob") {
            it.age = 100
        }
        then:
        bob.get().age == 100
    }

    def "gets useful message if type does not match registered type"() {
        container.registerFactory(Person, { new DefaultPerson(name: it) } as NamedDomainObjectFactory)
        container.registerFactory(AgeAwarePerson, { new DefaultAgeAwarePerson(name: it) } as NamedDomainObjectFactory)
        container.register("fred", Person)
        container.register("bob", AgeAwarePerson)
        when:
        container.named("fred", AgeAwarePerson)
        then:
        def e = thrown(InvalidUserDataException)
        e.message == "The domain object 'fred' (${Person.class.canonicalName}) is not a subclass of the given type (${AgeAwarePerson.class.canonicalName})."
    }

    protected void assertSchemaIs(Map<String, String> expectedSchema) {
        def actualSchema = container.collectionSchema
        Map<String, String> actualSchemaMap = actualSchema.elements.collectEntries { schema ->
            [schema.name, schema.publicType.simpleName]
        }.sort()
        def expectedSchemaMap = expectedSchema.sort()
        assert expectedSchemaMap == actualSchemaMap
    }
}
