/*
 * Copyright 2022 the original author or authors.
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

import org.gradle.integtests.fixtures.build.BuildTestFile

class CompositeBuildMultipleBuildLogicIntegrationTest extends AbstractCompositeBuildIntegrationTest {

    def "can have build-logic build and include build with build-logic build"() {
        def rootBuildBuildLogic = new BuildTestFile(buildA.file("build-logic"), "build-logic")
        rootBuildBuildLogic.buildFile """
            plugins {
                id 'java-gradle-plugin'
            }
        """
        rootBuildBuildLogic.settingsFile.createFile()
        buildA.settingsFile << """
            includeBuild 'build-logic'
        """

        def includedBuild = multiProjectBuild("buildB", ["project1", "project2"]) {
            includeBuild(buildA)
            settingsFile << """
                includeBuild 'build-logic'
            """
        }
        def includedBuildBuildLogic = new BuildTestFile(includedBuild.file("build-logic"), "build-logic")
        includedBuildBuildLogic.buildFile """
            plugins {
                id 'java-gradle-plugin'
            }
        """
        includedBuildBuildLogic.settingsFile.createFile()

        includeBuild(includedBuild)

        expect:
        succeeds(buildA, "help")

    }
}
