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
import org.gradle.nativeplatform.fixtures.app.IncrementalSwiftXCTestAddDiscoveryBundle
import org.gradle.nativeplatform.fixtures.app.IncrementalSwiftXCTestRemoveDiscoveryBundle
import org.gradle.nativeplatform.fixtures.app.SwiftFailingXCTestBundle
import org.gradle.nativeplatform.fixtures.app.SwiftLibWithXCTest
import org.gradle.nativeplatform.fixtures.app.SwiftXCTestBundle
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import spock.lang.Unroll

@Requires([TestPrecondition.SWIFT_SUPPORT, TestPrecondition.MAC_OS_X])
class XCTestIntegrationTest extends AbstractInstalledToolChainIntegrationSpec {

    def setup() {
        settingsFile << "rootProject.name = 'app'"
        buildFile << """
apply plugin: 'xctest'
"""
    }

    def "skips test tasks when no source is available"() {
        when:
        succeeds("test")

        then:
        result.assertTasksExecuted(":compileTestSwift", ":linkTest", ":bundleSwiftTest", ":xcTest", ":test")
        result.assertTasksSkipped(":compileTestSwift", ":linkTest", ":bundleSwiftTest", ":xcTest", ":test")
    }

    def "fails when test cases fail"() {
        def testBundle = new SwiftFailingXCTestBundle()

        given:
        testBundle.writeToProject(testDirectory)

        when:
        fails("test")

        then:
        result.assertTasksExecuted(":compileTestSwift", ":linkTest", ":bundleSwiftTest", ":xcTest")
        testBundle.assertTestCasesRan(output)
    }

    def "succeeds when test cases pass"() {
        def testBundle = new SwiftXCTestBundle()

        given:
        testBundle.writeToProject(testDirectory)

        when:
        succeeds("test")

        then:
        result.assertTasksExecuted(":compileTestSwift", ":linkTest", ":bundleSwiftTest", ":xcTest", ":test")
        testBundle.assertTestCasesRan(output)
    }

    @Unroll
    def "runs tests when #task lifecycle task executes"() {
        def testBundle = new SwiftXCTestBundle()

        given:
        testBundle.writeToProject(testDirectory)

        when:
        succeeds(task)

        then:
        executed(":xcTest")
        testBundle.assertTestCasesRan(output)

        where:
        task << ["test", "check", "build"]
    }

    def "doesn't execute removed test suite and case"() {
        def testBundle = new IncrementalSwiftXCTestRemoveDiscoveryBundle()

        given:
        testBundle.writeToProject(testDirectory)

        when:
        succeeds("test")

        then:
        result.assertTasksExecuted(":compileTestSwift", ":linkTest", ":bundleSwiftTest", ":xcTest", ":test")
        testBundle.expectedSummaryOutputPattern.matcher(output).find()

        when:
        testBundle.applyChangesToProject(testDirectory)
        succeeds("test")

        then:
        result.assertTasksExecuted(":compileTestSwift", ":linkTest", ":bundleSwiftTest", ":xcTest", ":test")
        testBundle.expectedAlternateSummaryOutputPattern.matcher(output).find()
    }

    def "executes added test suite and case"() {
        def testBundle = new IncrementalSwiftXCTestAddDiscoveryBundle()

        given:
        testBundle.writeToProject(testDirectory)

        when:
        succeeds("test")

        then:
        result.assertTasksExecuted(":compileTestSwift", ":linkTest", ":bundleSwiftTest", ":xcTest", ":test")
        testBundle.expectedSummaryOutputPattern.matcher(output).find()

        when:
        testBundle.applyChangesToProject(testDirectory)
        succeeds("test")

        then:
        result.assertTasksExecuted(":compileTestSwift", ":linkTest", ":bundleSwiftTest", ":xcTest", ":test")
        testBundle.expectedAlternateSummaryOutputPattern.matcher(output).find()
    }

    def "skips test tasks as up-to-date when nothing changes between invocation"() {
        def testBundle = new SwiftXCTestBundle()

        given:
        testBundle.writeToProject(testDirectory)

        when:
        succeeds("test")
        succeeds("test")

        then:
        result.assertTasksExecuted(":compileTestSwift", ":linkTest", ":bundleSwiftTest", ":xcTest", ":test")
        result.assertTasksSkipped(":compileTestSwift", ":linkTest", ":bundleSwiftTest", ":xcTest", ":test")
    }

    def "can specify a test dependency on another library"() {
        def lib = new SwiftLibWithXCTest()

        given:
        settingsFile << """
include 'greeter'
"""
        buildFile << """
project(':greeter') {
    apply plugin: 'swift-library'
}

dependencies {
    testImplementation project(':greeter')
}
"""
        lib.lib.writeToProject(file('greeter'))
        lib.test.writeToProject(testDirectory)

        when:
        succeeds("test")

        then:
        result.assertTasksExecuted(":greeter:compileDebugSwift", ":greeter:linkDebug", ":compileTestSwift", ":linkTest", ":bundleSwiftTest", ":xcTest", ":test")
    }

    def "does not build or run any of the tests when assemble task executes"() {
        def testBundle = new SwiftFailingXCTestBundle()

        given:
        testBundle.writeToProject(testDirectory)

        when:
        succeeds("assemble")

        then:
        result.assertTasksExecuted(":assemble")
        result.assertTasksSkipped(":assemble")
    }

    def "build logic can change source layout convention"() {
        def testBundle = new SwiftXCTestBundle()

        given:
        testBundle.writeToSourceDir(file("Tests"))
        file("src/test/swift/broken.swift") << "ignore me!"

        and:
        buildFile << """
            xctest {
                source.from 'Tests'
                resourceDir.set(file('Tests'))
            }
         """

        expect:
        succeeds "test"
        result.assertTasksExecuted(":compileTestSwift", ":linkTest", ":bundleSwiftTest", ":xcTest", ":test")

        file("build/obj/test").assertIsDir()
        executable("build/exe/test/AppTest").assertExists()
        testBundle.assertTestCasesRan(output)
    }
}
