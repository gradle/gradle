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
import org.gradle.integtests.fixtures.StableConfigurationCacheDeprecations
import org.gradle.integtests.fixtures.UnsupportedWithConfigurationCache

@UnsupportedWithConfigurationCache(because = "software model")
class InvalidManagedModelRuleIntegrationTest extends AbstractIntegrationSpec implements StableConfigurationCacheDeprecations {

    def "provides a useful error message when setting an incompatible type on a managed instance in Groovy"() {
        when:
        buildFile '''
            @Managed
            interface Person {
                int getThumbCount()
                void setThumbCount(int c)
            }

            class RulePlugin extends RuleSource {
                @Model
                void createPerson(Person person) {
                    person.setThumbCount(person)
                }

                @Mutate
                void addDependencyOnPerson(ModelMap<Task> tasks, Person person) {
                }
            }

            apply type: RulePlugin
        '''

        then:
        fails "tasks"

        and:
        failure.assertHasCause("Exception thrown while executing model rule: RulePlugin#createPerson(Person)")
        failure.assertHasCause("Cannot convert the provided notation to an object of type int")
    }

    def "provides a useful error message when setting an incompatible type on a managed instance in Java"() {
        when:
        file('buildSrc/src/main/java/Rules.java') << '''
            import org.gradle.api.*;
            import org.gradle.model.*;
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
                void addDependencyOnPerson(ModelMap<Task> tasks, Person person) {
                }
            }
        '''
        buildFile '''
            apply type: RulePlugin
        '''

        then:
        fails "tasks"

        and:
        failure.assertHasCause("Exception thrown while executing model rule: RulePlugin#createPerson(Person)")
        failure.assertHasCause("argument type mismatch")
    }

    def "cannot assign a non-managed instance to a property of a managed type"() {
        when:
        buildFile '''
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
                void addDependencyOnPlatform(ModelMap<Task> tasks, Platform platform) {
                }
            }

            apply type: RulePlugin

            class OperatingSystemImpl implements OperatingSystem {
                String name
            }

            model {
                platform {
                    operatingSystem = new OperatingSystemImpl()
                }
            }
        '''

        then:
        fails "tasks"

        and:
        failure.assertHasCause("Exception thrown while executing model rule: platform { ... } @ build.gradle line 31, column 17")
        failure.assertHasCause("Only managed model instances can be set as property 'operatingSystem' of class 'Platform'")
    }

    def "cannot use value type as subject of void model rule"() {
        when:
        buildFile '''
            class Rules extends RuleSource {
              @Model
              void s(String s) {}
            }

            apply type: Rules
        '''

        then:
        fails "tasks"

        and:
        failure.assertHasCause("Declaration of model rule Rules#s(String) is invalid.")
        failure.assertHasCause("A model element of type: 'java.lang.String' can not be constructed.")
    }

    def "cannot use unknown type as subject of void model rule"() {
        when:
        buildFile '''
            interface Thing { }

            class Rules extends RuleSource {
              @Model
              void s(Thing s) {}
            }

            apply type: Rules
        '''

        then:
        fails "tasks"

        and:
        failure.assertHasCause("Declaration of model rule Rules#s(Thing) is invalid.")
        failure.assertHasCause("A model element of type: 'Thing' can not be constructed.")
    }

    def "provides a useful error message when an invalid managed type is used in a rule"() {
        when:
        buildFile '''
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
        expectTaskGetProjectDeprecations()
        fails "model"

        and:
        failure.assertHasCause("Exception thrown while executing model rule: RulePlugin#createPerson(Person)")
        failure.assertHasCause("Invalid managed model type 'Person': read only property 'name' has non managed type java.lang.String, only managed types can be used")
    }
}
