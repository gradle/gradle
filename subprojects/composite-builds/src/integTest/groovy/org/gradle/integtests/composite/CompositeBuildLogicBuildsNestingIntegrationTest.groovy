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

import groovy.transform.NotYetImplemented

class CompositeBuildLogicBuildsNestingIntegrationTest extends AbstractCompositeBuildIntegrationTest {

    def setup() {
        buildLogicBuild('logic-1')
        buildLogicBuild('logic-2')
    }

    def "can nest included build logic builds"() {
        when:
        settingsFile << """
            pluginManagement {
                includeBuild('logic-1')
            }
        """
        file('logic-1/settings.gradle') << """
            includeBuild('../logic-2')
        """

        then:
        succeeds()
    }

    // fails with "The settings are not yet available for build."
    @NotYetImplemented
    def "can nest early included build logic builds"() {
        when:
        settingsFile << """
            pluginManagement {
                includeBuild('logic-1')
            }
            plugins {
                id("logic-1.settings-plugin")
            }
        """
        file('logic-1/settings.gradle') << """
            includeBuild('../logic-2')
        """

        then:
        succeeds()
    }

    def "nested included build logic build can contribute build logic to including included build"() {
        given:
        settingsFile << """
            pluginManagement {
                includeBuild("logic-2")
            }
        """
        file("logic-2/settings.gradle").setText("""
            pluginManagement {
                includeBuild("../logic-1")
            }
            rootProject.name = "logic-2"
        """)

        when:
        buildFile << """
            plugins {
                id("logic-2.project-plugin")
            }
        """
        file("logic-2/build.gradle").setText("""
            plugins {
                id("groovy-gradle-plugin")
                id("logic-1.project-plugin")
            }
        """)

        then:
        succeeds()
        outputContains("logic-1 project plugin applied")
        outputContains("logic-2 project plugin applied")
    }


    // To be decided if we want the transitivity to work or not. Currently all the included builds are visible by the root project
    @NotYetImplemented
    def "nested included build logic build can contribute build logic to the root build"() {
        given:
        settingsFile << """
            pluginManagement {
                includeBuild("logic-2")
            }
        """
        file("logic-2/settings.gradle").setText("""
            pluginManagement {
                includeBuild("../logic-1")
            }
            rootProject.name = "logic-2"
        """)

        when:
        buildFile << """
            plugins {
                id("logic-1.project-plugin")
                id("logic-2.project-plugin")
            }
        """

        then:
        succeeds()
        outputContains("logic-1 project plugin applied")
        outputContains("logic-2 project plugin applied")
    }

    // To be decided if we want the transitivity to work or not. Currently all the included builds are visible by the root project
    def "included build logic builds are not visible transitively"() {
        given:
        settingsFile << """
            pluginManagement {
                includeBuild("logic-2")
            }
        """
        file("logic-2/settings.gradle").setText("""
            pluginManagement {
                includeBuild("../logic-1")
            }
            rootProject.name = "logic-2"
        """)

        when:
        buildFile << """
            plugins {
                id("logic-1.project-plugin")
                id("logic-2.project-plugin")
            }
        """

        then:
        fails()
        failureDescriptionContains("Plugin [id: 'logic-1.project-plugin'] was not found in any of the following sources:")
    }

}
