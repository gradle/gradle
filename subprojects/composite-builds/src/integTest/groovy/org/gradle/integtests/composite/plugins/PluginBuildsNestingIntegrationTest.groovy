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

class PluginBuildsNestingIntegrationTest extends AbstractPluginBuildIntegrationTest {

    private final PluginBuildFixture build1 = pluginBuild("logic-1")
    private final PluginBuildFixture build2 = pluginBuild("logic-2")

    def "can nest included plugin builds"() {
        when:
        settingsFile << """
            pluginManagement {
                includeBuild("${build1.buildName}")
            }
        """
        build1.settingsFile << """
            includeBuild("../${build2.buildName}")
        """

        then:
        succeeds()
    }

    def "can nest early included plugin builds"() {
        when:
        settingsFile << """
            pluginManagement {
                includeBuild("${build1.buildName}")
            }
            plugins {
                id("${build1.settingsPluginId}")
            }
        """
        buildFile << """
            plugins {
                id("${build1.projectPluginId}")
            }
        """
        build1.settingsFile << """
            includeBuild("../${build2.buildName}")
        """

        then:
        succeeds()
    }

    def "nested included plugin build can contribute project plugins to including included build"() {
        given:
        settingsFile << """
            pluginManagement {
                includeBuild("${build2.buildName}")
            }
        """
        build2.settingsFile.setText("""
            pluginManagement {
                includeBuild("../${build1.buildName}")
            }
            rootProject.name = "${build2.buildName}"
        """)

        when:
        buildFile << """
            plugins {
                id("${build2.projectPluginId}")
            }
        """
        build2.buildFile.setText("""
            plugins {
                id("groovy-gradle-plugin")
                id("${build1.projectPluginId}")
            }
        """)

        then:
        succeeds()
        build1.assertProjectPluginApplied()
        build2.assertProjectPluginApplied()
    }

    def "nested included plugin build can contribute settings plugins to including included build"() {
        given:
        settingsFile << """
            pluginManagement {
                includeBuild("${build2.buildName}")
            }
        """
        build2.settingsFile.setText("""
            pluginManagement {
                includeBuild("../${build1.buildName}")
            }
            plugins {
                id("${build1.settingsPluginId}")
            }
            rootProject.name = "${build2.buildName}"
        """)

        when:
        buildFile << """
            plugins {
                id("${build2.projectPluginId}")
            }
        """

        then:
        succeeds()
        build1.assertSettingsPluginApplied()
        build2.assertProjectPluginApplied()
    }

    def "nested early included plugin build can contribute settings plugins to including included build"() {
        when:
        settingsFile << """
            pluginManagement {
                includeBuild("${build2.buildName}")
            }
            plugins {
                id("${build2.settingsPluginId}")
            }
        """
        build2.settingsFile.setText("""
            pluginManagement {
                includeBuild("../${build1.buildName}")
            }
            plugins {
                id("${build1.settingsPluginId}")
            }
            rootProject.name = "${build2.buildName}"
        """)

        then:
        succeeds()
        build1.assertSettingsPluginApplied()
        build2.assertSettingsPluginApplied()
    }

    def "included plugin build project plugins are not visible transitively"() {
        given:
        settingsFile << """
            pluginManagement {
                includeBuild("${build2.buildName}")
            }
        """
        build2.settingsFile.setText("""
            pluginManagement {
                includeBuild("../${build1.buildName}")
            }
            rootProject.name = "${build2.buildName}"
        """)

        when:
        buildFile << """
            plugins {
                id("${build1.projectPluginId}")
                id("${build2.projectPluginId}")
            }
        """

        then:
        fails()
        failureDescriptionContains("Plugin [id: '${build1.projectPluginId}'] was not found in any of the following sources:")
    }

    def "included plugin build project plugins are visible when included explicitly"() {
        given:
        settingsFile << """
            pluginManagement {
                includeBuild("${build2.buildName}")
                includeBuild("${build1.buildName}")
            }
        """
        build2.settingsFile.setText("""
            pluginManagement {
                includeBuild("../${build1.buildName}")
            }
            rootProject.name = "${build2.buildName}"
        """)

        when:
        buildFile << """
            plugins {
                id("${build1.projectPluginId}")
                id("${build2.projectPluginId}")
            }
        """

        then:
        succeeds()
        build1.assertProjectPluginApplied()
        build2.assertProjectPluginApplied()
    }

    def "included plugin build settings plugins are not visible transitively"() {
        when:
        settingsFile << """
            pluginManagement {
                includeBuild("${build2.buildName}")
            }
            plugins {
                id("${build1.settingsPluginId}")
                id("${build2.settingsPluginId}")
            }
        """
        build2.settingsFile.setText("""
            pluginManagement {
                includeBuild("../${build1.buildName}")
            }
            rootProject.name = "${build2.buildName}"
        """)

        then:
        fails()
        failureDescriptionContains("Plugin [id: '${build1.settingsPluginId}'] was not found in any of the following sources:")
    }

    def "included plugin build settings plugins are visible when included explicitly"() {
        given:
        settingsFile << """
            pluginManagement {
                includeBuild("${build2.buildName}")
                includeBuild("${build1.buildName}")
            }
            plugins {
                id("${build1.settingsPluginId}")
                id("${build2.settingsPluginId}")
            }
        """
        build2.settingsFile.setText("""
            pluginManagement {
                includeBuild("../${build1.buildName}")
            }
            rootProject.name = "${build2.buildName}"
        """)

        when:
        buildFile << """
            plugins {
                id("${build1.projectPluginId}")
                id("${build2.projectPluginId}")
            }
        """

        then:
        succeeds()
        build1.assertSettingsPluginApplied()
        build2.assertSettingsPluginApplied()
    }
}
