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

    def testContainer

    def setup() {
        instantiator = project.services.get(Instantiator)
        testContainer = instantiator.newInstance(DefaultPolymorphicDomainObjectContainer, Person, instantiator, collectionCallbackActionDecorator)
        project.extensions.add("testContainer", testContainer)
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
        testContainer.registerDefaultFactory({ new DefaultPerson(name: it) } as NamedDomainObjectFactory )

        when:
        project.testContainer {
            Fred
            Barney {}
        }

        then:
        testContainer.size() == 2
        testContainer.findByName("Fred") == realFred
        testContainer.findByName("Barney") == realBarney
        testContainer.asDynamicObject.getProperty("Fred") == realFred
        testContainer.asDynamicObject.getProperty("Barney") == realBarney
    }

    def "create elements with specified type"() {
        testContainer.registerFactory(Person, { new DefaultPerson(name: it) } as NamedDomainObjectFactory)
        testContainer.registerFactory(AgeAwarePerson, { new DefaultAgeAwarePerson(name: it, age: 42) } as NamedDomainObjectFactory)

        when:
        project.testContainer {
            Fred(Person)
            Barney(AgeAwarePerson) {}
        }

        then:
        testContainer.size() == 2
        testContainer.findByName("Fred") == realFred
        testContainer.findByName("Barney") == agedBarney
        testContainer.asDynamicObject.getProperty("Fred") == realFred
        testContainer.asDynamicObject.getProperty("Barney") == agedBarney
    }

    def "configure elements with default type"() {
        testContainer.registerDefaultFactory({ new DefaultAgeAwarePerson(name: it, age: 42) } as NamedDomainObjectFactory)

        when:
        project.testContainer {
            Fred {
                age = 11
            }
            Barney {
                age = 22
            }
        }

        then:
        testContainer.size() == 2
        testContainer.findByName("Fred").age == 11
        testContainer.findByName("Barney").age == 22
    }

    def "configure elements with specified type"() {
        testContainer.registerFactory(AgeAwarePerson, { new DefaultAgeAwarePerson(name: it, age: 42) } as NamedDomainObjectFactory)

        when:
        project.testContainer {
            Fred(AgeAwarePerson) {
                age = 11
            }
            Barney(AgeAwarePerson) {
                age = 22
            }
        }

        then:
        testContainer.size() == 2
        testContainer.findByName("Fred").age == 11
        testContainer.findByName("Barney").age == 22
    }

    def "configure same element multiple times"() {
        testContainer.registerFactory(AgeAwarePerson, { new DefaultAgeAwarePerson(name: it, age: 42) } as NamedDomainObjectFactory)

        when:
        project.testContainer {
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
        testContainer.size() == 2
        testContainer.findByName("Fred").age == 33
        testContainer.findByName("Barney").age == 44
    }

    def "create elements without configuration"() {
        testContainer.registerDefaultFactory({ new DefaultAgeAwarePerson(name: it, age: 42) } as NamedDomainObjectFactory)
        testContainer.registerFactory(AgeAwarePerson, { new DefaultAgeAwarePerson(name: it, age: 43) } as NamedDomainObjectFactory)

        when:
        project.testContainer {
            Fred
            Barney(AgeAwarePerson)
        }

        then:
        testContainer.size() == 2
        testContainer.findByName("Fred").age == 42
        testContainer.findByName("Barney").age == 43
    }
}
