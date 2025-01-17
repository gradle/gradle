/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.internal.cc.impl.isolated

import org.gradle.test.fixtures.file.TestFile

class IsolatedProjectsJavaPluginIntegrationTest extends AbstractIsolatedProjectsToolingApiIntegrationTest {

    def "java projects can be configured in a parallel"() {
        given:
        withSomeToolingModelBuilderPluginInBuildSrc()
        settingsFile << """
            include("a")
            include("b")
        """
        javaProject(file("a"))
        javaProject(file("b"), """
            dependencies {
                implementation(project(':a'))
            }
        """)

        when:
        executer.withArguments(ENABLE_CLI)
        runBuildAction(new FetchCustomModelForEachProjectInParallel())

        then:
        fixture.assertModelStored {
            projectsConfigured(":buildSrc", ":", ":a", ":b")
            buildModelCreated()
            modelsCreated(":a", ":b")
        }
    }

    def "java projects with a valid cross-references can be configured in a parallel"() {
        given:
        withSomeToolingModelBuilderPluginInBuildSrc()
        settingsFile << """
            include("a")
            include("b")
        """

        javaProject(file("a"), """
            group = "group"

            sourceSets {
               create("feature")
            }

            java {
                registerFeature('feature') {
                    usingSourceSet(sourceSets.feature)
                }
            }

            dependencies {
                implementation project(":b")
            }
        """)

        javaProject(file("b"), """
            dependencies {
                implementation(project(":a")) {
                    capabilities {
                        requireCapability("group:a-feature")
                    }
                }
            }
        """)

        when:
        executer.withArguments(ENABLE_CLI)
        runBuildAction(new FetchCustomModelForEachProjectInParallel())

        then:
        fixture.assertModelStored {
            projectsConfigured(":buildSrc", ":", ":a", ":b")
            buildModelCreated()
            modelsCreated(":a", ":b")
        }
    }

    TestFile javaProject(TestFile dir, String configuration = "") {
        def buildFile = dir.file("build.gradle")
        buildFile << """
            plugins {
                id("java-library")
                id("my.plugin")
            }

            $configuration
        """
        return buildFile
    }
}
