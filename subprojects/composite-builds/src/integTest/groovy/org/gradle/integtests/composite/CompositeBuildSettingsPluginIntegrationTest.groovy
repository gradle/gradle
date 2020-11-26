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

import spock.lang.Ignore

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

    def "early included build logic build can contribute settings plugins"() {
        given:
        file('included-build/src/main/groovy/my.settings-plugin.settings.gradle') << """
            println('settings plugin applied')
        """
        settingsFile << """
            pluginManagement {
                includeBuildEarly('included-build')
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

    def "early included build logic build can contribute project plugins"() {
        given:
        file('included-build/src/main/groovy/my.project-plugin.gradle') << """
            println('project plugin applied')
        """
        settingsFile << """
            pluginManagement {
                includeBuildEarly('included-build')
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
        outputContains("project plugin applied")
    }

    def "early included build logic build can contribute both settings and project plugins"() {
        given:
        file('included-build/src/main/groovy/my.project-plugin.gradle') << """
            println('project plugin applied')
        """
        file('included-build/src/main/groovy/my.settings-plugin.settings.gradle') << """
            println('settings plugin applied')
        """
        settingsFile << """
            pluginManagement {
                includeBuildEarly('included-build')
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

    def "included build logic builds can not contribute settings plugins"() {
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
        fails('help')

        then:
        failureDescriptionContains("Plugin [id: 'my.settings-plugin'] was not found in any of the following sources:")
    }

    def "included build logic builds can contribute project plugins"() {
        given:
        file('included-build/src/main/groovy/my.project-plugin.gradle') << """
            println('project plugin applied')
        """
        settingsFile << """
            pluginManagement {
                includeBuild('included-build')
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

    @Ignore("to be fixed in next iteration")
    def "regular included builds contributing project plugins is deprecated"() {
        given:
        file('included-build/src/main/groovy/my.project-plugin.gradle') << """
            println('project plugin applied')
        """
        settingsFile << """
            includeBuild('included-build')
        """
        buildFile << """
            plugins {
                id("my.project-plugin")
            }
        """

        when:
        executer.expectDocumentedDeprecationWarning("Including builds that contribute Gradle plugins outside of pluginManagement {} block in settings file has been deprecated. This is scheduled to be removed in Gradle 8.0. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_6.html#included_builds_contributing_plugins")
        succeeds('help')

        then:
        outputContains("project plugin applied")
    }

    def "does not print deprecation warning when plugin is included using pluginManagement and same build is included regularly in addition"() {
        given:
        file('included-build/src/main/groovy/my.project-plugin.gradle') << """
            println('project plugin applied')
        """
        settingsFile << """
            pluginManagement {
                includeBuild('included-build')
            }
            includeBuild('included-build')
        """
        buildFile << """
            plugins {
                id("my.project-plugin")
            }
        """

        when:
        succeeds('help')

        then:
        outputContains("project plugin applied")
    }

    def "does not print deprecation warning when plugin is included early using pluginManagement and same build is included regularly in addition"() {
        given:
        file('included-build/src/main/groovy/my.project-plugin.gradle') << """
            println('project plugin applied')
        """
        settingsFile << """
            pluginManagement {
                includeBuildEarly('included-build')
            }
            includeBuild('included-build')
        """
        buildFile << """
            plugins {
                id("my.project-plugin")
            }
        """

        when:
        succeeds('help')

        then:
        outputContains("project plugin applied")
    }
}
