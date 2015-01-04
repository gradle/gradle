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
import org.gradle.internal.reflect.MethodDescription
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition

class InterfaceBackedManagedTypeIntegrationTest extends AbstractIntegrationSpec {

    def "rule can provide a managed model element backed by an interface"() {
        when:
        buildScript '''
            import org.gradle.model.*
            import org.gradle.model.collection.*

            @Managed
            interface Person {
                String getName()
                void setName(String name)
            }

            @RuleSource
            class RulePlugin {
                @Model
                String name() {
                    "foo"
                }

                @Model
                void createPerson(Person person, @Path("name") String name) {
                    person.name = name
                }

                @Mutate
                void addPersonTask(CollectionBuilder<Task> tasks, Person person) {
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
        output.contains("name: foo")
    }

    @Requires(TestPrecondition.JDK8_OR_LATER)
    def "managed type implemented as interface can have generative getter default methods"() {
        when:
        file('buildSrc/src/main/java/RuleSource.java') << '''
            import org.gradle.api.*;
            import org.gradle.model.*;
            import org.gradle.model.collection.*;

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

            @RuleSource
            class RulePlugin {
                @Model
                void createPerson(Person person) {
                    person.setFirstName("Alan");
                    person.setLastName("Turing");
                }

                @Mutate
                void addPersonTask(CollectionBuilder<Task> tasks, Person person) {
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
        file('buildSrc/src/main/java/RuleSource.java') << '''
            import org.gradle.api.*;
            import org.gradle.model.*;
            import org.gradle.model.collection.*;

            @Managed
            interface Person {
                String getFirstName();
                void setFirstName(String firstName);

                default String getName() {
                    setFirstName("foo");
                    return getFirstName();
                }
            }

            @RuleSource
            class RulePlugin {
                @Model
                void createPerson(Person person) {
                }

                @Mutate
                void addPersonTask(CollectionBuilder<Task> tasks, Person person) {
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
        file('buildSrc/src/main/java/RuleSource.java') << '''
            import org.gradle.api.*;
            import org.gradle.model.*;
            import org.gradle.model.collection.*;

            @Managed
            interface Person {
                String getName();
                default void setName(String firstName) {
                }
            }

            @RuleSource
            class RulePlugin {
                @Model
                void createPerson(Person person) {
                }

                @Mutate
                void linkPersonToTasks(CollectionBuilder<Task> tasks, Person person) {
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
        file('buildSrc/src/main/java/RuleSource.java') << '''
            import org.gradle.api.*;
            import org.gradle.model.*;
            import org.gradle.model.collection.*;

            @Managed
            interface Person {
                default void foo() {
                }
            }

            @RuleSource
            class RulePlugin {
                @Model
                void createPerson(Person person) {
                }

                @Mutate
                void linkPersonToTasks(CollectionBuilder<Task> tasks, Person person) {
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
}
