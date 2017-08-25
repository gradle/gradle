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
import org.gradle.nativeplatform.fixtures.app.SwiftApp
import org.gradle.nativeplatform.fixtures.app.SwiftLib
import org.gradle.nativeplatform.fixtures.app.SwiftXcTestTestApp
import org.gradle.nativeplatform.fixtures.app.TestElement
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition

import static org.gradle.nativeplatform.fixtures.app.SourceTestElement.newTestSuite
import static org.gradle.nativeplatform.fixtures.app.SourceTestElement.newTestCase

@Requires([TestPrecondition.SWIFT_SUPPORT, TestPrecondition.MAC_OS_X])
class SwiftXCTestIntegrationTest extends AbstractInstalledToolChainIntegrationSpec {
    def setup() {
        buildFile << """
apply plugin: 'xctest'
"""
    }

    def "swift-library and xctest plugins behave well together"() {
        given:
        buildFile << """
apply plugin: 'swift-library'
"""

        when:
        succeeds("test")

        then:
        result.assertTasksSkipped(":compileDebugSwift", ":compileTestSwift", ":linkTest", ":createXcTestBundle", ":xcTest", ":test")
    }

    def "xctest plugin can test public and internal feature of a Swift library"() {
        def lib = new SwiftLib()
        def test = new SwiftXcTestTestApp([
                newTestSuite("FooTestSuite", [
                        newTestCase("testInternalMultiply", TestElement.TestCase.Result.PASS,
                            "XCTAssert(multiply(a: 21, b: 2) == ${lib.multiply(21, 2)})"),
                        newTestCase("testPublicSum", TestElement.TestCase.Result.PASS,
                            "XCTAssert(sum(a: 40, b: 2) == ${lib.sum(40, 2)})"),
                ], ["Greeter"])
        ])

        given:
        settingsFile << """
rootProject.name = 'Greeter'
"""
        buildFile << """
apply plugin: 'swift-library'
"""
        lib.writeToProject(testDirectory)
        test.writeToProject(testDirectory)

        when:
        succeeds("test")

        then:
        executedAndNotSkipped(":compileTestSwift", ":linkTest", ":createXcTestBundle", ":xcTest", ":test")
        test.expectedSummaryOutputPattern.matcher(output).find()
    }

    def "swift-executable and xctest plugins behave well together"() {
        given:
        buildFile << """
apply plugin: 'swift-executable'
"""

        when:
        succeeds("test")

        then:
        result.assertTasksSkipped(":compileDebugSwift", ":compileTestSwift", ":linkTest", ":createXcTestBundle", ":xcTest", ":test")
    }

    def "xctest plugin can test public and internal feature of a Swift executable"() {
        def app = new SwiftApp()
        def test = new SwiftXcTestTestApp([
            newTestSuite("FooTestSuite", [
                newTestCase("testInternalMultiply", TestElement.TestCase.Result.PASS,
                    "XCTAssert(multiply(a: 21, b: 2) == ${app.multiply.multiply(21, 2)})"),
                newTestCase("testPublicSum", TestElement.TestCase.Result.PASS,
                    "XCTAssert(sum(a: 40, b: 2) == ${app.sum.sum(40, 2)})"),
            ], ["App"])
        ])

        given:
        settingsFile << "rootProject.name = 'App'"
        buildFile << """
apply plugin: 'swift-executable'

linkTest.source = project.files(new HashSet(linkTest.source.from)).filter { !it.name.equals("main.o") }
"""
        app.writeToProject(testDirectory)
        test.writeToProject(testDirectory)

        when:
        succeeds("test")

        then:
        executedAndNotSkipped(":compileDebugSwift", ":compileTestSwift", ":linkTest", ":createXcTestBundle", ":xcTest", ":test")

    }
}
