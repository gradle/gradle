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

import org.gradle.integtests.fixtures.DefaultTestExecutionResult
import org.gradle.integtests.fixtures.TestExecutionResult
import org.gradle.language.swift.AbstractSwiftMixedLanguageIntegrationTest
import org.gradle.nativeplatform.fixtures.app.CppGreeterFunction
import org.gradle.nativeplatform.fixtures.app.SwiftLibTest
import org.gradle.nativeplatform.fixtures.app.SwiftLibWithCppDep
import org.gradle.nativeplatform.fixtures.app.SwiftLibWithCppDepXCTest
import org.gradle.nativeplatform.fixtures.xctest.XCTestFinderFixture

class SwiftXCTestCppInteroperabilityIntegrationTest extends AbstractSwiftMixedLanguageIntegrationTest {
    def setup() {
        def xcTestFinder = new XCTestFinderFixture(swiftToolChain)
        buildFile << """
            apply plugin: 'xctest'
        """
        buildFile << xcTestFinder.buildscript()
    }

    def "can depend on a c++ library"() {
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
            
            project(':cppGreeter') {
                apply plugin: 'cpp-library'
            }
        """
        lib.writeToProject(testDirectory)
        cppGreeter.asLib().writeToProject(file("cppGreeter"))

        when:
        succeeds("test")

        then:
        result.assertTasksExecuted(":cppGreeter:generateModuleMap", ":cppGreeter:compileDebugCpp", ":cppGreeter:linkDebug",
            ":compileDebugSwift", ":compileTestSwift", ":linkTest", ":installTest", ":xcTest", ":test")
        lib.assertTestCasesRan(testExecutionResult)
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
        result.assertTasksExecuted(":cppGreeter:generateModuleMap", ":cppGreeter:compileDebugCpp", ":cppGreeter:linkDebug",
            ":greeter:compileDebugSwift", ":greeter:linkDebug",
            ":compileDebugSwift", ":compileTestSwift", ":linkTest", ":installTest", ":xcTest", ":test")
    }

    TestExecutionResult getTestExecutionResult() {
        return new DefaultTestExecutionResult(testDirectory, 'build', '', '', 'xcTest')
    }
}
