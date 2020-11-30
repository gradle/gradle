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

class CompositeBuildLogicBuildsOrderingIntegrationTest extends AbstractCompositeBuildIntegrationTest {

    def setup() {
        buildLogicBuild('logic-1')
        buildLogicBuild('logic-2')

        settingsFile << """
            pluginManagement {
                includeBuild('logic-1')
                includeBuild('logic-2')
            }
        """
    }

    def "two included build logic builds can contribute plugins to including build"() {
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

    // in the current implementation, we fall back to configuring all including builds when a plugin is not found - this results in all the publications from included builds becoming visible everywhere
    // what we want: only the build that includes should see plugins from the included build
    @NotYetImplemented
    def "first included build can not see plugins contributed to root by second included build"() {
        when:
        file("logic-1/build.gradle").setText("""
            plugins {
                id("groovy-gradle-plugin")
                id("logic-2.project-plugin")
            }
        """)

        then:
        fails()
        failureDescriptionContains("Plugin [id: 'logic-2.project-plugin'] was not found in any of the following sources:")
    }

    // in the current implementation, we fall back to configuring all including builds when a plugin is not found - this results in all the publications from included builds becoming visible everywhere
    // what we want: only the build that includes should see plugins from the included build
    @NotYetImplemented
    def "second included build can not see plugins contributed to root by first included build"() {
        when:
        file("logic-2/build.gradle").setText("""
            plugins {
                id("groovy-gradle-plugin")
                id("logic-1.project-plugin")
            }
        """)

        then:
        fails()
        failureDescriptionContains("Plugin [id: 'logic-1.project-plugin'] was not found in any of the following sources:")
    }

}
