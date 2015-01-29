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
import org.gradle.util.Matchers

class InvalidManagedModelRuleIntegrationTest extends AbstractIntegrationSpec{

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

            class RulePlugin extends RuleSource {
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
        failure.assertHasDescription("Exception thrown while executing model rule: RulePlugin#createPerson(Person)")
        failure.assertThatCause(Matchers.containsLine(Matchers.matchesRegexp(/No signature of method: .*\.setName\(\) is applicable for argument types: \(java.lang.Integer\) values: \[123\]/)))
    }

    def "provides a useful error message when setting an incompatible type on a managed instance in Java"() {
        when:
        file('buildSrc/src/main/java/Rules.java') << '''
            import org.gradle.api.*;
            import org.gradle.model.*;
            import org.gradle.model.collection.*;
            import java.lang.reflect.*;

            @Managed
            interface Person {
                String getName();
                void setName(String name);
            }

            class RulePlugin extends RuleSource {
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
        failure.assertHasDescription("Exception thrown while executing model rule: RulePlugin#createPerson(Person)")
        failure.assertHasCause("argument type mismatch")
    }

    def "cannot assign a non-managed instance to a property of a managed type"() {
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

            class RulePlugin extends RuleSource {
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
        failure.assertHasDescription("Exception thrown while executing model rule: model.platform")
        failure.assertHasCause("Only managed model instances can be set as property 'operatingSystem' of class 'Platform'")
    }

    def "cannot use value type as subject of void model rule"() {
        given:
        when:
        buildScript '''
            import org.gradle.model.*

            class Rules extends RuleSource {
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

    def "provides a useful error message when an invalid managed type is used in a rule"() {
        when:
        buildScript '''
            import org.gradle.model.*
            import org.gradle.model.collection.*

            @Managed
            interface Person {
                String getName()
            }

            class RulePlugin extends RuleSource {
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
}
