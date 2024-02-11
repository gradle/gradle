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
import org.gradle.ide.xcode.internal.DefaultXcodeProject
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.nativeplatform.fixtures.app.CppAppWithLibrariesWithApiDependencies
import org.gradle.nativeplatform.fixtures.app.CppAppWithLibrary
import spock.lang.Ignore

import static org.gradle.ide.xcode.internal.XcodeUtils.toSpaceSeparatedList

class XcodeMultipleCppProjectIntegrationTest extends AbstractXcodeIntegrationSpec {
    def setup() {
        useXcodebuildTool()
    }

    @ToBeFixedForConfigurationCache
    def "can create xcode project for C++ application"() {
        given:
        settingsFile << """
            include 'app', 'greeter'
        """

        buildFile << """
            project(':app') {
                apply plugin: 'cpp-application'
                dependencies {
                    implementation project(':greeter')
                }
            }
            project(':greeter') {
                apply plugin: 'cpp-library'
            }
"""
        def app = new CppAppWithLibrary()
        app.greeter.writeToProject(file('greeter'))
        app.main.writeToProject(file('app'))

        when:
        succeeds("xcode")

        then:
        executedAndNotSkipped(":app:xcodeProject", ":app:xcodeProjectWorkspaceSettings", ":app:xcodeScheme", ":app:xcode",
            ":greeter:xcodeProject", ":greeter:xcodeProjectWorkspaceSettings", ":greeter:xcodeScheme", ":greeter:xcode",
            ":xcodeWorkspace", ":xcodeWorkspaceWorkspaceSettings", ":xcode")

        rootXcodeWorkspace.contentFile
            .assertHasProjects("${rootProjectName}.xcodeproj", 'app/app.xcodeproj', 'greeter/greeter.xcodeproj')

        def project = xcodeProject("app/app.xcodeproj").projectFile
        project.indexTarget.getBuildSettings().HEADER_SEARCH_PATHS == toSpaceSeparatedList(file("app/src/main/headers"), file("greeter/src/main/public"))

        when:
        def resultDebugApp = xcodebuild
            .withWorkspace(rootXcodeWorkspace)
            .withScheme('App')
            .succeeds()

        then:
        resultDebugApp.assertTasksExecuted(':greeter:compileDebugCpp', ':greeter:linkDebug',
            ':app:compileDebugCpp', ':app:linkDebug', ':app:installDebug', ':app:_xcode___App_Debug')

        when:
        def resultReleaseApp = xcodebuild
            .withWorkspace(rootXcodeWorkspace)
            .withScheme('App')
            .withConfiguration(DefaultXcodeProject.BUILD_RELEASE)
            .succeeds()

        then:
        resultReleaseApp.assertTasksExecuted(':greeter:compileReleaseCpp', ':greeter:linkRelease', ':greeter:stripSymbolsRelease',
            ':app:compileReleaseCpp', ':app:linkRelease', ':app:stripSymbolsRelease', ':app:installRelease', ':app:_xcode___App_Release')
    }

