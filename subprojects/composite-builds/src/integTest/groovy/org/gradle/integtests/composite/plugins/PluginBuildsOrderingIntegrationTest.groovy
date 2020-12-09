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

class PluginBuildsOrderingIntegrationTest extends AbstractPluginBuildIntegrationTest {

    private final PluginBuildFixture build1 = pluginBuild("logic-1")
    private final PluginBuildFixture build2 = pluginBuild("logic-2")

    def setup() {
        settingsFile << """
            pluginManagement {
                includeBuild("${build1.buildName}")
                includeBuild("${build2.buildName}")
            }
        """
    }

    def "two included plugin builds can contribute plugins to including build"() {
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

    def "first included build can not see plugins contributed to root by second included build"() {
        when:
        buildFile << """
            plugins {
                id("${build1.projectPluginId}")
            }
        """

        build1.buildFile.setText("""
            plugins {
                id("groovy-gradle-plugin")
                id("${build2.projectPluginId}")
            }
        """)

        then:
        fails()
        failureDescriptionContains("Plugin [id: '${build2.projectPluginId}'] was not found in any of the following sources:")
    }

    def "first included build can include the build it needs plugins from explicitly"() {
        when:
        buildFile << """
            plugins {
                id("${build1.projectPluginId}")
            }
        """

        build1.settingsFile.setText("""
            pluginManagement {
                includeBuild("../${build2.buildName}")
            }
            rootProject.name = "${build1.buildName}"
        """)
        build1.buildFile.setText("""
            plugins {
                id("groovy-gradle-plugin")
                id("${build2.projectPluginId}")
            }
        """)

        then:
        succeeds()
        build2.assertProjectPluginApplied()
        build1.assertProjectPluginApplied()
    }

    def "second included build can not see plugins contributed to root by first included build"() {
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
        fails()
        failureDescriptionContains("Plugin [id: '${build1.projectPluginId}'] was not found in any of the following sources:")
    }

    def "second included build can include the build it needs plugins from explicitly"() {
        when:
        buildFile << """
            plugins {
                id("${build2.projectPluginId}")
            }
        """

        build2.settingsFile.setText("""
            pluginManagement {
                includeBuild("../${build1.buildName}")
            }
            rootProject.name = "${build2.buildName}"
        """)
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
}
