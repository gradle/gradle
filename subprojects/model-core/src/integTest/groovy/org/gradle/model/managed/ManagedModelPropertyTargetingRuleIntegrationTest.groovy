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
import org.gradle.integtests.fixtures.EnableModelDsl

class ManagedModelPropertyTargetingRuleIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        EnableModelDsl.enable(executer)
    }

    def "rule can target structured property of managed element"() {
        when:
        buildScript '''
            @Managed
            interface Platform {
                OperatingSystem getOperatingSystem()
            }

            @Managed
            interface OperatingSystem {
                String getName()
                void setName(String name)
            }

            class RulePlugin extends RuleSource {
                @Model
                void platform(Platform platform) {
                  platform.operatingSystem.name = "foo"
                }

                @Mutate
                void addTask(ModelMap<Task> tasks, @Path("platform.operatingSystem") OperatingSystem os) {
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
        when:
        buildScript '''
            @Managed
            interface Platform {
                OperatingSystem getOperatingSystem()
            }

            @Managed
            interface OperatingSystem {
                String getName()
                void setName(String name)
            }

            class RulePlugin extends RuleSource {
                @Model
                void platform(Platform platform) {}

                @Mutate
                void setOsName(@Path("platform.operatingSystem") OperatingSystem os) {
                  os.name = "foo"
                }

                @Mutate
                void addTask(ModelMap<Task> tasks, @Path("platform.operatingSystem") OperatingSystem os) {
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
        when:
        buildScript '''
            @Managed
            interface Platform {
                String getName()
                void setName(String name)
            }

            class RulePlugin extends RuleSource {
                @Model
                void platform(Platform platform) {
                  platform.name = "foo"
                }

                @Mutate
                void addTask(ModelMap<Task> tasks, @Path("platform.name") String name) {
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
        when:
        buildScript '''
            @Managed
            interface Platform {
                OperatingSystem getOperatingSystem()
            }

            @Managed
            interface OperatingSystem {
                String getName()
                void setName(String name)
            }

            class RulePlugin extends RuleSource {
                @Model
                void platform(Platform platform) {
                  platform.operatingSystem.name = "foo"
                }

                @Mutate
                void addTask(ModelMap<Task> tasks, @Path("platform.operatingSystem.name") String name) {
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
            @Managed
            interface OperatingSystem {
                String getName()
                void setName(String name)
            }

            class RulePlugin extends RuleSource {
                @Model
                void operatingSystem(OperatingSystem operatingSystem) {
                  operatingSystem.name = "foo"
                }

                @Model
                String name(@Path("operatingSystem.name") String name) {
                  name
                }

                @Mutate
                void addTask(ModelMap<Task> tasks, @Path("name") String name) {
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
}
