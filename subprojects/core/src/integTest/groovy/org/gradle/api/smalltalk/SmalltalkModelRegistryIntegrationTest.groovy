/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.api.smalltalk

import org.gradle.api.Plugin
import org.gradle.api.initialization.Settings
import org.gradle.integtests.fixtures.AbstractIntegrationSpec

import javax.inject.Inject

class SmalltalkModelRegistryIntegrationTest extends AbstractIntegrationSpec {

    def "can inject registry in ad hoc settings plugin"() {
        settingsFile """
            abstract class MyPlugin implements Plugin<Settings> {
                @Inject
                abstract SmalltalkModelRegistry getRegistry()

                void apply(Settings s) {
                    if (getRegistry() != null) println("registry is available")
                }
            }

            apply plugin: MyPlugin
        """

        when:
        run "help"

        then:
        outputContains("registry is available")
    }

    def "can inject registry in precompiled settings plugin"() {
        buildFile file("build-logic/build.gradle"), """
            plugins {
                id 'groovy-gradle-plugin'
            }

            gradlePlugin {
                plugins {
                    mySettingsPlugin {
                        id = 'my-settings-plugin'
                        implementationClass = 'my.MySettingsPlugin'
                    }
                }
            }
        """

        groovyFile "build-logic/src/main/groovy/my/MySettingsPlugin.groovy", """
            package my
            import ${Plugin.name}
            import ${Settings.name}
            import ${SmalltalkModelRegistry.name}
            import ${Inject.name}

            abstract class MySettingsPlugin implements Plugin<Settings> {
                @Inject
                abstract SmalltalkModelRegistry getRegistry()

                void apply(Settings s) {
                    if (getRegistry() != null) {
                        println("registry is available")
                    }
                }
            }
        """

        settingsFile """
            pluginManagement {
                includeBuild("build-logic")
            }
            plugins {
                id("my-settings-plugin")
            }
        """

        when:
        run "help"

        then:
        outputContains("registry is available")
    }

}
