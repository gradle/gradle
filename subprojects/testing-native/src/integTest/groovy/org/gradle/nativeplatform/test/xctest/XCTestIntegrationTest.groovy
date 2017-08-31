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
import org.gradle.nativeplatform.fixtures.app.SwiftLib
import org.gradle.nativeplatform.fixtures.app.SwiftXcTestTestApp
import org.gradle.nativeplatform.fixtures.app.TestElement
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import spock.lang.Ignore

import static org.gradle.nativeplatform.fixtures.app.SourceTestElement.newTestCase
import static org.gradle.nativeplatform.fixtures.app.SourceTestElement.newTestSuite

@Requires([TestPrecondition.SWIFT_SUPPORT, TestPrecondition.MAC_OS_X])
class XCTestIntegrationTest extends AbstractInstalledToolChainIntegrationSpec {

    def setup() {
        settingsFile << "rootProject.name = 'app'"
        buildFile << """
apply plugin: 'xctest'
"""
    }

    def "test tasks are skipped when no source is available"() {
        when:
        succeeds("test")

        then:
        result.assertTasksExecutedInOrder(":compileTestSwift", ":linkTest", ":createXcTestBundle", ":xcTest", ":test")
        result.assertTasksSkipped(":compileTestSwift", ":linkTest", ":createXcTestBundle", ":xcTest", ":test")

    }

    def "test task fail when test cases fail"() {
        def testApp = new SwiftXcTestTestApp([
                newTestSuite("FailingTestSuite", [
                        newTestCase("testFail", TestElement.TestCase.Result.FAIL)
                ])
        ])

        given:
        testApp.writeToProject(testDirectory)

        when:
        fails("test")

        then:
        executedAndNotSkipped(":compileTestSwift", ":linkTest", ":createXcTestBundle", ":xcTest")
        testApp.expectedSummaryOutputPattern.matcher(output).find()
    }

    def "test task succeed when test cases pass"() {
        def testApp = new SwiftXcTestTestApp([
            newTestSuite("PassingTestSuite", [
                newTestCase("testPass", TestElement.TestCase.Result.PASS)
            ])
        ])

        given:
        testApp.writeToProject(testDirectory)

        when:
        succeeds("test")

        then:
        executedAndNotSkipped(":compileTestSwift", ":linkTest", ":createXcTestBundle", ":xcTest", ":test")
        testApp.expectedSummaryOutputPattern.matcher(output).find()
    }

    @Ignore("https://github.com/gradle/gradle-native/issues/94")
    def "doesn't execute removed test suite and case"() {
        def oldTestApp = new SwiftXcTestTestApp([
            newTestSuite("FooTestSuite", [
                newTestCase("testA", TestElement.TestCase.Result.PASS),
                newTestCase("testB", TestElement.TestCase.Result.PASS)
            ]),
            newTestSuite("BarTestSuite", [
                newTestCase("testA", TestElement.TestCase.Result.PASS)
            ])
        ])

        def newTestApp = new SwiftXcTestTestApp([
            newTestSuite("FooTestSuite", [
                newTestCase("testA", TestElement.TestCase.Result.PASS)
            ])
        ])

        given:
        oldTestApp.writeToProject(testDirectory)

        when:
        succeeds("test")

        then:
        executedAndNotSkipped(":compileTestSwift", ":linkTest", ":createXcTestBundle", ":xcTest", ":test")
        oldTestApp.expectedSummaryOutputPattern.matcher(output).find()

        when:
        file("src").deleteDir()
        newTestApp.writeToProject(testDirectory)
        succeeds("test")

        then:
        executedAndNotSkipped(":compileTestSwift", ":linkTest", ":createXcTestBundle", ":xcTest", ":test")
        newTestApp.expectedSummaryOutputPattern.matcher(output).find()
    }

    def "execute added test suite and case"() {
        def oldTestApp = new SwiftXcTestTestApp([
            newTestSuite("FooTestSuite", [
                newTestCase("testA", TestElement.TestCase.Result.PASS)
            ])
        ])

        def newTestApp = new SwiftXcTestTestApp([
            newTestSuite("FooTestSuite", [
                newTestCase("testA", TestElement.TestCase.Result.PASS),
                newTestCase("testB", TestElement.TestCase.Result.PASS)
            ]),
            newTestSuite("BarTestSuite", [
                newTestCase("testA", TestElement.TestCase.Result.PASS)
            ])
        ])

        given:
        oldTestApp.writeToProject(testDirectory)

        when:
        succeeds("test")

        then:
        executedAndNotSkipped(":compileTestSwift", ":linkTest", ":createXcTestBundle", ":xcTest", ":test")
        oldTestApp.expectedSummaryOutputPattern.matcher(output).find()

        when:
        file("src").deleteDir()
        newTestApp.writeToProject(testDirectory)
        succeeds("test")

        then:
        executedAndNotSkipped(":compileTestSwift", ":linkTest", ":createXcTestBundle", ":xcTest", ":test")
        newTestApp.expectedSummaryOutputPattern.matcher(output).find()
    }

    def "test tasks are up-to-date when nothing changes between invocation"() {
        def testApp = new SwiftXcTestTestApp([
            newTestSuite("PassingTestSuite", [
                newTestCase("testPass", TestElement.TestCase.Result.PASS)
            ])
        ])

        given:
        testApp.writeToProject(testDirectory)

        when:
        succeeds("test")
        succeeds("test")

        then:
        executed(":compileTestSwift", ":linkTest", ":createXcTestBundle", ":xcTest", ":test")
        result.assertTasksSkipped(":compileTestSwift", ":linkTest", ":createXcTestBundle", ":xcTest", ":test")
    }

    def "xctest component can specify a dependency on another library"() {
        def lib = new SwiftLib()
        def testApp = new SwiftXcTestTestApp([
            newTestSuite("PassingTestSuite", [
                newTestCase("testPass", TestElement.TestCase.Result.PASS, "XCTAssert(sum(a: 40, b: 2) == 42)")
            ], [], ["Greeter"])
        ])

        given:
        settingsFile << """
include 'Greeter'
"""
        buildFile << """
project(':Greeter') {
    apply plugin: 'swift-library'
}

dependencies {
    testImplementation project(':Greeter')
}
"""
        lib.writeToProject(file('Greeter'))
        testApp.writeToProject(testDirectory)

        when:
        succeeds("test")

        then:
        executedAndNotSkipped(":Greeter:compileDebugSwift", ":compileTestSwift", ":Greeter:linkDebug",
            ":compileTestSwift", ":linkTest", ":createXcTestBundle", ":xcTest", ":test")
    }

    def "assemble task doesn't build or run any of the tests"() {
        def testApp = new SwiftXcTestTestApp([
            newTestSuite("PassingTestSuite", [
                newTestCase("testPass", TestElement.TestCase.Result.PASS)
            ])
        ])

        given:
        testApp.writeToProject(testDirectory)

        when:
        succeeds("assemble")

        then:
        result.assertTasksExecutedInOrder(":assemble")
        result.assertTasksSkipped(":assemble")
    }
}
