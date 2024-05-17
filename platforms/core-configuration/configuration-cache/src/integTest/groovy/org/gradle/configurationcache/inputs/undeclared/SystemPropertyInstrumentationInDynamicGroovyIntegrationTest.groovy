/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.configurationcache.inputs.undeclared

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.configurationcache.AbstractConfigurationCacheIntegrationTest

class SystemPropertyInstrumentationInDynamicGroovyIntegrationTest extends AbstractConfigurationCacheIntegrationTest {
    def "#method is instrumented in dynamic Groovy #indyStatus"() {
        def configurationCache = newConfigurationCacheFixture()

        given:
        file("buildSrc/src/main/groovy/SomePlugin.groovy") << """
            import ${Plugin.name}
            import ${Project.name}
            import static ${System.name}.clearProperty
            import static ${System.name}.getProperties
            import static ${System.name}.getProperty
            import static ${System.name}.setProperty

            class SomePlugin implements Plugin<Project> {
                void apply(Project project) {
                    def returned = $method
                    println("returned = \$returned")
                }
            }
        """
        file("buildSrc/build.gradle") << """
            compileGroovy {
                groovyOptions.optimizationOptions.indy = $enableIndy
            }
        """

        buildScript("""
            apply plugin: SomePlugin
        """)

        when:
        configurationCacheRun("-Dsome.property=some.value")

        then:
        configurationCache.assertStateStored()
        outputContains("returned = some.value")
        problems.assertResultHasProblems(result) {
            withInput("Plugin class 'SomePlugin': system property 'some.property'")
        }

        where:
        [method, enableIndy] << [[
                                     "System.properties['some.property']",
                                     "System.getProperties().get('some.property')",
                                     "getProperties().get('some.property')",
                                     "System.getProperty('some.property')",
                                     "System.getProperty(*['some.property'])",
                                     "getProperty('some.property')",
                                     "System.getProperty('some.property', 'default.value')",
                                     "System.getProperty(*['some.property', 'default.value'])",
                                     "getProperty('some.property', 'default.value')",
                                     "System.setProperty('some.property', 'new.value')",
                                     "System.setProperty(*['some.property', 'new.value'])",
                                     "setProperty('some.property', 'new.value')",
                                     "System.clearProperty('some.property')",
                                     "System.clearProperty(*['some.property'])",
                                     "clearProperty('some.property')",
                                 ], [false, true]].combinations()
        indyStatus = enableIndy ? "with indy" : "without indy"
    }

    def "#setProperties is instrumented in dynamic Groovy #indyStatus"() {
        def configurationCache = newConfigurationCacheFixture()

        given:
        file("buildSrc/src/main/groovy/SomePlugin.groovy") << """
            import ${Plugin.name}
            import ${Project.name}
            import static ${System.name}.setProperties

            class SomePlugin implements Plugin<Project> {
                void apply(Project project) {
                    def newProps = new Properties()
                    System.properties.forEach { k, v -> newProps[k] = v }
                    newProps.replace("some.property", "new.value")
                    ${setProperties}(newProps)

                    project.tasks.register("printProperty") {
                        doLast {
                            println("returned = \${System.getProperty("some.property")}")
                        }
                    }
                }
            }
        """
        file("buildSrc/build.gradle") << """
            compileGroovy {
                groovyOptions.optimizationOptions.indy = $enableIndy
            }
        """

        buildScript("""
            apply plugin: SomePlugin
        """)

        when:
        configurationCacheRun("-Dsome.property=some.value", "printProperty")

        then:
        configurationCache.assertStateStored()
        outputContains("returned = new.value")

        when:
        configurationCacheRun("-Dsome.property=some.value", "printProperty")

        then:
        configurationCache.assertStateLoaded()
        outputContains("returned = new.value")

        where:
        [setProperties, enableIndy] << [
            ["System.setProperties", "setProperties"],
            [false, true]
        ].combinations()
        indyStatus = enableIndy ? "with indy" : "without indy"
    }
}
