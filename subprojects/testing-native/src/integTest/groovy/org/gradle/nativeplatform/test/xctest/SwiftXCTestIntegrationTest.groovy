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
        result.assertTasksSkipped(":compileTestSwift", ":linkTest", ":createXcTestBundle", ":xcTest", ":test")
    }

    // TODO - Integrate automatically with the Swift library
    def "applying the xctest plugin together with swift-library will not assemble the library by convention"() {
        def lib = new SwiftLib()
        def test = new SwiftXcTestTestApp([
                newTestSuite("FooTestSuite", [
                        newTestCase("testFoo", TestElement.TestCase.Result.PASS)
                ])
        ])

        given:
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
        result.assertTasksSkipped(":compileTestSwift", ":linkTest", ":createXcTestBundle", ":xcTest", ":test")
    }

    // TODO - Integrate automatically with the Swift executable
    def "applying the xctest plugin together with swift-executable will not assemble the executable by convention"() {
        def app = new SwiftApp()
        def test = new SwiftXcTestTestApp([
            newTestSuite("FooTestSuite", [
                newTestCase("testFoo", TestElement.TestCase.Result.PASS)
            ])
        ])

        given:
        buildFile << """
apply plugin: 'swift-executable'
"""
        app.writeToProject(testDirectory)
        test.writeToProject(testDirectory)

        when:
        succeeds("test")

        then:
        executedAndNotSkipped(":compileTestSwift", ":linkTest", ":createXcTestBundle", ":xcTest", ":test")

    }
}
