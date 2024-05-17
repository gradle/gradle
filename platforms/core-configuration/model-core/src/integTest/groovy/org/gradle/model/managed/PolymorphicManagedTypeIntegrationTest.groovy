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

@UnsupportedWithConfigurationCache(because = "software model")
class PolymorphicManagedTypeIntegrationTest extends AbstractIntegrationSpec {

    def "rule can provide a managed model element backed by an abstract class that implements interfaces"() {
        when:
        buildScript '''
            @Managed
            interface Named {
                String getName()
            }

            @Managed
            abstract class Person implements Named {
                abstract void setName(String name)
            }

            class RulePlugin extends RuleSource {
                @Model
                void createPerson(Person person) {
                    person.name = "foo"
                }

                @Mutate
                void addPersonTask(ModelMap<Task> tasks, Person person) {
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

    def "rule can provide a managed model element backed by an abstract class that extends other classes"() {
        when:
        buildScript '''
            @Managed
            abstract class Named {
                abstract String getName()
            }

            @Managed
            abstract class Person extends Named {
                abstract void setName(String name)
            }

            class RulePlugin extends RuleSource {
                @Model
                void createPerson(Person person) {
                    person.name = "foo"
                }

                @Mutate
                void addPersonTask(ModelMap<Task> tasks, Person person) {
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

    def "managed model interface can extend other interface"() {
        when:
        buildScript '''
            @Managed
            interface Named {
                String getName()
                void setName(String name)
            }

            @Managed
            interface NamedThing extends Named {
                String getValue()
                void setValue(String value)
            }

            class RulePlugin extends RuleSource {
                @Model
                void namedThing(NamedThing namedThing) {
                    namedThing.name = "name"
                    namedThing.value = "value"
                }

                @Mutate
                void addTask(ModelMap<Task> tasks, NamedThing namedThing) {
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
            @Managed
            interface Named {
                String getName()
                void setName(String name)
            }

            @Managed
            interface ManagedNamed extends Named {
            }

            class RulePlugin extends RuleSource {
                @Model
                void managedNamed(ManagedNamed namedThing) {
                }

                @Mutate
                void setName(Named named) {
                    named.name = "superclass"
                }

                @Mutate
                void addTask(ModelMap<Task> tasks, Named named) {
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
            @Managed
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

            class RulePlugin extends RuleSource {
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
                void addTask(ModelMap<Task> tasks, NamedString string, NamedInteger integer) {
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
}
