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
import org.gradle.nativeplatform.fixtures.app.CppLogger
import org.gradle.nativeplatform.fixtures.app.SwiftGreeterUsingCppFunction

class SwiftLibraryCppInteroperabilityIntegrationTest extends AbstractSwiftMixedLanguageIntegrationTest {
    def "can compile and link against a c++ library"() {
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
            }
            project(':cppGreeter') {
                apply plugin: 'cpp-library'
            }
        """
        lib.writeToProject(file("hello"))
        cppGreeter.asLib().writeToProject(file("cppGreeter"))

        expect:
        succeeds ":hello:assemble"
        result.assertTasksExecuted(
            ":cppGreeter:generateModuleMap", ":cppGreeter:dependDebugCpp", ":cppGreeter:compileDebugCpp", ":cppGreeter:linkDebug",
            ":hello:compileDebugSwift", ":hello:linkDebug", ":hello:assemble")

        swiftLibrary("hello/build/lib/main/debug/Hello").assertExists()
        cppLibrary("cppGreeter/build/lib/main/debug/cppGreeter").assertExists()
    }

    def "can compile and link against a c++ library with a dependency on a c++ library"() {
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
            }
        """
        lib.writeToProject(file("hello"))
        cppGreeter.asLib().writeToProject(file("cppGreeter"))
        logger.asLib().writeToProject(file("logger"))

        expect:
        succeeds ":hello:assemble"
        result.assertTasksExecuted(
            ":cppGreeter:generateModuleMap", ":cppGreeter:dependDebugCpp", ":cppGreeter:compileDebugCpp", ":cppGreeter:linkDebug",
            ":logger:dependDebugCpp", ":logger:compileDebugCpp", ":logger:linkDebug",
            ":hello:compileDebugSwift", ":hello:linkDebug", ":hello:assemble")

        swiftLibrary("hello/build/lib/main/debug/Hello").assertExists()
        cppLibrary("cppGreeter/build/lib/main/debug/cppGreeter").assertExists()
        cppLibrary("logger/build/lib/main/debug/logger").assertExists()
    }

    def "can override the module name of a c++ library"() {
        settingsFile << "include 'hello', 'cppGreeter'"
        def cppGreeter = new CppGreeterFunction()
        def lib = new SwiftGreeterUsingCppFunction(cppGreeter, "MyGreeter")

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
                
                generateModuleMap.moduleName.set "MyGreeter"
            }
        """
        lib.writeToProject(file("hello"))
        cppGreeter.asLib().writeToProject(file("cppGreeter"))

        expect:
        succeeds ":hello:assemble"
        result.assertTasksExecuted(
            ":cppGreeter:generateModuleMap", ":cppGreeter:dependDebugCpp", ":cppGreeter:compileDebugCpp", ":cppGreeter:linkDebug",
            ":hello:compileDebugSwift", ":hello:linkDebug", ":hello:assemble")

        swiftLibrary("hello/build/lib/main/debug/Hello").assertExists()
        cppLibrary("cppGreeter/build/lib/main/debug/cppGreeter").assertExists()
    }
}
