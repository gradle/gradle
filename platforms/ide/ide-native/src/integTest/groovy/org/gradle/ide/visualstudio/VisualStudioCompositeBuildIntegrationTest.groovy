/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.ide.visualstudio

import org.gradle.ide.visualstudio.fixtures.AbstractVisualStudioIntegrationSpec
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.nativeplatform.fixtures.app.CppHelloWorldApp

class VisualStudioCompositeBuildIntegrationTest extends AbstractVisualStudioIntegrationSpec {
    def app = new CppHelloWorldApp()

    @ToBeFixedForConfigurationCache
    def "includes a visual studio project for every project in build with a C++ component"() {
        when:
        createDirs("one", "two", "three", "util", "other")
        settingsFile << """
            rootProject.name = 'app'
            include 'one', 'two', 'three'
            includeBuild "util"
            includeBuild "other"
        """
        buildFile << """
            allprojects {
                apply plugin: 'visual-studio'
            }
            project(':one') {
                apply plugin: 'cpp-application'
            }
            project(':two') {
                apply plugin: 'cpp-library'
                dependencies {
                    implementation "test:util:1.3"
                }
            }
        """

        buildTestFixture.withBuildInSubDir()
        singleProjectBuild("util") {
            settingsFile << "rootProject.name = 'util'"
            buildFile << """
                apply plugin: 'cpp-library'
                apply plugin: 'visual-studio'
                group = 'test'
                version = '1.3'
            """
        }
        singleProjectBuild("other") {
            settingsFile << "rootProject.name = 'other'"
            buildFile << """
                apply plugin: 'cpp-application'
                apply plugin: 'visual-studio'
            """
        }

        and:
        run ":visualStudio"

        then:
        result.assertTasksExecuted(":appVisualStudioSolution",
            ":one:oneVisualStudioFilters", ":one:oneVisualStudioProject",
            ":two:twoDllVisualStudioFilters", ":two:twoDllVisualStudioProject",
            ":util:utilDllVisualStudioFilters", ":util:utilDllVisualStudioProject",
            ":other:otherVisualStudioFilters", ":other:otherVisualStudioProject",
            ":visualStudio")

        and:
        def oneProject = projectFile("one/one.vcxproj")
        def twoProject = projectFile("two/twoDll.vcxproj")
        def utilProject = projectFile("util/utilDll.vcxproj")
        def otherProject = projectFile("other/other.vcxproj")

        final mainSolution = solutionFile("app.sln")
        mainSolution.assertHasProjects("one", "twoDll", "utilDll", "other")
        mainSolution.assertReferencesProject(oneProject, projectConfigurations)
        mainSolution.assertReferencesProject(twoProject, projectConfigurations)
        mainSolution.assertReferencesProject(utilProject, projectConfigurations)
        mainSolution.assertReferencesProject(otherProject, projectConfigurations)
    }
}
