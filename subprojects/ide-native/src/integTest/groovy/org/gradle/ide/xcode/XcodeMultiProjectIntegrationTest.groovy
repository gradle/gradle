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
import org.gradle.nativeplatform.fixtures.app.CppHelloWorldApp
import org.gradle.nativeplatform.fixtures.app.ExeWithLibraryUsingSwiftLibraryHelloWorldApp
import org.gradle.nativeplatform.fixtures.app.SwiftHelloWorldApp
import org.gradle.util.CollectionUtils

import static org.gradle.ide.xcode.internal.XcodeUtils.toSpaceSeparatedList

class XcodeMultiProjectIntegrationTest extends AbstractXcodeIntegrationSpec {
    def setup() {
        settingsFile << """
            include 'app', 'greeter'
        """
    }

    def "create xcode project Swift executable"() {
        given:
        buildFile << """
            project(':app') {
                apply plugin: 'swift-executable'
                dependencies {
                    implementation project(':greeter')
                }
            }
            project(':greeter') {
                apply plugin: 'swift-library'
            }
        """
        def app = new SwiftHelloWorldApp()
        app.library.writeSources(file('greeter/src/main'))
        app.executable.writeSources(file('app/src/main'))

        when:
        succeeds("xcode")

        then:
        executedAndNotSkipped(":app:xcodeProject", ":app:xcodeProjectWorkspaceSettings", ":app:xcodeSchemeappExecutable", ":app:xcode",
            ":greeter:xcodeProject", ":greeter:xcodeProjectWorkspaceSettings", ":greeter:xcodeSchemegreeterSharedLibrary", ":greeter:xcode",
            ":xcodeWorkspace", ":xcodeWorkspaceWorkspaceSettings", ":xcode")

        xcodeWorkspace("${rootProjectName}.xcworkspace")
            .contentFile.assertHasProjects([file("${rootProjectName}.xcodeproj"), file('app/app.xcodeproj'), file('greeter/greeter.xcodeproj')]*.absolutePath)

        buildSettings(xcodeProject("app/app.xcodeproj").projectFile).SWIFT_INCLUDE_PATHS == toSpaceSeparatedList(file("greeter/build/main/objs"))
    }

    def "create xcode project Swift executable with transitive dependencies"() {
        def app = new ExeWithLibraryUsingSwiftLibraryHelloWorldApp()

        given:
        settingsFile.text =  """
            include 'app', 'greeting', 'hello'
            rootProject.name = "${rootProjectName}"
        """
        buildFile << """
            project(':app') {
                apply plugin: 'swift-executable'
                dependencies {
                    implementation project(':hello')
                }
            }
            project(':hello') {
                apply plugin: 'swift-library'
                dependencies {
                    api project(':greeting')
                }
            }
            project(':greeting') {
                apply plugin: 'swift-library'
            }
        """
        app.library.writeSources(file("hello/src/main"))
        app.greetingsLibrary.writeSources(file("greeting/src/main"))
        app.executable.writeSources(file('app/src/main'))

        when:
        succeeds("xcode")

        then:
        executedAndNotSkipped(":app:xcodeProject", ":app:xcodeProjectWorkspaceSettings", ":app:xcodeSchemeappExecutable", ":app:xcode",
            ":greeting:xcodeProject", ":greeting:xcodeProjectWorkspaceSettings", ":greeting:xcodeSchemegreetingSharedLibrary", ":greeting:xcode",
            ":hello:xcodeProject", ":hello:xcodeProjectWorkspaceSettings", ":hello:xcodeSchemehelloSharedLibrary", ":hello:xcode",
            ":xcodeWorkspace", ":xcodeWorkspaceWorkspaceSettings", ":xcode")

        xcodeWorkspace("${rootProjectName}.xcworkspace")
            .contentFile.assertHasProjects([file("${rootProjectName}.xcodeproj"), file('app/app.xcodeproj'), file('greeting/greeting.xcodeproj'), file('hello/hello.xcodeproj')]*.absolutePath)

        buildSettings(xcodeProject("app/app.xcodeproj").projectFile).SWIFT_INCLUDE_PATHS == toSpaceSeparatedList(file("hello/build/main/objs"), file("greeting/build/main/objs"))
        buildSettings(xcodeProject("hello/hello.xcodeproj").projectFile).SWIFT_INCLUDE_PATHS == toSpaceSeparatedList(file("greeting/build/main/objs"))
    }


