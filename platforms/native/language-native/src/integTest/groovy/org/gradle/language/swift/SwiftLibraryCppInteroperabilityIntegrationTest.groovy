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

import org.gradle.nativeplatform.fixtures.NativeBinaryFixture
import org.gradle.nativeplatform.fixtures.app.CppGreeterFunction
import org.gradle.nativeplatform.fixtures.app.CppGreeterFunctionUsesLogger
import org.gradle.nativeplatform.fixtures.app.CppLogger
import org.gradle.nativeplatform.fixtures.app.SwiftGreeterUsingCppFunction
import org.gradle.test.fixtures.file.DoesNotSupportNonAsciiPaths

@DoesNotSupportNonAsciiPaths(reason = "Swift sometimes fails when executed from non-ASCII directory")
class SwiftLibraryCppInteroperabilityIntegrationTest extends AbstractSwiftMixedLanguageIntegrationTest {

    def "can compile and link against a #linkage.toLowerCase() c++ library"() {
        createDirs("hello", "cppGreeter")
        settingsFile << "include 'hello', 'cppGreeter'"
        def cppGreeter = new CppGreeterFunction()
        def lib = new SwiftGreeterUsingCppFunction(cppGreeter)

        given:
        buildFile << """
            project(':hello') {
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
                library.linkage = [Linkage.${linkage}]
                library {
                    linkage = [Linkage.${linkage}]
                    binaries.configureEach {
                        compileTask.get().positionIndependentCode = true
                    }
                }
            }
        """
        lib.writeToProject(file("hello"))
        cppGreeter.asLib().writeToProject(file("cppGreeter"))

        expect:
        succeeds ":hello:assemble"
        result.assertTasksExecuted(
            ":cppGreeter:compileDebugCpp", ":cppGreeter:${createOrLink(linkage)}Debug",
            ":hello:compileDebugSwift", ":hello:linkDebug", ":hello:assemble")

        swiftLibrary("hello/build/lib/main/debug/Hello").assertExists()
        cppLibrary(linkage, "cppGreeter/build/lib/main/debug/cppGreeter").assertExists()

        where:
        linkage << [SHARED, STATIC]
    }

    def "can compile and link against a c++ library with a dependency on a #linkage.toLowerCase() c++ library"() {
        createDirs("hello", "cppGreeter", "logger")
        settingsFile << "include 'hello', 'cppGreeter', 'logger'"
        def cppGreeter = new CppGreeterFunctionUsesLogger()
        def logger = new CppLogger()
        def lib = new SwiftGreeterUsingCppFunction(cppGreeter)

        given:
        buildFile << """
            project(':hello') {
                apply plugin: 'swift-library'
                dependencies {
                    api project(':cppGreeter')
                }
            }
            project(':cppGreeter') {
                apply plugin: 'cpp-library'
                dependencies {
                    implementation project(':logger')
                }
            }
            project(':logger') {
                apply plugin: 'cpp-library'
                library {
                    linkage = [Linkage.${linkage}]
                    binaries.configureEach {
                        compileTask.get().positionIndependentCode = true
                    }
                }
            }
        """
        lib.writeToProject(file("hello"))
        cppGreeter.asLib().writeToProject(file("cppGreeter"))
        logger.asLib().writeToProject(file("logger"))

        expect:
        succeeds ":hello:assemble"
        result.assertTasksExecuted(
            ":cppGreeter:compileDebugCpp", ":cppGreeter:linkDebug",
            ":logger:compileDebugCpp", ":logger:${createOrLink(linkage)}Debug",
            ":hello:compileDebugSwift", ":hello:linkDebug", ":hello:assemble")

        swiftLibrary("hello/build/lib/main/debug/Hello").assertExists()
        cppLibrary("cppGreeter/build/lib/main/debug/cppGreeter").assertExists()
        cppLibrary(linkage,"logger/build/lib/main/debug/logger").assertExists()

        where:
        linkage << [SHARED, STATIC]
    }

    NativeBinaryFixture cppLibrary(String linkage, String path) {
        if (linkage == STATIC) {
            return staticCppLibrary(path)
        }

        if (linkage == SHARED) {
            return cppLibrary(path)
        }
    }
}
