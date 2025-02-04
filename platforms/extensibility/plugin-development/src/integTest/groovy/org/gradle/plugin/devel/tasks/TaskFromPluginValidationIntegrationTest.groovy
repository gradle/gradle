/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.plugin.devel.tasks

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.internal.reflect.validation.ValidationMessageChecker

import static org.gradle.internal.reflect.validation.TypeValidationProblemRenderer.convertToSingleLine

class TaskFromPluginValidationIntegrationTest extends AbstractIntegrationSpec implements ValidationMessageChecker {
    def setup() {
        expectReindentedValidationMessage()
    }

    @ToBeFixedForConfigurationCache(because = "https://github.com/gradle/gradle/issues/32296")
    def "detects that a problem is from a task declared in a precompiled script plugin"() {
        def pluginId = "test.gradle.demo.plugin"

        file("buildSrc/build.gradle") << """
            plugins {
                id 'groovy-gradle-plugin'
            }
        """
        def pluginFile = file("buildSrc/src/main/groovy/${pluginId}.gradle")
        pluginFile << DummyInvalidTask.source()

        buildFile """plugins {
            id '$pluginId'
        }"""
        expectThatExecutionOptimizationDisabledWarningIsDisplayed(executer, dummyValidationProblem { inPlugin(pluginId) })

        when:
        run(DummyInvalidTask.name)

        then:
        output.contains("- ${convertToSingleLine(dummyValidationProblemWithLink { inPlugin(pluginId) })}")
    }

    @ToBeFixedForConfigurationCache(because = "https://github.com/gradle/gradle/issues/32296")
    def "detects that a problem is from a task declared in plugin"() {
        def pluginId = "org.gradle.demo.plugin"

        settingsFile << """
            includeBuild 'my-plugin'
        """
        def pluginFile = file("my-plugin/src/main/groovy/org/gradle/demo/plugin/MyTask.groovy")
        pluginFile << """package ${pluginId}

            import org.gradle.api.*
            import org.gradle.api.file.*
            import org.gradle.api.provider.*
            import org.gradle.api.tasks.*

            ${DummyInvalidTask.definition()}
        """

        file("my-plugin/build.gradle") << """
            plugins {
                id 'groovy-gradle-plugin'
            }

            gradlePlugin {
                plugins {
                    create("simplePlugin") {
                        id = "${pluginId}"
                        implementationClass = "${pluginId}.MyPlugin"
                    }
                }
            }
        """
        file("my-plugin/settings.gradle") << """
            rootProject.name = "my-plugin"
        """
        file("my-plugin/src/main/groovy/org/gradle/demo/plugin/MyPlugin.groovy") << """package ${pluginId}
            import org.gradle.api.*
            class MyPlugin implements Plugin<Project> {
                void apply(Project p) {
                    ${DummyInvalidTask.registration("p")}
                }
            }
        """

        buildFile """plugins {
            id '$pluginId'
        }"""

        expectThatExecutionOptimizationDisabledWarningIsDisplayed(executer, dummyValidationProblem {
            type("${pluginId}.${DummyInvalidTask.className}")
            inPlugin(pluginId)
        })

        when:
        run(DummyInvalidTask.name)

        then:
        def expectedMessage = convertToSingleLine(dummyValidationProblemWithLink {
            type("${pluginId}.${DummyInvalidTask.className}")
            inPlugin(pluginId)
        })
        output.contains("- $expectedMessage")
    }

}
