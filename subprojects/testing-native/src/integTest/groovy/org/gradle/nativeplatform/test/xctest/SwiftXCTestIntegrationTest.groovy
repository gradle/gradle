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

import org.gradle.nativeplatform.fixtures.AbstractInstalledToolChainIntegrationSpec
import org.gradle.nativeplatform.fixtures.NativeBinaryFixture
import org.gradle.nativeplatform.fixtures.app.SwiftAppWithSingleXCTestSuite
import org.gradle.nativeplatform.fixtures.app.SwiftAppWithXCTest
import org.gradle.nativeplatform.fixtures.app.SwiftLibWithXCTestWithInfoPlist
import org.gradle.nativeplatform.fixtures.app.SwiftSingleFileLibWithSingleXCTestSuite
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition

@Requires([TestPrecondition.SWIFT_SUPPORT, TestPrecondition.MAC_OS_X])
class SwiftXCTestIntegrationTest extends AbstractInstalledToolChainIntegrationSpec {
    def setup() {
        buildFile << """
apply plugin: 'xctest'
"""
    }

    def "can apply swift-library and xctest plugins together"() {
        given:
        buildFile << """
apply plugin: 'swift-library'
"""

        when:
        succeeds("test")

        then:
        result.assertTasksExecuted(":compileDebugSwift", ":compileTestSwift", ":linkTest", ":bundleSwiftTest", ":xcTest", ":test")
        result.assertTasksSkipped(":compileDebugSwift", ":compileTestSwift", ":linkTest", ":bundleSwiftTest", ":xcTest", ":test")
    }

    def "can test public and internal features of a Swift library"() {
        def lib = new SwiftLibWithXCTestWithInfoPlist()

        given:
        settingsFile << "rootProject.name = 'greeter'"
        buildFile << """
apply plugin: 'swift-library'
"""
        lib.writeToProject(testDirectory)

        when:
        succeeds("test")

        then:
        result.assertTasksExecuted(":compileDebugSwift", ":compileTestSwift", ":linkTest", ":bundleSwiftTest", ":xcTest", ":test")
        lib.assertTestCasesRan(output)
    }

    def "can apply swift-executable and xctest plugins together"() {
        given:
        buildFile << """
apply plugin: 'swift-executable'
"""

        when:
        succeeds("test")

        then:
        result.assertTasksExecuted(":compileDebugSwift", ":compileTestSwift", ":linkTest", ":bundleSwiftTest", ":xcTest", ":test")
        result.assertTasksSkipped(":compileDebugSwift", ":compileTestSwift", ":linkTest", ":bundleSwiftTest", ":xcTest", ":test")
    }

    def "can test public and internal features of a Swift executable"() {
        def app = new SwiftAppWithXCTest()

        given:
        settingsFile << "rootProject.name = 'app'"
        buildFile << """
apply plugin: 'swift-executable'
"""
        app.writeToProject(testDirectory)

        when:
        succeeds("test")

        then:
        result.assertTasksExecuted(":compileDebugSwift", ":compileTestSwift", ":linkTest", ":bundleSwiftTest", ":xcTest", ":test")
    }

    def "can test features of a Swift executable using a single test source file"() {
        def app = new SwiftAppWithSingleXCTestSuite()

        given:
        settingsFile << "rootProject.name = 'app'"
        buildFile << """
apply plugin: 'swift-executable'
"""
        app.writeToProject(testDirectory)

        when:
        succeeds("test")

        then:
        result.assertTasksExecuted(":compileDebugSwift", ":compileTestSwift", ":linkTest", ":bundleSwiftTest", ":xcTest", ":test")
        assertMainSymbolIsAbsent(objectFiles(app.test, "build/obj/test"))
        assertMainSymbolIsAbsent(machOBundle("build/bundle/main/debug/App"))
    }

    def "can test features of a single file Swift library using a single test source file"() {
        def lib = new SwiftSingleFileLibWithSingleXCTestSuite()

        given:
        settingsFile << "rootProject.name = 'greeter'"
        buildFile << """
apply plugin: 'swift-library'
"""
        lib.writeToProject(testDirectory)

        when:
        succeeds("test")

        then:
        result.assertTasksExecuted(":compileDebugSwift", ":compileTestSwift", ":linkTest", ":bundleSwiftTest", ":xcTest", ":test")
        assertMainSymbolIsAbsent(objectFiles(lib.test, "build/obj/test"))
        assertMainSymbolIsAbsent(machOBundle("build/bundle/main/debug/Greeter"))
    }

    private void assertMainSymbolIsAbsent(List<NativeBinaryFixture> binaries) {
        binaries.each {
            assertMainSymbolIsAbsent(it)
        }
    }

    private void assertMainSymbolIsAbsent(NativeBinaryFixture binary) {
        assert !binary.binaryInfo.listSymbols().contains('_main')
    }
}
