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
class ManagedModelPropertyTargetingRuleIntegrationTest extends AbstractIntegrationSpec {

    def "rule can target nested element of managed element as input"() {
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
                    platform.operatingSystem.name = "windows"
                }

                @Mutate
                void configurePlatform(Platform platform) {
                    platform.operatingSystem.name = "${platform.operatingSystem.name} 10"
                }

                @Mutate
                void addTask(ModelMap<Task> tasks, @Path("platform.operatingSystem") OperatingSystem os) {
                    tasks.create("fromPlugin") {
                        doLast {
                            println "plugin input: $os"
                            println "plugin name: $os.name"
                        }
                    }
                }
            }

            apply type: RulePlugin

            model {
                tasks {
                    fromScript(Task) {
                        doLast {
                            println "script input: " + $("platform.operatingSystem")
                            println "script name: " + $("platform.operatingSystem").name
                        }
                    }
                }
            }
        '''

        then:
        succeeds "fromPlugin", "fromScript"

        and:
        output.contains("plugin input: OperatingSystem 'platform.operatingSystem'")
        output.contains("plugin name: windows 10")
        output.contains("script input: OperatingSystem 'platform.operatingSystem'")
        output.contains("script name: windows 10")
    }

    def "rule can target nested element of managed element as subject"() {
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
                    println "plugin subject: $os"
                    os.name = "foo"
                }

                @Mutate
                void addTask(ModelMap<Task> tasks, @Path("platform.operatingSystem") OperatingSystem os) {
                    tasks.create("fromPlugin") {
                        doLast {
                            println "plugin name: $os.name"
                        }
                    }
                }
            }

            apply type: RulePlugin

            model {
                platform.operatingSystem {
                    println "script subject: $it"
                    name = "$name os"
                }
                tasks {
                    fromScript(Task) {
                        doLast {
                            println "script name: " + $.platform.operatingSystem.name
                        }
                    }
                }
            }
        '''

        then:
        succeeds "fromPlugin", "fromScript"

        and:
        output.contains("plugin subject: OperatingSystem 'platform.operatingSystem'")
        output.contains("plugin name: foo os")
        output.contains("script subject: OperatingSystem 'platform.operatingSystem'")
        output.contains("script name: foo os")
    }

    def "rule can target managed element as input through a reference"() {
        when:
        buildScript '''
            @Managed
            interface Platform {
                OperatingSystem getOperatingSystem()
                void setOperatingSystem(OperatingSystem os)
            }

            @Managed
            interface OperatingSystem {
                String getName()
                void setName(String name)
            }

            class RulePlugin extends RuleSource {
                @Model
                void os(OperatingSystem os) {
                    os.name = "windows 10"
                }

                @Model
                void platform(Platform platform, OperatingSystem os) {
                    platform.operatingSystem = os
                }

                @Mutate
                void addTask(ModelMap<Task> tasks, @Path("platform.operatingSystem") OperatingSystem os) {
                    tasks.create("fromPlugin") {
                        doLast {
                            println "plugin input: $os"
                            println "plugin name: $os.name"
                        }
                    }
                }
            }

            apply type: RulePlugin

            model {
                tasks {
                    fromScript(Task) {
                        doLast {
                            println "script input: " + $("platform.operatingSystem")
                            println "script name: " + $("platform.operatingSystem").name
                        }
                    }
                }
            }
        '''

        then:
        succeeds "fromPlugin", "fromScript"

        and:
        output.contains("plugin input: OperatingSystem 'os'")
        output.contains("plugin name: windows 10")
        output.contains("script input: OperatingSystem 'os'")
        output.contains("script name: windows 10")
    }

    def "rule can target nested element of managed element as input through a reference to managed element"() {
        when:
        buildScript '''
            @Managed
            interface Platform {
                OperatingSystem getOperatingSystem()
                void setOperatingSystem(OperatingSystem os)
            }

            @Managed
            interface OperatingSystem {
                Family getFamily()
            }

            @Managed
            interface Family {
                String getName()
                void setName(String name)
            }

            class RulePlugin extends RuleSource {
                @Model
                void windows(OperatingSystem os) {
                    os.family.name = 'windows 10'
                }

                @Model
                void platform(Platform platform, OperatingSystem os) {
                    platform.operatingSystem = os
                }

                @Mutate
                void addTask(ModelMap<Task> tasks, @Path("platform.operatingSystem.family") Family family) {
                    tasks.create("fromPlugin") {
                        doLast {
                            println "plugin input: $family"
                            println "plugin name: $family.name"
                        }
                    }
                }
            }

            apply type: RulePlugin

            model {
                tasks {
                    fromScript(Task) {
                        doLast {
                            println "script input: " + $("platform.operatingSystem.family")
                            println "script name: " + $("platform.operatingSystem.family").name
                        }
                    }
                }
            }
        '''

        then:
        succeeds "fromPlugin", "fromScript"

        and:
        output.contains("plugin input: Family 'windows.family'")
        output.contains("plugin name: windows 10")
        output.contains("script input: Family 'windows.family'")
        output.contains("script name: windows 10")
    }

    def "rule can target managed element via a series of references"() {
        when:
        buildScript '''
            @Managed
            interface Platform {
                OperatingSystem getOperatingSystem()
                void setOperatingSystem(OperatingSystem os)
            }

            @Managed
            interface OperatingSystem {
                Family getFamily()
                void setFamily(Family family)
            }

            @Managed
            interface Family {
                String getName()
                void setName(String name)
            }

            class RulePlugin extends RuleSource {
                @Model
                void windows(Family family) {
                    family.name = 'windows 10'
                }

                @Model
                void windows10(OperatingSystem os, Family family) {
                    os.family = family
                }

                @Model
                void platform(Platform platform, OperatingSystem os) {
                    platform.operatingSystem = os
                }

                @Mutate
                void addTask(ModelMap<Task> tasks, @Path("platform.operatingSystem.family") Family family) {
                    tasks.create("fromPlugin") {
                        doLast {
                            println "plugin input: $family"
                            println "plugin name: $family.name"
                        }
                    }
                }
            }

            apply type: RulePlugin

            model {
                tasks {
                    fromScript(Task) {
                        doLast {
                            println "script input: " + $("platform.operatingSystem.family")
                            println "script name: " + $("platform.operatingSystem.family").name
                        }
                    }
                }
            }
        '''

        then:
        succeeds "fromPlugin", "fromScript"

        and:
        output.contains("plugin input: Family 'windows'")
        output.contains("plugin name: windows 10")
        output.contains("script input: Family 'windows'")
        output.contains("script name: windows 10")
    }

    def "target of reference is realized when used as an input"() {
        when:
        buildScript '''
            @Managed
            interface Platforms {
                OperatingSystem getCurrent()
                void setCurrent(OperatingSystem os)

                OperatingSystem getWindows()
                OperatingSystem getLinux()
            }

            @Managed
            interface OperatingSystem {
                String getName()
                void setName(String name)
            }

            class RulePlugin extends RuleSource {
                @Model
                void platforms(Platforms platforms) {
                    platforms.current = platforms.windows
                }

                @Defaults
                void platformDefaults(Platforms platforms) {
                    platforms.windows.name = 'windows'
                    platforms.linux.name = 'linux'
                }

                @Mutate
                void configureWindows(@Path('platforms.windows') OperatingSystem os) {
                    os.name = "$os.name 10"
                }

                @Mutate
                void addTask(ModelMap<Task> tasks, @Path("platforms.current") OperatingSystem os) {
                    tasks.create("fromPlugin") {
                        doLast {
                            println "plugin input: $os"
                            println "plugin name: $os.name"
                        }
                    }
                }
            }

            apply type: RulePlugin

            model {
                tasks {
                    fromScript(Task) {
                        doLast {
                            println "script input: " + $("platforms.current")
                            println "script name: " + $("platforms.current").name
                        }
                    }
                }
            }
        '''

        then:
        succeeds "fromPlugin", "fromScript"

        and:
        output.contains("plugin input: OperatingSystem 'platforms.windows'")
        output.contains("plugin name: windows 10")
        output.contains("script input: OperatingSystem 'platforms.windows'")
        output.contains("script name: windows 10")
    }

    def "rule can target scalar property of managed element as input"() {
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
                        doLast {
                            println "plugin name: $name"
                        }
                    }
                }
            }

            apply type: RulePlugin

            model {
                tasks {
                    fromScript(Task) {
                        doLast {
                            println "script name: " + $.platform.name
                        }
                    }
                }
            }
        '''

        then:
        succeeds "fromPlugin", "fromScript"

        and:
        output.contains("plugin name: foo")
        output.contains("script name: foo")
    }

    def "rule can configure scalar property of managed element"() {
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
                platform {
                    operatingSystem.name = "$operatingSystem.name os"
                }
                tasks {
                    fromScript(Task) {
                        doLast { println "fromScript: " + $.platform.operatingSystem.name }
                    }
                }
            }
        '''

        then:
        succeeds "fromPlugin", "fromScript"

        and:
        output.contains("fromPlugin: foo os")
        output.contains("fromScript: foo os")
    }

    def "creation rule can target scalar property of managed element as input"() {
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
