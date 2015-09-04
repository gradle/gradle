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
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition

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

    @Requires(TestPrecondition.JDK8_OR_LATER)
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

    @Requires(TestPrecondition.JDK8_OR_LATER)
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

    @Requires(TestPrecondition.JDK8_OR_LATER)
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
        failure.assertHasCause("Invalid managed model type Person: non-abstract setters are not allowed (invalid method: void Person#setName(java.lang.String))")
    }

    @Requires(TestPrecondition.JDK8_OR_LATER)
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
        failure.assertHasCause("Invalid managed model type Person: only paired getter/setter methods are supported (invalid methods: void Person#foo())")
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
