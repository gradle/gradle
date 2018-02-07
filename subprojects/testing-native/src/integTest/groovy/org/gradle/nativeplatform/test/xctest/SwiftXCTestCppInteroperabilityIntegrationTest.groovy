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

package org.gradle.nativeplatform.test.xctest

import org.gradle.language.swift.AbstractSwiftMixedLanguageIntegrationTest
import org.gradle.nativeplatform.fixtures.app.CppGreeterFunction
import org.gradle.nativeplatform.fixtures.app.SwiftLibTest
import org.gradle.nativeplatform.fixtures.app.SwiftLibWithCppDep
import org.gradle.nativeplatform.fixtures.app.SwiftLibWithCppDepXCTest
import spock.lang.Unroll

class SwiftXCTestCppInteroperabilityIntegrationTest extends AbstractSwiftMixedLanguageIntegrationTest implements XCTestExecutionResult {
    def setup() {
        buildFile << """
            apply plugin: 'xctest'
        """
    }

    @Unroll
    def "can depend on a #linkage.toLowerCase() c++ library"() {
        given:
        def cppGreeter = new CppGreeterFunction()
        def lib = new SwiftLibWithCppDepXCTest(cppGreeter)
        settingsFile << """
            rootProject.name = '${lib.projectName}'
            include ':cppGreeter'
        """
        buildFile << """
            apply plugin: 'swift-library'
            
            dependencies {
                implementation project(':cppGreeter')
            }

            [library, xctest]*.binaries*.configureEach {
                if (targetPlatform.operatingSystem.macOsX) {
                    linkTask.get().linkerArgs.add("-lc++")
                } else if (targetPlatform.operatingSystem.linux) {
                    linkTask.get().linkerArgs.add("-lstdc++")
                }
            }
            
            project(':cppGreeter') {
                apply plugin: 'cpp-library'
                library.linkage = [Linkage.${linkage}]
            }
        """
        lib.writeToProject(testDirectory)
        cppGreeter.asLib().writeToProject(file("cppGreeter"))

        when:
        succeeds("test")

        then:
        result.assertTasksExecuted(":cppGreeter:compileDebugCpp", ":cppGreeter:${createOrLink(linkage)}Debug",
            ":compileDebugSwift", ":compileTestSwift", ":linkTest", ":installTest", ":xcTest", ":test")
        lib.assertTestCasesRan(testExecutionResult)

        where:
        linkage << [SHARED, STATIC]
    }

    def "can specify a test dependency on a library with a dependency on a c++ library"() {
        def cppGreeter = new CppGreeterFunction()
        def lib = new SwiftLibWithCppDep(cppGreeter)
        def test = new SwiftLibTest(lib, lib.greeter, lib.sum, lib.multiply)

        given:
        settingsFile << """
            rootProject.name = 'app'
            include 'greeter', 'cppGreeter'
        """
        buildFile << """
            project(':greeter') {
                apply plugin: 'swift-library'
                dependencies {
                    api project(':cppGreeter')
                }
            }
            
            project(':cppGreeter') {
                apply plugin: 'cpp-library'
            }
            
            apply plugin: 'swift-library'
            
            dependencies {
                testImplementation project(':greeter')
            }
        """
        lib.writeToProject(file('greeter'))
        cppGreeter.asLib().writeToProject(file('cppGreeter'))
        test.writeToProject(testDirectory)

        when:
        succeeds("test")

        then:
        result.assertTasksExecuted(":cppGreeter:compileDebugCpp", ":cppGreeter:linkDebug",
            ":greeter:compileDebugSwift", ":greeter:linkDebug",
            ":compileDebugSwift", ":compileTestSwift", ":linkTest", ":installTest", ":xcTest", ":test")
    }
}
