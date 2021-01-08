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

import org.gradle.api.Named
import org.gradle.api.NamedDomainObjectFactory
import org.gradle.internal.reflect.Instantiator
import org.gradle.test.fixtures.AbstractProjectBuilderSpec

class DefaultPolymorphicDomainObjectContainerDslTest extends AbstractProjectBuilderSpec {
    def realFred = new DefaultPerson(name: "Fred")
    def realBarney = new DefaultPerson(name: "Barney")
    def agedBarney = new DefaultAgeAwarePerson(name: "Barney", age: 42)

    def instantiator
    def collectionCallbackActionDecorator = CollectionCallbackActionDecorator.NOOP

    def container

    def setup() {
        instantiator = project.services.get(Instantiator)
        container = instantiator.newInstance(DefaultPolymorphicDomainObjectContainer, Person, instantiator, collectionCallbackActionDecorator)
        project.extensions.add("container", container)
    }

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

    def "create elements with default type"() {
        container.registerDefaultFactory({ new DefaultPerson(name: it) } as NamedDomainObjectFactory )

        when:
        project.container {
            Fred
            Barney {}
        }

        then:
        container.size() == 2
        container.findByName("Fred") == realFred
        container.findByName("Barney") == realBarney
        container.asDynamicObject.getProperty("Fred") == realFred
        container.asDynamicObject.getProperty("Barney") == realBarney
    }

    def "create elements with specified type"() {
        container.registerFactory(Person, { new DefaultPerson(name: it) } as NamedDomainObjectFactory)
        container.registerFactory(AgeAwarePerson, { new DefaultAgeAwarePerson(name: it, age: 42) } as NamedDomainObjectFactory)

        when:
        project.container {
            Fred(Person)
            Barney(AgeAwarePerson) {}
        }

        then:
        container.size() == 2
        container.findByName("Fred") == realFred
        container.findByName("Barney") == agedBarney
        container.asDynamicObject.getProperty("Fred") == realFred
        container.asDynamicObject.getProperty("Barney") == agedBarney
    }

    def "configure elements with default type"() {
        container.registerDefaultFactory({ new DefaultAgeAwarePerson(name: it, age: 42) } as NamedDomainObjectFactory)

        when:
        project.container {
            Fred {
                age = 11
            }
            Barney {
                age = 22
            }
        }

        then:
        container.size() == 2
        container.findByName("Fred").age == 11
        container.findByName("Barney").age == 22
    }

    def "configure elements with specified type"() {
        container.registerFactory(AgeAwarePerson, { new DefaultAgeAwarePerson(name: it, age: 42) } as NamedDomainObjectFactory)

        when:
        project.container {
            Fred(AgeAwarePerson) {
                age = 11
            }
            Barney(AgeAwarePerson) {
                age = 22
            }
        }

        then:
        container.size() == 2
        container.findByName("Fred").age == 11
        container.findByName("Barney").age == 22
    }

    def "configure same element multiple times"() {
        container.registerFactory(AgeAwarePerson, { new DefaultAgeAwarePerson(name: it, age: 42) } as NamedDomainObjectFactory)

        when:
        project.container {
            Fred(AgeAwarePerson) {
                age = 11
            }
            Barney(AgeAwarePerson) {
                age = 22
            }
            Fred(AgeAwarePerson) {
                age = 33
            }
            Barney(AgeAwarePerson) {
                age = 44
            }
        }

        then:
        container.size() == 2
        container.findByName("Fred").age == 33
        container.findByName("Barney").age == 44
    }

    def "create elements without configuration"() {
        container.registerDefaultFactory({ new DefaultAgeAwarePerson(name: it, age: 42) } as NamedDomainObjectFactory)
        container.registerFactory(AgeAwarePerson, { new DefaultAgeAwarePerson(name: it, age: 43) } as NamedDomainObjectFactory)

        when:
        project.container {
            Fred
            Barney(AgeAwarePerson)
        }

        then:
        container.size() == 2
        container.findByName("Fred").age == 42
        container.findByName("Barney").age == 43
    }
}
