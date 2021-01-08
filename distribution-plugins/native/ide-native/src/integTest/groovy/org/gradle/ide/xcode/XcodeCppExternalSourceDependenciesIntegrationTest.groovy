/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.ide.xcode

import org.gradle.ide.xcode.fixtures.AbstractXcodeIntegrationSpec
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.integtests.fixtures.build.BuildTestFile
import org.gradle.nativeplatform.fixtures.app.CppAppWithLibrary
import org.gradle.vcs.fixtures.GitFileRepository
import org.gradle.vcs.internal.SourceDependencies
import org.junit.Rule

import static org.gradle.ide.xcode.internal.XcodeUtils.toSpaceSeparatedList

class XcodeCppExternalSourceDependenciesIntegrationTest extends AbstractXcodeIntegrationSpec implements SourceDependencies {
    @Rule
    GitFileRepository repo = new GitFileRepository('greeter', temporaryFolder.getTestDirectory())
    BuildTestFile depProject

    @ToBeFixedForConfigurationCache
    def "adds source dependencies C++ headers of main component to Xcode indexer search path"() {
        def fixture = new CppAppWithLibrary()

        given:
        buildTestFixture.withBuildInSubDir()
        depProject = singleProjectBuild("greeter") {
            buildFile << """
                apply plugin: 'cpp-library'
            """
            fixture.greeter.writeToProject(it)
        }
        def commit = repo.commit('initial commit')

        and:
        settingsFile << """
            sourceControl {
                vcsMappings {
                    withModule("org.test:greeter") {
                        from(GitVersionControlSpec) {
                            url = "${repo.url}"
                        }
                    }
                }
            }
        """
        buildFile << """
            apply plugin: 'cpp-application'
            apply plugin: 'xcode'
            group = 'org.gradle'
            version = '2.0'

            dependencies {
                implementation "org.test:greeter:latest.integration"
            }
        """
        fixture.main.writeToProject(testDirectory)

        when:
        succeeds ':xcode'

        then:
        result.assertTasksExecuted(":xcodeProject", ":xcodeProjectWorkspaceSettings", ":xcodeScheme", ":xcodeWorkspaceWorkspaceSettings", ":xcodeWorkspace", ":xcode")
        rootXcodeWorkspace.contentFile.assertHasProjects("${rootProjectName}.xcodeproj")

        def appProject = xcodeProject("${rootProjectName}.xcodeproj").projectFile
        appProject.indexTarget.getBuildSettings().HEADER_SEARCH_PATHS == toSpaceSeparatedList(file('src/main/headers'), checkoutDir(repo.name, commit.id.name, repo.id).file('src/main/public'))

        when:
        succeeds ':xcodeProject'

        then:
        result.assertTasksExecuted(":xcodeProjectWorkspaceSettings", ":xcodeScheme", ":xcodeProject")
    }

    @ToBeFixedForConfigurationCache
    def "does not add source dependencies Xcode project of main component to Xcode workspace"() {
        def fixture = new CppAppWithLibrary()

        given:
        buildTestFixture.withBuildInSubDir()
        depProject = singleProjectBuild("greeter") {
            buildFile << """
                apply plugin: 'cpp-library'
                apply plugin: 'xcode'
            """
            fixture.greeter.writeToProject(it)
        }
        def commit = repo.commit('initial commit')

        and:
        settingsFile << """
            sourceControl {
                vcsMappings {
                    withModule("org.test:greeter") {
                        from(GitVersionControlSpec) {
                            url = "${repo.url}"
                        }
                    }
                }
            }
        """
        buildFile << """
            apply plugin: 'cpp-application'
            apply plugin: 'xcode'
            group = 'org.gradle'
            version = '2.0'

            dependencies {
                implementation "org.test:greeter:latest.integration"
            }
        """
        fixture.main.writeToProject(testDirectory)

        when:
        succeeds ':xcode'

        then:
        result.assertTasksExecuted(":xcodeProject", ":xcodeProjectWorkspaceSettings", ":xcodeScheme", ":xcodeWorkspaceWorkspaceSettings", ":xcodeWorkspace", ":xcode")
        rootXcodeWorkspace.contentFile.assertHasProjects("${rootProjectName}.xcodeproj")

        def appProject = xcodeProject("${rootProjectName}.xcodeproj").projectFile
        appProject.indexTarget.getBuildSettings().HEADER_SEARCH_PATHS == toSpaceSeparatedList(file('src/main/headers'), checkoutDir(repo.name, commit.id.name, repo.id).file('src/main/public'))
    }

