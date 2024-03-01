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
import org.gradle.test.fixtures.dsl.GradleDsl

class SystemPropertyInstrumentationInKotlinIntegrationTest extends AbstractConfigurationCacheIntegrationTest {
    def "#method is instrumented in Kotlin"() {
        def configurationCache = newConfigurationCacheFixture()

        given:
        // Why the separate plugin? The Project.getProperties() is available in the build.gradle.kts as getProperties().
        file("buildSrc/src/main/kotlin/SomePlugin.kt") << """
            import ${Plugin.name}
            import ${Project.name}

            class SomePlugin : Plugin<Project> {
                override fun apply(project: Project) {
                    val returned = $method
                    println("returned = \$returned")
                }
            }
        """

        file("buildSrc/build.gradle.kts") << """
            plugins {
                `kotlin-dsl`
            }
            ${mavenCentralRepository(GradleDsl.KOTLIN)}
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
            ignoringUnexpectedInputs()
        }

        where:
        method                                                     | _
        "System.getProperties().get(\"some.property\")"            | _
        "System.getProperty(\"some.property\")"                    | _
        "System.getProperty(\"some.property\", \"default.value\")" | _
        "System.setProperty(\"some.property\", \"new.value\")"     | _
        "System.clearProperty(\"some.property\")"                  | _
    }
}