    def "create xcode project C++ executable"() {
        given:
        buildFile << """
            project(':app') {
                apply plugin: 'cpp-executable'
                dependencies {
                    implementation project(':greeter')
                }
            }
            project(':greeter') {
                apply plugin: 'cpp-library'
            }
"""
        def app = new CppHelloWorldApp()
        app.library.writeSources(file('greeter/src/main'))
        app.executable.writeSources(file('app/src/main'))

        when:
        succeeds("xcode")

        then:
        executedAndNotSkipped(":app:xcodeProject", ":app:xcodeProjectWorkspaceSettings", ":app:xcodeSchemeappExecutable", ":app:xcode",
            ":greeter:xcodeProject", ":greeter:xcodeProjectWorkspaceSettings", ":greeter:xcodeSchemegreeterSharedLibrary", ":greeter:xcode",
            ":xcodeWorkspace", ":xcodeWorkspaceWorkspaceSettings", ":xcode")

        xcodeWorkspace("${rootProjectName}.xcworkspace")
            .contentFile.assertHasProjects([file("${rootProjectName}.xcodeproj"), file('app/app.xcodeproj'), file('greeter/greeter.xcodeproj')]*.absolutePath)

        buildSettings(xcodeProject("app/app.xcodeproj").projectFile).HEADER_SEARCH_PATHS == toSpaceSeparatedList(file("app/src/main/headers"), file("greeter/src/main/public"))
    }

    def "Gradle project with added xcode plugin are not included in the workspace"() {
        given:
        file('greeter/build.gradle') << """
            apply plugin: 'swift-library'
        """
        file('app/build.gradle') << """
            apply plugin: 'swift-executable'
            dependencies {
                implementation project(':greeter')
            }
        """

        def app = new SwiftHelloWorldApp()
        app.library.writeSources(file('greeter/src/main'))
        app.executable.writeSources(file('app/src/main'))

        when:
        buildFile.text = """
            apply plugin: 'xcode'
            project('app') {
                apply plugin: 'xcode'
            }
        """
        succeeds("xcode")

        then:
        executedAndNotSkipped(":app:xcodeProject", ":app:xcodeProjectWorkspaceSettings", ":app:xcodeSchemeappExecutable", ":app:xcode",
            ":xcodeWorkspace", ":xcodeWorkspaceWorkspaceSettings", ":xcode")

        xcodeWorkspace("${rootProjectName}.xcworkspace")
            .contentFile.assertHasProjects([file("${rootProjectName}.xcodeproj"), file('app/app.xcodeproj')]*.absolutePath)

        when:
        buildFile.text = """
            allprojects {
                apply plugin: 'xcode'
            }
        """
        succeeds("xcode")

        then:
        executedAndNotSkipped(":app:xcodeProject", ":app:xcodeProjectWorkspaceSettings", ":app:xcodeSchemeappExecutable", ":app:xcode",
            ":greeter:xcodeProject", ":greeter:xcodeProjectWorkspaceSettings", ":greeter:xcodeSchemegreeterSharedLibrary", ":greeter:xcode",
            ":xcodeWorkspace", ":xcodeWorkspaceWorkspaceSettings", ":xcode")

        xcodeWorkspace("${rootProjectName}.xcworkspace")
            .contentFile.assertHasProjects([file("${rootProjectName}.xcodeproj"), file('app/app.xcodeproj'), file('greeter/greeter.xcodeproj')]*.absolutePath)
    }

    def "Gradle project with removed xcode plugin are not included in the workspace"() {
        given:
        file('greeter/build.gradle') << """
            apply plugin: 'swift-library'
        """
        file('app/build.gradle') << """
            apply plugin: 'swift-executable'
            dependencies {
                implementation project(':greeter')
            }
        """

        def app = new SwiftHelloWorldApp()
        app.library.writeSources(file('greeter/src/main'))
        app.executable.writeSources(file('app/src/main'))

        when:
        buildFile.text = """
            allprojects {
                apply plugin: 'xcode'
            }
        """
        succeeds("xcode")

        then:
        executedAndNotSkipped(":app:xcodeProject", ":app:xcodeProjectWorkspaceSettings", ":app:xcodeSchemeappExecutable", ":app:xcode",
            ":greeter:xcodeProject", ":greeter:xcodeProjectWorkspaceSettings", ":greeter:xcodeSchemegreeterSharedLibrary", ":greeter:xcode",
            ":xcodeWorkspace", ":xcodeWorkspaceWorkspaceSettings", ":xcode")

        xcodeWorkspace("${rootProjectName}.xcworkspace")
            .contentFile.assertHasProjects([file("${rootProjectName}.xcodeproj"), file('app/app.xcodeproj'), file('greeter/greeter.xcodeproj')]*.absolutePath)

        when:
        buildFile.text = """
            apply plugin: 'xcode'
            project('app') {
                apply plugin: 'xcode'
            }
        """
        succeeds("xcode")

        then:
        executedAndNotSkipped(":app:xcodeProject", ":app:xcodeProjectWorkspaceSettings", ":app:xcodeSchemeappExecutable", ":app:xcode",
            ":xcodeWorkspace", ":xcodeWorkspaceWorkspaceSettings", ":xcode")

        xcodeWorkspace("${rootProjectName}.xcworkspace")
            .contentFile.assertHasProjects([file("${rootProjectName}.xcodeproj"), file('app/app.xcodeproj')]*.absolutePath)

    }

    private static def buildSettings(def project) {
        CollectionUtils.single(project.targets.find(indexTargets()).buildConfigurationList.buildConfigurations).buildSettings
    }
}
