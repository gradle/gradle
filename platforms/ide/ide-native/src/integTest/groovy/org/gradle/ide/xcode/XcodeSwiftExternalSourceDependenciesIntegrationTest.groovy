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
import org.gradle.nativeplatform.fixtures.app.SwiftAppWithLibrary
import org.gradle.nativeplatform.fixtures.app.SwiftLib
import org.gradle.nativeplatform.fixtures.app.SwiftLibTest
import org.gradle.test.fixtures.file.DoesNotSupportNonAsciiPaths
import org.gradle.vcs.fixtures.GitFileRepository
import org.gradle.vcs.internal.SourceDependencies
import org.junit.Rule

import static org.gradle.ide.xcode.internal.XcodeUtils.toSpaceSeparatedList

@DoesNotSupportNonAsciiPaths(reason = "Swift sometimes fails when executed from non-ASCII directory")
class XcodeSwiftExternalSourceDependenciesIntegrationTest extends AbstractXcodeIntegrationSpec implements SourceDependencies {
    @Rule
    GitFileRepository repo = new GitFileRepository('greeter', temporaryFolder.getTestDirectory())

    def setup() {
        requireSwiftToolChain()
    }

    @ToBeFixedForConfigurationCache
    def "adds source dependencies Swift module of main component to Xcode indexer search path"() {
        def fixture = new SwiftAppWithLibrary()

        given:
        buildTestFixture.withBuildInSubDir()
        singleProjectBuild("greeter") {
            buildFile << """
                apply plugin: 'swift-library'
            """
            fixture.library.writeToProject(it)
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
            apply plugin: 'swift-application'
            apply plugin: 'xcode'
            group = 'org.gradle'
            version = '2.0'

            dependencies {
                implementation "org.test:greeter:latest.integration"
            }
        """
        fixture.executable.writeToProject(testDirectory)

        when:
        succeeds ':xcode'

        then:
        result.assertTasksExecuted(":greeter:compileDebugSwift",
            ":xcodeProject", ":xcodeProjectWorkspaceSettings", ":xcodeScheme",
            ":xcodeWorkspace", ":xcodeWorkspaceWorkspaceSettings", ":xcode")
        rootXcodeWorkspace.contentFile.assertHasProjects("${rootProjectName}.xcodeproj")

        def appProject = xcodeProject("${rootProjectName}.xcodeproj").projectFile
        appProject.indexTarget.getBuildSettings().SWIFT_INCLUDE_PATHS == toSpaceSeparatedList(checkoutDir(repo.name, commit.id.name, repo.id).file('build/modules/main/debug'))

        when:
        succeeds ':xcodeProject'

        then:
        result.assertTasksExecuted(":greeter:compileDebugSwift",
            ":xcodeProject", ":xcodeProjectWorkspaceSettings", ":xcodeScheme")
    }

    @ToBeFixedForConfigurationCache
    def "does not add source dependencies Xcode project of main component to Xcode workspace"() {
        def fixture = new SwiftAppWithLibrary()

        given:
        buildTestFixture.withBuildInSubDir()
        singleProjectBuild("greeter") {
            buildFile << """
                apply plugin: 'swift-library'
                apply plugin: 'xcode'
            """
            fixture.library.writeToProject(it)
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
            apply plugin: 'swift-application'
            apply plugin: 'xcode'
            group = 'org.gradle'
            version = '2.0'

            dependencies {
                implementation "org.test:greeter:latest.integration"
            }
        """
        fixture.executable.writeToProject(testDirectory)

        when:
        succeeds ':xcode'

        then:
        result.assertTasksExecuted(":greeter:compileDebugSwift",
            ":xcodeProject", ":xcodeProjectWorkspaceSettings", ":xcodeScheme",
            ":xcodeWorkspace", ":xcodeWorkspaceWorkspaceSettings", ":xcode")
        rootXcodeWorkspace.contentFile.assertHasProjects("${rootProjectName}.xcodeproj")

        def appProject = xcodeProject("${rootProjectName}.xcodeproj").projectFile
        appProject.indexTarget.getBuildSettings().SWIFT_INCLUDE_PATHS == toSpaceSeparatedList(checkoutDir(repo.name, commit.id.name, repo.id).file('build/modules/main/debug'))
    }

    @ToBeFixedForConfigurationCache
    def "adds source dependencies Swift module of test component to Xcode indexer search path"() {
        def library = new SwiftLib()
        def test = new SwiftLibTest(library, library.greeter, library.sum, library.multiply)

        given:
        buildTestFixture.withBuildInSubDir()
        singleProjectBuild("greeter") {
            buildFile << """
                apply plugin: 'swift-library'
            """
            library.writeToProject(it)
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
            apply plugin: 'swift-library'
            apply plugin: 'xcode'
            apply plugin: 'xctest'

            dependencies {
                testImplementation "org.test:greeter:latest.integration"
            }
        """
        test.writeToProject(testDirectory)

        when:
        succeeds ':xcode'

        then:
        result.assertTasksExecuted(":greeter:compileDebugSwift",
            ":compileDebugSwift",
            ":xcodeProject", ":xcodeProjectWorkspaceSettings", ":xcodeScheme",
            ":xcodeWorkspace", ":xcodeWorkspaceWorkspaceSettings", ":xcode")
        rootXcodeWorkspace.contentFile.assertHasProjects("${rootProjectName}.xcodeproj")

        def appProject = xcodeProject("${rootProjectName}.xcodeproj").projectFile
        appProject.targets.find { it.isUnitTest() }.getBuildSettings().SWIFT_INCLUDE_PATHS == toSpaceSeparatedList(file('build/modules/main/debug'), checkoutDir(repo.name, commit.id.name, repo.id).file('build/modules/main/debug'))
        appProject.indexTarget.getBuildSettings().SWIFT_INCLUDE_PATHS == toSpaceSeparatedList(file('build/modules/main/debug'), checkoutDir(repo.name, commit.id.name, repo.id).file('build/modules/main/debug'))
    }

    @ToBeFixedForConfigurationCache
    def "does not add source dependencies Xcode project of test component to Xcode workspace"() {
        def library = new SwiftLib()
        def test = new SwiftLibTest(library, library.greeter, library.sum, library.multiply)

        given:
        buildTestFixture.withBuildInSubDir()
        singleProjectBuild("greeter") {
            buildFile << """
                apply plugin: 'swift-library'
                apply plugin: 'xcode'
            """
            library.writeToProject(it)
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
            apply plugin: 'swift-library'
            apply plugin: 'xcode'
            apply plugin: 'xctest'

            dependencies {
                testImplementation "org.test:greeter:latest.integration"
            }
        """
        test.writeToProject(testDirectory)

        when:
        succeeds ':xcode'

        then:
        result.assertTasksExecuted(":greeter:compileDebugSwift",
            ":compileDebugSwift",
            ":xcodeProject", ":xcodeProjectWorkspaceSettings", ":xcodeScheme",
            ":xcodeWorkspace", ":xcodeWorkspaceWorkspaceSettings", ":xcode")
        rootXcodeWorkspace.contentFile.assertHasProjects("${rootProjectName}.xcodeproj")

        def appProject = xcodeProject("${rootProjectName}.xcodeproj").projectFile
        appProject.targets.find { it.isUnitTest() }.getBuildSettings().SWIFT_INCLUDE_PATHS == toSpaceSeparatedList(file('build/modules/main/debug'), checkoutDir(repo.name, commit.id.name, repo.id).file('build/modules/main/debug'))
        appProject.indexTarget.getBuildSettings().SWIFT_INCLUDE_PATHS == toSpaceSeparatedList(file('build/modules/main/debug'), checkoutDir(repo.name, commit.id.name, repo.id).file('build/modules/main/debug'))
    }

    @ToBeFixedForConfigurationCache
    def "adds source dependencies Swift module of main component to Xcode indexer search path when no component in root project"() {
        def fixture = new SwiftAppWithLibrary()

        given:
        buildTestFixture.withBuildInSubDir()
        singleProjectBuild("greeter") {
            buildFile << """
                apply plugin: 'swift-library'
            """
            fixture.library.writeToProject(it)
        }
        def commit = repo.commit('initial commit')

        and:
        singleProjectBuild("app") {
            buildFile << """
                apply plugin: 'swift-application'
                apply plugin: 'xcode'
                group = 'org.gradle'
                version = '2.0'

                dependencies {
                    implementation "org.test:greeter:latest.integration"
                }
            """
            fixture.executable.writeToProject(it)
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
        result.assertTasksExecuted(":greeter:compileDebugSwift",
            ":app:xcodeProject", ":app:xcodeProjectWorkspaceSettings", ":app:xcodeScheme",
            ":xcodeProject", ":xcodeProjectWorkspaceSettings",
            ":xcodeWorkspace", ":xcodeWorkspaceWorkspaceSettings", ":xcode")
        rootXcodeWorkspace.contentFile.assertHasProjects("${rootProjectName}.xcodeproj", "app/app.xcodeproj")

        def appProject = xcodeProject("app/app.xcodeproj").projectFile
        appProject.indexTarget.getBuildSettings().SWIFT_INCLUDE_PATHS == toSpaceSeparatedList(checkoutDir(repo.name, commit.id.name, repo.id).file('build/modules/main/debug'))
    }

    @ToBeFixedForConfigurationCache
    def "does not add source dependencies Xcode project of main component to Xcode workspace when no component in root project"() {
        def fixture = new SwiftAppWithLibrary()

        given:
        buildTestFixture.withBuildInSubDir()
        singleProjectBuild("greeter") {
            buildFile << """
                apply plugin: 'swift-library'
                apply plugin: 'xcode'
            """
            fixture.library.writeToProject(it)
        }
        def commit = repo.commit('initial commit')

        and:
        singleProjectBuild("app") {
            buildFile << """
                apply plugin: 'swift-application'
                apply plugin: 'xcode'
                group = 'org.gradle'
                version = '2.0'

                dependencies {
                    implementation "org.test:greeter:latest.integration"
                }
            """
            fixture.executable.writeToProject(it)
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
        result.assertTasksExecuted(":greeter:compileDebugSwift",
            ":app:xcodeProject", ":app:xcodeProjectWorkspaceSettings", ":app:xcodeScheme",
            ":xcodeProject", ":xcodeProjectWorkspaceSettings",
            ":xcodeWorkspace", ":xcodeWorkspaceWorkspaceSettings", ":xcode")
        rootXcodeWorkspace.contentFile.assertHasProjects("${rootProjectName}.xcodeproj", "app/app.xcodeproj")

        def appProject = xcodeProject("app/app.xcodeproj").projectFile
        appProject.indexTarget.getBuildSettings().SWIFT_INCLUDE_PATHS == toSpaceSeparatedList(checkoutDir(repo.name, commit.id.name, repo.id).file('build/modules/main/debug'))
    }
}
