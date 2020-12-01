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

    private final BuildLogicBuildFixture build1 = buildLogicBuild("logic-1")
    private final BuildLogicBuildFixture build2 = buildLogicBuild("logic-2")

    def setup() {
        settingsFile << """
            pluginManagement {
                includeBuild("${build1.buildName}")
                includeBuild("${build2.buildName}")
            }
        """
    }

    def "two included build logic builds can contribute plugins to including build"() {
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

    // currently the second included build can see plugins from the build that was included first by the root - order of inclusion matters
    // what we want: only the build that includes should see plugins from the included build
    @NotYetImplemented
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

}
