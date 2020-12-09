/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.integtests.composite.plugins

class PluginBuildConfigurationIntegrationTest extends AbstractPluginBuildIntegrationTest {

    def "included settings plugin can not clash with root subproject name"() {
        given:
        def pluginBuild = pluginBuild("build-logic")
        settingsFile << """
            pluginManagement {
                includeBuild("$pluginBuild")
            }
            plugins {
                id("${pluginBuild.settingsPluginId}")
            }
            include("$pluginBuild")
        """

        when:
        fails()

        then:
        failureDescriptionContains("build-logic has name 'build-logic' which is the same as a project of the main build")
    }

    def "included project plugin build can not clash with root subproject name"() {
        given:
        def pluginBuild = pluginBuild("build-logic")
        settingsFile << """
            pluginManagement {
                includeBuild("$pluginBuild")
            }
            include("$pluginBuild")
        """
        buildFile << """
            plugins {
                id("${pluginBuild.projectPluginId}")
            }
        """

        when:
        fails()

        then:
        failureDescriptionContains("build-logic has name 'build-logic' which is the same as a project of the main build")
    }

    def "included settings plugin build can be renamed"() {
        given:
        def pluginBuild = pluginBuild("build-logic")
        def newPluginBuildName = "renamed-build"
        settingsFile << """
            pluginManagement {
                includeBuild("$pluginBuild") {
                    name = "$newPluginBuildName"
                }
            }
            plugins {
                id("${pluginBuild.settingsPluginId}")
            }
            include("$pluginBuild")
        """

        when:
        succeeds()

        then:
        executed(":${newPluginBuildName}:jar")
        pluginBuild.assertSettingsPluginApplied()
    }

    def "included project plugin build can be renamed"() {
        given:
        def pluginBuild = pluginBuild("build-logic")
        def newPluginBuildName = "renamed-build"
        settingsFile << """
            pluginManagement {
                includeBuild("$pluginBuild") {
                    name = "$newPluginBuildName"
                }
            }
            include("$pluginBuild")
        """
        buildFile << """
            plugins {
                id("${pluginBuild.projectPluginId}")
            }
        """

        when:
        succeeds()

        then:
        executed(":${newPluginBuildName}:jar")
        pluginBuild.assertProjectPluginApplied()
    }

    def "included plugin build can not have dependency substitutions"() {
        given:
        def pluginBuild = pluginBuild("build-logic")
        settingsFile << """
            pluginManagement {
                includeBuild("$pluginBuild") {
                    dependencySubstitution {
                    }
                }
            }
        """
        buildFile << """
            plugins {
                id("${pluginBuild.projectPluginId}")
            }
        """

        when:
        fails()

        then:
        failureCauseContains("Could not find method dependencySubstitution()")
    }

}