    @ToBeFixedForConfigurationCache
    def "adds source dependencies C++ module of main component to Xcode indexer search path when no component in root project"() {
        def fixture = new CppAppWithLibrary()

        given:
        buildTestFixture.withBuildInSubDir()
        singleProjectBuild("greeter") {
            buildFile << """
                apply plugin: 'cpp-library'
            """
            fixture.greeter.writeToProject(it)
        }
        def commit = repo.commit('initial commit')

        and:
        singleProjectBuild("app") {
            buildFile << """
                apply plugin: 'cpp-application'
                apply plugin: 'xcode'
                group = 'org.gradle'
                version = '2.0'

                dependencies {
                    implementation "org.test:greeter:latest.integration"
                }
            """
            fixture.main.writeToProject(it)
        }

        and:
        settingsFile << """
            sourceControl {
                vcsMappings {
                    withModule("org.test:greeter") {
                        from(GitVersionControlSpec) {
                            url = "${repo.url}"
                        }
                    }
                }
            }
            include 'app'
        """
        buildFile << """
            apply plugin: 'xcode'
        """

        when:
        succeeds ':xcode'

        then:
        result.assertTasksExecuted(":app:xcodeProject", ":app:xcodeProjectWorkspaceSettings", ":app:xcodeScheme",
            ":xcodeProject", ":xcodeProjectWorkspaceSettings", ":xcodeWorkspaceWorkspaceSettings", ":xcodeWorkspace", ":xcode")
        rootXcodeWorkspace.contentFile.assertHasProjects("${rootProjectName}.xcodeproj", "app/app.xcodeproj")

        def appProject = xcodeProject("app/app.xcodeproj").projectFile
        appProject.indexTarget.getBuildSettings().HEADER_SEARCH_PATHS == toSpaceSeparatedList(file('app/src/main/headers'), checkoutDir(repo.name, commit.id.name, repo.id).file('src/main/public'))
    }

    @ToBeFixedForConfigurationCache
    def "does not add source dependencies Xcode project of main component to Xcode workspace when no component in root project"() {
        def fixture = new CppAppWithLibrary()

        given:
        buildTestFixture.withBuildInSubDir()
        singleProjectBuild("greeter") {
            buildFile << """
                apply plugin: 'cpp-library'
                apply plugin: 'xcode'
            """
            fixture.greeter.writeToProject(it)
        }
        def commit = repo.commit('initial commit')

        and:
        singleProjectBuild("app") {
            buildFile << """
                apply plugin: 'cpp-application'
                apply plugin: 'xcode'
                group = 'org.gradle'
                version = '2.0'

                dependencies {
                    implementation "org.test:greeter:latest.integration"
                }
            """
            fixture.main.writeToProject(it)
        }

        and:
        settingsFile << """
            sourceControl {
                vcsMappings {
                    withModule("org.test:greeter") {
                        from(GitVersionControlSpec) {
                            url = "${repo.url}"
                        }
                    }
                }
            }
            include 'app'
        """
        buildFile << """
            apply plugin: 'xcode'
        """

        when:
        succeeds ':xcode'

        then:
        result.assertTasksExecuted(":app:xcodeProject", ":app:xcodeProjectWorkspaceSettings", ":app:xcodeScheme",
            ":xcodeProject", ":xcodeProjectWorkspaceSettings", ":xcodeWorkspaceWorkspaceSettings", ":xcodeWorkspace", ":xcode")
        rootXcodeWorkspace.contentFile.assertHasProjects("${rootProjectName}.xcodeproj", "app/app.xcodeproj")

        def appProject = xcodeProject("app/app.xcodeproj").projectFile
        appProject.indexTarget.getBuildSettings().HEADER_SEARCH_PATHS == toSpaceSeparatedList(file('app/src/main/headers'), checkoutDir(repo.name, commit.id.name, repo.id).file('src/main/public'))
    }
}