    @ToBeFixedForConfigurationCache
    def "can create xcode project for C++ application with transitive dependencies"() {
        def app = new CppAppWithLibrariesWithApiDependencies()

        given:
        settingsFile.text = """
            include 'app', 'deck', 'card', 'shuffle'
            rootProject.name = "${rootProjectName}"
        """
        buildFile << """
            project(':app') {
                apply plugin: 'cpp-application'
                dependencies {
                    implementation project(':deck')
                }
            }
            project(':deck') {
                apply plugin: 'cpp-library'
                dependencies {
                    api project(':card')
                    implementation project(':shuffle')
                }
            }
            project(':card') {
                apply plugin: 'cpp-library'
            }
            project(':shuffle') {
                apply plugin: 'cpp-library'
            }
        """
        app.deck.writeToProject(file("deck"))
        app.card.writeToProject(file("card"))
        app.shuffle.writeToProject(file("shuffle"))
        app.main.writeToProject(file("app"))

        when:
        succeeds("xcode")

        then:
        executedAndNotSkipped(":app:xcodeProject", ":app:xcodeProjectWorkspaceSettings", ":app:xcodeScheme", ":app:xcode",
            ":deck:xcodeProject", ":deck:xcodeProjectWorkspaceSettings", ":deck:xcodeScheme", ":deck:xcode",
            ":card:xcodeProject", ":card:xcodeProjectWorkspaceSettings", ":card:xcodeScheme", ":card:xcode",
            ":shuffle:xcodeProject", ":shuffle:xcodeProjectWorkspaceSettings", ":shuffle:xcodeScheme", ":shuffle:xcode",
            ":xcodeWorkspace", ":xcodeWorkspaceWorkspaceSettings", ":xcode")

        rootXcodeWorkspace.contentFile
            .assertHasProjects("${rootProjectName}.xcodeproj", 'app/app.xcodeproj', 'deck/deck.xcodeproj', 'card/card.xcodeproj', 'shuffle/shuffle.xcodeproj')

        def appProject = xcodeProject("app/app.xcodeproj").projectFile
        appProject.indexTarget.getBuildSettings().HEADER_SEARCH_PATHS == toSpaceSeparatedList(file("app/src/main/headers"), file("deck/src/main/public"), file("card/src/main/public"))
        def deckProject = xcodeProject("deck/deck.xcodeproj").projectFile
        deckProject.indexTarget.getBuildSettings().HEADER_SEARCH_PATHS == toSpaceSeparatedList(file("deck/src/main/public"), file("deck/src/main/headers"), file("card/src/main/public"), file("shuffle/src/main/public"))

        when:
        def resultDebugApp = xcodebuild
            .withWorkspace(rootXcodeWorkspace)
            .withScheme('App')
            .succeeds()

        then:
        resultDebugApp.assertTasksExecuted(':shuffle:compileDebugCpp', ':shuffle:linkDebug',
            ':card:compileDebugCpp', ':card:linkDebug',
            ':deck:compileDebugCpp', ':deck:linkDebug',
            ':app:compileDebugCpp', ':app:linkDebug', ':app:installDebug', ':app:_xcode___App_Debug')

        when:
        def resultReleaseHello = xcodebuild
            .withWorkspace(rootXcodeWorkspace)
            .withScheme('Deck')
            .withConfiguration(DefaultXcodeProject.BUILD_RELEASE)
            .succeeds()

        then:
        resultReleaseHello.assertTasksExecuted(':shuffle:compileReleaseCpp', ':shuffle:linkRelease', ':shuffle:stripSymbolsRelease',
            ':card:compileReleaseCpp', ':card:linkRelease', ':card:stripSymbolsRelease',
            ':deck:compileReleaseCpp', ':deck:linkRelease', ':deck:stripSymbolsRelease', ':deck:_xcode___Deck_Release')
    }

