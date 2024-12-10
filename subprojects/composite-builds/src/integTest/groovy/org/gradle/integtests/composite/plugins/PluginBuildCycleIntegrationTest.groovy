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

package org.gradle.integtests.composite.plugins

class PluginBuildCycleIntegrationTest extends AbstractPluginBuildIntegrationTest {

    def 'cannot resolve plugin from included build if cycle is present in definition'() {
        given:
        def pluginsA = pluginBuild("plugins-a", ["plugins-b.project-plugin"])
        def pluginsB = pluginBuild("plugins-b", ["plugins-a.project-plugin"])

        pluginsA.settingsFile.text = """
            pluginManagement {
                includeBuild("../plugins-b")
            }
        """

        pluginsB.settingsFile.text = """
            pluginManagement {
                includeBuild("../plugins-a")
            }
        """

        settingsFile """
            pluginManagement {
                includeBuild("${pluginsA.buildName}")
            }
        """
        buildFile """
            plugins {
                id("${pluginsA.projectPluginId}")
            }
        """

        when:
        fails "help"

        then:
        failureDescriptionContains("Plugin [id: 'plugins-a.project-plugin'] was not found in any of the following sources:")
    }

    def 'cannot use dependency substitution from included build if cycle is present in definition'() {
        given:
        def pluginsLibA = pluginAndLibraryBuild(pluginBuild("plugins-lib-a", ["plugins-b.project-plugin"]))
        def pluginsB = pluginBuild("plugins-b")

        pluginsLibA.settingsFile.text = """
            pluginManagement {
                includeBuild("../${pluginsB.buildName}")
            }
        """

        pluginsB.settingsFile << """
            includeBuild("../${pluginsLibA.buildName}")
        """
        pluginsB.buildFile << """
            dependencies {
                implementation("${pluginsLibA.group}:${pluginsLibA.buildName}")
            }
        """

        settingsFile << """
            pluginManagement {
                includeBuild("${pluginsLibA.buildName}")
            }
        """
        buildFile << """
            plugins {
                id("${pluginsLibA.projectPluginId}")
            }
        """

        when:
        fails "help"

        then:
        failureCauseContains("Could not find com.example:plugins-lib-a:.\nRequired by:\n    project :plugins-lib-a > project :plugins-b")
    }
}
