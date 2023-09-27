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

import groovy.transform.CompileStatic
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.configurationcache.AbstractConfigurationCacheIntegrationTest

class SystemPropertyInstrumentationInStaticGroovyIntegrationTest extends AbstractConfigurationCacheIntegrationTest {
    def "#method is instrumented in static Groovy"() {
        def configurationCache = newConfigurationCacheFixture()

        given:
        // Why the separate plugin? The Project.getProperties() is available in the build.gradle as getProperties().
        // Therefore, it is impossible to call System.getProperties() with static import there, and testing static
        // import is important because Groovy generates different code in this case.
        file("buildSrc/src/main/groovy/SomePlugin.groovy") << """
            import ${CompileStatic.name}
            import ${Plugin.name}
            import ${Project.name}
            import static ${System.name}.clearProperty
            import static ${System.name}.getProperties
            import static ${System.name}.getProperty
            import static ${System.name}.setProperty

            @CompileStatic
            class SomePlugin implements Plugin<Project> {
                void apply(Project project) {
                    def returned = $method
                    println("returned = \$returned")
                }
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
        method                                                 | _
        "System.properties['some.property']"                   | _
        "System.getProperties().get('some.property')"          | _
        "getProperties().get('some.property')"                 | _
        "System.getProperty('some.property')"                  | _
        "getProperty('some.property')"                         | _
        "System.getProperty('some.property', 'default.value')" | _
        "getProperty('some.property', 'default.value')"        | _
        "System.setProperty('some.property', 'new.value')"     | _
        "setProperty('some.property', 'new.value')"            | _
        "System.clearProperty('some.property')"                | _
        "clearProperty('some.property')"                       | _
    }

    def "#setProperties is instrumented in static Groovy"() {
        def configurationCache = newConfigurationCacheFixture()

        given:
        buildScript("""
            import ${CompileStatic.name}
            import static ${System.name}.setProperties

            @CompileStatic
            class SomePlugin implements Plugin<Project> {
                void apply(Project project) {
                    def newProps = new Properties()
                    System.properties.forEach { k, v -> newProps[k] = v }
                    newProps.replace("some.property", "new.value")
                    ${setProperties}(newProps)
                }
            }

            apply plugin: SomePlugin

            tasks.register("printProperty") {
                doLast {
                    println("returned = \${System.getProperty("some.property")}")
                }
            }
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
        setProperties          | _
        "System.setProperties" | _
        "setProperties"        | _
    }
}
