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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class CompositeBuildIsolationIntegrationTest extends AbstractIntegrationSpec {
    def "included build can access root project sneakily"() {
        given:
        buildTestFixture.withBuildInSubDir()
        singleProjectBuild("included") {
            buildFile << """
                apply plugin: 'java'

                def rootGradleBuild = gradle
                while (rootGradleBuild.parent != null) {
                    rootGradleBuild = rootGradleBuild.parent
                }
                assert rootGradleBuild.rootProject.name == "root"
            """
            settingsFile << "rootProject.name = 'included'"
        }
        buildFile << """
            plugins {
                id 'java'
            }
            dependencies {
                implementation("org.test:included")
            }
        """
        settingsFile << """
            rootProject.name = "root"
            includeBuild("included")
        """

        expect:
        succeeds("assemble")
    }

    def "included build can access root project sneakily when used as a plugin"() {
        given:
        buildTestFixture.withBuildInSubDir()
        singleProjectBuild("included") {
            buildFile << """
                apply plugin: 'java'

                def rootGradleBuild = gradle
                while (rootGradleBuild.parent != null) {
                    rootGradleBuild = rootGradleBuild.parent
                }
                assert rootGradleBuild.rootProject.name == "root"
            """
            settingsFile << "rootProject.name = 'included'"
        }
        buildFile << """
            buildscript {
                dependencies {
                    classpath "org.test:included"
                }
            }
        """
        settingsFile << """
            rootProject.name = "root"
            includeBuild("included")
        """

        expect:
        succeeds("help")
    }
}