    @ToBeFixedForConfigurationCache
    def "can create xcode project for C++ application with binary-specific dependencies"() {
        def app = new CppAppWithLibrariesWithApiDependencies()

        given:
        settingsFile.text = """
            include 'app', 'deck', 'card', 'shuffle'
            rootProject.name = "${rootProjectName}"
        """
        buildFile << """
            project(':app') {
                apply plugin: 'cpp-application'
                dependencies {
                    implementation project(':deck')
                }
            }
            project(':deck') {
                apply plugin: 'cpp-library'
                dependencies {
                    api project(':card')
                }
                library {
                    binaries.configureEach {
                        dependencies {
                            if (targetMachine.operatingSystemFamily.macOs) {
                                implementation project(':shuffle')
                            }
                        }
                    }
                }
            }
            project(':card') {
                apply plugin: 'cpp-library'
            }
            project(':shuffle') {
                apply plugin: 'cpp-library'
            }
        """
        app.deck.writeToProject(file("deck"))
        app.card.writeToProject(file("card"))
        app.shuffle.writeToProject(file("shuffle"))
        app.main.writeToProject(file("app"))

        when:
        succeeds("xcode")

        then:
        executedAndNotSkipped(":app:xcodeProject", ":app:xcodeProjectWorkspaceSettings", ":app:xcodeScheme", ":app:xcode",
            ":deck:xcodeProject", ":deck:xcodeProjectWorkspaceSettings", ":deck:xcodeScheme", ":deck:xcode",
            ":card:xcodeProject", ":card:xcodeProjectWorkspaceSettings", ":card:xcodeScheme", ":card:xcode",
            ":shuffle:xcodeProject", ":shuffle:xcodeProjectWorkspaceSettings", ":shuffle:xcodeScheme", ":shuffle:xcode",
            ":xcodeWorkspace", ":xcodeWorkspaceWorkspaceSettings", ":xcode")

        rootXcodeWorkspace.contentFile
            .assertHasProjects("${rootProjectName}.xcodeproj", 'app/app.xcodeproj', 'deck/deck.xcodeproj', 'card/card.xcodeproj', 'shuffle/shuffle.xcodeproj')

        def appProject = xcodeProject("app/app.xcodeproj").projectFile
        appProject.indexTarget.getBuildSettings().HEADER_SEARCH_PATHS == toSpaceSeparatedList(file("app/src/main/headers"), file("deck/src/main/public"), file("card/src/main/public"))
        def deckProject = xcodeProject("deck/deck.xcodeproj").projectFile
        deckProject.indexTarget.getBuildSettings().HEADER_SEARCH_PATHS == toSpaceSeparatedList(file("deck/src/main/public"), file("deck/src/main/headers"), file("card/src/main/public"), file("shuffle/src/main/public"))

        when:
        def resultDebugApp = xcodebuild
            .withWorkspace(rootXcodeWorkspace)
            .withScheme('App')
            .succeeds()

        then:
        resultDebugApp.assertTasksExecuted(':shuffle:compileDebugCpp', ':shuffle:linkDebug',
            ':card:compileDebugCpp', ':card:linkDebug',
            ':deck:compileDebugCpp', ':deck:linkDebug',
            ':app:compileDebugCpp', ':app:linkDebug', ':app:installDebug', ':app:_xcode___App_Debug')

        when:
        def resultReleaseHello = xcodebuild
            .withWorkspace(rootXcodeWorkspace)
            .withScheme('Deck')
            .withConfiguration(DefaultXcodeProject.BUILD_RELEASE)
            .succeeds()

        then:
        resultReleaseHello.assertTasksExecuted(':shuffle:compileReleaseCpp', ':shuffle:linkRelease', ':shuffle:stripSymbolsRelease',
            ':card:compileReleaseCpp', ':card:linkRelease', ':card:stripSymbolsRelease',
            ':deck:compileReleaseCpp', ':deck:linkRelease', ':deck:stripSymbolsRelease', ':deck:_xcode___Deck_Release')
    }

    @Ignore("https://github.com/gradle/gradle-native-private/issues/274")
    def "can create xcode project for C++ application inside composite build"() {
        given:
        settingsFile.text = """
            includeBuild 'greeter'
            rootProject.name = '${rootProjectName}'
        """
        buildFile << """
            apply plugin: 'cpp-application'
            apply plugin: 'xcode'

            dependencies {
                implementation 'test:greeter:1.3'
            }
        """

        file("greeter/settings.gradle") << "rootProject.name = 'greeter'"
        file('greeter/build.gradle') << """
            apply plugin: 'cpp-library'
            apply plugin: 'xcode'

            group = 'test'
        """

        def app = new CppAppWithLibrary()
        app.greeter.writeToProject(file('greeter'))
        app.main.writeToProject(testDirectory)

        when:
        succeeds("xcode")

        then:
        executedAndNotSkipped(":greeter:xcodeProject", ":greeter:xcodeProjectWorkspaceSettings", ":greeter:xcodeScheme",
            ":xcodeWorkspace", ":xcodeWorkspaceWorkspaceSettings", ":xcode")

        rootXcodeWorkspace.contentFile
            .assertHasProjects("${rootProjectName}.xcodeproj", 'greeter/greeter.xcodeproj')

        def project = rootXcodeProject.projectFile
        project.indexTarget.getBuildSettings().HEADER_SEARCH_PATHS == toSpaceSeparatedList(file("src/main/headers"), file("greeter/src/main/public"))

        when:
        def resultDebugApp = xcodebuild
            .withWorkspace(rootXcodeWorkspace)
            .withScheme('App')
            .succeeds()

        then:
        resultDebugApp.assertTasksExecuted(':greeter:compileDebugCpp', ':greeter:linkDebug',
            ':compileDebugCpp', ':linkDebug', ':installDebug', ':_xcode___App_Debug')

        when:
        def resultReleaseGreeter = xcodebuild
            .withWorkspace(rootXcodeWorkspace)
            .withScheme('Greeter')
            .withConfiguration(DefaultXcodeProject.BUILD_RELEASE)
            .succeeds()

        then:
        resultReleaseGreeter.assertTasksExecuted(':compileReleaseCpp', ':linkRelease', ':stripSymbolsRelease', ':_xcode___Greeter_Release')
    }
}
