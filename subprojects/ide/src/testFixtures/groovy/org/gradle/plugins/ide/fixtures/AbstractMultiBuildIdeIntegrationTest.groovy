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

package org.gradle.plugins.ide.fixtures

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.test.fixtures.file.TestFile
import spock.lang.Issue

/**
 * Common behaviour tests for all IDE plugins dealing with multiple builds (buildSrc, composite builds, etc).
 */
abstract class AbstractMultiBuildIdeIntegrationTest extends AbstractIntegrationSpec {
    abstract String getPluginId()
    abstract String getWorkspaceTask()
    abstract String getLibraryPluginId()
    abstract IdeWorkspaceFixture workspace(TestFile workspaceDir, String ideWorkspaceName)
    abstract IdeProjectFixture project(TestFile projectDir, String ideProjectName)

    @ToBeFixedForConfigurationCache(because = "ide plugins")
    @Issue("https://github.com/gradle/gradle/issues/5110")
    def "buildSrc project can apply IDE plugin"() {
        file("buildSrc/build.gradle") << """
            apply plugin: '${pluginId}'
        """

        expect:
        succeeds(":buildSrc:${workspaceTask}")
        def workspace = workspace(file("buildSrc"), "buildSrc")
        if (libraryPluginId == "java-library") {
            def project = project(file("buildSrc"), "buildSrc")
            workspace.assertContains(project)
        } // else, unspecified
    }

    @ToBeFixedForConfigurationCache(because = "ide plugins")
    def "workspace includes projects from included builds"() {
        buildTestFixture.withBuildInSubDir()
        def buildA = singleProjectBuild("buildA") {
            settingsFile << """
                rootProject.name = "ide"
                includeBuild("../buildB")
            """
            buildFile << """
                allprojects {
                    apply plugin: '${pluginId}'
                    apply plugin: '${libraryPluginId}'
                }
                dependencies { implementation 'org.test:p1:1.2' }
            """
        }
        def buildB = multiProjectBuild("buildB", ["p1", "p2"]) {
            buildFile << """
                allprojects {
                    apply plugin: '${pluginId}'
                    apply plugin: '${libraryPluginId}'
                }
            """
        }

        when:
        executer.inDirectory(buildA)
        run(":${workspaceTask}")

        then:
        def workspace = workspace(buildA, "ide")
        workspace.assertContains(project(buildA, "ide"))
        workspace.assertContains(project(buildB, "buildB"))
        workspace.assertContains(project(buildB.file("p1"), "p1"))
        workspace.assertContains(project(buildB.file("p2"), "p2"))
    }

    @ToBeFixedForConfigurationCache(because = "ide plugins")
    def "workspace includes projects from nested included builds"() {
        buildTestFixture.withBuildInSubDir()
        def buildA = singleProjectBuild("buildA") {
            settingsFile << """
                rootProject.name = "ide"
                includeBuild("../buildB")
            """
            buildFile << """
                allprojects {
                    apply plugin: '${pluginId}'
                    apply plugin: '${libraryPluginId}'
                }
                dependencies { implementation 'org.test:buildB:1.2' }
            """
        }
        def buildB = singleProjectBuild("buildB") {
            settingsFile << """
                includeBuild("../buildC")
            """
            buildFile << """
                allprojects {
                    apply plugin: '${pluginId}'
                    apply plugin: '${libraryPluginId}'
                }
                dependencies { implementation 'org.test:p1:1.2' }
            """
        }
        def buildC = multiProjectBuild("buildC", ["p1", "p2"]) {
            buildFile << """
                allprojects {
                    apply plugin: '${pluginId}'
                    apply plugin: '${libraryPluginId}'
                }
            """
        }

        when:
        executer.inDirectory(buildA)
        run(":${workspaceTask}")

        then:
        def workspace = workspace(buildA, "ide")
        workspace.assertContains(project(buildA, "ide"))
        workspace.assertContains(project(buildB, "buildB"))
        workspace.assertContains(project(buildC, "buildC"))
        workspace.assertContains(project(buildC.file("p1"), "p1"))
        workspace.assertContains(project(buildC.file("p2"), "p2"))
    }
}
