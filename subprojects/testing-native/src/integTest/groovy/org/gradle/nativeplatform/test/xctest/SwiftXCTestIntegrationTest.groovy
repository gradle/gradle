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
import org.gradle.nativeplatform.fixtures.app.SwiftAppWithXCTest
import org.gradle.nativeplatform.fixtures.app.SwiftLibWithXCTest
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
        def lib = new SwiftLibWithXCTest()

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

linkTest.source = project.files(new HashSet(linkTest.source.from)).filter { !it.name.equals("main.o") }
"""
        app.writeToProject(testDirectory)

        when:
        succeeds("test")

        then:
        result.assertTasksExecuted(":compileDebugSwift", ":compileTestSwift", ":linkTest", ":bundleSwiftTest", ":xcTest", ":test")
    }
}
