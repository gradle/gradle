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

import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.language.swift.AbstractSwiftMixedLanguageIntegrationTest
import org.gradle.language.swift.SwiftTaskNames
import org.gradle.nativeplatform.fixtures.AvailableToolChains
import org.gradle.nativeplatform.fixtures.app.CppGreeterFunction
import org.gradle.nativeplatform.fixtures.app.SwiftLibTest
import org.gradle.nativeplatform.fixtures.app.SwiftLibWithCppDep
import org.gradle.nativeplatform.fixtures.app.SwiftLibWithCppDepXCTest
import org.gradle.test.fixtures.file.DoesNotSupportNonAsciiPaths
import org.gradle.test.precondition.Requires

import static org.gradle.test.preconditions.UnitTestPreconditions.HasXCTest

@DoesNotSupportNonAsciiPaths(reason = "Swift sometimes fails when executed from non-ASCII directory")
@Requires(HasXCTest)
class SwiftXCTestCppInteroperabilityIntegrationTest extends AbstractSwiftMixedLanguageIntegrationTest implements XCTestExecutionResult, SwiftTaskNames {
    def setup() {
        buildFile << """
            apply plugin: 'xctest'
        """
    }

    @ToBeFixedForConfigurationCache
    def "can depend on a #linkage.toLowerCase() c++ library"() {
        given:
        def cppGreeter = new CppGreeterFunction()
        def lib = new SwiftLibWithCppDepXCTest(cppGreeter)
        createDirs("cppGreeter")
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
                if (targetMachine.operatingSystemFamily.macOs) {
                    linkTask.get().linkerArgs.add("-lc++")
                } else if (targetMachine.operatingSystemFamily.linux) {
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
                tasks.debug.compile, tasks.test.allToInstall, ":xcTest", ":test")
        lib.assertTestCasesRan(testExecutionResult)

        where:
        linkage << [SHARED, STATIC]
    }

    @ToBeFixedForConfigurationCache
    def "can specify a test dependency on a library with a dependency on a c++ library"() {
        def cppGreeter = new CppGreeterFunction()
        def lib = new SwiftLibWithCppDep(cppGreeter)
        def test = new SwiftLibTest(lib, lib.greeter, lib.sum, lib.multiply)

        given:
        createDirs("greeter", "cppGreeter")
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
            tasks(":greeter").debug.allToLink,
            tasks.debug.compile, tasks.test.allToInstall, ":xcTest", ":test")
    }

    @Override
    AvailableToolChains.InstalledToolChain getToolchainUnderTest() {
        return swiftToolChain
    }
}
