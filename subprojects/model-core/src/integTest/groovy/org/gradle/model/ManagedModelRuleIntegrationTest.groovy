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

package org.gradle.model

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.EnableModelDsl
import org.gradle.util.Matchers

class ManagedModelRuleIntegrationTest extends AbstractIntegrationSpec {

    def "rule can provide a managed model element"() {
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
                void createPerson(Person person, String name) {
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

    def "provides a useful error message when an invalid managed type is used in a rule"() {
        when:
        buildScript '''
            import org.gradle.model.*
            import org.gradle.model.collection.*

            @Managed
            interface Person {
                String getName()
            }

            @RuleSource
            class RulePlugin {
                @Model
                void createPerson(Person person) {
                }
            }

            apply type: RulePlugin
        '''

        then:
        fails "tasks"

        and:
        failure.assertHasCause("Declaration of model rule RulePlugin#createPerson(Person) is invalid")
        failure.assertHasCause("Invalid managed model type Person: no corresponding setter for getter (method: getName)")
    }

    def "provides a useful error message when setting an incompatible type on a managed instance in Groovy"() {
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
                void createPerson(Person person) {
                    person.setName(123)
                }

                @Mutate
                void addDependencyOnPerson(CollectionBuilder<Task> tasks, Person person) {
                }
            }

            apply type: RulePlugin
        '''

        then:
        fails "tasks"

        and:
        failure.assertHasCause("Exception thrown while executing model rule: RulePlugin#createPerson(Person)")
        failure.assertThatCause(Matchers.containsLine(Matchers.matchesRegexp(/No signature of method: .*\.setName\(\) is applicable for argument types: \(java.lang.Integer\) values: \[123\]/)))
    }

    def "provides a useful error message when setting an incompatible type on a managed instance in Java"() {
        when:
        file('buildSrc/src/main/java/RuleSource.java') << '''
            import org.gradle.api.*;
            import org.gradle.model.*;
            import org.gradle.model.collection.*;
            import java.lang.reflect.*;

            @Managed
            interface Person {
                String getName();
                void setName(String name);
            }

            @RuleSource
            class RulePlugin {
                @Model
                void createPerson(Person person) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
                    Method setter = person.getClass().getMethod("setName", String.class);
                    setter.invoke(person, 123);
                }

                @Mutate
                void addDependencyOnPerson(CollectionBuilder<Task> tasks, Person person) {
                }
            }
        '''
        buildScript '''
            apply type: RulePlugin
        '''

        then:
        fails "tasks"

        and:
        failure.assertHasCause("Exception thrown while executing model rule: RulePlugin#createPerson(Person)")
        failure.assertHasCause("argument type mismatch")
    }

    def "rule can provide a composite managed model element"() {
        when:
        buildScript '''
            import org.gradle.model.*
            import org.gradle.model.collection.*

            @Managed
            interface Platform {
                String getDisplayName()
                void setDisplayName(String name)

                OperatingSystem getOperatingSystem()
            }

            @Managed
            interface OperatingSystem {
                String getName()
                void setName(String name)
            }

            @RuleSource
            class RulePlugin {
                @Model
                void createPlatform(Platform platform) {
                    platform.displayName = "Microsoft Windows"
                    platform.operatingSystem.name = "windows"
                }

                @Mutate
                void addPersonTask(CollectionBuilder<Task> tasks, Platform platform) {
                    tasks.create("echo") {
                        it.doLast {
                            println "platform: $platform.operatingSystem.name"
                        }
                    }
                }
            }

            apply type: RulePlugin
        '''

        then:
        succeeds "echo"

        and:
        output.contains("platform: windows")
    }

    def "rule can provide a managed model element that references another managed model element"() {
        when:
        buildScript '''
            import org.gradle.model.*
            import org.gradle.model.collection.*

            @Managed
            interface Platform {
                String getDisplayName()
                void setDisplayName(String name)

                OperatingSystem getOperatingSystem()
                void setOperatingSystem(OperatingSystem operatingSystem)
            }

            @Managed
            interface OperatingSystem {
                String getName()
                void setName(String name)
            }

            @RuleSource
            class RulePlugin {
                @Model
                void createOs(OperatingSystem os) {
                  os.name = "windows"
                }

                @Model
                void createPlatform(Platform platform, OperatingSystem os) {
                  platform.displayName = "Microsoft Windows"
                  platform.operatingSystem = os
                }

                @Mutate
                void addPersonTask(CollectionBuilder<Task> tasks, Platform platform) {
                    tasks.create("echo") {
                        it.doLast {
                            println "platform: $platform.operatingSystem.name"
                        }
                    }
                }
            }

            apply type: RulePlugin
        '''

        then:
        succeeds "echo"

        and:
        output.contains("platform: windows")
    }

    def "cannot assign a non-managed instance to a property of a managed type"() {
        given:
        EnableModelDsl.enable(executer)

        when:
        buildScript '''
            import org.gradle.model.*
            import org.gradle.model.collection.*

            @Managed
            interface Platform {
                OperatingSystem getOperatingSystem()
                void setOperatingSystem(OperatingSystem operatingSystem)
            }

            @Managed
            interface OperatingSystem {
                String getName()
                void setName(String name)
            }

            @RuleSource
            class RulePlugin {
                @Model
                void platform(Platform platform) {
                }

                @Mutate
                void addDependencyOnPlatform(CollectionBuilder<Task> tasks, Platform platform) {
                }
            }

            apply type: RulePlugin

            model {
                platform {
                    operatingSystem = new OperatingSystem() {
                        String name
                    }
                }
            }
        '''

        then:
        fails "tasks"

        and:
        failure.assertHasCause("Exception thrown while executing model rule: model.platform")
        failure.assertHasCause("Only managed model instances can be set as property 'operatingSystem' of class 'Platform'")
    }

    def "managed types can have cyclical managed type references"() {
        when:
        buildScript '''
            import org.gradle.model.*
            import org.gradle.model.collection.*

            @Managed
            interface Parent {
                String getName()
                void setName(String name)

                Child getChild()
            }

            @Managed
            interface Child {
                Parent getParent()
                void setParent(Parent parent)
            }

            @RuleSource
            class RulePlugin {
                @Model
                void createParent(Parent parent) {
                    parent.name = "parent"
                    parent.child.parent = parent
                }

                @Mutate
                void addEchoTask(CollectionBuilder<Task> tasks, Parent parent) {
                    tasks.create("echo") {
                        it.doLast {
                            println "name: $parent.child.parent.name"
                        }
                    }
                }
            }

            apply type: RulePlugin
        '''

        then:
        succeeds "echo"

        and:
        output.contains("name: parent")
    }

    def "managed types can have cyclical managed type references where more than two types constitute the cycle"() {
        when:
        buildScript '''
            import org.gradle.model.*
            import org.gradle.model.collection.*

            @Managed
            interface A {
                String getName()
                void setName(String name)

                B getB()
            }

            @Managed
            interface B {
                C getC()
            }

            @Managed
            interface C {
                A getA()
                void setA(A a)
            }

            @RuleSource
            class RulePlugin {
                @Model
                void createA(A a) {
                    a.name = "a"
                    a.b.c.a = a
                }

                @Mutate
                void addEchoTask(CollectionBuilder<Task> tasks, A a) {
                    tasks.create("echo") {
                        it.doLast {
                            println "name: $a.b.c.a.name"
                        }
                    }
                }
            }

            apply type: RulePlugin
        '''

        then:
        succeeds "echo"

        and:
        output.contains("name: a")
    }

    def "values of primitive types and boxed primitive types are widened as usual when using groovy"() {
        when:
        buildScript '''
            import org.gradle.model.*
            import org.gradle.model.collection.*

            @Managed
            interface PrimitiveTypes {
                Long getLongPropertyFromInt()
                void setLongPropertyFromInt(Long value)

                Long getLongPropertyFromInteger()
                void setLongPropertyFromInteger(Long value)
            }

            @RuleSource
            class RulePlugin {
                @Model
                void createPrimitiveTypes(PrimitiveTypes primitiveTypes) {
                    primitiveTypes.longPropertyFromInt = 123
                    primitiveTypes.longPropertyFromInteger = new Integer(321)
                }

                @Mutate
                void addEchoTask(CollectionBuilder<Task> tasks, final PrimitiveTypes primitiveTypes) {
                    tasks.create("echo") {
                        it.doLast {
                            println "from int: $primitiveTypes.longPropertyFromInt"
                            println "from Integer: $primitiveTypes.longPropertyFromInteger"
                        }
                    }
                }
            }

            apply type: RulePlugin
        '''

        then:
        succeeds "echo"

        and:
        output.contains "from int: 123"
        output.contains "from Integer: 321"
    }

    def "values of primitive types are boxed as usual when using java"() {
        when:
        file('buildSrc/src/main/java/RuleSource.java') << '''
            import org.gradle.api.*;
            import org.gradle.model.*;
            import org.gradle.model.collection.*;

            @Managed
            interface PrimitiveProperty {
                Long getLongProperty();
                void setLongProperty(Long value);
            }

            @RuleSource
            class RulePlugin {
                @Model
                void createPrimitiveProperty(PrimitiveProperty primitiveProperty) {
                    primitiveProperty.setLongProperty(123l);
                }

                @Mutate
                void addEchoTask(CollectionBuilder<Task> tasks, final PrimitiveProperty primitiveProperty) {
                    tasks.create("echo", new Action<Task>() {
                        public void execute(Task task) {
                            task.doLast(new Action<Task>() {
                                public void execute(Task unused) {
                                    System.out.println(String.format("value: %d", primitiveProperty.getLongProperty()));
                                }
                            });
                        }
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
        output.contains "value: 123"
    }

    def "rule can create a managed collection of managed model elements"() {
        given:
        EnableModelDsl.enable(executer)

        when:
        buildScript '''
            import org.gradle.model.*
            import org.gradle.model.collection.*

            @Managed
            interface Person {
              String getName()
              void setName(String string)
            }

            @RuleSource
            class Rules {
              @Model
              void people(ManagedSet<Person> people) {}

              @Mutate void addPeople(ManagedSet<Person> people) {
                people.create { it.name = "p1" }
                people.create { it.name = "p2" }
              }
            }

            apply type: Rules

            model {
              people {
                create { it.name = "p3" }
              }

              tasks {
                create("printPeople") {
                  it.doLast {
                    def names = $("people")*.name.sort().join(", ")
                    println "people: $names"
                  }
                }
              }
            }
        '''

        then:
        succeeds "printPeople"

        and:
        output.contains 'people: p1, p2, p3'
    }
}
