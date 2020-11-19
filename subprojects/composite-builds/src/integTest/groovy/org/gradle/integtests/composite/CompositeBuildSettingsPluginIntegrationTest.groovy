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

package org.gradle.integtests.composite

class CompositeBuildSettingsPluginIntegrationTest extends AbstractCompositeBuildIntegrationTest {

    def setup() {
        file('included-build/settings.gradle') << """
            rootProject.name='included-build'
        """
        file('included-build/build.gradle') << """
            plugins {
                id("groovy-gradle-plugin")
            }
        """
    }

    def "included build logic build can contribute settings plugins"() {
        given:
        file('included-build/src/main/groovy/my.settings-plugin.settings.gradle') << """
            println('settings plugin applied')
        """
        settingsFile << """
            pluginManagement {
                includeBuild('included-build')
            }
            plugins {
                id("my.settings-plugin")
            }
        """

        when:
        succeeds('help')

        then:
        outputContains("settings plugin applied")
    }

    def "included build logic build can contribute both settings and project plugins"() {
        given:
        file('included-build/src/main/groovy/my.project-plugin.gradle') << """
            println('project plugin applied')
        """
        file('included-build/src/main/groovy/my.settings-plugin.settings.gradle') << """
            println('settings plugin applied')
        """
        settingsFile << """
            pluginManagement {
                includeBuild('included-build')
            }
            plugins {
                id("my.settings-plugin")
            }
        """
        buildFile << """
            plugins {
                id("my.project-plugin")
            }
        """

        when:
        succeeds('help')

        then:
        outputContains("settings plugin applied")
        outputContains("project plugin applied")
    }

    def "regular included build can not contribute settings plugins"() {
        given:
        file('included-build/src/main/groovy/my.settings-plugin.settings.gradle') << """
            println('settings plugin applied')
        """
        settingsFile << """
            plugins {
                id("my.settings-plugin")
            }
            includeBuild('included-build')
        """

        when:
        fails('help')

        then:
        failureDescriptionContains("Plugin [id: 'my.settings-plugin'] was not found in any of the following sources:")
    }

}
