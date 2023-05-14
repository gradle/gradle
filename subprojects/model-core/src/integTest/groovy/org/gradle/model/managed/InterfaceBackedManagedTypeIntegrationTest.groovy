/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.model.managed

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.UnsupportedWithConfigurationCache
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import spock.lang.Issue

// Caused by: java.lang.IncompatibleClassChangeError: Method Person.getName()Ljava/lang/String; must be InterfaceMethodref constant
// Fail since build 125
@Requires(UnitTestPreconditions.Jdk8OrEarlier)
@Issue('https://github.com/gradle/gradle/issues/721')
@UnsupportedWithConfigurationCache(because = "software model")
class InterfaceBackedManagedTypeIntegrationTest extends AbstractIntegrationSpec {

    def "rule method can define a managed model element backed by an interface"() {
        when:
        buildScript '''
            @Managed
            interface Person {
                String getName()
                void setName(String name)
            }

            @Managed
            interface Names {
                String getName()
                void setName(String name)
            }

            class RulePlugin extends RuleSource {
                @Model
                void name(Names names) {
                    assert names == names
                    assert names.name == null

                    names.name = "foo"

                    assert names.name == "foo"
                }

                @Model
                void someone(Person person, Names names) {
                    assert person == person
                    assert person.name == null

                    person.name = names.name
                }

                @Mutate
                void addEchoTask(ModelMap<Task> tasks, Person person) {
                    tasks.create("echo") {
                        it.doLast {
                            println "person: $person"
                            println "name: $person.name"
                        }
                    }
                }
            }

            apply type: RulePlugin
        '''

        then:
        succeeds "echo"

        and:
        output.contains("person: Person 'someone'")
        output.contains("name: foo")
    }

    def "can view a managed element as ModelElement"() {
        when:
        buildScript '''
            @Managed
            interface Person {
                String getName()
                void setName(String name)
            }

            class RulePlugin extends RuleSource {
                @Model
                void someone(Person person) {
                }

                @Mutate
                void addEchoTask(ModelMap<Task> tasks, @Path("someone") ModelElement person) {
                    tasks.create("echo") {
                        it.doLast {
                            println "person: $person"
                            println "name: $person.name"
                            println "display-name: $person.displayName"
                        }
                    }
                }
            }

            apply type: RulePlugin
        '''

        then:
        succeeds "echo"

        and:
        output.contains("person: Person 'someone'")
        output.contains("name: someone")
        output.contains("display-name: Person 'someone'")
    }

    def "rule method can apply defaults to a managed model element"() {
        when:
        buildScript '''
            @Managed
            interface Person {
                String getName()
                void setName(String name)
            }

            class Names {
                String name
            }

            class RulePlugin extends RuleSource {
                @Model
                Names name() {
                    return new Names(name: "before")
                }

                @Model
                void person(Person person) {
                    person.name += " init"
                }

                @Defaults
                void beforePerson(Person person, Names names) {
                    person.name = names.name
                }

                @Finalize
                void afterPerson(Person person) {
                    person.name += " after"
                }

                @Mutate
                void configurePerson(Person person) {
                    person.name += " configure"
                }

                @Mutate
                void addEchoTask(ModelMap<Task> tasks, Person person) {
                    tasks.create("echo") {
                        it.doLast {
                            println "name: $person.name"
                        }
                    }
                }
            }

            apply type: RulePlugin
        '''

        then:
        succeeds "echo"

        and:
        output.contains("name: before init configure after")
    }

    def "managed type implemented as interface can have generative getter default methods"() {
        when:
        file('buildSrc/src/main/java/Rules.java') << '''
            import org.gradle.api.*;
            import org.gradle.model.*;

            @Managed
            interface Person {
                String getFirstName();
                void setFirstName(String firstName);
                String getLastName();
                void setLastName(String lastName);

                default String getName() {
                    return getFirstName() + " " + getLastName();
                }
            }

            class RulePlugin extends RuleSource {
                @Model
                void createPerson(Person person) {
                    person.setFirstName("Alan");
                    person.setLastName("Turing");
                }

                @Mutate
                void addPersonTask(ModelMap<Task> tasks, Person person) {
                    tasks.create("echo", task -> {
                        task.doLast(unused -> {
                            System.out.println(String.format("name: %s", person.getName()));
                        });
                    });
                }
            }
        '''

        buildScript '''
            apply type: RulePlugin
        '''

        then:
        succeeds "echo"

        and:
        output.contains("name: Alan Turing")
    }

