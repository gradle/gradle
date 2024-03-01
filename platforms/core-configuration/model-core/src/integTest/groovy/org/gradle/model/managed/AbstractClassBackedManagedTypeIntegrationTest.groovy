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
class AbstractClassBackedManagedTypeIntegrationTest extends AbstractIntegrationSpec {

    def "rule can provide a managed model element backed by an abstract class"() {
        when:
        buildScript '''
            @Managed
            abstract class Person {
                abstract String getName()
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

    def "managed type implemented as abstract class can have generative getters"() {
        when:
        buildScript '''
            @Managed
            abstract class Person {
                abstract String getFirstName()
                abstract void setFirstName(String firstName)
                abstract String getLastName()
                abstract void setLastName(String lastName)

                String getName() {
                    "$firstName $lastName"
                }
            }

            class RulePlugin extends RuleSource {
                @Model
                void createPerson(Person person) {
                    person.firstName = "Alan"
                    person.lastName = "Turing"
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
        output.contains("name: Alan Turing")
    }

    def "managed type implemented as abstract class can have a custom toString() implementation"() {
        when:
        buildScript '''
            @Managed
            abstract class CustomToString {
                abstract String getStringRepresentation()
                abstract void setStringRepresentation(String representation)

                String toString() {
                    stringRepresentation
                }
            }

            class RulePlugin extends RuleSource {
                @Model
                void createElement(CustomToString element) {
                    element.stringRepresentation = "custom string representation"
                }

                @Mutate
                void addEchoTask(ModelMap<Task> tasks, CustomToString element) {
                    tasks.create("echo") {
                        it.doLast {
                            println "element: ${element.toString()}"
                        }
                    }
                }
            }

            apply type: RulePlugin
        '''

        then:
        succeeds "echo"

        and:
        output.contains("element: custom string representation")
    }

    def "calling setters from custom toString() implementation is not allowed"() {
        when:
        buildFile << '''
            @Managed
            abstract class CustomToStringCallingSetter {
                abstract String getStringRepresentation()
                abstract void setStringRepresentation(String representation)

                String toString() {
                    stringRepresentation = "foo"
                }
            }

            class RulePlugin extends RuleSource {
                @Model
                void createModelElementCallingSetterInCustomToString(CustomToStringCallingSetter element) {
                }

                @Mutate
                void addEchoTask(ModelMap<Task> tasks, CustomToStringCallingSetter element) {
                    tasks.create("echo") {
                        it.doLast {
                            println "element: ${element.toString()}"
                        }
                    }
                }
            }

            apply type: RulePlugin
        '''

        then:
        fails 'echo'

        and:
        failure.assertHasCause("Calling setters of a managed type on itself is not allowed")
    }

    private void defineCallsSetterInNonAbstractGetterClass() {
        buildFile << '''
            @Managed
            abstract class CallsSetterInNonAbstractGetter {
                abstract String getName()
                abstract void setName(String name)

                String getInvalidGenerativeProperty() {
                    name = "foo"
                }
            }
        '''
    }

    def "calling setters from non-abstract getters is not allowed"() {
        when:
        defineCallsSetterInNonAbstractGetterClass()
        buildFile << '''
            class RulePlugin extends RuleSource {
                @Model
                void createModelElementCallingSetterInNonAbstractGetter(CallsSetterInNonAbstractGetter element) {
                }

                @Mutate
                void accessInvalidGenerativeProperty(ModelMap<Task> tasks, CallsSetterInNonAbstractGetter element) {
                    element.invalidGenerativeProperty
                }
            }

            apply type: RulePlugin
        '''

        then:
        fails 'tasks'

        and:
        failure.assertHasCause("Calling setters of a managed type on itself is not allowed")
    }

    def "calling setters of super class from non-abstract getters is not allowed"() {
        when:
        defineCallsSetterInNonAbstractGetterClass()
        buildFile << '''
            @Managed
            abstract class CallsSuperGetterInNonAbstractGetter extends CallsSetterInNonAbstractGetter {

                String getInvalidGenerativeProperty() {
                    super.getInvalidGenerativeProperty()
                }

                String getGenerativeProperty() {
                    super.getGenerativeProperty()
                }
            }

            class RulePlugin extends RuleSource {
                @Model
                void createModelElementCallingSuperGetterInNonAbstractGetter(CallsSuperGetterInNonAbstractGetter element) {
                }

                @Mutate
                void accessInvalidGenerativeProperty(ModelMap<Task> tasks, CallsSuperGetterInNonAbstractGetter element) {
                    element.invalidGenerativeProperty
                }
            }

            apply type: RulePlugin
        '''

        then:
        fails 'tasks'

        and:
        failure.assertHasCause("Calling setters of a managed type on itself is not allowed")
    }

    def "reports managed abstract type in missing property error message"() {
        when:
        buildScript '''
            @Managed
            abstract class Person {
                abstract String getName()
                abstract void setName(String name)
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
