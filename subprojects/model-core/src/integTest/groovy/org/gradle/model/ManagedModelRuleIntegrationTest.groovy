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
}
