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
        failure.assertHasCause("Invalid managed model type Person: read only property 'name' has non managed type java.lang.String, only managed types can be used")
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
                void os(OperatingSystem os) {
                  os.name = "windows"
                }

                @Model
                void createPlatform(Platform platform, @Path("os") OperatingSystem os) {
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

    def "managed model type has property of collection of managed types"() {
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

            @Managed
            interface Group {
              String getName()
              void setName(String string)
              ManagedSet<Person> getMembers()
            }

            @RuleSource
            class Rules {
              @Model
              void group(Group group) {
                group.name = "Women in computing"
                group.members.create { name = "Ada Lovelace" }
                group.members.create { name = "Grace Hooper" }
              }
            }

            apply type: Rules

            model {
              tasks {
                create("printGroup") {
                  it.doLast {
                    def members = $("group").members*.name.sort().join(", ")
                    def name = $("group").name
                    println "$name: $members"
                  }
                }
              }
            }
        '''

        then:
        succeeds "printGroup"

        and:
        output.contains 'Women in computing: Ada Lovelace, Grace Hooper'
    }

    def "managed model type can reference a collection of managed types"() {
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

            @Managed
            interface Group {
              String getName()
              void setName(String string)
              ManagedSet<Person> getMembers()
              void setMembers(ManagedSet<Person> members)
            }

            @RuleSource
            class Rules {
              @Model
              void people(ManagedSet<Person> people) {
                people.create { it.name = "Ada Lovelace" }
                people.create { it.name = "Grace Hooper" }
              }

              @Model
              void group(Group group, @Path("people") ManagedSet<Person> people) {
                group.name = "Women in computing"
                group.members = people
              }
            }

            apply type: Rules

            model {
              tasks {
                create("printGroup") {
                  it.doLast {
                    def members = $("group").members*.name.sort().join(", ")
                    def name = $("group").name
                    println "$name: $members"
                  }
                }
              }
            }
        '''

        then:
        succeeds "printGroup"

        and:
        output.contains 'Women in computing: Ada Lovelace, Grace Hooper'
    }

    def "cannot use value type as subject of void model rule"() {
        given:
        when:
        buildScript '''
            import org.gradle.model.*

            @RuleSource
            class Rules {
              @Model
              void s(String s) {}
            }

            apply type: Rules
        '''

        then:
        fails "tasks"

        and:
        failure.assertHasCause("Rules#s(java.lang.String) is not a valid model rule method: a void returning model element creation rule cannot take a value type as the first parameter, which is the element being created. Return the value from the method.")
    }

    def "can use enums in managed model elements"() {
        given:
        EnableModelDsl.enable(executer)

        when:
        buildScript '''
            import org.gradle.model.*
            import org.gradle.model.collection.*

            enum Gender {
                FEMALE, MALE, OTHER
            }

            @Managed
            interface Person {
              String getName()
              void setName(String string)

              Gender getGender()
              void setGender(Gender gender)
            }

            @RuleSource
            class Rules {
              @Model
              void p1(Person p1) {}
            }

            apply type: Rules

            model {
              p1 {
                gender = "MALE" // relying on Groovy enum coercion here
              }

              tasks {
                create("printGender") {
                  it.doLast {
                    println "gender: " + $("p1").gender
                  }
                }
              }
            }
        '''

        then:
        succeeds "printGender"

        and:
        output.contains 'gender: MALE'
    }

    def "rule can target structured property of managed element"() {
        given:
        EnableModelDsl.enable(executer)

        when:
        buildScript '''
            import org.gradle.model.*
            import org.gradle.model.collection.*

            @Managed
            interface Platform {
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
                void platform(Platform platform) {
                  platform.operatingSystem.name = "foo"
                }

                @Mutate
                void addTask(CollectionBuilder<Task> tasks, @Path("platform.operatingSystem") OperatingSystem os) {
                  tasks.create("fromPlugin") {
                    doLast { println "fromPlugin: $os.name" }
                  }
                }
            }

            apply type: RulePlugin

            model {
                tasks {
                  create("fromScript") {
                    it.doLast { println "fromScript: " + $("platform.operatingSystem").name }
                  }
                }
            }
        '''

        then:
        succeeds "fromPlugin", "fromScript"

        and:
        output.contains("fromPlugin: foo")
        output.contains("fromScript: foo")
    }

    def "rule can target structured property of managed element as subject"() {
        given:
        EnableModelDsl.enable(executer)

        when:
        buildScript '''
            import org.gradle.model.*
            import org.gradle.model.collection.*

            @Managed
            interface Platform {
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
                void platform(Platform platform) {}

                @Mutate
                void setOsName(@Path("platform.operatingSystem") OperatingSystem os) {
                  os.name = "foo"
                }

                @Mutate
                void addTask(CollectionBuilder<Task> tasks, @Path("platform.operatingSystem") OperatingSystem os) {
                  tasks.create("fromPlugin") {
                    doLast { println "fromPlugin: $os.name" }
                  }
                }
            }

            apply type: RulePlugin

            model {
                tasks {
                  create("fromScript") {
                    it.doLast { println "fromScript: " + $("platform.operatingSystem.name") }
                  }
                }
            }
        '''

        then:
        succeeds "fromPlugin", "fromScript"

        and:
        output.contains("fromPlugin: foo")
        output.contains("fromScript: foo")
    }

    def "rule can target simple property of managed element"() {
        given:
        EnableModelDsl.enable(executer)

        when:
        buildScript '''
            import org.gradle.model.*
            import org.gradle.model.collection.*

            @Managed
            interface Platform {
                String getName()
                void setName(String name)
            }

            @RuleSource
            class RulePlugin {
                @Model
                void platform(Platform platform) {
                  platform.name = "foo"
                }

                @Mutate
                void addTask(CollectionBuilder<Task> tasks, @Path("platform.name") String name) {
                  tasks.create("fromPlugin") {
                    doLast { println "fromPlugin: $name" }
                  }
                }
            }

            apply type: RulePlugin

            model {
                tasks {
                  create("fromScript") {
                    it.doLast { println "fromScript: " + $("platform.name") }
                  }
                }
            }
        '''

        then:
        succeeds "fromPlugin", "fromScript"

        and:
        output.contains("fromPlugin: foo")
        output.contains("fromScript: foo")
    }

    def "mutation rule can target property of managed element"() {
        given:
        EnableModelDsl.enable(executer)

        when:
        buildScript '''
            import org.gradle.model.*
            import org.gradle.model.collection.*

            @Managed
            interface Platform {
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
                void platform(Platform platform) {
                  platform.operatingSystem.name = "foo"
                }

                @Mutate
                void addTask(CollectionBuilder<Task> tasks, @Path("platform.operatingSystem.name") String name) {
                  tasks.create("fromPlugin") {
                    doLast { println "fromPlugin: $name" }
                  }
                }
            }

            apply type: RulePlugin

            model {
                tasks {
                  create("fromScript") {
                    it.doLast { println "fromScript: " + $("platform.operatingSystem.name") }
                  }
                }
            }
        '''

        then:
        succeeds "fromPlugin", "fromScript"

        and:
        output.contains("fromPlugin: foo")
        output.contains("fromScript: foo")
    }

    def "creation rule can target property of managed element"() {
        when:
        buildScript '''
            import org.gradle.model.*
            import org.gradle.model.collection.*

            @Managed
            interface OperatingSystem {
                String getName()
                void setName(String name)
            }

            @RuleSource
            class RulePlugin {
                @Model
                void operatingSystem(OperatingSystem operatingSystem) {
                  operatingSystem.name = "foo"
                }

                @Model
                String name(@Path("operatingSystem.name") String name) {
                  name
                }

                @Mutate
                void addTask(CollectionBuilder<Task> tasks, @Path("name") String name) {
                  tasks.create("echo") {
                    doLast { println "name: $name" }
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

    def "managed model interface can extend other interface"() {
        when:
        buildScript '''
            import org.gradle.model.*
            import org.gradle.model.collection.*

            interface Named {
                String getName()
                void setName(String name)
            }

            @Managed
            interface NamedThing extends Named {
                String getValue()
                void setValue(String value)
            }

            @RuleSource
            class RulePlugin {
                @Model
                void namedThing(NamedThing namedThing) {
                    namedThing.name = "name"
                    namedThing.value = "value"
                }

                @Mutate
                void addTask(CollectionBuilder<Task> tasks, NamedThing namedThing) {
                    tasks.create("echo") {
                        it.doLast {
                            println "name: $namedThing.name, value: $namedThing.value"
                        }
                    }
                }
            }

            apply type: RulePlugin
        '''

        then:
        succeeds "echo"

        and:
        output.contains("name: name, value: value")
    }

    def "can depend on managed super type as input and subject"() {
        when:
        buildScript '''
            import org.gradle.model.*
            import org.gradle.model.collection.*

            interface Named {
                String getName()
                void setName(String name)
            }

            @Managed
            interface ManagedNamed extends Named {
            }

            @RuleSource
            class RulePlugin {
                @Model
                void managedNamed(ManagedNamed namedThing) {
                }

                @Mutate
                void setName(Named named) {
                    named.name = "superclass"
                }

                @Mutate
                void addTask(CollectionBuilder<Task> tasks, Named named) {
                    tasks.create("echo") {
                        it.doLast {
                            println "name: $named.name"
                        }
                    }
                }
            }

            apply type: RulePlugin
        '''

        then:
        succeeds "echo"

        and:
        output.contains("name: superclass")
    }

    def "two managed types can extend the same parent"() {
        when:
        buildScript '''
            import org.gradle.model.*
            import org.gradle.model.collection.*

            interface Named {
                String getName()
                void setName(String name)
            }

            @Managed
            interface NamedString extends Named {
                String getValue()
                void setValue(String value)
            }

            @Managed
            interface NamedInteger extends Named {
                Integer getValue()
                void setValue(Integer value)
            }

            @RuleSource
            class RulePlugin {
                @Model
                void namedString(NamedString namedString) {
                    namedString.name = "string"
                    namedString.value = "some value"
                }

                @Model
                void namedInteger(NamedInteger namedInteger) {
                    namedInteger.name = "integer"
                    namedInteger.value = 1234
                }

                @Mutate
                void addTask(CollectionBuilder<Task> tasks, NamedString string, NamedInteger integer) {
                    tasks.create("echo") {
                        it.doLast {
                            println "name: $string.name, value: $string.value"
                            println "name: $integer.name, value: $integer.value"
                        }
                    }
                }
            }

            apply type: RulePlugin
        '''

        then:
        succeeds "echo"

        and:
        output.contains("name: string, value: some value")
        output.contains("name: integer, value: 1234")
    }

    def "can have unmanaged property"() {
        when:
        buildScript '''
            import org.gradle.model.*
            import org.gradle.model.collection.*

            class UnmanagedThing {
              String value
            }

            @Managed
            interface ManagedThing {
                @Unmanaged
                UnmanagedThing getUnmanaged()
                void setUnmanaged(UnmanagedThing unmanaged)
            }

            @RuleSource
            class RulePlugin {
                @Model
                void m(ManagedThing thing) {
                    thing.unmanaged = new UnmanagedThing(value: "foo")
                }

                @Mutate
                void addTask(CollectionBuilder<Task> tasks, ManagedThing thing) {
                    tasks.create("echo") {
                        it.doLast {
                            println "value: $thing.unmanaged.value"
                        }
                    }
                }
            }

            apply type: RulePlugin
        '''

        then:
        succeeds "echo"

        and:
        output.contains('value: foo')
    }

    def "unmanaged property of managed can be targeted by rules"() {
        given:
        EnableModelDsl.enable(executer)

        when:
        buildScript '''
            import org.gradle.model.*
            import org.gradle.model.collection.*

            @Managed
            interface Platform {
                @Unmanaged
                OperatingSystem getOperatingSystem()
                void setOperatingSystem(OperatingSystem os)
            }

            class OperatingSystem {
                String name
            }

            @RuleSource
            class RulePlugin {
                @Model
                void platform(Platform platform) {}

                @Mutate
                void setOs(Platform platform) {
                    platform.operatingSystem = new OperatingSystem()
                }

                @Mutate
                void setOsName(@Path("platform.operatingSystem") OperatingSystem os) {
                  os.name = "foo"
                }

                @Mutate
                void addTask(CollectionBuilder<Task> tasks, @Path("platform.operatingSystem") OperatingSystem os) {
                  tasks.create("fromPlugin") {
                    doLast { println "fromPlugin: $os.name" }
                  }
                }
            }

            apply type: RulePlugin

            model {
                tasks {
                  create("fromScript") {
                    it.doLast { println "fromScript: " + $("platform.operatingSystem").name }
                  }
                }
            }
        '''

        then:
        succeeds "fromPlugin", "fromScript"

        and:
        output.contains("fromPlugin: foo")
        output.contains("fromScript: foo")
    }

}
