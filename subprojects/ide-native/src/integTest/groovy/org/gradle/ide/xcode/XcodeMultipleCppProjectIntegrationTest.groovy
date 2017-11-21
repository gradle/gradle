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
import org.gradle.nativeplatform.fixtures.app.CppAppWithLibrariesWithApiDependencies
import org.gradle.nativeplatform.fixtures.app.CppAppWithLibrary
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition

import static org.gradle.ide.xcode.internal.XcodeUtils.toSpaceSeparatedList

@Requires(TestPrecondition.XCODE)
class XcodeMultipleCppProjectIntegrationTest extends AbstractXcodeIntegrationSpec {
    def setup() {
        useXcodebuildTool()
    }

    def "can create xcode project for C++ executable"() {
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
        executedAndNotSkipped(":app:xcodeProject", ":app:xcodeProjectWorkspaceSettings", ":app:xcodeSchemeAppExecutable", ":app:xcode",
            ":greeter:xcodeProject", ":greeter:xcodeProjectWorkspaceSettings", ":greeter:xcodeSchemeGreeterSharedLibrary", ":greeter:xcode",
            ":xcodeWorkspace", ":xcodeWorkspaceWorkspaceSettings", ":xcode")

        rootXcodeWorkspace.contentFile
            .assertHasProjects("${rootProjectName}.xcodeproj", 'app/app.xcodeproj', 'greeter/greeter.xcodeproj')

        def project = xcodeProject("app/app.xcodeproj").projectFile
        project.indexTarget.getBuildSettings().HEADER_SEARCH_PATHS == toSpaceSeparatedList(file("app/src/main/headers"), file("greeter/src/main/public"))

        when:
        def resultDebugApp = xcodebuild
            .withWorkspace(rootXcodeWorkspace)
            .withScheme('App Executable')
            .succeeds()

        then:
        resultDebugApp.assertTasksExecuted(':greeter:dependDebugCpp', ':greeter:compileDebugCpp', ':greeter:linkDebug',
            ':app:dependDebugCpp', ':app:compileDebugCpp', ':app:linkDebug', ':app:_xcode___App_Debug')

        when:
        def resultReleaseApp = xcodebuild
            .withWorkspace(rootXcodeWorkspace)
            .withScheme('App Executable')
            .withConfiguration(DefaultXcodeProject.BUILD_RELEASE)
            .succeeds()

        then:
        resultReleaseApp.assertTasksExecuted(':greeter:dependReleaseCpp', ':greeter:compileReleaseCpp', ':greeter:linkRelease', ':greeter:extractSymbolsRelease', ':greeter:stripSymbolsRelease',
            ':app:dependReleaseCpp', ':app:compileReleaseCpp', ':app:linkRelease', ':app:_xcode___App_Release')
    }

    def "can create xcode project for C++ executable with transitive dependencies"() {
        def app = new CppAppWithLibrariesWithApiDependencies()

        given:
        settingsFile.text =  """
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
        executedAndNotSkipped(":app:xcodeProject", ":app:xcodeProjectWorkspaceSettings", ":app:xcodeSchemeAppExecutable", ":app:xcode",
            ":deck:xcodeProject", ":deck:xcodeProjectWorkspaceSettings", ":deck:xcodeSchemeDeckSharedLibrary", ":deck:xcode",
            ":card:xcodeProject", ":card:xcodeProjectWorkspaceSettings", ":card:xcodeSchemeCardSharedLibrary", ":card:xcode",
            ":shuffle:xcodeProject", ":shuffle:xcodeProjectWorkspaceSettings", ":shuffle:xcodeSchemeShuffleSharedLibrary", ":shuffle:xcode",
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
            .withScheme('App Executable')
            .succeeds()

        then:
        resultDebugApp.assertTasksExecuted(':shuffle:dependDebugCpp', ':shuffle:compileDebugCpp', ':shuffle:linkDebug',
            ':card:dependDebugCpp', ':card:compileDebugCpp', ':card:linkDebug',
            ':deck:dependDebugCpp', ':deck:compileDebugCpp', ':deck:linkDebug',
            ':app:dependDebugCpp', ':app:compileDebugCpp', ':app:linkDebug', ':app:_xcode___App_Debug')

        when:
        def resultReleaseHello = xcodebuild
            .withWorkspace(rootXcodeWorkspace)
            .withScheme('Deck SharedLibrary')
            .withConfiguration(DefaultXcodeProject.BUILD_RELEASE)
            .succeeds()

        then:
        resultReleaseHello.assertTasksExecuted(':shuffle:dependReleaseCpp', ':shuffle:compileReleaseCpp', ':shuffle:linkRelease', ':shuffle:extractSymbolsRelease', ':shuffle:stripSymbolsRelease',
            ':card:dependReleaseCpp', ':card:compileReleaseCpp', ':card:linkRelease', ':card:extractSymbolsRelease', ':card:stripSymbolsRelease',
            ':deck:dependReleaseCpp', ':deck:compileReleaseCpp', ':deck:linkRelease', ':deck:_xcode___Deck_Release')
    }

    def "can create xcode project for C++ executable inside composite build"() {
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
        executedAndNotSkipped(":greeter:xcodeProject", ":greeter:xcodeProjectWorkspaceSettings", ":greeter:xcodeSchemeGreeterSharedLibrary",
            ":xcodeWorkspace", ":xcodeWorkspaceWorkspaceSettings", ":xcode")

        rootXcodeWorkspace.contentFile
            .assertHasProjects("${rootProjectName}.xcodeproj", 'greeter/greeter.xcodeproj')

        def project = rootXcodeProject.projectFile
        project.indexTarget.getBuildSettings().HEADER_SEARCH_PATHS == toSpaceSeparatedList(file("src/main/headers"), file("greeter/src/main/public"))

        when:
        def resultDebugApp = xcodebuild
            .withWorkspace(rootXcodeWorkspace)
            .withScheme('App Executable')
            .succeeds()

        then:
        resultDebugApp.assertTasksExecuted(':greeter:dependDebugCpp', ':greeter:compileDebugCpp', ':greeter:linkDebug',
            ':dependDebugCpp', ':compileDebugCpp', ':linkDebug', ':_xcode___App_Debug')

        when:
        def resultReleaseGreeter = xcodebuild
            .withWorkspace(rootXcodeWorkspace)
            .withScheme('Greeter SharedLibrary')
            .withConfiguration(DefaultXcodeProject.BUILD_RELEASE)
            .succeeds()

        then:
        resultReleaseGreeter.assertTasksExecuted(':dependReleaseCpp', ':compileReleaseCpp', ':linkRelease', ':_xcode___Greeter_Release')
    }
}
