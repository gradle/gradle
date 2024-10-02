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

package org.gradle.language.swift

import org.gradle.nativeplatform.fixtures.app.CppGreeterFunction
import org.gradle.nativeplatform.fixtures.app.CppGreeterFunctionUsesLogger
import org.gradle.nativeplatform.fixtures.app.CppGreeterFunctionUsesLoggerApi
import org.gradle.nativeplatform.fixtures.app.CppLogger
import org.gradle.nativeplatform.fixtures.app.CppLoggerWithGreeterApi
import org.gradle.nativeplatform.fixtures.app.SwiftApp
import org.gradle.nativeplatform.fixtures.app.SwiftAppWithDep
import org.gradle.nativeplatform.fixtures.app.SwiftGreeterUsingCppFunction
import org.gradle.nativeplatform.fixtures.app.SwiftMainWithCppDep
import org.gradle.nativeplatform.fixtures.app.SwiftSum
import org.gradle.test.fixtures.file.DoesNotSupportNonAsciiPaths

@DoesNotSupportNonAsciiPaths(reason = "Swift sometimes fails when executed from non-ASCII directory")
class SwiftApplicationCppInteroperabilityIntegrationTest extends AbstractSwiftMixedLanguageIntegrationTest {
    def "can compile and link against a #linkage.toLowerCase() c++ library"() {
        createDirs("app", "cppGreeter")
        settingsFile << "include 'app', 'cppGreeter'"
        def cppGreeter = new CppGreeterFunction()
        def app = new SwiftMainWithCppDep(cppGreeter)

        given:
        buildFile << """
            project(':app') {
                apply plugin: 'swift-application'
                dependencies {
                    implementation project(':cppGreeter')
                }
                application.binaries.configureEach {
                    if (targetMachine.operatingSystemFamily.macOs) {
                        linkTask.get().linkerArgs.add("-lc++")
                    } else if (targetMachine.operatingSystemFamily.linux) {
                        linkTask.get().linkerArgs.add("-lstdc++")
                    }
                }
            }
            project(':cppGreeter') {
                apply plugin: 'cpp-library'
                library.linkage = [Linkage.${linkage}]
            }
        """
        app.writeToProject(file("app"))
        cppGreeter.asLib().writeToProject(file("cppGreeter"))

        expect:
        succeeds ":app:assemble"
        result.assertTasksExecuted(
            ":cppGreeter:compileDebugCpp", ":cppGreeter:${createOrLink(linkage)}Debug",
            ":app:compileDebugSwift", ":app:linkDebug", ":app:installDebug", ":app:assemble")

        installation("app/build/install/main/debug").exec().out == app.expectedOutput

        where:
        linkage << [SHARED, STATIC]
    }

    def "can compile and link against a c++ library with both static and shared linkages"() {
        createDirs("app", "cppGreeter")
        settingsFile << "include 'app', 'cppGreeter'"
        def cppGreeter = new CppGreeterFunction()
        def app = new SwiftMainWithCppDep(cppGreeter)

        given:
        buildFile << """
            project(':app') {
                apply plugin: 'swift-application'
                dependencies {
                    implementation project(':cppGreeter')
                }
            }
            project(':cppGreeter') {
                apply plugin: 'cpp-library'
                library.linkage = [Linkage.STATIC, Linkage.SHARED]
            }
        """
        app.writeToProject(file("app"))
        cppGreeter.asLib().writeToProject(file("cppGreeter"))

        expect:
        succeeds ":app:assemble"
        result.assertTasksExecuted(
            ":cppGreeter:compileDebugSharedCpp", ":cppGreeter:linkDebugShared",
            ":app:compileDebugSwift", ":app:linkDebug", ":app:installDebug", ":app:assemble")

        installation("app/build/install/main/debug").exec().out == app.expectedOutput
    }

    def "can compile and link against a library with a dependency on a #linkage.toLowerCase() c++ library"() {
        createDirs("app", "greeter", "cppGreeter")
        settingsFile << "include 'app', 'greeter', 'cppGreeter'"
        def cppGreeter = new CppGreeterFunction()
        def swiftGreeter = new SwiftGreeterUsingCppFunction(cppGreeter)
        def sumLibrary = new SwiftSum()
        def app = new SwiftAppWithDep(swiftGreeter, sumLibrary)

        given:
        buildFile << """
            project(':app') {
                apply plugin: 'swift-application'
                dependencies {
                    implementation project(':greeter')
                }
            }
            project(':greeter') {
                apply plugin: 'swift-library'
                dependencies {
                    api project(':cppGreeter')
                }
                library.binaries.configureEach {
                    if (targetMachine.operatingSystemFamily.macOs) {
                        linkTask.get().linkerArgs.add("-lc++")
                    } else if (targetMachine.operatingSystemFamily.linux) {
                        linkTask.get().linkerArgs.add("-lstdc++")
                    }
                }
            }
            project(':cppGreeter') {
                apply plugin: 'cpp-library'
                library {
                    linkage = [Linkage.${linkage}]
                    binaries.configureEach {
                        compileTask.get().positionIndependentCode = true
                    }
                }
            }
        """
        swiftGreeter.writeToProject(file("greeter"))
        app.writeToProject(file("app"))
        sumLibrary.writeToProject(file("app"))
        cppGreeter.asLib().writeToProject(file("cppGreeter"))

        expect:
        succeeds ":app:assemble"
        result.assertTasksExecuted(
            ":greeter:compileDebugSwift", ":greeter:linkDebug",
            ":cppGreeter:compileDebugCpp", ":cppGreeter:${createOrLink(linkage)}Debug",
            ":app:compileDebugSwift", ":app:linkDebug", ":app:installDebug", ":app:assemble")

        installation("app/build/install/main/debug").exec().out == app.expectedOutput

        where:
        linkage << [SHARED, STATIC]
    }