    def "generative getters implemented as default methods cannot call setters"() {
        when:
        file('buildSrc/src/main/java/Rules.java') << '''
            import org.gradle.api.*;
            import org.gradle.model.*;

            @Managed
            interface Person {
                String getFirstName();
                void setFirstName(String firstName);

                default String getName() {
                    setFirstName("foo");
                    return getFirstName();
                }
            }

            class RulePlugin extends RuleSource {
                @Model
                void createPerson(Person person) {
                }

                @Mutate
                void addPersonTask(ModelMap<Task> tasks, Person person) {
                    tasks.create("accessGenerativeName", task -> {
                        task.doLast(unused -> {
                            person.getName();
                        });
                    });
                }
            }
        '''

        buildScript '''
            apply type: RulePlugin
        '''

        then:
        fails "accessGenerativeName"

        and:
        failure.assertHasCause("Calling setters of a managed type on itself is not allowed")
    }

    def "non-abstract setters implemented as default interface methods are not allowed"() {
        when:
        file('buildSrc/src/main/java/Rules.java') << '''
            import org.gradle.api.*;
            import org.gradle.model.*;

            @Managed
            interface Person {
                String getName();
                default void setName(String firstName) {
                }
            }

            class RulePlugin extends RuleSource {
                @Model
                void createPerson(Person person) {
                }

                @Mutate
                void linkPersonToTasks(ModelMap<Task> tasks, Person person) {
                }
            }
        '''

        buildScript '''
            apply type: RulePlugin
        '''

        then:
        fails "tasks"

        and:
        failure.assertHasCause """Type Person is not a valid managed type:
- Property 'name' is not valid: it must have either only abstract accessor methods or only implemented accessor methods"""
    }

    def "non-mutative non-abstract methods implemented as default interface methods are not allowed"() {
        when:
        file('buildSrc/src/main/java/Rules.java') << '''
            import org.gradle.api.*;
            import org.gradle.model.*;

            @Managed
            interface Person {
                default void foo() {
                }
            }

            class RulePlugin extends RuleSource {
                @Model
                void createPerson(Person person) {
                }

                @Mutate
                void linkPersonToTasks(ModelMap<Task> tasks, Person person) {
                }
            }
        '''

        buildScript '''
            apply type: RulePlugin
        '''

        then:
        fails "tasks"

        and:
        failure.assertHasCause """Type Person is not a valid managed type:
- Method foo() is not a valid method: Default interface methods are only supported for getters and setters."""
    }

    def "two views of the same element are equal"() {
        when:
        buildScript '''
            @Managed
            interface Address {
                String getCity()
                void setCity(String name)
            }

            @Managed
            interface Person {
                String getName()
                void setName(String name)
                Address getAddress()
                Address getPostalAddress()
                void setPostalAddress(Address a)
            }

            class RulePlugin extends RuleSource {
                @Model
                void someone(Person person) {
                    person.postalAddress = person.address
                }

                @Mutate
                void tasks(ModelMap<Task> tasks, Person p1, Person p2) {
                    assert p1 == p2
                    assert p1.address == p2.address
                    assert p1.postalAddress == p2.postalAddress
                    assert p1.postalAddress == p1.address
                }
            }

            apply type: RulePlugin
        '''

        then:
        succeeds "help"
    }

    def "reports managed interface type in missing property error message"() {
        when:
        buildScript '''
            @Managed
            interface Person {
                String getName()
                void setName(String name)
            }

            class RulePlugin extends RuleSource {
                @Model
                void someone(Person person) {
                }

                @Mutate
                void tasks(ModelMap<Task> tasks, Person person) {
                    println person.unknown
                }
            }

            apply type: RulePlugin
        '''

        then:
        fails "help"

        and:
        failure.assertHasFileName("Build file '$buildFile'")
        failure.assertHasLineNumber(15)
        failure.assertHasCause("No such property: unknown for class: Person")
    }

}