    def "can compile and link against a #linkage.toLowerCase() c++ library with a dependency on another c++ library"() {
        createDirs("app", "greeter", "cppGreeter", "logger")
        settingsFile << "include 'app', 'greeter', 'cppGreeter', ':logger'"
        def logger = new CppLogger()
        def cppGreeter = new CppGreeterFunctionUsesLogger()
        def app = new SwiftMainWithCppDep(cppGreeter)

        given:
        buildFile << """
            project(':app') {
                apply plugin: 'swift-application'
                dependencies {
                    implementation project(':cppGreeter')
                }
                application.binaries.configureEach {
                    if (targetMachine.operatingSystemFamily.macOs) {
                        linkTask.get().linkerArgs.add("-lc++")
                    } else if (targetMachine.operatingSystemFamily.linux) {
                        linkTask.get().linkerArgs.add("-lstdc++")
                    }
                }
            }
            project(':cppGreeter') {
                apply plugin: 'cpp-library'
                dependencies {
                    implementation project(':logger')
                }
                library.linkage = [Linkage.${linkage}]
            }
            project(':logger') {
                apply plugin: 'cpp-library'
                library.linkage = [Linkage.${linkage}]
            }
        """
        app.writeToProject(file("app"))
        cppGreeter.asLib().writeToProject(file("cppGreeter"))
        logger.asLib().writeToProject(file("logger"))

        expect:
        succeeds ":app:assemble"
        result.assertTasksExecuted(
            ":cppGreeter:compileDebugCpp", ":cppGreeter:${createOrLink(linkage)}Debug",
            ":logger:compileDebugCpp", ":logger:${createOrLink(linkage)}Debug",
            ":app:compileDebugSwift", ":app:linkDebug", ":app:installDebug", ":app:assemble")

        installation("app/build/install/main/debug").exec().out == app.expectedOutput

        where:
        linkage << [SHARED, STATIC]
    }

    def "can compile and link against a c++ library with an api dependency on another c++ library"() {
        createDirs("app", "greeter", "cppGreeter", "logger")
        settingsFile << "include 'app', 'greeter', 'cppGreeter', ':logger'"
        def logger = new CppLoggerWithGreeterApi()
        def cppGreeter = new CppGreeterFunctionUsesLoggerApi()
        String[] imports = ["cppGreeter", "logger"]
        def app = new SwiftMainWithCppDep(cppGreeter, imports)

        given:
        buildFile << """
            project(':app') {
                apply plugin: 'swift-application'
                dependencies {
                    implementation project(':cppGreeter')
                }
            }
            project(':cppGreeter') {
                apply plugin: 'cpp-library'
                dependencies {
                    api project(':logger')
                }
            }
            project(':logger') {
                apply plugin: 'cpp-library'
            }
        """
        app.writeToProject(file("app"))
        cppGreeter.asLib().writeToProject(file("cppGreeter"))
        logger.asLib().writeToProject(file("logger"))

        expect:
        succeeds ":app:assemble"
        result.assertTasksExecuted(
            ":cppGreeter:compileDebugCpp", ":cppGreeter:linkDebug",
            ":logger:compileDebugCpp", ":logger:linkDebug",
            ":app:compileDebugSwift", ":app:linkDebug", ":app:installDebug", ":app:assemble")

        installation("app/build/install/main/debug").exec().out == app.expectedOutput
    }

    def "declaring a dependency on a c++ library without public headers does not fail"() {
        createDirs("app", "cppGreeter")
        settingsFile << "include 'app', 'cppGreeter'"
        def cppGreeter = new CppGreeterFunction()
        def app = new SwiftApp()

        given:
        buildFile << """
            project(':app') {
                apply plugin: 'swift-application'
                dependencies {
                    implementation project(':cppGreeter')
                }
            }
            project(':cppGreeter') {
                apply plugin: 'cpp-library'
            }
        """
        app.writeToProject(file("app"))
        // writes headers to the private header dir
        cppGreeter.writeToProject(file("cppGreeter"))

        expect:
        succeeds ":app:assemble"
        result.assertTasksExecuted(
            ":cppGreeter:compileDebugCpp", ":cppGreeter:linkDebug",
            ":app:compileDebugSwift", ":app:linkDebug", ":app:installDebug", ":app:assemble")

        installation("app/build/install/main/debug").exec().out == app.expectedOutput
    }

}
